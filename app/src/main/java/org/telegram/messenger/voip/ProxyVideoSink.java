package org.telegram.messenger.voip;

import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;

/**
 * Постоянный приёмник кадров для нативного кода: C++ держит его всё время звонка,
 * а экран (SurfaceViewRenderer) может появляться и исчезать. Смена цели синхронизирована с
 * доставкой кадра, поэтому после setTarget(null) рендерер можно безопасно освобождать.
 */
public class ProxyVideoSink implements VideoSink {
    private VideoSink target;

    public synchronized void setTarget(VideoSink newTarget) {
        target = newTarget;
    }

    /** Снимает цель, только если она всё ещё [expected]: старый экран не должен отключить новый. */
    public synchronized void clearTarget(VideoSink expected) {
        if (target == expected) target = null;
    }

    @Override
    public synchronized void onFrame(VideoFrame frame) {
        VideoSink t = target;
        if (t == null) return;
        try {
            t.onFrame(frame);
        } catch (Throwable ignored) {
            // Исключение в нативном потоке недопустимо: кадр просто теряется.
        }
    }
}
