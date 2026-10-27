package app.cerbere.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The Somfy login (email, password, session tokens, chosen site), encrypted at rest with an
 * AES-256-GCM key that lives in the Android Keystore and cannot be extracted. The password is kept
 * so a dead refresh token can be replaced without asking again, the way Somfy's own app does.
 */
class Vault(context: Context) {
    private val file = File(context.noBackupFilesDir, "somfy.bin")

    @Volatile private var loaded = false
    @Volatile private var cached: Map<String, String> = emptyMap()

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    @Synchronized
    fun all(): Map<String, String> {
        if (!loaded) {
            cached = decrypt()
            loaded = true
        }
        return cached
    }

    operator fun get(key: String): String? = all()[key]

    /** Merges [values] in; a null value removes the key. */
    @Synchronized
    fun put(vararg values: Pair<String, String?>) {
        val next = all().toMutableMap()
        values.forEach { (k, v) -> if (v == null) next.remove(k) else next[k] = v }
        if (next == cached) return
        val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val ct = cipher.doFinal(JSONObject(next as Map<*, *>).toString().toByteArray())
        val tmp = File(file.parentFile, "somfy.tmp")
        tmp.writeBytes(byteArrayOf(cipher.iv.size.toByte()) + cipher.iv + ct)
        tmp.renameTo(file)
        cached = next
    }

    @Synchronized
    fun clear() {
        file.delete()
        cached = emptyMap()
        loaded = true
    }

    private fun decrypt(): Map<String, String> {
        if (!file.exists()) return emptyMap()
        return try {
            val raw = file.readBytes()
            val ivLen = raw[0].toInt()
            val iv = raw.copyOfRange(1, 1 + ivLen)
            val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
            val json = JSONObject(String(cipher.doFinal(raw, 1 + ivLen, raw.size - 1 - ivLen)))
            json.keys().asSequence().associateWith { json.getString(it) }
        } catch (e: Exception) {
            // Keystore key gone (backup restored on another phone): the login has to be typed again.
            emptyMap()
        }
    }

    companion object {
        private const val ALIAS = "cerbere-vault"
        private const val TRANSFORM = "AES/GCM/NoPadding"

        const val EMAIL = "email"
        const val PASSWORD = "password"
        const val ACCESS = "access_token"
        const val REFRESH = "refresh_token"
        const val EXPIRES_AT = "expires_at"
        const val SITE_ID = "site_id"
        const val SITE_NAME = "site_name"
    }
}
