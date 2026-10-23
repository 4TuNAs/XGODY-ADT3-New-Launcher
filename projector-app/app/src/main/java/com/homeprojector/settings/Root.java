package com.homeprojector.settings;

import android.content.Context;
import android.content.pm.PackageManager;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** One long-lived root process running {@link Helper}; every call goes through one background thread. */
final class Root {
    interface Callback {
        void done(String result);
    }

    static final String VENDOR = "com.mylove.setting";

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final AtomicBoolean queued = new AtomicBoolean();
    private volatile int[] pending;
    private volatile String error = "";
    private Process process;
    private BufferedWriter out;
    private BufferedReader in;

    void start(Context context, Callback callback) {
        String ours = context.getApplicationInfo().sourceDir;
        String vendor;
        try {
            vendor = context.getPackageManager()
                    .getApplicationInfo(VENDOR, PackageManager.MATCH_DISABLED_COMPONENTS).sourceDir;
        } catch (PackageManager.NameNotFoundException e) {
            error = VENDOR + " is not installed";
            callback.done(null);
            return;
        }
        io.execute(() -> callback.done(open(ours, vendor)));
    }

    private String open(String ours, String vendor) {
        try {
            process = new ProcessBuilder("su").redirectErrorStream(true).start();
            out = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
            in = new BufferedReader(new InputStreamReader(process.getInputStream()));
            out.write("export CLASSPATH='" + ours + ":" + vendor + "'\n");
            out.write("exec app_process /system/bin " + Helper.class.getName() + "\n");
            out.flush();
            String line, last = "su refused";
            while ((line = in.readLine()) != null) {
                if (line.startsWith("ready")) return call("state");
                if (!line.trim().isEmpty()) last = line.trim();
            }
            error = last;
        } catch (IOException e) {
            error = String.valueOf(e.getMessage());
        }
        return null;
    }

    private String call(String command) throws IOException {
        out.write(command);
        out.write('\n');
        out.flush();
        String line;
        while ((line = in.readLine()) != null) {
            if (line.startsWith("ok") || line.startsWith("state")) return line;
            if (line.startsWith("err")) {
                error = line.substring(3).trim();
                return line;
            }
        }
        throw new IOException("root helper stopped");
    }

    /** Applies the latest corner set; a burst of key repeats collapses into the newest value. */
    void corners(int[] values) {
        pending = values.clone();
        if (queued.getAndSet(true)) return;
        io.execute(() -> {
            queued.set(false);
            int[] v = pending;
            StringBuilder sb = new StringBuilder("ks");
            for (int x : v) sb.append(' ').append(x);
            try {
                call(sb.toString());
            } catch (IOException e) {
                error = String.valueOf(e.getMessage());
            }
        });
    }

    void command(String command) {
        io.execute(() -> {
            try {
                call(command);
            } catch (IOException e) {
                error = String.valueOf(e.getMessage());
            }
        });
    }

    /** Runs a command and hands its answer line (or null) to the callback, on the background thread. */
    void query(String command, Callback callback) {
        io.execute(() -> {
            String result = null;
            try {
                result = call(command);
            } catch (IOException e) {
                error = String.valueOf(e.getMessage());
            }
            callback.done(result);
        });
    }

    void stop() {
        io.execute(() -> {
            try {
                if (out != null) call("quit");
            } catch (IOException ignored) {
            }
            if (process != null) process.destroy();
        });
        io.shutdown();
    }

    String error() {
        return error;
    }
}
