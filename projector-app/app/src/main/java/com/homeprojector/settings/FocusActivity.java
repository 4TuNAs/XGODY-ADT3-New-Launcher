package com.homeprojector.settings;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.WindowManager;

/** Full-screen focus chart: OK swaps black and white, Back returns. */
public class FocusActivity extends Activity {
    private PatternView chart;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        chart = new PatternView(this, PatternView.FOCUS);
        setContentView(chart);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            chart.toggleInverted();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
