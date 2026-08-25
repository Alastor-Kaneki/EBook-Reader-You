package dev.alastorkaneki.cursedkeyboard;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private SharedPreferences prefs;
    private int surface;
    private int card;
    private int text;
    private int muted;
    private int accent;
    private int accentText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("cursed_keyboard_prefs", MODE_PRIVATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        buildUi();
    }

    private void buildUi() {
        loadColors();
        getWindow().setStatusBarColor(surface);
        getWindow().setNavigationBarColor(surface);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(surface);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(28), dp(20), dp(28));
        root.setBackgroundColor(surface);
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        TextView title = textView("Cursed Keyboard", 31f, text);
        title.setGravity(Gravity.START);
        root.addView(title);

        TextView subtitle = textView("Material You • QWERTY • glyphs • ciphers • Unicode styles", 14f, muted);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.topMargin = dp(4);
        root.addView(subtitle, subLp);

        TextView preview = textView(
                "Q  W  E  R  T  Y\n҂  Ш  ⊗  Я  Ŧ  ¥\n\n⌨   Ȝ   ↯   𝓕   #+=   123   ¤   ✦",
                20f, text);
        preview.setGravity(Gravity.CENTER);
        preview.setPadding(dp(16), dp(18), dp(16), dp(18));
        preview.setBackground(round(card, dp(22)));
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(-1, -2);
        previewLp.topMargin = dp(24);
        root.addView(preview, previewLp);

        addSectionTitle(root, "Keyboard");
        addPrimaryButton(root, "Enable keyboard", () ->
                startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        addSecondaryButton(root, "Choose keyboard", () -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showInputMethodPicker();
        });

        addSectionTitle(root, "Appearance & feedback");

        boolean haptics = prefs.getBoolean("haptics", true);
        addToggleButton(root,
                "Key haptics", haptics,
                "Short vibration on each key press",
                () -> {
                    prefs.edit().putBoolean("haptics", !prefs.getBoolean("haptics", true)).apply();
                    buildUi();
                });

        boolean amoled = prefs.getBoolean("amoled", false);
        addToggleButton(root,
                "AMOLED black", amoled,
                "Use pure black keyboard surface in dark mode",
                () -> {
                    prefs.edit().putBoolean("amoled", !prefs.getBoolean("amoled", false)).apply();
                    buildUi();
                });

        addSectionTitle(root, "Modes");

        addInfoCard(root, "Ȝ  Glyph",
                "English letters stay visible above the glyphs. Tap = glyph; long-press = normal letter.");
        addInfoCard(root, "↯  Nullfracture",
                "Live chained cipher output with deterministic decoy characters and reversible backspace state.");
        addInfoCard(root, "𝓕  Styled text",
                "Tap 𝓕 again while selected to cycle Fullwidth, Circled, Monospace, and Small Caps.");
        addInfoCard(root, "¤  Null / decoy",
                "Direct access to the intentionally meaningless null-character set.");

        TextView privacy = textView(
                "This build does not request internet permission. Keyboard output stays on-device.",
                12.5f, muted);
        privacy.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams privacyLp = new LinearLayout.LayoutParams(-1, -2);
        privacyLp.topMargin = dp(26);
        root.addView(privacy, privacyLp);

        setContentView(scroll);
    }

    private void loadColors() {
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        boolean amoled = prefs.getBoolean("amoled", false);

        if (dark) {
            surface = amoled ? Color.BLACK : systemColor("system_neutral1_1000", Color.rgb(20, 20, 24));
            card = systemColor("system_neutral2_900", Color.rgb(35, 35, 42));
            text = Color.WHITE;
            muted = systemColor("system_neutral2_200", Color.rgb(190, 190, 202));
            accent = systemColor("system_accent1_400", Color.rgb(179, 136, 255));
        } else {
            surface = systemColor("system_neutral1_50", Color.rgb(247, 245, 250));
            card = systemColor("system_neutral2_100", Color.rgb(232, 228, 236));
            text = Color.rgb(28, 27, 31);
            muted = systemColor("system_neutral2_700", Color.rgb(84, 80, 88));
            accent = systemColor("system_accent1_600", Color.rgb(103, 80, 164));
        }
        accentText = contrastText(accent);
    }

    private int systemColor(String name, int fallback) {
        int id = getResources().getIdentifier(name, "color", "android");
        if (id == 0) return fallback;
        try {
            return getColor(id);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private int contrastText(int color) {
        double luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0;
        return luminance > 0.58 ? Color.BLACK : Color.WHITE;
    }

    private void addSectionTitle(LinearLayout root, String label) {
        TextView title = textView(label, 16f, text);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(26);
        lp.bottomMargin = dp(8);
        root.addView(title, lp);
    }

    private void addPrimaryButton(LinearLayout root, String label, Runnable action) {
        Button button = baseButton(label, accent, accentText);
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(56));
        lp.topMargin = dp(6);
        root.addView(button, lp);
    }

    private void addSecondaryButton(LinearLayout root, String label, Runnable action) {
        Button button = baseButton(label, card, text);
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(56));
        lp.topMargin = dp(10);
        root.addView(button, lp);
    }

    private void addToggleButton(LinearLayout root, String title, boolean enabled, String description, Runnable action) {
        String label = title + "\n" + description + "\n" + (enabled ? "ON" : "OFF");
        Button button = baseButton(label, enabled ? blend(card, accent, 0.25f) : card, text);
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        button.setPadding(dp(18), dp(10), dp(18), dp(10));
        button.setTextSize(14f);
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(82));
        lp.topMargin = dp(10);
        root.addView(button, lp);
    }

    private void addInfoCard(LinearLayout root, String title, String body) {
        LinearLayout cardView = new LinearLayout(this);
        cardView.setOrientation(LinearLayout.VERTICAL);
        cardView.setPadding(dp(16), dp(14), dp(16), dp(14));
        cardView.setBackground(round(card, dp(18)));

        TextView t = textView(title, 16f, text);
        cardView.addView(t);
        TextView b = textView(body, 13f, muted);
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(-1, -2);
        bodyLp.topMargin = dp(4);
        cardView.addView(b, bodyLp);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(9);
        root.addView(cardView, lp);
    }

    private Button baseButton(String label, int background, int foreground) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(foreground);
        b.setTextSize(15f);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(12), 0, dp(12), 0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);

        GradientDrawable mask = round(background, dp(18));
        int rippleColor = Color.argb(80, Color.red(accent), Color.green(accent), Color.blue(accent));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(rippleColor), mask, null));
        return b;
    }

    private TextView textView(String value, float size, int color) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        return v;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(radius);
        return bg;
    }

    private int blend(int a, int b, float amount) {
        amount = Math.max(0f, Math.min(1f, amount));
        int r = Math.round(Color.red(a) * (1f - amount) + Color.red(b) * amount);
        int g = Math.round(Color.green(a) * (1f - amount) + Color.green(b) * amount);
        int bl = Math.round(Color.blue(a) * (1f - amount) + Color.blue(b) * amount);
        return Color.rgb(r, g, bl);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
