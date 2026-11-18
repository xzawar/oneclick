---
name: oneclick-android
description: Use when writing, testing, or reviewing code under android/ in One Click — Kotlin, Jetpack Compose screens, ViewModels, Repositories, Retrofit, Room, DataStore, Hilt, Google Maps, Agora calls, FCM, location foreground service.
---

# One Click Android rules

Detail: `docs/AGENTS.md` §3, screens/flows in `docs/DESIGN.md`. UI tokens → also load `oneclick-design`.

## Stack
Kotlin 2.x (DEFAULT — spec said 1.9, Compose now needs 2.x compiler plugin), single Activity, Jetpack Compose + Material 3 + Navigation Compose, Hilt, coroutines + Flow (no RxJava), ViewModel + `StateFlow<UiState>`, Retrofit + OkHttp, Room (lists/history), DataStore (settings), Glide (Compose integration) , Maps Compose, Agora Voice SDK, FCM, Socket.io client.

## Patterns
- `sealed interface UiState<out T> { Loading; Success(data); Error(message) }`.
- Repository returns `Flow`/`Result`; ViewModel never touches Retrofit directly.
- DTOs = `data class ...Dto` mirroring API JSON (names from `oneclick-naming`).
- Money = `Long` rupees. Display via one formatter (`Rs. 1,250`).
- Mode toggle in app state; every API call adds `X-Active-Mode` via OkHttp interceptor.

## Security
- JWT: encrypt with Android Keystore key (Tink AEAD) → store ciphertext in DataStore. Never plain SharedPreferences.
- Selfie: camera only (in-app CameraX preferred over `ACTION_IMAGE_CAPTURE`; never gallery).
- No tokens/phone/CNIC in Logcat (use a redacting logger; strip logs in release).
- Location: foreground service type `location` only while online or on booking; ping 5s active / 20s idle; stop when offline.

## Must handle
Socket disconnect → "Reconnecting…" banner + re-sync booking state. FCM high-priority for `new_job_request` and incoming call (full-screen intent). Urdu RTL (`values-ur/strings.xml`), sp sizes from theme, 48dp touch targets, `contentDescription` on icons.

## Banned
Flutter, Hive, XML layouts, Fragments, RxJava, masked numbers.
