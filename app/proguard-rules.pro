# TDLib обращается к своим классам из нативного кода (JNI) — их нельзя переименовывать или удалять
-keep class org.drinkless.tdlib.** { *; }
-keep class dev.g000sha256.tdl.** { *; }

# NTgCalls (звонки): нативный код вызывает Java-классы и колбэки по именам, без keep в release звонки не работают
-keep class io.github.pytgcalls.** { *; }
-keepclassmembers class io.github.pytgcalls.** { *; }
-dontwarn io.github.pytgcalls.**

-keepclassmembers class * {
    native <methods>;
}
