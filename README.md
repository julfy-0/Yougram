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
