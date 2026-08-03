package com.alastorkaneki.ebookreaderyou;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;

public final class ThemeManager {
    private static final String PREFS = "appearance";
    private static final String AMOLED = "amoled";
    private static final String DYNAMIC = "dynamic";
    private static final String IMMERSIVE = "immersive";
    private final SharedPreferences preferences;
    public final int background;
    public final int surface;
    public final int surfaceHigh;
    public final int text;
    public final int textMuted;
    public final int accent;
    public final int accentContainer;
    public final boolean amoled;
    public final boolean dynamic;

    public ThemeManager(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        amoled = preferences.getBoolean(AMOLED, true);
        dynamic = preferences.getBoolean(DYNAMIC, true);
        background = amoled ? Color.BLACK : Color.rgb(16, 16, 22);
        surface = amoled ? Color.rgb(8, 8, 10) : Color.rgb(27, 27, 35);
        surfaceHigh = amoled ? Color.rgb(18, 18, 22) : Color.rgb(42, 41, 52);
        text = Color.rgb(245, 243, 250);
        textMuted = Color.rgb(188, 184, 198);
        int dynamicAccent = Color.rgb(139, 92, 246);
        int dynamicContainer = Color.rgb(62, 43, 91);
        if (dynamic && Build.VERSION.SDK_INT >= 31) {
            int accentId = context.getResources().getIdentifier("system_accent1_300", "color", "android");
            int containerId = context.getResources().getIdentifier("system_accent1_800", "color", "android");
            if (accentId != 0) dynamicAccent = context.getColor(accentId);
            if (containerId != 0) dynamicContainer = context.getColor(containerId);
        }
        accent = dynamicAccent;
        accentContainer = dynamicContainer;
    }

    public void setAmoled(boolean value) {
        preferences.edit().putBoolean(AMOLED, value).apply();
    }

    public void setDynamic(boolean value) {
        preferences.edit().putBoolean(DYNAMIC, value).apply();
    }

    public boolean isImmersive() {
        return preferences.getBoolean(IMMERSIVE, true);
    }

    public void setImmersive(boolean value) {
        preferences.edit().putBoolean(IMMERSIVE, value).apply();
    }

    public static void applyWindow(Activity activity, int background, boolean immersive) {
        Window window = activity.getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(background);
        window.getDecorView().setBackgroundColor(background);
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
                if (immersive) {
                    controller.hide(WindowInsets.Type.systemBars());
                    controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                } else {
                    controller.show(WindowInsets.Type.systemBars());
                }
            }
        } else if (immersive) {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                            View.SYSTEM_UI_FLAG_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
        }
    }
}
