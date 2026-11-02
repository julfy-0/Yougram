#include <jni.h>

extern "C" int tgvoipOnJNILoad(JavaVM *vm, JNIEnv *env);

extern "C" jint JNI_OnLoad(JavaVM *vm, void *) {
    JNIEnv *env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) return -1;
    tgvoipOnJNILoad(vm, env);
    return JNI_VERSION_1_6;
}
