package org.telegram.messenger.voip;

import android.media.projection.MediaProjection;

// Заглушка: нативный код создаёт этот класс даже в аудиозвонке. Видео не поддерживается.
public class VideoCapturerDevice {
    public VideoCapturerDevice(boolean screencast) { }
    public void onDestroy() { }
    public static MediaProjection getMediaProjection() { return null; }
}
