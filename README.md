# Yougram

Клиент Telegram для Android на TDLib. Только Android, minSdk 31 (Android 12), Kotlin + Jetpack Compose.

## Что работает в первой версии
- Вход: номер телефона, код, облачный пароль (2FA)
- Список чатов (обновляется в реальном времени)
- Экран чата: чтение истории, получение и отправка текстовых сообщений

## Запуск
1. Получите `api_id` и `api_hash` на https://my.telegram.org (API development tools).
2. Скопируйте `local.properties.example` в `local.properties` и впишите `TG_API_ID`, `TG_API_HASH` (и `sdk.dir`, если Android Studio не создала файл сама).
3. Откройте проект в Android Studio, дождитесь Gradle Sync (студия предложит создать gradle wrapper), запустите на устройстве с Android 12+.

## Структура
```
app/src/main/java/app/yougram/
├── YougramApp.kt, MainActivity.kt
├── data/
│   ├── TelegramClient.kt    # единственная обёртка над TDLib (tdl-coroutines)
│   ├── AuthRepository.kt    # шаги авторизации
│   ├── ChatRepository.kt    # чаты, история, отправка
│   └── AppContainer.kt      # ручной DI
└── ui/
    ├── YougramNavHost.kt    # навигация, завязана на состояние авторизации
    ├── auth/  chats/  chat/ # экран + ViewModel
    └── theme/
```

## TDLib
Используется `dev.g000sha256:tdl-coroutines` — готовая сборка TDLib с корутинным API. Версия указана в `gradle/libs.versions.toml`; проверьте актуальную на Maven Central.
TDLib в минорных релизах меняет сигнатуры методов — если после обновления версии что-то не компилируется, смотрите `data/`: весь код TDLib находится только там.

## Дальше
Медиа и аватары, push-уведомления, поиск, пагинация истории и чатов, регистрация нового аккаунта, QR-вход.

## Звонки, браузер, баннеры
- **Звонки**: `NTgCallsEngine` теперь реально подключён в `AppContainer` (раньше стоял `NoCallEngine` и звука не было). Добавлены маршрут звука (динамик / громкая связь / Bluetooth, `CallAudio`), фоновая служба с уведомлением и рингтоном (`CallService`), гашение экрана у уха, запрос микрофона при ответе на входящий.
- **Встроенный браузер**: `ui/browser/BrowserScreen.kt` (WebView). Ссылки в сообщениях кликабельны (`ui/Links.kt`), переключатель в «Режим призрака и шпион». Только http(s), http поднимается до https.
- **Баннеры профиля**: 12 бит (палитра, узор, градиент) лежат в bio сразу после невидимой метки Yougram (`data/YougramBanner.kt`). Другие клиенты Telegram их не видят и не рисуют. Редактор: «Режим призрака и шпион» → «Баннер профиля».

## C++ плагины

Плагины Yougram — нативные библиотеки (`.so`), которые загружаются через JNI (`plugin/NativePluginManager.kt` + `src/main/cpp/yougram_plugin_host.cpp`).
Интерфейс — чистый C ABI: `src/main/cpp/include/yougram_plugin.h`, пример — `src/main/cpp/example_hello.cpp`.

Пакет `.ygplugin` — zip:

```
manifest.json
lib/arm64-v8a/libmyplugin.so
lib/armeabi-v7a/libmyplugin.so   (по желанию, другие ABI)
```

```json
{ "id": "com.example.my", "name": "My plugin", "version": "1.0.0", "api": "1.0",
  "author": "me", "description": "...", "entry": "libmyplugin.so", "permissions": [] }
```

Плагин экспортирует `yougram_plugin_entry()` и получает `on_load / on_enable / on_disable / on_event(event, json)`.
События: `message_received`, `message_edited`, `message_deleted`; тело — JSON (для `message_received` — все поля `PluginMessage`, `content` — рекурсивно сконвертированный объект TDLib, поэтому новые типы сообщений доступны без изменения API).
Из плагина доступны `host->log(...)` и `host->notify(...)`.

Безопасность: нативный код **не изолирован** — он работает с правами приложения, а его падение закрывает приложение. Перед установкой приложение показывает предупреждение. Lua-плагины больше не поддерживаются.

## 0.8.3 — объединённый релиз: багфиксы, Telegram-функции, Stories и комментарии

- Исправлена авторизация и защита TDLib от повторного запуска/гонок при входе в аккаунт.
- Обновлён движок плагинов: native C++/NDK runtime через JNI вместо старого Lua runtime.
- Добавлен базовый C++ Plugin SDK и формат native-плагинов Yougram.
- Добавлены комментарии под постами каналов через реальные Telegram discussion threads TDLib.
- Кнопка комментариев отображается только у сообщений, для которых Telegram предоставляет discussion thread.
- В комментариях можно просматривать сообщения и отправлять новые комментарии.
- Добавлены Telegram Stories: просмотр активных историй, полноэкранный просмотр и публикация своей фото/видео-истории.
- Для просмотра историй используется `openStory`, а публикация выполняется через `postStory`.
- Исправлена загрузка наборов стикеров и сохранённых GIF при быстром переключении вкладок.
- Ошибки TDLib при загрузке GIF/стикеров теперь отображаются в состоянии чата.
- Для GIF без миниатюры добавлена корректная заглушка вместо бесконечной загрузки.
- Улучшены пустые состояния панели стикеров и GIF.
- `Julfy` в разделе разработчиков и благодарностей открывает `@julfiyy`.

