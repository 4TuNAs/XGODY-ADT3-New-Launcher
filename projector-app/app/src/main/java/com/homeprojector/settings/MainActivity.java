package com.homeprojector.settings;

import static com.homeprojector.settings.L.t;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Projector settings. Left: picture geometry (keystone by corner, zoom, projection mode, focus chart).
 * Right: device switches (HDMI at startup, power-on mode, HDMI-CEC, Bluetooth pairing).
 * Keystone values are inward offsets of the four corners in panel pixels, the same ones the vendor app
 * writes; the display HAL keeps them in persist.display.keystone_* and applies them at every boot.
 */
public class MainActivity extends BaseActivity {
    private static final String[] PROJECTION_EN = {"Front", "Rear", "Ceiling", "Ceiling, rear"};
    private static final String[] PROJECTION_RU = {"Прямая", "Обратная", "Потолок", "Потолок, обратная"};
    private static final String[] CORNER_EN = {"Top-left", "Top-right", "Bottom-right", "Bottom-left"};
    private static final String[] CORNER_RU = {"Левый верхний", "Правый верхний", "Правый нижний", "Левый нижний"};
    // Offsets are ltx lty rtx rty lbx lby rbx rby. Per corner in OK order (TL, TR, BR, BL): which value
    // moves with Left/Right and Up/Down, and the sign that makes a Right or Down press move it that way.
    private static final int[] CX = {0, 2, 6, 4}, CY = {1, 3, 7, 5};
    private static final int[] SX = {1, -1, -1, 1}, SY = {1, 1, -1, -1};
    private static final int MAX = 400, STEP = 4, ZOOM_STEPS = 25, PANEL_W = 1280;
    private static final String PAIRING = "com.google.android.intent.action.CONNECT_INPUT";

    private final Root root = new Root();
    private final int[] base = new int[8];
    private int zoom, projection, corner = -1, powerMode = -1;
    private int cecEnabled = -1, cecWake = -1, cecStandby = -1, castScreen = -1, fast = -1, keys = -1;
    private boolean ready;
    private SharedPreferences prefs;
    private PatternView pattern;
    private LinearLayout panels;
    private TextView status, zoomValue, projectionValue, hint;
    private TextView hdmiCheck, powerCheck, cecCheck, wakeCheck, standbyCheck, keysCheck, screenCheck, fastCheck;
    private View keystoneRow;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(BootReceiver.PREFS, MODE_PRIVATE);
        zoom = prefs.getInt("zoom", 0);

        FrameLayout screen = new FrameLayout(this);
        pattern = new PatternView(this, PatternView.GRID);
        screen.addView(pattern, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        panels = new LinearLayout(this);
        panels.setOrientation(LinearLayout.HORIZONTAL);
        panels.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(410), ViewGroup.LayoutParams.WRAP_CONTENT);
        panels.addView(picturePanel(), lp);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(dp(410), ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.leftMargin = dp(24);
        panels.addView(devicePanel(), rp);
        screen.addView(panels, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        hint = text(17, WHITE);
        hint.setGravity(Gravity.CENTER);
        hint.setLineSpacing(0, 1.25f);
        hint.setBackground(glass(18));
        hint.setPadding(dp(28), dp(16), dp(28), dp(16));
        hint.setVisibility(View.GONE);
        screen.addView(hint, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        setContentView(screen);
        refresh();
        setStatus(t("Connecting to the display service…", "Подключение к дисплейному сервису…"));
        root.start(this, result -> runOnUiThread(() -> connected(result)));
    }

    @Override
    protected void onDestroy() {
        root.stop();
        super.onDestroy();
    }

    // The Picture panel is see-through (for HDMI behind it); over this screen only the test grid should show.
    @Override
    protected void onPause() {
        super.onPause();
        if (corner < 0) panels.setVisibility(View.INVISIBLE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (corner < 0) panels.setVisibility(View.VISIBLE);
    }

    private LinearLayout picturePanel() {
        LinearLayout p = panel(t("Projector", "Проектор"));
        keystoneRow = row(p, t("Keystone", "Трапеция"), t("4 corners  ›", "4 угла  ›"), v -> enterKeystone(), null);
        zoomValue = (TextView) row(p, t("Zoom", "Масштаб"), "", null, this::changeZoom).getTag();
        projectionValue = (TextView) row(p, t("Projection", "Проекция"), "", v -> changeProjection(1), this::changeProjection).getTag();
        row(p, t("Picture", "Изображение"), t("Open  ›", "Открыть  ›"), v -> startActivity(new Intent(this, PictureActivity.class)), null);
        row(p, t("Focus chart", "Таблица фокуса"), t("Open  ›", "Открыть  ›"), v -> startActivity(new Intent(this, FocusActivity.class)), null);
        row(p, t("Reset geometry", "Сбросить геометрию"), "", v -> reset(), null);
        status = text(14, GREY);
        status.setPadding(dp(14), dp(16), dp(8), 0);
        p.addView(status);
        return p;
    }

    private LinearLayout devicePanel() {
        LinearLayout p = panel(t("Device", "Устройство"));
        hdmiCheck = checkRow(p, t("HDMI at startup", "HDMI при включении"), v -> toggleHdmi());
        powerCheck = checkRow(p, t("Turn on with power", "Включаться от розетки"), v -> togglePower());
        cecCheck = checkRow(p, "HDMI-CEC", v -> toggleCec("hdmi_cec_enabled", cecEnabled));
        wakeCheck = checkRow(p, t("Wake with the source", "Включаться с приставкой"), v -> toggleCec("tv_wake_on_one_touch_play", cecWake));
        standbyCheck = checkRow(p, t("Switch the source off too", "Выключать приставку"), v -> toggleCec("tv_send_standby_on_sleep", cecStandby));
        keysCheck = checkRow(p, t("Remote to the HDMI box", "Пульт → приставке в HDMI"), v -> toggleKeys());
        screenCheck = checkRow(p, "AirPlay / DLNA", v -> toggleCast("screen", castScreen));
        fastCheck = checkRow(p, t("Faster interface", "Быстрый интерфейс"), v -> toggleFast());
        row(p, t("Pair remote or gamepad", "Подключить пульт / геймпад"), "›", v -> pair(), null);
        return p;
    }

    private void connected(String state) {
        if (state == null || !state.startsWith("state")) {
            setStatus(t("No root access: ", "Нет root-доступа: ") + root.error());
            return;
        }
        String[] a = state.trim().split("\\s+");
        if (a.length < 10) {
            setStatus("Unexpected answer: " + state);
            return;
        }
        int ix = insetX(), iy = insetY();
        for (int i = 0; i < 8; i++) base[i] = Math.max(0, parse(a[i + 1]) - (i % 2 == 0 ? ix : iy));
        projection = Math.max(0, Math.min(3, parse(a[9])));
        ready = true;
        setStatus(t("Saved automatically, kept after a restart", "Сохраняется само и после перезагрузки"));
        refresh();
        keystoneRow.requestFocus();
        root.query("pmode", r -> runOnUiThread(() -> powerAnswer(r, null)));
        root.query("cec", r -> runOnUiThread(() -> cecAnswer(r, null)));
        root.query("cast", r -> runOnUiThread(() -> castAnswer(r, null)));
        root.query("anim", r -> runOnUiThread(() -> fastAnswer(r, null)));
        root.query("keysvc", r -> runOnUiThread(() -> keysAnswer(r, null)));
    }

    private boolean needRoot() {
        if (ready) return false;
        setStatus(t("No root access: ", "Нет root-доступа: ") + root.error());
        return true;
    }

    private void changeZoom(int d) {
        if (needRoot()) return;
        zoom = clamp(zoom + d, 0, ZOOM_STEPS);
        prefs.edit().putInt("zoom", zoom).apply();
        for (int i = 0; i < 8; i++) base[i] = clamp(base[i], 0, MAX - (i % 2 == 0 ? insetX() : insetY()));
        apply();
        refresh();
    }

    private void changeProjection(int d) {
        if (needRoot()) return;
        projection = (projection + d + 4) % 4;
        root.command("proj " + projection);
        refresh();
    }

    private void toggleHdmi() {
        boolean on = !prefs.getBoolean(BootReceiver.KEY, false);
        prefs.edit().putBoolean(BootReceiver.KEY, on).apply();
        refresh();
        setStatus(on ? t("HDMI opens by itself after power-on", "После включения сразу откроется HDMI")
                : t("Starts on the home screen", "После включения — главный экран"));
    }

    private void togglePower() {
        if (needRoot() || powerMode < 0) return;
        int next = powerMode == 1 ? 0 : 1;
        root.query("pmode " + next, r -> runOnUiThread(() -> powerAnswer(r, next == 1
                ? t("Turns on as soon as it gets power", "Включится сразу при подаче питания")
                : t("Waits in standby for the remote", "Ждёт в режиме ожидания кнопку пульта"))));
    }

    private void powerAnswer(String answer, String message) {
        if (answer == null || !answer.startsWith("ok ")) {
            setStatus(t("Power-on mode: ", "Режим включения: ") + root.error());
            return;
        }
        powerMode = parse(answer.substring(3));
        refresh();
        if (message != null) setStatus(message);
    }

    private void toggleCec(String name, int current) {
        if (needRoot() || current < 0) return;
        int next = current == 1 ? 0 : 1;
        root.query("cec set " + name + " " + next, r -> runOnUiThread(() -> cecAnswer(r,
                t("HDMI-CEC saved", "HDMI-CEC сохранено"))));
    }

    private void cecAnswer(String answer, String message) {
        String[] a = answer == null ? new String[0] : answer.trim().split("\\s+");
        if (a.length < 4 || !a[0].equals("ok")) {
            setStatus("HDMI-CEC: " + root.error());
            return;
        }
        cecEnabled = parse(a[1]);
        cecWake = parse(a[2]);
        cecStandby = parse(a[3]);
        refresh();
        if (message != null) setStatus(message);
    }

    private void toggleCast(String which, int current) {
        if (needRoot() || current < 0) return;
        int next = current == 1 ? 0 : 1;
        String name = "AirPlay / DLNA";
        root.query("cast " + which + " " + next, r -> runOnUiThread(() -> castAnswer(r, next == 1
                ? name + t(": on, kept running in the background", ": включено, работает в фоне")
                : name + t(": off, memory freed", ": выключено, память освобождена"))));
    }

    private void castAnswer(String answer, String message) {
        String[] a = answer == null ? new String[0] : answer.trim().split("\\s+");
        if (a.length < 3 || !a[0].equals("ok")) {
            setStatus(t("Casting: ", "Трансляция: ") + root.error());
            return;
        }
        castScreen = parse(a[2]);
        refresh();
        if (message != null) setStatus(message);
    }

    private void toggleFast() {
        if (needRoot() || fast < 0) return;
        int next = fast == 1 ? 0 : 1;
        root.query("anim " + next, r -> runOnUiThread(() -> fastAnswer(r, next == 1
                ? t("Animations at half length: menus feel quicker", "Анимации вдвое короче: меню откликаются быстрее")
                : t("Standard animations", "Обычные анимации"))));
    }

    private void fastAnswer(String answer, String message) {
        if (answer == null || !answer.startsWith("ok ")) {
            setStatus(t("Animations: ", "Анимации: ") + root.error());
            return;
        }
        fast = parse(answer.substring(3));
        refresh();
        if (message != null) setStatus(message);
    }

    private void toggleKeys() {
        if (needRoot() || keys < 0) return;
        int next = keys == 1 ? 0 : 1;
        root.query("keysvc " + next, r -> runOnUiThread(() -> keysAnswer(r, next == 1
                ? t("In HDMI: Back and Home go to the box; Input or a long Back leaves HDMI",
                    "В HDMI: «Назад» и «Домой» идут на приставку; выход — Source или долгое «Назад»")
                : t("The remote works as before in HDMI", "Пульт в HDMI работает как раньше"))));
    }

    private void keysAnswer(String answer, String message) {
        if (answer == null || !answer.startsWith("ok ")) {
            setStatus(t("Remote: ", "Пульт: ") + root.error());
            return;
        }
        keys = parse(answer.substring(3));
        refresh();
        if (message != null) setStatus(message);
    }

    private void pair() {
        // TvSettings protects its accessory search; only root (or the system) may start it.
        if (ready) {
            root.command("pair");
            return;
        }
        try {
            startActivity(new Intent(PAIRING));
        } catch (RuntimeException e) {
            setStatus(t("The pairing screen is not available", "Экран сопряжения недоступен"));
        }
    }

    private void reset() {
        if (needRoot()) return;
        java.util.Arrays.fill(base, 0);
        zoom = 0;
        prefs.edit().putInt("zoom", 0).apply();
        apply();
        refresh();
        setStatus(t("Keystone and zoom reset", "Трапеция и масштаб сброшены"));
    }

    private void apply() {
        int[] c = new int[8];
        for (int i = 0; i < 8; i++) c[i] = clamp(base[i] + (i % 2 == 0 ? insetX() : insetY()), 0, MAX);
        root.corners(c);
    }

    private void enterKeystone() {
        if (needRoot()) return;
        corner = 0;
        panels.setVisibility(View.GONE);
        hint.setVisibility(View.VISIBLE);
        pattern.setActiveCorner(corner);
        updateHint();
    }

    private void exitKeystone() {
        corner = -1;
        pattern.setActiveCorner(-1);
        hint.setVisibility(View.GONE);
        panels.setVisibility(View.VISIBLE);
        keystoneRow.requestFocus();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (corner < 0) return super.dispatchKeyEvent(e);
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        int limX = MAX - insetX(), limY = MAX - insetY();
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                move(CX[corner], -SX[corner], limX);
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                move(CX[corner], SX[corner], limX);
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                move(CY[corner], -SY[corner], limY);
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                move(CY[corner], SY[corner], limY);
                break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                corner = (corner + 1) % 4;
                pattern.setActiveCorner(corner);
                break;
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
                exitKeystone();
                return true;
            default:
                return super.dispatchKeyEvent(e);
        }
        updateHint();
        return true;
    }

    private void move(int index, int direction, int limit) {
        base[index] = clamp(base[index] + direction * STEP, 0, limit);
        apply();
    }

    private void updateHint() {
        String name = (L.ru() ? CORNER_RU : CORNER_EN)[corner];
        hint.setText(t(name + " corner     x " + base[CX[corner]] + "   y " + base[CY[corner]]
                        + "\nArrows — move   ·   OK — next corner   ·   Back — done",
                name + " угол     x " + base[CX[corner]] + "   y " + base[CY[corner]]
                        + "\nСтрелки — двигать   ·   OK — следующий угол   ·   Назад — готово"));
    }

    private void refresh() {
        zoomValue.setText("‹   " + zoomPercent() + "%   ›");
        projectionValue.setText("‹   " + (L.ru() ? PROJECTION_RU : PROJECTION_EN)[projection] + "   ›");
        check(hdmiCheck, prefs.getBoolean(BootReceiver.KEY, false));
        check(powerCheck, powerMode == 1);
        check(cecCheck, cecEnabled == 1);
        check(wakeCheck, cecWake == 1);
        check(standbyCheck, cecStandby == 1);
        check(keysCheck, keys == 1);
        check(screenCheck, castScreen == 1);
        check(fastCheck, fast == 1);
    }

    private void setStatus(String s) {
        status.setText(s);
    }

    private int insetX() {
        return zoom * 8;
    }

    private int insetY() {
        return Math.round(zoom * 4.5f);
    }

    private int zoomPercent() {
        return Math.round(100f * (PANEL_W - 2 * insetX()) / PANEL_W);
    }

}
