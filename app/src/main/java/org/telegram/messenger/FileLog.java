package org.telegram.messenger;

import android.util.Log;

public class FileLog {
    public static void d(String m) { Log.d("tgvoip", m); }
    public static void e(String m) { Log.e("tgvoip", m); }
    public static void e(Throwable t) { Log.e("tgvoip", "error", t); }
    public static void e(String m, Throwable t) { Log.e("tgvoip", m, t); }
}
