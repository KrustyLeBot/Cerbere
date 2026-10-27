package app.cerbere.somfy

import android.util.Base64
import app.cerbere.security.Vault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

open class SomfyException(message: String, val status: Int = 0) : IOException(message)

/** The saved login no longer works: the user has to type it again. */
class LoginRequired(message: String) : SomfyException(message, 401)

/**
 * Somfy Protect's cloud API, the one the official app uses (unofficial: reverse-engineered by the
 * community; endpoints, fields and pacing come from the Homey app, where they are proven live).
 *
 * Gentle on purpose, so the account never looks like abuse:
 * - the password login only runs when typed, or when the refresh token is dead, and never twice
 *   within [LOGIN_SPACING_MS];
 * - a site read younger than [READ_CACHE_MS] is answered from memory;
 * - a 429 (or any "slow down" answer) stops every call until the delay Somfy asked for has passed.
 * Calls are serialized: two screens asking at once make one request.
 */
class SomfyApi(private val http: OkHttpClient, private val vault: Vault) {
    private val lock = Mutex()
    private var lastLogin = 0L
    private var blockedUntil = 0L
    private var cachedSite: Site? = null
    private var cachedAt = 0L

    val loggedIn: Boolean get() = vault[Vault.EMAIL] != null && vault[Vault.PASSWORD] != null

    /** Logs in with the password grant and returns the account's sites. */
    suspend fun login(email: String, password: String): List<Site> = lock.withLock {
        passwordGrant(email, password)
        vault.put(Vault.EMAIL to email, Vault.PASSWORD to password)
        SomfyJson.sites(authorized("GET", "/v3/site"))
    }

    fun chooseSite(site: Site) {
        vault.put(Vault.SITE_ID to site.id, Vault.SITE_NAME to site.name)
        cachedSite = null
    }

    val email: String? get() = vault[Vault.EMAIL]
    val siteId: String? get() = vault[Vault.SITE_ID]
    val siteName: String? get() = vault[Vault.SITE_NAME]

    fun logout() {
        vault.clear()
        cachedSite = null
    }

    /** The chosen site, from memory when it was read less than [READ_CACHE_MS] ago (unless [force]d). */
    suspend fun site(force: Boolean = false): Site = lock.withLock {
        val id = siteId ?: throw LoginRequired("Aucune installation choisie")
        val now = System.currentTimeMillis()
        cachedSite?.takeIf { now - cachedAt < (if (force) MIN_READ_SPACING_MS else READ_CACHE_MS) }?.let { return it }
        val site = SomfyJson.site(JSONObject(authorized("GET", "/v3/site/$id")))
        cachedSite = site
        cachedAt = now
        site
    }

    /** Arms, disarms or switches to night mode. The body field is `status`, not `security_level`. */
    suspend fun setLevel(level: Level) = lock.withLock {
        val id = siteId ?: throw LoginRequired("Aucune installation choisie")
        authorized("PUT", "/v3/site/$id/security", JSONObject().put("status", level.raw).toString().toRequestBody(JSON))
        cachedSite = cachedSite?.copy(level = level)
        cachedAt = System.currentTimeMillis()
    }

    /** Remembers a level pushed by the websocket, so the next read can come from memory. */
    fun pushed(level: Level) {
        cachedSite = cachedSite?.copy(level = level)
        cachedAt = System.currentTimeMillis()
    }

    /** A valid access token (refreshed when it expired), for the websocket. */
    suspend fun accessToken(forceRefresh: Boolean = false): String = lock.withLock { token(forceRefresh) }

    // ---- internals (call with the lock held) ----

    private suspend fun token(forceRefresh: Boolean = false): String {
        val access = vault[Vault.ACCESS]
        val expires = vault[Vault.EXPIRES_AT]?.toLongOrNull() ?: 0
        if (!forceRefresh && access != null && System.currentTimeMillis() < expires) return access
        val refresh = vault[Vault.REFRESH]
        if (refresh != null) {
            try {
                grant(FormBody.Builder().add("grant_type", "refresh_token").add("refresh_token", refresh))
                return vault[Vault.ACCESS]!!
            } catch (e: LoginRequired) {
                // Dead refresh token (invalid_grant): fall back to the saved password below.
            }
        }
        val email = vault[Vault.EMAIL] ?: throw LoginRequired("Connecte-toi à Somfy")
        val password = vault[Vault.PASSWORD] ?: throw LoginRequired("Connecte-toi à Somfy")
        if (System.currentTimeMillis() - lastLogin < LOGIN_SPACING_MS) throw SomfyException("Somfy refuse la session ; nouvel essai dans quelques minutes")
        passwordGrant(email, password)
        return vault[Vault.ACCESS]!!
    }

    private suspend fun passwordGrant(email: String, password: String) {
        lastLogin = System.currentTimeMillis()
        grant(FormBody.Builder().add("grant_type", "password").add("username", email).add("password", password))
    }

    private suspend fun grant(form: FormBody.Builder) {
        val body = form.add("client_id", CLIENT_ID).add("client_secret", CLIENT_SECRET).build()
        val json = JSONObject(send(Request.Builder().url("https://$SSO_HOST$TOKEN_PATH").post(body), auth = true))
        val expiresIn = json.optLong("expires_in", 3600)
        vault.put(
            Vault.ACCESS to json.getString("access_token"),
            Vault.REFRESH to (json.optString("refresh_token").ifBlank { null } ?: vault[Vault.REFRESH]),
            // A margin so a token never expires in the middle of a request.
            Vault.EXPIRES_AT to (System.currentTimeMillis() + (expiresIn - 60) * 1000).toString(),
        )
    }

    private suspend fun authorized(method: String, path: String, body: RequestBody? = null): String {
        fun request(token: String) = Request.Builder().url("https://$API_HOST$path")
            .header("Authorization", "Bearer $token").method(method, body)
        return try {
            send(request(token()))
        } catch (e: SomfyException) {
            // Rejected although it looked valid (clock drift, revoked): one retry with a fresh token.
            if (e.status != 401) throw e
            send(request(token(forceRefresh = true)))
        }
    }

    private suspend fun send(builder: Request.Builder, auth: Boolean = false): String = withContext(Dispatchers.IO) {
        val wait = blockedUntil - System.currentTimeMillis()
        if (wait > 0) throw SomfyException("Somfy demande de patienter (${wait / 1000 + 1} s)", 429)
        http.newCall(builder.header("User-Agent", "Somfy Protect").build()).execute().use { r ->
            val text = r.body?.string().orEmpty()
            when {
                r.isSuccessful -> text
                r.code == 429 || r.code == 503 -> {
                    val seconds = r.header("Retry-After")?.toLongOrNull() ?: 60
                    blockedUntil = System.currentTimeMillis() + seconds.coerceIn(30, 900) * 1000
                    throw SomfyException("Somfy demande de patienter (${seconds} s)", r.code)
                }
                auth && (r.code == 400 || r.code == 401) ->
                    throw LoginRequired(if (text.contains("invalid_grant")) "Email ou mot de passe refusé par Somfy" else "Connexion refusée par Somfy")
                r.code == 401 -> throw SomfyException("Session Somfy expirée", 401)
                else -> throw SomfyException("Somfy a répondu ${r.code}", r.code)
            }
        }
    }

    companion object {
        private const val SSO_HOST = "sso.myfox.io"
        private const val TOKEN_PATH = "/oauth/oauth/v2/token"
        private const val API_HOST = "api.myfox.io"
        private val JSON = "application/json".toMediaType()

        const val READ_CACHE_MS = 20_000L
        private const val MIN_READ_SPACING_MS = 5_000L
        private const val LOGIN_SPACING_MS = 10 * 60_000L

        // The official app's own OAuth client (same for every install), not a secret of ours.
        private val CLIENT_ID = decode(
            "ODRlZGRmNDgtMmI4ZS0xMWU1LWIyYTUtMTI0Y2ZhYjI1NTk1XzQ3NWJ1cXJmOHY4a2d3b280Z293MDhna2tjMGNrODA0ODh3bzQ0czhvNDhzZzg0azQw"
        )
        private val CLIENT_SECRET = decode("NGRzcWZudGlldTB3Y2t3d280MGt3ODQ4Z3c0bzBjOGs0b3djODBrNGdvMGNzMGs4NDQ=")

        private fun decode(b64: String) = String(Base64.decode(b64, Base64.DEFAULT))

        const val WEBSOCKET = "wss://websocket.myfox.io/events/websocket"
    }
}
