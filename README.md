# Yougram

**Yougram** — неофициальный Android-клиент Telegram с собственным интерфейсом и дополнительными функциями поверх TDLib.

Проект написан на **Kotlin + Jetpack Compose**, использует **TDLib через `tdl-coroutines`**, а для звонков и системы нативных плагинов содержит C++/JNI-компоненты.

> **Текущая версия:** `0.8.4` (`versionCode 18`)  
> **Платформа:** Android  
> **Минимальная версия:** Android 12 / API 31  
> **Target SDK:** 36  
> **Язык:** Kotlin  
> **UI:** Jetpack Compose + Material 3  
> **Нативная часть:** C++ / NDK / JNI

---

## Содержание

- [Что такое Yougram](#что-такое-yougram)
- [Основные возможности](#основные-возможности)
- [Технологии](#технологии)
- [Архитектура](#архитектура)
- [Структура проекта](#структура-проекта)
- [Требования](#требования)
- [Настройка проекта](#настройка-проекта)
- [Сборка](#сборка)
- [Конфигурация](#конфигурация)
- [Telegram / TDLib](#telegram--tdlib)
- [Авторизация и аккаунты](#авторизация-и-аккаунты)
- [Чаты и сообщения](#чаты-и-сообщения)
- [Медиа, стикеры и GIF](#медиа-стикеры-и-gif)
- [Звонки](#звонки)
- [Stories](#stories)
- [Комментарии и форумные темы](#комментарии-и-форумные-темы)
- [Поиск](#поиск)
- [Уведомления и фоновое соединение](#уведомления-и-фоновое-соединение)
- [Режимы Yougram](#режимы-yougram)
- [Профиль, бейджи и баннеры](#профиль-бейджи-и-баннеры)
- [Безопасность приложения](#безопасность-приложения)
- [Система плагинов](#система-плагинов)
- [Обновление приложения](#обновление-приложения)
- [Разработка](#разработка)
- [Известные ограничения](#известные-ограничения)
- [Планы развития](#планы-развития)
- [Лицензия](#лицензия)

---

## Что такое Yougram

Yougram — отдельный клиент Telegram для Android. Он не является форком официального интерфейса Telegram: приложение имеет собственную архитектуру, UI, настройки и дополнительные функции.

Telegram-функциональность реализована через TDLib. Основная задача проекта — сохранить привычные возможности Telegram, одновременно добавляя функции, которые относятся именно к Yougram:

- собственный интерфейс на Compose;
- расширенные настройки;
- режим призрака;
- архив удалённых сообщений и истории правок;
- фильтры сообщений;
- встроенный браузер;
- собственные бейджи и баннеры;
- нативные плагины;
- звонки;
- Stories;
- комментарии к публикациям;
- дополнительные инструменты для профилей и чатов.

---

# Основные возможности

## Авторизация

Поддерживается стандартный сценарий авторизации Telegram:

- номер телефона;
- код подтверждения;
- облачный пароль Telegram 2FA;
- прокси;
- повторное подключение TDLib;
- сохранение нескольких аккаунтов.

## Аккаунты

В проекте присутствует менеджер аккаунтов:

- добавление аккаунтов;
- переключение аккаунтов;
- отображение имени, телефона и аватарки;
- загрузка данных текущего аккаунта;
- синхронизация состояния аккаунта после авторизации.

Основные компоненты:

```text
data/AccountManager.kt
data/AccountRepository.kt
data/AuthRepository.kt
```

---

# Чаты и сообщения

Yougram работает с личными чатами, группами, супергруппами и каналами.

Поддерживаются:

- список чатов;
- папки Telegram;
- пагинация списка;
- история сообщений;
- отправка сообщений;
- ответы;
- редактирование;
- удаление;
- реакции;
- темы форумов;
- комментарии;
- медиа;
- голосовые сообщения;
- видеосообщения;
- GIF;
- стикеры;
- документы;
- поиск внутри чата;
- глобальный поиск;
- просмотр профилей.

Основная логика работы с Telegram находится в:

```text
data/ChatRepository.kt
data/TelegramClient.kt
```

`ChatRepository` является основным слоем между TDLib и UI.

---

# Медиа, стикеры и GIF

В чатах реализована обработка нескольких типов медиа:

| Тип | Поддержка |
|---|---|
| Фото | Да |
| Видео | Да |
| GIF / Animation | Да |
| Стикеры | Да |
| TGS | Да |
| WEBM-стикеры | Да |
| Документы | Да |
| Голосовые сообщения | Да |
| Видеосообщения / кружки | Да |

Для загрузки файлов используется состояние `FileState`, связанное с TDLib.

Основные UI-компоненты находятся в:

```text
ui/chat/
├── MessageMedia.kt
├── PhotoViewer.kt
├── MediaPlayers.kt
├── StickerGifPanel.kt
├── VoiceRecorder.kt
├── VoiceAndRound.kt
├── VideoNoteRecorder.kt
└── AudioPlayback.kt
```

Также реализовано скачивание файлов в фоне и отображение состояния загрузки.

---

# Звонки

Yougram содержит собственную интеграцию звонков.

Используются:

```text
data/CallManager.kt
data/CallEngine.kt
data/NTgCallsEngine.kt
data/CallAudio.kt
data/CallService.kt
data/CallLog.kt
```

Поддерживаются:

- входящие звонки;
- исходящие звонки;
- пропущенные звонки;
- отклонённые звонки;
- аудио;
- видео-звонки;
- управление маршрутом звука;
- динамик;
- Bluetooth;
- фоновый foreground service;
- уведомление о звонке;
- рингтон;
- отключение/гашение экрана во время разговора;
- запрос микрофона при необходимости;
- история звонков.

`NTgCallsEngine` используется как основной engine звонков.

---

# Stories

В Yougram добавлена работа с Telegram Stories.

Компоненты:

```text
feature/stories/
├── StoriesRepository.kt
├── StoriesScreen.kt
└── StoriesViewModel.kt
```

Поддерживаются:

- получение активных историй;
- просмотр Stories;
- полноэкранный просмотр;
- открытие истории;
- публикация фото-истории;
- публикация видео-истории.

Для работы с Telegram используются соответствующие TDLib API, включая `openStory` и `postStory`.

---

# Комментарии и форумные темы

Yougram поддерживает Telegram discussion threads.

Для постов каналов:

- определяется наличие discussion thread;
- отображается кнопка комментариев;
- открывается отдельный экран комментариев;
- загружаются сообщения thread;
- можно отправлять новые комментарии.

Компоненты:

```text
feature/
├── CommentsButton.kt
├── CommentsRepository.kt
├── CommentsRoute.kt
├── CommentsScreen.kt
├── CommentsViewModel.kt
└── OpenComments.kt
```

Для форумных групп реализована работа с темами:

```text
ui/chat/ForumTopicsScreen.kt
```

---

# Поиск

Реализованы два уровня поиска.

## Поиск внутри чата

Позволяет искать сообщения конкретного чата и переходить между найденными результатами.

## Глобальный поиск

Поддерживается поиск:

- людей;
- контактов;
- чатов;
- публичных чатов;
- сообщений по всему Telegram.

Компоненты:

```text
ui/chat/GlobalSearchScreen.kt
ui/chat/GlobalSearchViewModel.kt
```

---

# Уведомления и фоновое соединение

Yougram содержит собственную систему уведомлений.

Компоненты:

```text
data/NotificationCenter.kt
data/NotificationActionReceiver.kt
data/ConnectionService.kt
data/BootReceiver.kt
```

Поддерживаются:

- уведомления о сообщениях;
- действия из уведомлений;
- открытие конкретного чата по нажатию;
- постоянное соединение с Telegram в фоне;
- запуск фонового соединения после перезагрузки устройства;
- foreground service для постоянного соединения.

Фоновое соединение можно управлять из настроек.

---

# Режимы Yougram

В настройках присутствует отдельный раздел дополнительных возможностей.

## Режим призрака

Режим предназначен для управления тем, какие действия клиента отправляются в Telegram.

Он включает собственные настройки приватности и поведения чтения/просмотра.

Компонент:

```text
ui/settings/ExtrasScreens.kt
```

## Шпион

`SpyStore` сохраняет локальную историю некоторых изменений сообщений.

Отслеживаются:

- удалённые сообщения;
- изменения сообщений;
- история правок;
- некоторые события чтения;
- связанные с сообщениями вложения.

Данные хранятся локально на устройстве.

Компоненты:

```text
data/SpyStore.kt
data/MessageFilters.kt
```

## Фильтры

Система фильтров позволяет управлять обработкой сообщений и отдельными событиями.

---

# Профиль, бейджи и баннеры

Yougram добавляет собственную систему идентификации пользователей Yougram.

## Бейдж Yougram

При включении приложение может записывать специальную метку в поле «О себе» пользователя.

Другие клиенты Yougram могут распознавать эту метку и показывать соответствующий значок.

Реализация:

```text
data/YougramBadge.kt
ui/YougramBadge.kt
```

## Бейджи проекта

В коде предусмотрены отдельные типы:

- обычный Yougram;
- золотой бейдж;
- бейдж создателя.

## Баннер профиля

Yougram может кодировать собственный профильный баннер в данные профиля.

Баннер содержит:

- палитру;
- узор;
- градиент;
- версию данных.

Реализация:

```text
data/YougramBanner.kt
ui/ProfileBanner.kt
ui/CustomBanner.kt
```

Баннер является расширением Yougram и не является стандартным объектом Telegram.

---

# Встроенный браузер

В проекте есть WebView-браузер:

```text
ui/browser/BrowserScreen.kt
```

Ссылки в сообщениях распознаются автоматически.

Обрабатываются:

- обычные HTTP/HTTPS ссылки;
- Telegram-ссылки;
- ссылки на `t.me`;
- открытие Telegram-чата;
- открытие веб-страниц.

Для обычного HTTP используется переход на HTTPS.

В настройках есть переключатель:

> Встроенный браузер

---

# Профили

Для пользователей, групп, каналов и ботов используется общий профильный слой.

Поддерживаются:

- аватар;
- имя;
- username;
- дополнительные username;
- описание;
- телефон, если доступен;
- ссылка;
- тип объекта;
- mute;
- общие группы;
- дополнительные данные профиля.

Компоненты:

```text
ui/profile/
├── ProfileScreen.kt
├── ProfileViewModel.kt
└── DossierSheet.kt
```

---

# Настройки

Настройки разделены на отдельные страницы.

Основные разделы:

```text
ui/settings/
├── SettingsScreen.kt
├── SettingsHomeScreen.kt
├── AccountScreen.kt
├── ChatSettingsScreen.kt
├── NotificationsScreen.kt
├── DataStorageScreen.kt
├── DevicesScreen.kt
├── FoldersScreen.kt
├── LanguageScreen.kt
├── PrivacyScreen.kt
├── SecurityScreen.kt
├── PowerSavingScreen.kt
├── BannerScreen.kt
├── PremiumScreens.kt
├── TelegramHubScreen.kt
├── ExtrasScreens.kt
├── PluginsScreen.kt
└── AboutScreen.kt
```

В настройках доступны, в частности:

- аккаунт;
- Telegram;
- уведомления;
- чаты;
- папки;
- устройства;
- данные и хранилище;
- язык;
- приватность;
- безопасность;
- энергосбережение;
- тема;
- баннер;
- режим призрака;
- шпион;
- фильтры;
- плагины;
- информация о приложении.

---

# Безопасность приложения

В Yougram реализована локальная блокировка приложения.

Поддерживаются:

- PIN-код;
- графический ключ;
- биометрическая аутентификация;
- отдельный Lock Screen.

Компоненты:

```text
data/AppLock.kt
ui/security/
├── BiometricAuth.kt
├── LockInputs.kt
└── LockScreen.kt
```

Также используется Android `FileProvider` для безопасной передачи APK установщику при обновлении.

---

# Архитектура

Упрощённо приложение построено следующим образом:

```text
┌──────────────────────────────────────────┐
│              Jetpack Compose             │
│                                          │
│  Screens / ViewModels / Navigation       │
└────────────────────┬─────────────────────┘
                     │
                     ▼
┌──────────────────────────────────────────┐
│              Repository layer             │
│                                          │
│ ChatRepository / AuthRepository /        │
│ AccountRepository / Comments / Stories   │
└────────────────────┬─────────────────────┘
                     │
                     ▼
┌──────────────────────────────────────────┐
│             TelegramClient               │
│                                          │
│       TDLib / tdl-coroutines             │
└──────────────────────────────────────────┘

Дополнительно:

Android Services ──► notifications / calls / connection
JNI + C++ ─────────► native plugin runtime
CameraX ───────────► camera / video capture
```

`AppContainer` используется как простой ручной DI-контейнер.

Основные зависимости:

```text
YougramApp
    ↓
AppContainer
    ├── TelegramClient
    ├── AuthRepository
    ├── AccountRepository
    ├── ChatRepository
    ├── CommentsRepository
    ├── StoriesRepository
    ├── NotificationCenter
    ├── CallManager
    ├── SettingsRepository
    └── NativePluginManager
```

---

# Структура проекта

Актуальная структура приложения:

```text
Yougram/
├── README.md
├── build.gradle.kts
├── gradle.properties
├── local.properties.example
├── settings.gradle.kts
├── yougram.jks
│
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   │
│   └── src/main/
│       ├── AndroidManifest.xml
│       │
│       ├── assets/
│       │   └── plugins/
│       │
│       ├── cpp/
│       │   ├── CMakeLists.txt
│       │   ├── example_hello.cpp
│       │   ├── yougram_plugin_host.cpp
│       │   └── include/
│       │       └── yougram_plugin.h
│       │
│       ├── java/app/yougram/
│       │   ├── MainActivity.kt
│       │   ├── RestartActivity.kt
│       │   ├── YougramApp.kt
│       │   │
│       │   ├── data/
│       │   ├── feature/
│       │   ├── plugin/
│       │   ├── ui/
│       │   └── util/
│       │
│       └── res/
│           ├── drawable/
│           ├── mipmap-anydpi-v26/
│           ├── values/
│           └── xml/
│
└── gradle/
    ├── libs.versions.toml
    └── wrapper/
```

---

# Требования

Для разработки рекомендуется:

- Android Studio с поддержкой современного Android Gradle Plugin;
- JDK 17;
- Android SDK 36;
- Android SDK Platform 31 или выше;
- Android NDK;
- CMake 3.22.1;
- устройство или эмулятор с Android 12+.

Минимальная версия приложения:

```text
Android 12 / API 31
```

---

# Настройка проекта

## 1. Получить Telegram API ID и API Hash

Откройте:

```text
https://my.telegram.org
```

Перейдите в:

```text
API development tools
```

Создайте приложение и получите:

```text
api_id
api_hash
```

---

## 2. Создать local.properties

Скопируйте:

```text
local.properties.example
```

в:

```text
local.properties
```

Пример:

```properties
sdk.dir=/path/to/Android/Sdk

TG_API_ID=12345678
TG_API_HASH=your_api_hash

UPDATE_URL=https://example.com/update.json
```

`local.properties` не должен попадать в Git.

---

# Конфигурация

Используемые параметры:

| Параметр | Назначение |
|---|---|
| `sdk.dir` | путь к Android SDK |
| `TG_API_ID` | Telegram API ID |
| `TG_API_HASH` | Telegram API Hash |
| `UPDATE_URL` | URL JSON с информацией об обновлении |
| `KS_PASS` | пароль release keystore |

Значения Telegram API передаются в `BuildConfig`.

---

# Сборка

## Debug

В Android Studio:

```text
Run → Run 'app'
```

или:

```bash
./gradlew assembleDebug
```

APK появится в:

```text
app/build/outputs/apk/debug/
```

## Release

```bash
./gradlew assembleRelease
```

APK появится в:

```text
app/build/outputs/apk/release/
```

Release-сборка использует:

```text
yougram.jks
```

если в `local.properties` указан `KS_PASS`.

**Не публикуйте keystore и пароль от него в открытом репозитории.**

---

# Telegram / TDLib

Основная интеграция с Telegram находится в:

```text
data/TelegramClient.kt
```

Используется библиотека:

```text
dev.g000sha256:tdl-coroutines
```

TDLib является основным источником Telegram-состояния.

Код проекта старается держать непосредственную работу с TDLib в data/repository-слое, чтобы UI не зависел напрямую от низкоуровневых TDLib объектов.

Если версия TDLib меняется и ломает API, в первую очередь проверяйте:

```text
data/TelegramClient.kt
data/ChatRepository.kt
data/AuthRepository.kt
```

---

# Система плагинов

Yougram имеет расширяемую систему плагинов.

Текущий основной runtime — **нативные C++ плагины**, загружаемые через JNI.

Компоненты:

```text
plugin/
├── NativePluginManager.kt
├── PluginEventBus.kt
├── PluginMessage.kt
├── PluginModels.kt
└── TdObjectMapper.kt

cpp/
├── yougram_plugin_host.cpp
├── example_hello.cpp
└── include/
    └── yougram_plugin.h
```

## Формат `.ygplugin`

Плагин представляет собой ZIP-архив с расширением:

```text
.ygplugin
```

Пример:

```text
myplugin.ygplugin
├── manifest.json
└── lib/
    └── arm64-v8a/
        └── libmyplugin.so
```

Можно добавить несколько ABI:

```text
lib/
├── arm64-v8a/
│   └── libmyplugin.so
├── armeabi-v7a/
│   └── libmyplugin.so
└── x86_64/
    └── libmyplugin.so
```

## manifest.json

```json
{
  "id": "com.example.myplugin",
  "name": "My Plugin",
  "version": "1.0.0",
  "api": "1.0",
  "author": "Developer",
  "description": "Example plugin",
  "entry": "libmyplugin.so",
  "permissions": []
}
```

## Entry point

Плагин должен экспортировать:

```cpp
extern "C" YG_EXPORT const YgPlugin* yougram_plugin_entry(void);
```

API описан здесь:

```text
app/src/main/cpp/include/yougram_plugin.h
```

Плагин может реализовать:

```text
on_load
on_enable
on_disable
on_event
```

## События

Сейчас предусмотрены события:

```text
message_received
message_edited
message_deleted
```

Событие передаётся плагину как JSON.

Для `message_received` передаётся структура сообщения Yougram, включая рекурсивно преобразованные данные TDLib.

## Host API

Плагин получает:

```text
host->log(...)
host->notify(...)
```

Это позволяет:

- писать сообщения в Logcat;
- показывать уведомления из плагина.

## Важное предупреждение

Нативные плагины **не являются sandboxed**.

`.so` работает внутри процесса приложения и имеет права процесса Yougram.

Это означает:

- плагин потенциально может получить доступ к данным приложения;
- ошибка в плагине может привести к падению приложения;
- вредоносный плагин нельзя считать безопасным;
- установка сторонних `.ygplugin` должна выполняться только из доверенных источников.

Перед установкой пользователь должен понимать, что это нативный код.

---

# Обновление приложения

Yougram умеет проверять обновления из приложения.

Адрес задаётся:

```properties
UPDATE_URL=https://example.com/update.json
```

Ожидаемый JSON:

```json
{
  "versionCode": 19,
  "versionName": "0.8.5",
  "apkUrl": "https://example.com/yougram.apk",
  "notes": "Исправления и новые функции"
}
```

Проверка выполняется по `versionCode`.

Если удалённая версия выше текущей:

1. показывается доступное обновление;
2. APK скачивается в cache;
3. пользователь запускает установку;
4. Android открывает системный установщик.

Ссылки на обновления и APK должны использовать:

```text
https://
```

Для установки APK приложение использует Android `FileProvider`.

---

# UI и дизайн

Интерфейс полностью построен на Jetpack Compose.

Используются:

- Material 3;
- собственная система тем;
- адаптивный двухпанельный интерфейс;
- glass/backdrop-эффекты;
- собственные компоненты настроек;
- динамические цвета;
- отдельные стили пузырей сообщений;
- кастомные баннеры;
- адаптация под разные размеры экранов.

Главные UI-компоненты:

```text
ui/
├── main/
├── auth/
├── chats/
├── chat/
├── calls/
├── contacts/
├── profile/
├── settings/
├── security/
├── browser/
├── glass/
├── adaptive/
└── theme/
```

---

# CameraX

Для функций, связанных с камерой и записью видео, используется CameraX:

```text
androidx.camera.core
androidx.camera.camera2
androidx.camera.lifecycle
androidx.camera.video
androidx.camera.view
```

Это используется в сценариях записи видео и видеосообщений.

---

# Разработка

## Где добавлять Telegram-функции

Низкоуровневый Telegram-код:

```text
data/
```

Логика конкретной функции:

```text
feature/
```

или соответствующий раздел:

```text
ui/chat/
ui/profile/
ui/settings/
```

Рекомендуемый поток:

```text
TDLib
  ↓
TelegramClient / Repository
  ↓
ViewModel
  ↓
Compose Screen
```

Не рекомендуется вызывать TDLib напрямую из Compose UI.

---

# Добавление нового экрана

Обычно используются:

```text
Screen.kt
ViewModel.kt
Route.kt
```

Если экран требует отдельного состояния, ViewModel должна хранить его через `StateFlow` / `MutableStateFlow`.

Навигация централизована в:

```text
ui/YougramNavHost.kt
```

---

# Добавление настройки

Основная логика хранения:

```text
data/SettingsRepository.kt
```

UI:

```text
ui/settings/
```

Для новой страницы обычно нужно:

1. добавить значение в `SettingsPage`;
2. добавить экран;
3. добавить маршрут;
4. добавить пункт в `SettingsHomeScreen` или соответствующий раздел;
5. сохранить состояние через `SettingsRepository`.

---

# Известные ограничения

## Нативные плагины

Нативные плагины не изолированы.

Это сознательная архитектурная особенность текущей системы.

## Telegram API

Некоторые возможности Telegram зависят от того, что возвращает TDLib и какие возможности доступны конкретному аккаунту.

## Фоновая работа

Android может ограничивать фоновые процессы и foreground services в зависимости от версии Android, настроек производителя и режима энергосбережения.

## Premium / Stars / Business

В проекте есть UI-разделы для Telegram Premium, Stars и Business, однако наличие интерфейса не означает обход или эмуляцию серверных возможностей Telegram.

## Локальный Premium

Опция:

```text
Локальный Premium
```

изменяет отображение Premium-элементов только внутри Yougram на данном устройстве и не выдаёт серверный Telegram Premium аккаунту.

## Yougram-метки

Бейджи и баннеры Yougram используют собственный формат данных и не являются официальными Telegram-бейджами.

---

# Текущее состояние проекта

На момент версии `0.8.4` проект уже содержит значительно больше, чем базовый Telegram-клиент.

Основные готовые подсистемы:

- [x] авторизация Telegram;
- [x] 2FA;
- [x] несколько аккаунтов;
- [x] список чатов;
- [x] папки;
- [x] личные чаты;
- [x] группы;
- [x] каналы;
- [x] сообщения;
- [x] ответы;
- [x] реакции;
- [x] редактирование;
- [x] удаление;
- [x] медиа;
- [x] документы;
- [x] голосовые;
- [x] видеосообщения;
- [x] GIF;
- [x] стикеры;
- [x] TGS/WEBM;
- [x] поиск;
- [x] глобальный поиск;
- [x] форумные темы;
- [x] комментарии;
- [x] Stories;
- [x] аудио/видеозвонки;
- [x] история звонков;
- [x] уведомления;
- [x] фоновое соединение;
- [x] встроенный браузер;
- [x] режим призрака;
- [x] шпион;
- [x] фильтры;
- [x] бейджи Yougram;
- [x] профильные баннеры;
- [x] блокировка приложения;
- [x] биометрия;
- [x] CameraX;
- [x] нативная система плагинов;
- [x] внутренняя система обновлений;
- [x] адаптивный UI.

---

# Планы развития

Направления, которые естественно продолжают текущую архитектуру:

- расширение Plugin SDK;
- дополнительные события Plugin API;
- более богатый API для плагинов;
- дополнительные настройки приватности;
- улучшение работы звонков;
- расширение поддержки Telegram Stories;
- улучшение медиа и кэширования;
- дополнительные инструменты поиска;
- улучшение производительности больших чатов;
- улучшение адаптивного интерфейса;
- автоматизация CI/CD и релизов;
- отдельная документация для разработчиков плагинов.

---

# Важное замечание по исходникам

Проект содержит как Kotlin-код приложения, так и нативную часть:

```text
app/src/main/java/
app/src/main/cpp/
```

Поэтому для полноценной сборки недостаточно только Android SDK: также необходима корректная установка NDK/CMake.

---

# Быстрый старт

Кратко:

```bash
# 1. Клонировать проект
git clone <repository>
cd Yougram

# 2. Создать local.properties
cp local.properties.example local.properties

# 3. Указать Telegram API
# TG_API_ID=...
# TG_API_HASH=...

# 4. Открыть проект в Android Studio

# 5. Выполнить Gradle Sync

# 6. Запустить
./gradlew assembleDebug
```

После установки приложения:

```text
Yougram
  ↓
Авторизация
  ↓
Telegram
  ↓
Список чатов
  ↓
Чаты / звонки / Stories / настройки / плагины
```

---

# Лицензия

В корне проекта на данный момент отсутствует отдельный файл лицензии.

Перед публичной публикацией проекта рекомендуется добавить:

```text
LICENSE
```

и явно определить условия использования исходного кода, нативных компонентов и SDK.

---

## Yougram

Android-клиент Telegram с собственным интерфейсом и расширениями.

```text
Kotlin
Jetpack Compose
TDLib
C++
JNI
CameraX
NTgCalls
```
