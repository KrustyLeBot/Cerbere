package app.cerbere.car

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.MediaBrowserServiceCompat
import app.cerbere.R

/**
 * The Android Auto surface: a media app whose two "tracks" are really webhooks (DÉPART, ARRIVÉE).
 * Android Auto only admits approved app categories and has none for "a button that calls a URL".
 *
 * Three load-bearing rules, so that whatever is playing keeps playing:
 *  1. never call requestAudioFocus() — the app plays nothing and has no reason to ask;
 *  2. never reach STATE_PLAYING — the most recently active session owns media key routing,
 *     steering wheel buttons included, and we must not steal it;
 *  3. no MediaStyle notification, no foreground service of its own — stay out of the player stack.
 *
 * The session still has to be active, otherwise Android Auto refuses to talk to the service.
 * Items are FLAG_PLAYABLE, not browsable: onPlayFromMediaId is called on an explicit tap and only
 * then, whereas Android Auto prefetches the children of browsable nodes (the gate would open by
 * itself) and caches them (a second tap would do nothing).
 */
class CarMediaService : MediaBrowserServiceCompat() {

    private lateinit var session: MediaSessionCompat
    private lateinit var store: WebhookStore
    private val idle = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        store = WebhookStore(this)
        session = MediaSessionCompat(this, "CerbereCar").apply {
            setPlaybackState(stoppedState())
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) = handleTap(mediaId)

                // None of these may start anything.
                override fun onPlay() = Unit
                override fun onPause() = Unit
                override fun onStop() = Unit
            })
            isActive = true
        }
        sessionToken = session.sessionToken
    }

    override fun onDestroy() {
        idle.removeCallbacksAndMessages(null)
        session.isActive = false
        session.release()
        super.onDestroy()
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot {
        val extras = Bundle().apply {
            putBoolean(CONTENT_STYLE_SUPPORTED, true)
            putInt(CONTENT_STYLE_BROWSABLE_HINT, CONTENT_STYLE_GRID)
            putInt(CONTENT_STYLE_PLAYABLE_HINT, CONTENT_STYLE_GRID)
        }
        return BrowserRoot(ROOT_ID, extras)
    }

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaItem>>) {
        result.sendResult(if (parentId == ROOT_ID) ActionId.entries.map(::mediaItem).toMutableList() else mutableListOf())
    }

    private fun mediaItem(id: ActionId): MediaItem {
        val subtitle = if (!store.load(id).isConfigured) "URL à configurer dans Cerbère" else store.lastResult(id).orEmpty()
        val description = MediaDescriptionCompat.Builder()
            .setMediaId(id.key)
            .setTitle(id.label)
            .setSubtitle(subtitle)
            .setIconUri(iconUri(id))
            .build()
        return MediaItem(description, MediaItem.FLAG_PLAYABLE)
    }

    private fun iconUri(id: ActionId): Uri {
        val res = if (id == ActionId.DEPART) R.drawable.car_depart else R.drawable.car_arrivee
        return Uri.parse("android.resource://$packageName/$res")
    }

    private fun handleTap(mediaId: String?) {
        val action = ActionId.fromKey(mediaId) ?: return
        // Answer SYNCHRONOUSLY, before returning: with no usable state Android Auto opens "now playing"
        // and waits for a STATE_PLAYING that never comes (endless spinner). STATE_ERROR is the only
        // channel it reads without opening the player, so it carries our feedback.
        feedback("${action.label} — envoi…")
        WebhookClient.fire(store.load(action)) { result ->
            store.setLastResult(action, result.message)
            feedback("${action.label} : ${result.message}")
            notifyChildrenChanged(ROOT_ID)
            idle.removeCallbacksAndMessages(null)
            idle.postDelayed({ session.setPlaybackState(stoppedState()) }, 5000L)
        }
    }

    private fun feedback(message: String) {
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID)
                .setState(PlaybackStateCompat.STATE_ERROR, 0L, 0f)
                .setErrorMessage(PlaybackStateCompat.ERROR_CODE_APP_ERROR, message)
                .build(),
        )
    }

    private fun stoppedState(): PlaybackStateCompat = PlaybackStateCompat.Builder()
        .setActions(PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID)
        .setState(PlaybackStateCompat.STATE_STOPPED, 0L, 0f)
        .build()

    private companion object {
        const val ROOT_ID = "cerbere_car_root"
        const val CONTENT_STYLE_SUPPORTED = "android.media.browse.CONTENT_STYLE_SUPPORTED"
        const val CONTENT_STYLE_BROWSABLE_HINT = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
        const val CONTENT_STYLE_PLAYABLE_HINT = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
        const val CONTENT_STYLE_GRID = 2
    }
}
