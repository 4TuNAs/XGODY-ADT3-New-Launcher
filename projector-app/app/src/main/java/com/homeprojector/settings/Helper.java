package com.homeprojector.settings;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.lang.reflect.Method;

/**
 * Runs as root through app_process, with this APK and the vendor settings APK (com.mylove.setting) on the
 * classpath. It drives the same Allwinner display manager the vendor app uses, so keystone, zoom and the
 * projection mode end up in the same persist.* properties and are put back by the display HAL at boot.
 *
 * Protocol, one line each way:
 *   state                              -> state ltx lty rtx rty lbx lby rbx rby projection
 *   ks ltx lty rtx rty lbx lby rbx rby -> ok    (inward offsets in panel pixels)
 *   proj n                             -> ok    (0 front, 1 rear, 2 ceiling, 3 ceiling + rear)
 *   pmode [n]                          -> ok n  (power-on mode: 0 standby, 1 on with mains power, 2 last state)
 *   cec [set name 0|1]                 -> ok enabled wake standby   (HDMI-CEC settings of the framework)
 *   cast [screen 0|1]                  -> ok 0 screen   (AirPlay/DLNA kept running by cyt-cast.sh; Google Cast was removed)
 *   anim [0|1]                         -> ok n  (1: window/transition/animator scales 0.5x, 0: back to 1x)
 *   pair                               -> ok    (opens the system Bluetooth accessory search; apps may not start it)
 *   pq                                 -> ok mode=.. modes=[..] brightness=.. contrast=.. ... (picture settings of the current source)
 *   pqset name value                   -> ok ...  (mode <name> | brightness|contrast|saturation|hue|sharpness 0..100
 *                                                  | backlight n | range n | pc 0 auto,1 video,2 PC | temp 0..3)
 *   pic                                -> ok mode modes b c s h sharp temp pc   (for the Picture screen)
 *   picmode name | picset key 0..100 | pictemp n | picpc n -> same as pic
 *   keysvc [0|1]                       -> ok n  (the accessibility service that sends remote keys to the HDMI source)
 *
 * Started with the argument "keys" it is a small CEC key sender instead (one line per key: key CODE -> sent | none),
 * used by KeyService while HDMI is on screen.
 *   quit                               -> ok
 */
public final class Helper {
    private static final String[] KEYS = {"ltx", "lty", "rtx", "rty", "lbx", "lby", "rbx", "rby"};

    public static void main(String[] args) {
        PrintStream out = System.out;
        try {
            if (args.length > 0 && args[0].equals("keys")) runKeys(out);
            else run(out);
        } catch (Throwable t) {
            out.println("err " + t);
            out.flush();
        }
    }

    private static void run(PrintStream out) throws Exception {
        if (android.os.Looper.myLooper() == null) android.os.Looper.prepareMainLooper();
        Class<?> props = Class.forName("android.os.SystemProperties");
        Method get = props.getMethod("get", String.class);
        Method set = props.getMethod("set", String.class, String.class);
        Class<?> manager = Class.forName("com.softwinner.tv.AwTvDisplayManager");
        Object display = manager.getMethod("getInstance").invoke(null);
        Method keystone = manager.getMethod("setKeystoreValue", float.class, float.class,
                int.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class);
        Class<?> type = Class.forName("com.softwinner.tv.common.AwTvDisplayTypes$EnumPanelConfigType");
        Object mirror = enumValue(type, "E_AW_PANEL_CONFIG_MIRROR");
        Method setPanel = manager.getMethod("factorySetPanelValue", type, int.class);
        Method getPanel = manager.getMethod("factoryGetPanelValue", type);

        Object server = Class.forName("com.softwinner.tv.client.AwTvServerManagerProxy")
                .getMethod("getInstance").invoke(null);
        Object tv = server.getClass().getMethod("getAwTvServerManager").invoke(server);
        Method setPower = tv.getClass().getMethod("factorySetPowerMode", int.class);
        Method getPower = tv.getClass().getMethod("factoryGetPowerMode");

        out.println("ready");
        out.flush();
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        String line;
        while ((line = in.readLine()) != null) {
            String[] a = line.trim().split("\\s+");
            try {
                switch (a[0]) {
                    case "state": {
                        StringBuilder sb = new StringBuilder("state");
                        for (String k : KEYS) sb.append(' ').append(number((String) get.invoke(null, "persist.display.keystone_" + k)));
                        sb.append(' ').append(getPanel.invoke(display, mirror));
                        out.println(sb);
                        break;
                    }
                    case "ks": {
                        int[] v = new int[8];
                        for (int i = 0; i < 8; i++) v[i] = Math.abs(Integer.parseInt(a[i + 1]));
                        // setKeystoreValue(zoomH, zoomV, lbx, lby, rbx, rby, ltx, lty, rtx, rty); it also writes the
                        // persist.display.keystone_* properties the HAL reads at boot.
                        keystone.invoke(display, 100f, 100f, v[4], v[5], v[6], v[7], v[0], v[1], v[2], v[3]);
                        out.println("ok");
                        break;
                    }
                    case "proj": {
                        int n = Integer.parseInt(a[1]);
                        setPanel.invoke(display, mirror, n);
                        set.invoke(null, "persist.sys.projection", String.valueOf(n));
                        out.println("ok");
                        break;
                    }
                    case "pmode": {
                        if (a.length > 1) setPower.invoke(tv, Integer.parseInt(a[1]));
                        out.println("ok " + getPower.invoke(tv));
                        break;
                    }
                    case "cec": {
                        if (a.length >= 4 && a[1].equals("set") && CEC.contains(a[2]) && a[3].matches("[01]")) {
                            exec("cmd", "hdmi_control", "cec_setting", "set", a[2], a[3]);
                        }
                        out.println("ok " + cec("hdmi_cec_enabled") + " " + cec("tv_wake_on_one_touch_play")
                                + " " + cec("tv_send_standby_on_sleep"));
                        break;
                    }
                    case "cast": {
                        if (a.length >= 3 && a[1].equals("screen") && a[2].matches("[01]")) {
                            boolean google = false;
                            java.io.File dir = new java.io.File(CAST_DIR);
                            dir.mkdirs();
                            java.io.File flag = new java.io.File(dir, "cast_" + a[1]);
                            if (a[2].equals("1")) {
                                flag.createNewFile();
                                exec("sh", "-c", START_SCREEN);
                            } else {
                                flag.delete();
                                // force-stop also keeps the app from starting itself again at boot
                                exec("am", "force-stop", "com.android.toofifi");
                            }
                        }
                        out.println("ok " + (new java.io.File(CAST_DIR, "cast_google").exists() ? 1 : 0)
                                + " " + (new java.io.File(CAST_DIR, "cast_screen").exists() ? 1 : 0));
                        break;
                    }
                    case "anim": {
                        if (a.length >= 2 && a[1].matches("[01]")) {
                            String scale = a[1].equals("1") ? "0.5" : "1.0";
                            for (String k : new String[]{"window_animation_scale", "transition_animation_scale", "animator_duration_scale"}) {
                                exec("settings", "put", "global", k, scale);
                            }
                        }
                        String now = exec("settings", "get", "global", "window_animation_scale").trim();
                        out.println("ok " + (now.startsWith("0.5") ? 1 : 0));
                        break;
                    }
                    case "pair": {
                        exec("am", "start", "-a", "com.google.android.intent.action.CONNECT_INPUT");
                        out.println("ok");
                        break;
                    }
                    case "pq":
                        out.println("ok " + pq(display, tv));
                        break;
                    case "pqset":
                        pqset(display, tv, a[1], a[2]);
                        out.println("ok " + pq(display, tv));
                        break;
                    case "pic":
                        out.println(pic(display, tv));
                        break;
                    case "picmode":
                        tv.getClass().getMethod("setPictureModeByName", String.class).invoke(tv, a[1]);
                        out.println(pic(display, tv));
                        break;
                    case "picset":
                        picset(display, tv, a[1], Math.max(0, Math.min(100, Integer.parseInt(a[2]))));
                        out.println(pic(display, tv));
                        break;
                    case "picpreset": {
                        // repair tool: writes a value into the mode on screen, whichever it is (presets included)
                        Class<?> basic = Class.forName(TYPES + "EnumPQBasicType");
                        Object max = enumValue(Class.forName(TYPES + "EnumTvSourceType"), "E_AW_SOURCE_TYPE_MAX");
                        String current = (String) tv.getClass().getMethod("getPictureModeName").invoke(tv);
                        display.getClass().getMethod("factorySetBasicControl", int.class, String.class, basic, int.class)
                                .invoke(display, (Integer) max.getClass().getMethod("getValue").invoke(max), current,
                                        basic.getEnumConstants()[java.util.Arrays.asList(BASIC).indexOf(a[1])],
                                        Integer.parseInt(a[2]));
                        out.println(pic(display, tv));
                        break;
                    }
                    case "pictemp":
                        pqset(display, tv, "temp", a[1]);
                        out.println(pic(display, tv));
                        break;
                    case "picpc":
                        pqset(display, tv, "pc", a[1]);
                        out.println(pic(display, tv));
                        break;
                    case "keysvc":
                        out.println("ok " + keyService(a.length > 1 ? a[1] : null));
                        break;
                    case "quit":
                        out.println("ok");
                        out.flush();
                        return;
                    default:
                        out.println("err unknown command " + a[0]);
                }
            } catch (Throwable t) {
                Throwable cause = t.getCause() != null ? t.getCause() : t;
                out.println("err " + cause);
            }
            out.flush();
        }
    }

    private static final String TYPES = "com.softwinner.tv.common.AwTvDisplayTypes$";
    private static final String[] BASIC = {"brightness", "contrast", "saturation", "hue", "sharpness"};

    /** Picture settings as the tv server reports them for the source on screen. */
    private static String pq(Object display, Object tv) {
        StringBuilder sb = new StringBuilder();
        sb.append("mode=").append(call(tv, "getPictureModeName"));
        sb.append(" modes=").append(String.valueOf(call(tv, "getPictureModeList")).replace(" ", ""));
        try {
            Class<?> basic = Class.forName(TYPES + "EnumPQBasicType");
            Method get = display.getClass().getMethod("getBasicControl", basic);
            for (Object e : basic.getEnumConstants()) {
                String name = ((Enum<?>) e).name().replace("E_AW_PQ_BASIC_TYPE_", "").toLowerCase();
                sb.append(' ').append(name).append('=').append(get.invoke(display, e));
            }
        } catch (Throwable t) {
            sb.append(" basic=err");
        }
        for (String m : new String[]{"getBacklight", "getColorTemp", "getGammaFactor", "getVideoRange",
                "getHDMIVideoPCMode", "getDynamicBacklight"}) {
            sb.append(' ').append(m.substring(3).toLowerCase()).append('=').append(call(display, m));
        }
        return sb.toString();
    }

    private static void pqset(Object display, Object tv, String name, String value) throws Exception {
        Class<?> dm = display.getClass();
        if (name.equals("mode")) {
            tv.getClass().getMethod("setPictureModeByName", String.class).invoke(tv, value);
            return;
        }
        int n = Integer.parseInt(value);
        int basic = java.util.Arrays.asList(BASIC).indexOf(name);
        if (basic >= 0) {
            Class<?> type = Class.forName(TYPES + "EnumPQBasicType");
            dm.getMethod("setBasicControl", type, int.class).invoke(display, type.getEnumConstants()[basic], n);
        } else if (name.equals("backlight")) {
            dm.getMethod("setBacklight", int.class).invoke(display, n);
        } else if (name.equals("range")) {
            dm.getMethod("setVideoRange", int.class).invoke(display, n);
        } else if (name.equals("pc")) {
            Class<?> type = Class.forName(TYPES + "EnumVideoPCMode");
            dm.getMethod("setHDMIVideoPCMode", type).invoke(display, type.getEnumConstants()[n]);
        } else if (name.equals("temp")) {
            Class<?> type = Class.forName(TYPES + "EnumColorTempMode");
            dm.getMethod("setColorTemp", type).invoke(display, type.getEnumConstants()[n]);
        } else {
            throw new IllegalArgumentException("unknown picture setting " + name);
        }
    }

    /** "ok mode modes b c s h sharp temp pc", modes comma separated, temp and pc as enum ordinals. */
    private static String pic(Object display, Object tv) throws Exception {
        StringBuilder sb = new StringBuilder("ok ");
        sb.append(tv.getClass().getMethod("getPictureModeName").invoke(tv)).append(' ');
        Object modes = tv.getClass().getMethod("getPictureModeList").invoke(tv);
        sb.append(String.join(",", (java.util.List<String>) modes));
        Class<?> basic = Class.forName(TYPES + "EnumPQBasicType");
        Method get = display.getClass().getMethod("getBasicControl", basic);
        for (Object e : basic.getEnumConstants()) sb.append(' ').append(get.invoke(display, e));
        sb.append(' ').append(((Enum<?>) display.getClass().getMethod("getColorTemp").invoke(display)).ordinal());
        sb.append(' ').append(((Enum<?>) display.getClass().getMethod("getHDMIVideoPCMode").invoke(display)).ordinal());
        return sb.toString();
    }

    /**
     * Brightness and the rest can only be changed in the "custom" mode, as in the vendor app. Moving a slider in
     * another mode first copies that mode's values into "custom", so the picture does not jump.
     */
    private static void picset(Object display, Object tv, String key, int value) throws Exception {
        int index = java.util.Arrays.asList(BASIC).indexOf(key);
        if (index < 0) throw new IllegalArgumentException("unknown picture setting " + key);
        Class<?> basic = Class.forName(TYPES + "EnumPQBasicType");
        Object[] types = basic.getEnumConstants();
        Class<?> dm = display.getClass();
        Method get = dm.getMethod("getBasicControl", basic);
        Method store = dm.getMethod("factorySetBasicControl", int.class, String.class, basic, int.class);
        Object max = enumValue(Class.forName(TYPES + "EnumTvSourceType"), "E_AW_SOURCE_TYPE_MAX");
        int all = (Integer) max.getClass().getMethod("getValue").invoke(max);
        Method setMode = tv.getClass().getMethod("setPictureModeByName", String.class);
        if (!"custom".equals(tv.getClass().getMethod("getPictureModeName").invoke(tv))) {
            // The tv server only takes values for the mode on screen (as in the vendor app, which edits "custom"
            // only): switch first, then write the preset's values into it.
            int[] seed = new int[types.length];
            for (int i = 0; i < types.length; i++) seed[i] = (Integer) get.invoke(display, types[i]);
            setMode.invoke(tv, "custom");
            for (int i = 0; i < types.length; i++) store.invoke(display, all, "custom", types[i], seed[i]);
        }
        store.invoke(display, all, "custom", types[index], value);
    }

    private static final String KEY_SERVICE = "com.homeprojector.settings/.KeyService";

    private static int keyService(String want) throws Exception {
        String full = "com.homeprojector.settings/com.homeprojector.settings.KeyService";
        String current = exec("settings", "get", "secure", "enabled_accessibility_services").trim();
        java.util.List<String> keep = new java.util.ArrayList<>();
        if (!current.isEmpty() && !current.equals("null")) {
            for (String s : current.split(":")) {
                if (!s.isEmpty() && !s.equals(KEY_SERVICE) && !s.equals(full)) keep.add(s);
            }
        }
        // The owner's choice is the flag file: the firmware empties the accessibility list on every focus change,
        // and cyt-keys.sh (Magisk) puts the service back while HDMI is in front as long as the flag exists.
        java.io.File flag = new java.io.File(CAST_DIR, "keys");
        boolean on = flag.exists();
        if (want != null && want.matches("[01]")) {
            on = want.equals("1");
            flag.getParentFile().mkdirs();
            if (on) flag.createNewFile();
            else flag.delete();
            if (on) keep.add(KEY_SERVICE);
            if (keep.isEmpty()) exec("settings", "delete", "secure", "enabled_accessibility_services");
            else exec("settings", "put", "secure", "enabled_accessibility_services", String.join(":", keep));
            exec("settings", "put", "secure", "accessibility_enabled", keep.isEmpty() ? "0" : "1");
        }
        return on ? 1 : 0;
    }

    /** CEC key sender: the projector is the CEC TV, keys go to the active source (the box on HDMI). */
    private static void runKeys(PrintStream out) throws Exception {
        Class<?> manager = Class.forName("android.os.ServiceManager");
        android.os.IBinder binder = (android.os.IBinder) manager.getMethod("getService", String.class)
                .invoke(null, "hdmi_control");
        Object hdmi = Class.forName("android.hardware.hdmi.IHdmiControlService$Stub")
                .getMethod("asInterface", android.os.IBinder.class).invoke(null, binder);
        Method send = hdmi.getClass().getMethod("sendKeyEvent", int.class, int.class, boolean.class);
        Method active = hdmi.getClass().getMethod("getActiveSource");
        out.println("ready");
        out.flush();
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        String line;
        while ((line = in.readLine()) != null) {
            String[] a = line.trim().split("\\s+");
            if (a[0].equals("quit")) return;
            if (!a[0].equals("key") || a.length < 2) continue;
            String answer;
            try {
                Object source = active.invoke(hdmi);
                int address = source == null ? -1
                        : (Integer) source.getClass().getMethod("getLogicalAddress").invoke(source);
                // 0 is the projector itself, 15 is unregistered; a plain HDMI source has no CEC address
                if (address > 0 && address < 15) {
                    int code = Integer.parseInt(a[1]);
                    send.invoke(hdmi, 0, code, true);
                    send.invoke(hdmi, 0, code, false);
                    answer = "sent";
                } else {
                    answer = "none";
                }
            } catch (Throwable t) {
                answer = "none " + (t.getCause() != null ? t.getCause() : t);
            }
            out.println(answer);
            out.flush();
        }
    }

    private static Object call(Object target, String method) {
        try {
            return target.getClass().getMethod(method).invoke(target);
        } catch (Throwable t) {
            Throwable c = t.getCause() != null ? t.getCause() : t;
            return "err:" + c.getClass().getSimpleName();
        }
    }

    private static final String CAST_DIR = "/data/adb/cyt";
    private static final String START_SCREEN = "for s in .service.LicenseCheckService .service.PairService"
            + " .service.PincodeService .service.ExtraService .service.RemoteService;"
            + " do am startservice -n com.android.toofifi/$s; done";

    private static final java.util.List<String> CEC = java.util.Arrays.asList(
            "hdmi_cec_enabled", "tv_wake_on_one_touch_play", "tv_send_standby_on_sleep");

    /** "name = 1" from `cmd hdmi_control cec_setting get name`, as 0 or 1. */
    private static int cec(String name) throws Exception {
        String r = exec("cmd", "hdmi_control", "cec_setting", "get", name).trim();
        return r.endsWith("1") ? 1 : 0;
    }

    private static String exec(String... command) throws Exception {
        Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
        }
        p.waitFor();
        return sb.toString();
    }

    private static int number(String s) {
        try {
            return (int) Math.round(Double.parseDouble(s));
        } catch (RuntimeException e) {
            return 0;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumValue(Class<?> type, String name) {
        return Enum.valueOf((Class) type, name);
    }
}
