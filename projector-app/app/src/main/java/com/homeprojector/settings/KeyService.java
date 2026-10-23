package com.homeprojector.settings;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The remote while HDMI plays (the vendor's com.softwinner.awlivetv): Back and Home go to the box over HDMI-CEC
 * instead of closing HDMI; Back held for 0.7 s leaves HDMI for the home screen. Keys reach the box through a small
 * root helper (Helper keys) that only runs while HDMI is on screen. If the source has no CEC, or root is missing,
 * Back and Home do what they always did.
 *
 * Input and Menu are not here: the firmware handles them before any app sees them (TvWindowManager). Input becomes
 * an HDMI / home toggle in cyt-keys.sh; Menu opens the vendor picture panel over HDMI.
 *
 * The firmware clears the whole accessibility list on every focus change (DisplayContent.enableNfxAccessibility,
 * a Netflix hack), so cyt-keys.sh switches this service back on whenever HDMI is in front.
 */
public class KeyService extends AccessibilityService {
    private static final String TAG = "ProjectorKeys";
    static final String HDMI_APP = "com.softwinner.awlivetv";
    private static final long LONG_PRESS = 700;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean ready;
    private Process process;      // io thread only
    private BufferedWriter out;   // io thread only
    private BufferedReader in;    // io thread only
    private int held = -1;        // key whose press we took, so its release is ours too

    @Override
    protected void onServiceConnected() {
        if (inHdmi()) io.execute(this::open);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        if (e.getPackageName() == null || e.getClassName() == null) return;
        String pkg = e.getPackageName().toString();
        try {
            // only whole screens count; dialogs, toasts and overlays come and go on top of HDMI
            getPackageManager().getActivityInfo(new ComponentName(pkg, e.getClassName().toString()), 0);
        } catch (PackageManager.NameNotFoundException ex) {
            return;
        }
        if (pkg.equals(HDMI_APP)) io.execute(this::open);
        else if (!pkg.equals(getPackageName())) io.execute(this::close);
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    protected boolean onKeyEvent(KeyEvent k) {
        int code = k.getKeyCode();
        boolean down = k.getAction() == KeyEvent.ACTION_DOWN;
        if (!down && code == held) {
            held = -1;
            if (code == KeyEvent.KEYCODE_BACK && k.getEventTime() - k.getDownTime() >= LONG_PRESS) leave();
            else forward(code);
            return true;
        }
        if (!down || k.getRepeatCount() > 0) return code == held;
        if (code != KeyEvent.KEYCODE_BACK && code != KeyEvent.KEYCODE_HOME) return false;
        if (!inHdmi()) return false;
        if (!ready) {
            io.execute(this::open);   // HDMI came up without an event we saw; the next press will go to the box
            return false;
        }
        held = code;
        return true;
    }

    private boolean inHdmi() {
        try {
            AccessibilityNodeInfo node = getRootInActiveWindow();
            return node != null && node.getPackageName() != null && HDMI_APP.contentEquals(node.getPackageName());
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void forward(int code) {
        io.execute(() -> {
            String answer = null;
            try {
                if (out != null) {
                    out.write("key " + code + "\n");
                    out.flush();
                    answer = in.readLine();
                }
            } catch (IOException e) {
                Log.w(TAG, "key helper: " + e);
                close();
            }
            if (answer == null || !answer.startsWith("sent")) {
                Log.i(TAG, "no CEC box for key " + code + ": " + answer);
                main.post(this::leave);   // what Back and Home did before: back to the home screen
            }
        });
    }

    private void leave() {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(home);
        } catch (RuntimeException e) {
            performGlobalAction(GLOBAL_ACTION_HOME);
        }
    }

    private void open() {
        if (process != null) return;
        try {
            process = new ProcessBuilder("su").redirectErrorStream(true).start();
            out = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
            in = new BufferedReader(new InputStreamReader(process.getInputStream()));
            out.write("export CLASSPATH='" + getApplicationInfo().sourceDir + "'\n");
            out.write("exec app_process /system/bin " + Helper.class.getName() + " keys\n");
            out.flush();
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("ready")) {
                    ready = true;
                    Log.i(TAG, "CEC key helper ready");
                    return;
                }
                Log.w(TAG, "key helper: " + line);
            }
        } catch (IOException e) {
            Log.w(TAG, "key helper: " + e);
        }
        close();
    }

    private void close() {
        ready = false;
        if (process == null) return;
        try {
            out.write("quit\n");
            out.flush();
        } catch (IOException ignored) {
        }
        process.destroy();
        process = null;
        out = null;
        in = null;
    }

    @Override
    public void onDestroy() {
        io.execute(this::close);
        io.shutdown();
        super.onDestroy();
    }
}
