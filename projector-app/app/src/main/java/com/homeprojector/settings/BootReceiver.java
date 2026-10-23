package com.homeprojector.settings;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * "HDMI at startup": after boot, opens Allwinner's source app, which switches to the HDMI input.
 * It is started through su, because Android does not let a boot receiver open an activity by itself.
 */
public class BootReceiver extends BroadcastReceiver {
    static final String PREFS = "projector", KEY = "hdmi_boot";
    static final String HDMI = "com.softwinner.awsource/.MainActivity";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        if (!context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)) return;
        PendingResult result = goAsync();
        new Thread(() -> {
            try {
                Thread.sleep(4000);   // let Home finish coming up first, so HDMI ends on top
                new ProcessBuilder("su", "-c", "am start -n " + HDMI).redirectErrorStream(true).start().waitFor();
            } catch (Exception ignored) {
            } finally {
                result.finish();
            }
        }).start();
    }
}
