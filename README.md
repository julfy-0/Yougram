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

## Lua plugin message API

Plugins receive structured Telegram messages through `events.on("message_received", ...)`.
The `message.type` value is derived from the TDLib `MessageContent` class and is not limited to text/media. `message.content` contains a recursively converted TDLib object, so new TDLib message types can be exposed without changing the Lua API.

Supported message families include text, photo, video, animation/GIF, audio, voice note, video note, document, sticker, contact, location, venue, poll, dice, game, invoice, story, call, service/system messages, and any additional `MessageContent` type provided by the installed TDLib version.

Example:

```lua
events.on("message_received", function(message)
    print(message.type)
    print(message.chat_id)
    print(message.text)
    print(message.content)
end)

events.on("message_edited", function(message)
    print("edited:", message.type, message.message_id)
end)

events.on("message_deleted", function(event)
    print("deleted in chat", event.chat_id)
end)
```
