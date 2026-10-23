package com.homeprojector.settings;

import static com.homeprojector.settings.L.t;

import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Picture settings of the Allwinner tv server (mode, brightness, contrast, saturation, sharpness, colour,
 * HDMI signal type), opened from the main Projector screen. The panel is see-through; the values apply to HDMI.
 * While HDMI plays, Menu on the remote opens the vendor's own picture panel over the picture.
 */
public class PictureActivity extends BaseActivity {
    private static final String[] MODE_KEYS = {"standard", "cinema", "vivid", "game", "energy_saving", "computer", "hdr", "custom"};
    private static final String[] MODE_EN = {"Standard", "Cinema", "Vivid", "Game", "Energy saving", "Computer", "HDR", "Custom"};
    private static final String[] MODE_RU = {"Стандарт", "Кино", "Яркий", "Игра", "Экономия", "Компьютер", "HDR", "Свой"};
    private static final String[] TEMP_EN = {"Standard", "Cool", "Warm"};
    private static final String[] TEMP_RU = {"Стандарт", "Холодный", "Тёплый"};
    private static final String[] PC_EN = {"Auto", "Video", "PC"};
    private static final String[] PC_RU = {"Авто", "Видео", "ПК"};
    // order of the values in the helper's "pic" answer after mode and modes
    private static final String[] BASIC = {"brightness", "contrast", "saturation", "hue", "sharpness"};

    private final Root root = new Root();
    private final Map<String, Integer> pending = new LinkedHashMap<>();
    private final int[] basic = new int[5];
    private String mode = "";
    private String[] modes = new String[0];
    private int temp, pc;
    private boolean ready, sending;
    private TextView modeValue, tempValue, pcValue, status;
    private final TextView[] basicValue = new TextView[5];
    private View first;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout screen = new FrameLayout(this);
        LinearLayout p = panel(t("Picture", "Изображение"));
        first = row(p, t("Mode", "Режим"), "", v -> changeMode(1), this::changeMode);
        modeValue = (TextView) first.getTag();
        basicValue[0] = stepper(p, t("Brightness", "Яркость"), 0);
        basicValue[1] = stepper(p, t("Contrast", "Контраст"), 1);
        basicValue[2] = stepper(p, t("Saturation", "Насыщенность"), 2);
        basicValue[4] = stepper(p, t("Sharpness", "Резкость"), 4);
        tempValue = (TextView) row(p, t("Colour", "Цвет"), "", v -> changeTemp(1), this::changeTemp).getTag();
        pcValue = (TextView) row(p, t("HDMI signal", "Сигнал HDMI"), "", v -> changePc(1), this::changePc).getTag();
        row(p, t("Reset to Standard", "Сбросить на «Стандарт»"), "", v -> setMode("standard"), null);
        status = text(14, GREY);
        status.setPadding(dp(14), dp(14), dp(8), 0);
        status.setMaxWidth(dp(380));
        p.addView(status);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(420), ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.END | Gravity.CENTER_VERTICAL);
        lp.rightMargin = dp(36);
        screen.addView(p, lp);
        setContentView(screen);
        refresh();
        setStatus(t("Connecting…", "Подключение…"));
        root.start(this, r -> runOnUiThread(() -> {
            if (r == null || !r.startsWith("state")) {
                setStatus(t("No root access: ", "Нет root-доступа: ") + root.error());
                return;
            }
            root.query("pic", a -> runOnUiThread(() -> answer(a)));
        }));
    }

    @Override
    protected void onDestroy() {
        root.stop();
        super.onDestroy();
    }

    @Override
    public boolean onKeyUp(int code, KeyEvent e) {
        // Menu closes the panel again (it is also what opens it over HDMI)
        if (code == KeyEvent.KEYCODE_MENU) {
            finish();
            return true;
        }
        return super.onKeyUp(code, e);
    }

    private TextView stepper(LinearLayout p, String label, int index) {
        return (TextView) row(p, label, "", null, d -> changeBasic(index, d)).getTag();
    }

    private void answer(String a) {
        // ok mode modes b c s h sharp temp pc
        String[] f = a == null ? new String[0] : a.trim().split("\\s+");
        if (f.length < 10 || !f[0].equals("ok")) {
            setStatus(t("Picture: ", "Изображение: ") + (a != null && a.startsWith("err") ? a.substring(3).trim() : root.error()));
            return;
        }
        boolean firstAnswer = !ready;
        ready = true;
        if (!pending.isEmpty()) return;   // more changes are on their way; keep what the user sees
        mode = f[1];
        modes = f[2].split(",");
        for (int i = 0; i < 5; i++) basic[i] = parse(f[3 + i]);
        temp = parse(f[8]);
        pc = parse(f[9]);
        refresh();
        if (firstAnswer) {
            setStatus(t("Applies to HDMI. While HDMI plays, Menu on the remote opens the same settings over the picture.",
                    "Действует на HDMI. Прямо в HDMI те же настройки открывает кнопка Menu на пульте."));
            first.requestFocus();
        }
    }

    private void changeMode(int d) {
        if (!ready || modes.length == 0) return;
        int i = java.util.Arrays.asList(modes).indexOf(mode);
        setMode(modes[((i < 0 ? 0 : i) + d + modes.length) % modes.length]);
    }

    private void setMode(String next) {
        if (!ready) return;
        mode = next;
        refresh();
        root.query("picmode " + next, a -> runOnUiThread(() -> answer(a)));
    }

    private void changeBasic(int index, int d) {
        if (!ready) return;
        basic[index] = clamp(basic[index] + d, 0, 100);
        mode = "custom";   // the helper switches to "custom", carrying the current values over
        refresh();
        pending.put(BASIC[index], basic[index]);
        if (!sending) flush();
    }

    /** Sends the newest value of each changed setting; key repeats collapse into one call per setting. */
    private void flush() {
        if (pending.isEmpty()) {
            sending = false;
            return;
        }
        sending = true;
        Map.Entry<String, Integer> e = pending.entrySet().iterator().next();
        String key = e.getKey();
        int value = e.getValue();
        pending.remove(key);
        root.query("picset " + key + " " + value, a -> runOnUiThread(() -> {
            if (pending.isEmpty()) answer(a);
            flush();
        }));
    }

    private void changeTemp(int d) {
        if (!ready) return;
        temp = ((Math.min(temp, 2) + d) % 3 + 3) % 3;
        refresh();
        root.query("pictemp " + temp, a -> runOnUiThread(() -> answer(a)));
    }

    private void changePc(int d) {
        if (!ready) return;
        pc = ((pc + d) % 3 + 3) % 3;
        refresh();
        root.query("picpc " + pc, a -> runOnUiThread(() -> answer(a)));
    }

    private void refresh() {
        int m = java.util.Arrays.asList(MODE_KEYS).indexOf(mode);
        String name = m < 0 ? mode : (L.ru() ? MODE_RU : MODE_EN)[m];
        modeValue.setText(ready ? "‹   " + name + "   ›" : "…");
        for (int i = 0; i < 5; i++) {
            if (basicValue[i] != null) basicValue[i].setText(ready ? "‹   " + basic[i] + "   ›" : "…");
        }
        tempValue.setText(ready ? "‹   " + (L.ru() ? TEMP_RU : TEMP_EN)[Math.min(temp, 2)] + "   ›" : "…");
        pcValue.setText(ready ? "‹   " + (L.ru() ? PC_RU : PC_EN)[Math.min(pc, 2)] + "   ›" : "…");
    }

    private void setStatus(String s) {
        status.setText(s);
    }
}
