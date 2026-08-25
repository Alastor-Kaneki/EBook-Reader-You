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
    private int surface, card, text, muted, accent, accentText;

    @Override protected void onCreate(Bundle savedInstanceState) { super.onCreate(savedInstanceState); prefs = getSharedPreferences("cursed_keyboard_prefs", MODE_PRIVATE); }
    @Override protected void onResume() { super.onResume(); buildUi(); }

    private void buildUi() {
        loadColors();
        getWindow().setStatusBarColor(surface);
        getWindow().setNavigationBarColor(surface);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(surface);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(20), dp(28), dp(20), dp(32)); root.setBackgroundColor(surface);
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        TextView title = textView("Cursed Keyboard", 31f, text); root.addView(title);
        TextView subtitle = textView("Material You • hybrid mobile keyboard • Unicode/cipher modes • local-first", 13.5f, muted);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2); subLp.topMargin = dp(4); root.addView(subtitle, subLp);

        TextView preview = textView("q   w   e   r   t   y\n҂   Ш   ⊗   Я   Ŧ   ¥\n\nABC   Ȝ   ↯   𝓕   😀   📋   PC   #+=   123   ¤   ✦", 18f, text);
        preview.setGravity(Gravity.CENTER); preview.setPadding(dp(14), dp(18), dp(14), dp(18)); preview.setBackground(round(card, dp(22)));
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(-1, -2); previewLp.topMargin = dp(22); root.addView(preview, previewLp);

        addSectionTitle(root, "Keyboard");
        addPrimaryButton(root, "Enable keyboard", () -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        addSecondaryButton(root, "Choose keyboard", () -> { InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE); if (imm != null) imm.showInputMethodPicker(); });

        addSectionTitle(root, "Appearance & feel");
        addToggleButton(root, "Key haptics", prefs.getBoolean("haptics", true), "Short vibration on taps and cursor swipes", () -> { prefs.edit().putBoolean("haptics", !prefs.getBoolean("haptics", true)).apply(); buildUi(); });
        addToggleButton(root, "AMOLED black", prefs.getBoolean("amoled", false), "Pure black keyboard surface in dark mode", () -> { prefs.edit().putBoolean("amoled", !prefs.getBoolean("amoled", false)).apply(); buildUi(); });
        addToggleButton(root, "Number row", prefs.getBoolean("number_row", false), "Keep 1–0 above QWERTY in letter/glyph/cipher/style modes", () -> { prefs.edit().putBoolean("number_row", !prefs.getBoolean("number_row", false)).apply(); buildUi(); });

        int keyStyle = prefs.getInt("key_style", 0);
        addCycleButton(root, "Key style", keyStyleName(keyStyle), "Filled → Borderless → Outline", () -> { prefs.edit().putInt("key_style", (prefs.getInt("key_style", 0) + 1) % 3).apply(); buildUi(); });
        int keyHeight = prefs.getInt("key_height", 54);
        addCycleButton(root, "Key height", keyHeightName(keyHeight), "Compact → Normal → Tall", () -> { int current = prefs.getInt("key_height", 54); int next = current <= 48 ? 54 : (current <= 54 ? 60 : 48); prefs.edit().putInt("key_height", next).apply(); buildUi(); });

        addSectionTitle(root, "Hybrid features");
        addInfoCard(root, "Ȝ  Dual-label glyph mode", "English letters stay visible with each cursed glyph. Tap outputs the glyph; long-press outputs the normal letter.");
        addInfoCard(root, "↯  Nullfracture", "Live chained cipher output with deterministic decoys and state-aware backspace.");
        addInfoCard(root, "𝓕  Unicode styles", "Fullwidth, Circled, Monospace, Small Caps, Double-struck and Bold Script. Tap 𝓕 again to cycle.");
        addInfoCard(root, "😀  Emoji panel", "Three local emoji pages. Tap 😀 again or NEXT to change pages.");
        addInfoCard(root, "📋  Clipboard panel", "Small local clipboard history. Tap to paste; long-press an item to remove it.");
        addInfoCard(root, "PC  Power-user layer", "Esc, Tab, Home, End, arrows, Page Up/Down, Forward Delete, Insert and Backspace.");
        addInfoCard(root, "Spacebar cursor control", "Swipe left/right across space to move the cursor. Hold space to open Android's keyboard picker.");
        addInfoCard(root, "⌫  Delete word", "Long-press Backspace in QWERTY-style modes to delete the previous word.");

        addSectionTitle(root, "Local-first");
        TextView privacy = textView("No INTERNET permission. Reference APKs were used for interaction/layout ideas only; this is an independent implementation.", 12.5f, muted);
        privacy.setGravity(Gravity.CENTER); privacy.setPadding(dp(12), dp(10), dp(12), dp(10)); privacy.setBackground(round(card, dp(16))); root.addView(privacy);
        setContentView(scroll);
    }

    private String keyStyleName(int value) { switch (value) { case 1: return "Borderless"; case 2: return "Outline"; default: return "Filled"; } }
    private String keyHeightName(int value) { if (value <= 48) return "Compact"; if (value >= 60) return "Tall"; return "Normal"; }

    private void loadColors() {
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        boolean amoled = prefs.getBoolean("amoled", false);
        if (dark) {
            surface = amoled ? Color.BLACK : systemColor("system_neutral1_1000", Color.rgb(20,20,24)); card = systemColor("system_neutral2_900", Color.rgb(35,35,42)); text = Color.WHITE; muted = systemColor("system_neutral2_200", Color.rgb(190,190,202)); accent = systemColor("system_accent1_400", Color.rgb(179,136,255));
        } else {
            surface = systemColor("system_neutral1_50", Color.rgb(247,245,250)); card = systemColor("system_neutral2_100", Color.rgb(232,228,236)); text = Color.rgb(28,27,31); muted = systemColor("system_neutral2_700", Color.rgb(84,80,88)); accent = systemColor("system_accent1_600", Color.rgb(103,80,164));
        }
        accentText = contrastText(accent);
    }

    private int systemColor(String name, int fallback) { int id = getResources().getIdentifier(name, "color", "android"); if (id == 0) return fallback; try { return getColor(id); } catch (Exception ignored) { return fallback; } }
    private int contrastText(int color) { double l = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0; return l > 0.58 ? Color.BLACK : Color.WHITE; }

    private void addSectionTitle(LinearLayout root, String label) { TextView title = textView(label, 16f, text); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(26); lp.bottomMargin = dp(8); root.addView(title, lp); }
    private void addPrimaryButton(LinearLayout root, String label, Runnable action) { Button b = baseButton(label, accent, accentText); b.setOnClickListener(v -> action.run()); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(56)); lp.topMargin = dp(6); root.addView(b, lp); }
    private void addSecondaryButton(LinearLayout root, String label, Runnable action) { Button b = baseButton(label, card, text); b.setOnClickListener(v -> action.run()); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(56)); lp.topMargin = dp(10); root.addView(b, lp); }

    private void addToggleButton(LinearLayout root, String title, boolean enabled, String description, Runnable action) {
        String label = title + "\n" + description + "\n" + (enabled ? "ON" : "OFF"); Button b = baseButton(label, enabled ? blend(card, accent, 0.25f) : card, text); b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL); b.setPadding(dp(18), dp(9), dp(18), dp(9)); b.setTextSize(13.5f); b.setOnClickListener(v -> action.run()); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(84)); lp.topMargin = dp(9); root.addView(b, lp);
    }

    private void addCycleButton(LinearLayout root, String title, String value, String description, Runnable action) {
        String label = title + "  •  " + value + "\n" + description; Button b = baseButton(label, card, text); b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL); b.setPadding(dp(18), dp(9), dp(18), dp(9)); b.setTextSize(13.5f); b.setOnClickListener(v -> action.run()); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(72)); lp.topMargin = dp(9); root.addView(b, lp);
    }

    private void addInfoCard(LinearLayout root, String title, String body) {
        LinearLayout c = new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); c.setPadding(dp(16), dp(14), dp(16), dp(14)); c.setBackground(round(card, dp(18))); c.addView(textView(title, 16f, text)); TextView b = textView(body, 13f, muted); LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(-1, -2); bodyLp.topMargin = dp(4); c.addView(b, bodyLp); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(9); root.addView(c, lp);
    }

    private Button baseButton(String label, int background, int foreground) {
        Button b = new Button(this); b.setText(label); b.setTextColor(foreground); b.setTextSize(15f); b.setAllCaps(false); b.setGravity(Gravity.CENTER); b.setPadding(dp(12), 0, dp(12), 0); b.setMinWidth(0); b.setMinimumWidth(0); b.setMinHeight(0); b.setMinimumHeight(0); GradientDrawable mask = round(background, dp(18)); int rippleColor = Color.argb(80, Color.red(accent), Color.green(accent), Color.blue(accent)); b.setBackground(new RippleDrawable(ColorStateList.valueOf(rippleColor), mask, null)); return b;
    }

    private TextView textView(String value, float size, int color) { TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color); return v; }
    private GradientDrawable round(int color, int radius) { GradientDrawable bg = new GradientDrawable(); bg.setColor(color); bg.setCornerRadius(radius); return bg; }
    private int blend(int a, int b, float m) { m = Math.max(0f, Math.min(1f, m)); return Color.rgb(Math.round(Color.red(a)*(1f-m)+Color.red(b)*m), Math.round(Color.green(a)*(1f-m)+Color.green(b)*m), Math.round(Color.blue(a)*(1f-m)+Color.blue(b)*m)); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
