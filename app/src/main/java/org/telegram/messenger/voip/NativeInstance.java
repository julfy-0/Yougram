package org.telegram.messenger.voip;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class NativeInstance {
    static {
        System.loadLibrary("yougram_voip");
    }

    private long nativePtr; // читается из C++ по имени

    private Instance.OnStateUpdatedListener onStateUpdatedListener;
    private Instance.OnSignalBarsUpdatedListener onSignalBarsUpdatedListener;
    private Instance.OnSignalingDataListener onSignalDataListener;
    private Instance.OnRemoteMediaStateUpdatedListener onRemoteMediaStateUpdatedListener;

    private volatile Instance.FinalState finalState;
    private volatile CountDownLatch stopBarrier;

    /** Для аудиозвонка remoteSink = null и videoCapturer = 0. */
    public static NativeInstance make(String version, Instance.Config config, String persistentStatePath,
                                      Instance.Endpoint[] endpoints, Instance.Proxy proxy, int networkType,
                                      Instance.EncryptionKey key, float aspectRatio) {
        NativeInstance instance = new NativeInstance();
        instance.nativePtr = makeNativeInstance(version, instance, config, persistentStatePath, endpoints,
                proxy, networkType, key, null, 0L, aspectRatio);
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

    /** Блокирующий вызов: только не с главного потока. */
    public Instance.FinalState stop() {
        stopBarrier = new CountDownLatch(1);
        stopNative();
        try {
            stopBarrier.await(5, TimeUnit.SECONDS);
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
}
