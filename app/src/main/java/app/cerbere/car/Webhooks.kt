package app.cerbere.car

import android.content.Context
import android.os.Handler
import android.os.Looper
import app.cerbere.security.Vault
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/** The two car tiles. The store, the settings card and the Android Auto grid all derive from this. */
enum class ActionId(val key: String, val label: String) {
    DEPART("depart", "DÉPART"),
    ARRIVEE("arrivee", "ARRIVÉE");

    companion object {
        fun fromKey(key: String?): ActionId? = entries.firstOrNull { it.key == key }
    }
}

data class ActionConfig(val id: ActionId, val url: String, val post: Boolean, val body: String) {
    val isConfigured: Boolean get() = url.isNotBlank()
}

/**
 * The webhook URLs open the garage door, so they are a secret: kept in their own Keystore-encrypted
 * file, separate from the Somfy login (logging out of Somfy must not wipe them, and the car service
 * must work without a Somfy session). Read fresh on every tap, so an edit applies at once.
 */
class WebhookStore(context: Context) {
    // One Vault per process: it caches what it read, so the settings screen and the car service must share it.
    private val vault = shared(context.applicationContext)
    private val last = context.applicationContext.getSharedPreferences("carhooks_last", Context.MODE_PRIVATE)

    fun load(id: ActionId) = ActionConfig(
        id = id,
        url = vault["${id.key}_url"].orEmpty().trim(),
        post = vault["${id.key}_post"] == "1",
        body = vault["${id.key}_body"].orEmpty(),
    )

    fun save(config: ActionConfig) {
        val k = config.id.key
        vault.put("${k}_url" to config.url.trim(), "${k}_post" to if (config.post) "1" else "0", "${k}_body" to config.body)
    }

    private companion object {
        @Volatile private var instance: Vault? = null
        fun shared(context: Context): Vault = instance ?: synchronized(this) { instance ?: Vault(context, "carhooks").also { instance = it } }
    }

    fun lastResult(id: ActionId): String? = last.getString(id.key, null)

    fun setLastResult(id: ActionId, text: String) {
        last.edit().putString(id.key, text).apply()
    }
}

data class WebhookResult(val ok: Boolean, val message: String)

/** Plain HttpURLConnection, off the main thread: a webhook needs a request and a status code, nothing more. */
object WebhookClient {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val clock = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun fire(config: ActionConfig, onResult: (WebhookResult) -> Unit) {
        if (!config.isConfigured) {
            onResult(WebhookResult(false, "Aucune URL configurée pour ${config.id.label}"))
            return
        }
        executor.execute {
            val result = runCatching { send(config) }
                .getOrElse { WebhookResult(false, "${clock.format(Date())} — échec : ${it.message}") }
            main.post { onResult(result) }
        }
    }

    private fun send(config: ActionConfig): WebhookResult {
        val connection = (URL(config.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Cerbere/1.0")
        }
        try {
            if (config.post) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                val payload = config.body.ifBlank { "{}" }.toByteArray(Charsets.UTF_8)
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setFixedLengthStreamingMode(payload.size)
                connection.outputStream.use { it.write(payload) }
            } else {
                connection.requestMethod = "GET"
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val snippet = stream?.bufferedReader()?.use { it.readText() }?.trim()?.take(120).orEmpty()
            val time = clock.format(Date())
            return if (code in 200..299) WebhookResult(true, "$time — OK ($code)")
            else WebhookResult(false, "$time — HTTP $code $snippet".trim())
        } finally {
            connection.disconnect()
        }
    }
}
