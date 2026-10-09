package org.telegram.messenger;

import android.os.Handler;
import android.os.Looper;

public class AndroidUtilities {
    private static final Handler ui = new Handler(Looper.getMainLooper());

    public static void runOnUIThread(Runnable r) { ui.post(r); }
    public static void runOnUIThread(Runnable r, long delay) { ui.postDelayed(r, delay); }
    public static void cancelRunOnUIThread(Runnable r) { ui.removeCallbacks(r); }
}
