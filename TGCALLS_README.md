# Перенос звонков на родной tgcalls

1. Распакуйте архив поверх проекта Yougram (файлы заменятся).
2. Из корня проекта. Windows: `powershell -ExecutionPolicy Bypass -File tools\setup_tgvoip.ps1`; Linux/macOS: `bash tools/setup_tgvoip.sh`. Скрипт скачает Telegram-Android и скопирует нативный код,
   Java-слой `org.webrtc` и удалит старый `NTgCallsEngine.kt`.
3. На Windows держите проект по короткому пути (например `C:\yg`): у нативной сборки глубокие каталоги, а лимит пути 260 символов.
4. Соберите проект. Нужен NDK 27.2.12479018 и CMake 3.22.1.
5. Если сборка упадёт, пришлите первую ошибку CMake или компилятора.

Что изменено
- `app/build.gradle.kts`, `gradle/libs.versions.toml`: убран NTgCalls, добавлены `ndkVersion` и `abiFilters = arm64-v8a`.
- `app/proguard-rules.pro`: keep-правила для `org.telegram.messenger.voip` и `org.webrtc`.
- `app/src/main/cpp/CMakeLists.txt`: `project(... C CXX ASM)` и `add_subdirectory(tgvoip)`.
- `app/src/main/cpp/tgvoip/*`: сборка tgcalls, JNI_OnLoad, заглушка `tgnet/FileLog.h`.
- `app/src/main/java/org/telegram/...`: JNI-классы `Instance`, `NativeInstance`, заглушки `VideoCapturerDevice`,
  `FileLog`, `ApplicationLoader`, `SharedConfig`, `AndroidUtilities`, `LivePlayer`.
- `feature/calls/data/TgCallsEngine.kt`: новый движок; `AppContainer.kt` и `YougramApp.kt` переключены на него.

CallManager, CallOverlay и остальной UI звонков не менялись. Видео по-прежнему выключено.
