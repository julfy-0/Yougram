package org.telegram.messenger.voip;

import org.webrtc.VideoSink;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class NativeInstance {
    static {
        System.loadLibrary("yougram_voip");
    }

    private long nativePtr; // читается из C++ по имени

    /**
     * Читается из C++ по имени в stopNative (сохранение persistent state). Раньше поля не было:
     * JNI получал NoSuchFieldError на нативном потоке, и приложение падало при завершении звонка.
     */
    private String persistentStateFilePath;

    private Instance.OnStateUpdatedListener onStateUpdatedListener;
    private Instance.OnSignalBarsUpdatedListener onSignalBarsUpdatedListener;
    private Instance.OnSignalingDataListener onSignalDataListener;
    private Instance.OnRemoteMediaStateUpdatedListener onRemoteMediaStateUpdatedListener;

    private volatile Instance.FinalState finalState;
    private volatile CountDownLatch stopBarrier;
    private boolean stopRequested;

    /**
     * [remoteSink] обязателен и не должен быть null: нативный код оборачивает его при создании
     * и вызывает на каждом кадре собеседника (для аудиозвонка кадров просто не будет).
     */
    public static NativeInstance make(String version, Instance.Config config, String persistentStatePath,
                                      Instance.Endpoint[] endpoints, Instance.Proxy proxy, int networkType,
                                      Instance.EncryptionKey key, VideoSink remoteSink, float aspectRatio) {
        NativeInstance instance = new NativeInstance();
        instance.persistentStateFilePath = persistentStatePath;
        instance.nativePtr = makeNativeInstance(version, instance, config, persistentStatePath, endpoints,
                proxy, networkType, key, remoteSink, 0L, aspectRatio);
        if (instance.nativePtr == 0L) {
            throw new IllegalStateException("tgcalls: не удалось создать экземпляр");
        }
        return instance;
    }

    public void setOnStateUpdatedListener(Instance.OnStateUpdatedListener l) { onStateUpdatedListener = l; }
    public void setOnSignalBarsUpdatedListener(Instance.OnSignalBarsUpdatedListener l) { onSignalBarsUpdatedListener = l; }
    public void setOnSignalDataListener(Instance.OnSignalingDataListener l) { onSignalDataListener = l; }
    public void setOnRemoteMediaStateUpdatedListener(Instance.OnRemoteMediaStateUpdatedListener l) { onRemoteMediaStateUpdatedListener = l; }

    // ---- вызываются из C++ по именам ----
    private void onStateUpdated(int state) {
        Instance.OnStateUpdatedListener l = onStateUpdatedListener;
        if (l != null) l.onStateUpdated(state);
    }

    private void onSignalBarsUpdated(int bars) {
        Instance.OnSignalBarsUpdatedListener l = onSignalBarsUpdatedListener;
        if (l != null) l.onSignalBarsUpdated(bars);
    }

    private void onSignalingData(byte[] data) {
        Instance.OnSignalingDataListener l = onSignalDataListener;
        if (l != null) l.onSignalingData(data);
    }

    private void onRemoteMediaStateUpdated(int audioState, int videoState) {
        Instance.OnRemoteMediaStateUpdatedListener l = onRemoteMediaStateUpdatedListener;
        if (l != null) l.onMediaStateUpdated(audioState, videoState);
    }

    private void onAudioLevelsUpdated(int[] uids, float[] levels, boolean[] voice) { }

    private void onStop(Instance.FinalState state) {
        finalState = state;
        CountDownLatch b = stopBarrier;
        if (b != null) b.countDown();
    }
    // -------------------------------------

    /**
     * Блокирующий вызов: только не с главного потока. Повторный вызов ничего не делает:
     * после остановки C++ освобождает holder, и любой следующий нативный вызов был бы use-after-free.
     */
    public Instance.FinalState stop() {
        CountDownLatch barrier = new CountDownLatch(1);
        synchronized (this) {
            if (stopRequested) return finalState;
            stopRequested = true;
            stopBarrier = barrier;
        }
        stopNative();
        try {
            barrier.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        }
        return finalState;
    }

    private static native long makeNativeInstance(String version, NativeInstance instance, Instance.Config config,
                                                  String persistentStateFilePath, Instance.Endpoint[] endpoints,
                                                  Instance.Proxy proxy, int networkType,
                                                  Instance.EncryptionKey encryptionKey, Object remoteSink,
                                                  long videoCapturer, float aspectRatio);

    public static native String[] getAllVersions();
    public native void setNetworkType(int networkType);
    public native void setMuteMicrophone(boolean mute);
    public native void setBufferSize(int size);
    public native void setEchoCancellationStrength(int strength);
    public native void setAudioOutputGainControlEnabled(boolean enabled);
    public native void onSignalingDataReceive(byte[] data);
    public native String getLastError();
    public native String getDebugInfo();
    public native Instance.TrafficStats getTrafficStats();
    public native byte[] getPersistentState();
    private native void stopNative();

    // ---- видео ----
    /** Создаёт камеру внутри tgcalls и подключает её к звонку. [type]: 1 — фронтальная, 0 — основная. */
    public native void setupOutgoingVideo(VideoSink localSink, int type);
    /** См. Instance.VIDEO_STATE_*. */
    public native void setVideoState(int state);
    public native void switchCamera(boolean front);
    public native boolean hasVideoCapturer();
}
