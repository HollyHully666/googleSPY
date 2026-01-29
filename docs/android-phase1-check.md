# Проверка папки android на соответствие 030-android-specific

## Правила 030-android-specific

- **API:** Target SDK 34 (Android 14+), min 28.
- **Service:** ForegroundService с NotificationChannel.
- **Reconnect:** Handler.postDelayed для retry.
- **Capture:** Останавливай стрим после 30s без активности.

---

## Что проверено и соответствует

| Параметр | Файл | Статус |
|----------|------|--------|
| minSdk 28 | `app/build.gradle.kts` | ✓ |
| targetSdk 34 | `app/build.gradle.kts` | ✓ |
| compileSdk 34 | `app/build.gradle.kts` | ✓ |
| Kotlin | `libs.versions.toml`, плагины | ✓ |
| Jetpack Compose | `app/build.gradle.kts` (buildFeatures.compose, зависимости) | ✓ |
| MainActivity на Compose | `MainActivity.kt` (ComponentActivity, setContent, Compose UI) | ✓ |
| Только wss (без cleartext) | `AndroidManifest.xml` (usesCleartextTraffic="false") | ✓ добавлено при проверке |

---

## Что будет нужно на Фазе 3 (не на Фазе 1)

Правила про **ForegroundService**, **Reconnect** и **Capture** относятся к коду и манифесту, которые добавляются на Фазе 3:

- **AndroidManifest.xml:** разрешение `FOREGROUND_SERVICE`, объявление `<service>` с `android:foregroundServiceType` (например, `camera` / `microphone` / `specialUse` по типам захвата).
- **Код:** ForegroundService с NotificationChannel, Handler.postDelayed для reconnect (например, 5000 ms), остановка стрима через 30s без активности.
- **Зависимости:** OkHttp (WebSocket) — добавить в `app/build.gradle.kts` на Фазе 3.

На Фазе 1 пустой проект эти пункты не требуются.

---

## Структура проекта

- **android/build.gradle.kts** — корневой плагин (application, kotlin-compose apply false). Ок.
- **android/settings.gradle.kts** — имя "GoogleSPY", include(":app"). Ок.
- **android/gradle/libs.versions.toml** — AGP 9.0.0, Kotlin 2.0.21, Compose BOM. Ок.
- **android/gradle-wrapper.properties** — Gradle 9.1.0. Ок.
- **android/app/** — исходники, манифест, ресурсы. Ок.

Итог: начальные параметры (SDK, Kotlin, Compose, запрет cleartext) соответствуют 030-android-specific; остальное по правилам будет добавляться на Фазе 3.
