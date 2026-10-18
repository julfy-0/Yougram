package org.telegram.messenger.voip;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjection;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import org.telegram.messenger.ApplicationLoader;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CapturerObserver;
import org.webrtc.ContextUtils;
import org.webrtc.EglBase;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Камера для tgcalls. Нативный код сам создаёт экземпляр (AndroidContext) и зовёт init/onStateChanged/onDestroy
 * по именам, а кадры отдаёт нативному источнику через CapturerObserver.
 * Раньше здесь была заглушка, поэтому исходящее видео никуда не уходило.
 */
public class VideoCapturerDevice {
    private static final String TAG = "VideoCapturerDevice";
    private static final int CAPTURE_WIDTH = 1280;
    private static final int CAPTURE_HEIGHT = 720;
    private static final int CAPTURE_FPS = 30;
    private static final int MAX_RETRIES = 5;
    private static final int STATE_ACTIVE = 2;

    private static EglBase eglBase;
    private static final List<VideoCapturerDevice> LIVE = new CopyOnWriteArrayList<>();

    private final boolean screencast;
    private final Object lock = new Object();

    private HandlerThread thread;
    private Handler handler;
    private boolean destroyed;
    private long nativePtr;
    private String deviceName = "front";
    private boolean wantActive;

    // Только с потока handler.
    private VideoCapturer videoCapturer;
    private SurfaceTextureHelper surfaceTextureHelper;
    private boolean capturing;
    private int retries;

    public VideoCapturerDevice(boolean screencast) {
        this.screencast = screencast;
        LIVE.add(this);
    }

    public static MediaProjection getMediaProjection() { return null; }

    public static synchronized EglBase.Context getSharedEGLContext() {
        if (eglBase == null) {
            eglBase = EglBase.create(null, EglBase.CONFIG_PLAIN);
        }
        return eglBase.getEglBaseContext();
    }

    /** Останавливает и освобождает все камеры. Блокирует вызывающий поток, не вызывать с главного. */
    public static void releaseAllCameras() {
        for (VideoCapturerDevice d : LIVE) d.releaseCameraBlocking();
    }

    // ---- вызываются из C++ по именам ----

    private void init(long ptr, String name) {
        Handler h;
        synchronized (lock) {
            if (destroyed || screencast) return;
            nativePtr = ptr;
            deviceName = name == null ? "front" : name;
            retries = 0;
            h = ensureThread();
        }
        h.post(() -> setup(ptr));
    }

    private void onStateChanged(long ptr, int state) {
        Handler h;
        synchronized (lock) {
            if (destroyed || screencast) return;
            wantActive = state == STATE_ACTIVE;
            h = handler;
        }
        if (h != null) h.post(this::applyState);
    }

    private void onAspectRatioRequested(float aspectRatio) { }

    public void onDestroy() {
        Handler h;
        HandlerThread t;
        synchronized (lock) {
            destroyed = true;
            nativePtr = 0;
            wantActive = false;
            h = handler;
            t = thread;
            handler = null;
            thread = null;
        }
        LIVE.remove(this);
        if (h != null) {
            h.post(() -> {
                disposeCapturer();
                if (t != null) t.quitSafely();
            });
        }
    }

    private static native CapturerObserver nativeGetJavaVideoCapturerObserver(long ptr);

    // ---- внутреннее ----

    private Handler ensureThread() {
        if (handler == null) {
            thread = new HandlerThread("VideoCapturerDevice");
            thread.start();
            handler = new Handler(thread.getLooper());
        }
        return handler;
    }

    private static Context appContext() {
        Context c = null;
        try { c = ContextUtils.getApplicationContext(); } catch (Throwable ignored) { }
        return c != null ? c : ApplicationLoader.applicationContext;
    }

    private void setup(long ptr) {
        final String name;
        synchronized (lock) {
            if (destroyed || nativePtr != ptr) return;
            name = deviceName;
        }
        disposeCapturer();
        Context ctx = appContext();
        if (ctx == null) {
            Log.w(TAG, "нет контекста приложения");
            return;
        }
        if (ctx.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "нет разрешения CAMERA");
            return;
        }
        try {
            Camera2Enumerator enumerator = new Camera2Enumerator(ctx);
            String[] names = enumerator.getDeviceNames();
            final boolean front = "front".equals(name);
            String selected = null;
            for (String n : names) {
                if (enumerator.isFrontFacing(n) == front) { selected = n; break; }
            }
            if (selected == null && names.length > 0) selected = names[0];
            if (selected == null) {
                Log.w(TAG, "камер нет");
                return;
            }
            VideoCapturer capturer = enumerator.createCapturer(selected, new CameraVideoCapturer.CameraEventsHandler() {
                @Override public void onCameraError(String d) { Log.w(TAG, "camera error: " + d); retryLater(ptr); }
                @Override public void onCameraDisconnected() { Log.w(TAG, "camera disconnected"); retryLater(ptr); }
                @Override public void onCameraFreezed(String d) { Log.w(TAG, "camera froze: " + d); retryLater(ptr); }
                @Override public void onCameraOpening(String d) { }
                @Override public void onFirstFrameAvailable() { retries = 0; }
                @Override public void onCameraClosed() { }
            });
            if (capturer == null) return;

            SurfaceTextureHelper helper = SurfaceTextureHelper.create("VideoCapturerThread", getSharedEGLContext());
            CapturerObserver observer;
            synchronized (lock) {
                // Нативный capturer мог быть уничтожен или заменён (переключение камеры) пока шла подготовка.
                if (destroyed || nativePtr != ptr) {
                    helper.dispose();
                    capturer.dispose();
                    return;
                }
                observer = nativeGetJavaVideoCapturerObserver(ptr);
            }
            capturer.initialize(helper, ctx, observer);
            videoCapturer = capturer;
            surfaceTextureHelper = helper;
            capturing = false;
            applyState();
        } catch (Throwable e) {
            Log.e(TAG, "setup failed", e);
            disposeCapturer();
        }
    }

    private void applyState() {
        VideoCapturer c = videoCapturer;
        if (c == null) return;
        boolean want;
        synchronized (lock) { want = wantActive && !destroyed; }
        try {
            if (want && !capturing) {
                c.startCapture(CAPTURE_WIDTH, CAPTURE_HEIGHT, CAPTURE_FPS);
                capturing = true;
            } else if (!want && capturing) {
                c.stopCapture();
                capturing = false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable e) {
            Log.e(TAG, "applyState failed", e);
        }
    }

    private void retryLater(long ptr) {
        Handler h;
        int attempt;
        synchronized (lock) {
            if (destroyed || nativePtr != ptr || !wantActive) return;
            h = handler;
            attempt = retries;
            if (attempt >= MAX_RETRIES) return;
            retries = attempt + 1;
        }
        if (h != null) h.postDelayed(() -> setup(ptr), 800L * (attempt + 1));
    }

    /** Только с потока handler. */
    private void disposeCapturer() {
        VideoCapturer c = videoCapturer;
        SurfaceTextureHelper h = surfaceTextureHelper;
        videoCapturer = null;
        surfaceTextureHelper = null;
        if (c != null) {
            try { if (capturing) c.stopCapture(); } catch (Throwable ignored) { }
            try { c.dispose(); } catch (Throwable ignored) { }
        }
        capturing = false;
        if (h != null) {
            try { h.dispose(); } catch (Throwable ignored) { }
        }
    }

    /**
     * Камера обязана остановиться до уничтожения нативного источника кадров, иначе поздний кадр
     * попадёт в освобождённую память. Поэтому ждём завершения на потоке камеры.
     */
    private void releaseCameraBlocking() {
        Handler h;
        synchronized (lock) {
            wantActive = false;
            nativePtr = 0;
            h = handler;
        }
        if (h == null) return;
        CountDownLatch done = new CountDownLatch(1);
        boolean posted = h.post(() -> {
            try { disposeCapturer(); } finally { done.countDown(); }
        });
        if (!posted) return;
        try {
            done.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
