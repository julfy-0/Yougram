# TDLib обращается к своим классам из нативного кода (JNI) — их нельзя переименовывать или удалять
-keep class org.drinkless.tdlib.** { *; }
-keep class dev.g000sha256.tdl.** { *; }

# tgcalls: C++ читает поля и вызывает методы по именам
-keep class org.telegram.messenger.voip.** { *; }
-keep class org.telegram.messenger.ApplicationLoader { *; }
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

-keepclassmembers class * {
    native <methods>;
}

# Вызывается из правленого байткода tdl-coroutines (buildSrc/TdJsonPatchFactory.kt)
-keep class app.yougram.core.telegram.TdJsonFix { *; }
