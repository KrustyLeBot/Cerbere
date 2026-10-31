# Cerbère

Android app for a **Somfy Protect** alarm (Home Alarm, Home Alarm Advanced). Part of the personal
app suite (Synchrotron, Griffonote, Ecoutify, Météorite): same look, same update flow. UI in French.

## What it does

- **Three states, one tap**: Désactivée, Mode nuit, Activée — in the app and in the home-screen
  widget "les trois têtes", where the logo's three dogs are the three buttons and the active one
  lights up in its color.
- **Night mode is never forgotten**: while the alarm is armed or in night mode, a notification
  stays (put back if swiped away) with a "Désactiver" button, and every time the phone is unlocked a full-screen prompt asks to disarm (needs "display
  over other apps"; without it the prompt comes as a notification). It can be turned off in Settings.
- **Live**: changes made anywhere (keypad, Somfy's app, another phone) show within a second.

## In the car (Android Auto)

Cerbère also shows up in Android Auto as a media app with two tiles, **DÉPART** (orange) and **ARRIVÉE**
(green). A tap calls a webhook you set in Settings → Android Auto (URL, GET or POST + JSON body, test
button), typically a Homey Flow that arms the alarm, checks and closes the garage door, etc. It does **not**
go through the Somfy API. The tile subtitle shows the last result (`14:32:07 — OK (200)`).

- Android Auto only admits approved app categories, none of which is "a button that calls a URL", so the app
  presents itself as a media app whose two "tracks" are the actions (`car/CarMediaService`).
- The URLs open the garage, so they are stored encrypted (Keystore), apart from the Somfy login. HTTPS only.
- Rules that keep it working: never request audio focus, never reach `STATE_PLAYING` (so Spotify keeps
  playing and the steering-wheel keys stay with it), no media notification; items are `FLAG_PLAYABLE`
  (browsable ones get prefetched and would fire by themselves); feedback goes through `STATE_ERROR`, the
  only channel Android Auto reads without opening a "now playing" screen that never ends; the manifest
  metadata uses `android:resource`, never `android:value` (the app would be invisible).
- Android Auto needs "Unknown sources" in its developer settings for an app installed outside the Play
  Store; that setting resets when Android Auto updates. After changing discovery metadata, uninstall,
  reinstall, force-stop Android Auto and reconnect. The desktop head unit emulator tests the grid.

## How it talks to Somfy

There is no public Somfy Protect API: Cerbère uses the one of Somfy's own app, reverse-engineered by
the community and proven live by the Homey app (`KrustyLeBot/zigbidouillehomeyapp`, see its
`app/lib/somfy-*.js` and `FINDINGS.md`). It can break if Somfy changes it.

- OAuth2 password grant on `sso.myfox.io` with the official app's client id; tokens kept and
  refreshed, the password grant only runs at login or when the refresh token is dead, and never
  twice within 10 minutes.
- `GET /v3/site/{id}` reads `security_level` (`disarmed`, `partial` = night mode, `armed`);
  `PUT /v3/site/{id}/security` writes it with the body field **`status`**.
- `wss://websocket.myfox.io/events/websocket?token=…` pushes `security.level.change`; a foreground
  service keeps it open (ping every 15 s, reconnect backoff 5 s → 1 min, token refresh on
  `websocket.error.token`). A read every 15 minutes is the safety net.
- Pacing: reads younger than 20 s are served from memory, a 429/503 stops every call for the delay
  Somfy asks (30 s to 15 min), calls are serialized.

Use a **guest** account created for Cerbère in the Somfy Protect app: it can arm and disarm (by
design, guests must be able to switch the alarm off) but cannot change the installation or users.
Email, password and tokens are encrypted with an Android Keystore key and never leave the phone
except to Somfy.

## Code

```
app/src/main/java/app/cerbere/
  car/        CarMediaService (Android Auto tiles), Webhooks (store, HTTP sender)
  somfy/      SomfyApi (HTTP, tokens, pacing), SomfyJson (parsing, unit-tested), AlarmRepository (state)
  service/    GuardService (websocket + poll + unlock), Notifications, Reminder, ActionReceiver, BootReceiver
  widget/     AlarmWidget (RemoteViews)
  ui/         Login, Home, Settings, ReminderActivity, theme
  suite/      the suite kit, copied from Synchrotron (updates, banners, settings cards, dialogs)
  security/   Vault (Keystore-encrypted login)
```

## Builds and updates

Every push to `main` builds a signed release APK and publishes it as the rolling `latest` release
(`cerbere.apk`, "Cerbère build N"). The app checks it on open and installs it from a banner. See
[docs/RELEASE.md](docs/RELEASE.md) for the signing key. The build refuses to run without the key:
there is no unsigned or debug release.
