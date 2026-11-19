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

# Нативные плагины: JNI вызывает onNativeNotify по имени
-keep class app.yougram.plugin.NativePluginManager { *; }
-keepclassmembers class app.yougram.plugin.NativePluginManager {
    void onNativeNotify(java.lang.String, byte[], byte[]);
    byte[] onNativeCall(java.lang.String, java.lang.String, byte[]);
}

# Вызывается из правленого байткода tdl-coroutines (buildSrc/TdJsonPatchFactory.kt)
-keep class app.yougram.core.telegram.TdJsonFix { *; }

# MediaPipe GenAI (локальная ИИ-модель): JNI и protobuf обращаются к классам по именам
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**
-dontwarn javax.lang.model.**
-dontwarn com.google.auto.value.**
-dontwarn autovalue.shaded.**

# Для читаемых стектрейсов в release
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile