package app.cerbere.somfy

import org.json.JSONArray
import org.json.JSONObject

/** Somfy Protect's `security_level`. Night mode is `partial` (confirmed live by the Homey app). */
enum class Level(val raw: String, val label: String) {
    DISARMED("disarmed", "Désactivée"),
    PARTIAL("partial", "Mode nuit"),
    ARMED("armed", "Activée");

    companion object {
        fun of(raw: String?): Level? = entries.firstOrNull { it.raw == raw }
    }
}

data class Site(val id: String, val name: String, val level: Level?)

/** Pure JSON reading, kept apart from the HTTP code so it can be unit-tested. */
object SomfyJson {
    fun sites(body: String): List<Site> {
        val trimmed = body.trim()
        val items: JSONArray = if (trimmed.startsWith("[")) JSONArray(trimmed)
        else JSONObject(trimmed).optJSONArray("items") ?: JSONArray()
        return List(items.length()) { site(items.getJSONObject(it)) }
    }

    fun site(o: JSONObject) = Site(
        id = o.getString("site_id"),
        name = o.optString("label").ifBlank { o.optString("name") }.ifBlank { "Maison" },
        level = Level.of(o.optString("security_level")),
    )

    /** A websocket message: the site and its new level for `security.level.change`, null otherwise. */
    fun levelChange(message: String): Pair<String, Level>? {
        val o = runCatching { JSONObject(message) }.getOrNull() ?: return null
        if (o.optString("key") != "security.level.change") return null
        val level = Level.of(o.optString("security_level")) ?: return null
        val site = o.optString("site_id").ifBlank { return null }
        return site to level
    }

    fun isTokenError(message: String): Boolean =
        runCatching { JSONObject(message).optString("key") }.getOrNull() == "websocket.error.token"
}
