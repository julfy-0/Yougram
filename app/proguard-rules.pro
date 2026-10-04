# TDLib обращается к своим классам из нативного кода (JNI) — их нельзя переименовывать или удалять
-keep class org.drinkless.tdlib.** { *; }
-keep class dev.g000sha256.tdl.** { *; }
-keepclassmembers class * {
    native <methods>;
}
