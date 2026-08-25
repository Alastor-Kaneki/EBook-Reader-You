package dev.alastorkaneki.cursedkeyboard;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.inputmethodservice.InputMethodService;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayDeque;
import java.util.Deque;

public final class CursedKeyboardService extends InputMethodService {
    private enum Mode {
        NORMAL("ABC"), GLYPH("Ȝ"), NULLFRACTURE("↯"), STYLED("𝓕"),
        SYMBOLS("#+="), NUMBERS("123"), NULLS("¤"), CUSTOM("✦");
        final String label;
        Mode(String label) { this.label = label; }
    }

    private enum TextStyle {
        FULLWIDTH("ＦＵＬＬ"),
        CIRCLED("ⒸⒾⓇⒸⓁⒺ"),
        MONO("𝙼𝙾𝙽𝙾"),
        SMALLCAPS("sᴍᴀʟʟ");
        final String label;
        TextStyle(String label) { this.label = label; }
    }

    private static final String[][] LETTER_ROWS = {
            {"Q","W","E","R","T","Y","U","I","O","P"},
            {"A","S","D","F","G","H","J","K","L"},
            {"Z","X","C","V","B","N","M"}
    };

    private static final String[][] GLYPH_ROWS = {
            {"҂","Ш","⊗","Я","Ŧ","¥","µ","Ʌ","⌁","Φ"},
            {"Ȝ","§","ʭ","Ϟ","Δ","⟁","☍","Ж","Ȣ"},
            {"Ƶ","×","Ψ","V","†","Ѫ","∴"}
    };

    private static final String[][] SYMBOL_ROWS = {
            {"!","@","#","$","%","^","&","*","(",")"},
            {"~","`","|","\\","/","?","<",">","[","]"},
            {"{","}","_","-","+=","=",";",":","\"","'"}
    };

    private static final String[][] NUMBER_ROWS = {
            {"1","2","3","4","5","6","7","8","9","0"},
            {"!","@","#","$","%","&","*","(",")"},
            {".",",","?","!","-","_","/","\\",":",";"}
    };

    private static final String[][] NULL_ROWS = {
            {"¤","☒","ꙮ","⸸","⧖","𖤐","∅","꩜","⦻","⋮"},
            {"☒","¤","⦻","∅","꩜","⧖","⸸","ꙮ","𖤐"},
            {"⋮","⦻","¤","꩜","∅","𖤐","☒"}
    };

    private static final String[][] CUSTOM_ROWS = {
            {"Ѫ","Ж","҂","Ψ","Δ","Φ","Я","Ŧ","Ȝ","Ȣ"},
            {"⟁","☍","Ϟ","§","†","×","¥","µ","Ʌ"},
            {"⌁","⊗","Ш","Ƶ","∴","ʭ","V"}
    };

    private static final String[] CIPHER_GLYPHS = {
            "Ȝ","†","Ψ","ʭ","⊗","Ϟ","Δ","⟁","Ʌ","☍","Ж","Ȣ","∴",
            "Ѫ","⌁","Φ","҂","Я","§","Ŧ","µ","V","Ш","×","¥","Ƶ"
    };
    private static final String[] DECOYS = {"¤","☒","ꙮ","⸸","⧖","𖤐","∅","꩜","⦻"};
    private static final String[] SPACE_MARKERS = {"/","//","⋮","⸬","::"};
    private static final String CIPHER_KEY = "NULLCROWN";
    private static final String[] SMALL_CAPS = {
            "ᴀ","ʙ","ᴄ","ᴅ","ᴇ","ꜰ","ɢ","ʜ","ɪ","ᴊ","ᴋ","ʟ","ᴍ",
            "ɴ","ᴏ","ᴘ","Q","ʀ","ꜱ","ᴛ","ᴜ","ᴠ","ᴡ","x","ʏ","ᴢ"
    };

    private LinearLayout root;
    private Mode mode = Mode.GLYPH;
    private TextStyle textStyle = TextStyle.FULLWIDTH;
    private boolean shifted;
    private int cipherPosition;
    private int previousCipherValue;
    private final Deque<CipherSnapshot> cipherHistory = new ArrayDeque<>();

    private int surfaceColor;
    private int keyColor;
    private int textColor;
    private int mutedColor;
    private int accentColor;
    private int accentTextColor;
    private boolean hapticsEnabled;

    private static final class CipherSnapshot {
        final int position;
        final int previous;
        final int deleteUnits;
        CipherSnapshot(int position, int previous, int deleteUnits) {
            this.position = position;
            this.previous = previous;
            this.deleteUnits = deleteUnits;
        }
    }

    @Override
    public View onCreateInputView() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(4), dp(4), dp(4), dp(6));
        refreshTheme();
        renderKeyboard();
        return root;
    }

    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        resetCipherState();
        shifted = false;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        refreshTheme();
        renderKeyboard();
    }

    private void refreshTheme() {
        SharedPreferences prefs = getSharedPreferences("cursed_keyboard_prefs", MODE_PRIVATE);
        hapticsEnabled = prefs.getBoolean("haptics", true);
        boolean amoled = prefs.getBoolean("amoled", false);

        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;

        if (dark) {
            surfaceColor = amoled ? Color.BLACK : systemColor("system_neutral1_1000", Color.rgb(20, 20, 24));
            keyColor = systemColor("system_neutral2_800", Color.rgb(43, 43, 50));
            textColor = Color.WHITE;
            mutedColor = systemColor("system_neutral2_200", Color.rgb(198, 198, 208));
            accentColor = systemColor("system_accent1_400", Color.rgb(179, 136, 255));
        } else {
            surfaceColor = systemColor("system_neutral1_50", Color.rgb(247, 245, 250));
            keyColor = systemColor("system_neutral2_100", Color.rgb(229, 225, 233));
            textColor = Color.rgb(28, 27, 31);
            mutedColor = systemColor("system_neutral2_700", Color.rgb(85, 82, 90));
            accentColor = systemColor("system_accent1_600", Color.rgb(103, 80, 164));
        }
        accentTextColor = contrastText(accentColor);
        if (root != null) root.setBackgroundColor(surfaceColor);
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

    private void renderKeyboard() {
        if (root == null) return;
        root.removeAllViews();
        root.setBackgroundColor(surfaceColor);
        root.addView(makeToolbar());
        root.addView(makeModeHeader());

        switch (mode) {
            case NORMAL: addLetterRows(false, false, false); break;
            case GLYPH: addLetterRows(true, false, false); break;
            case NULLFRACTURE: addLetterRows(false, true, false); break;
            case STYLED: addLetterRows(false, false, true); break;
            case SYMBOLS: addLiteralRows(SYMBOL_ROWS); break;
            case NUMBERS: addLiteralRows(NUMBER_ROWS); break;
            case NULLS: addLiteralRows(NULL_ROWS); break;
            case CUSTOM: addLiteralRows(CUSTOM_ROWS); break;
        }

        root.addView(makeBottomRow());
    }

    private LinearLayout makeToolbar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(dp(2), 0, dp(2), dp(2));

        addModeButton(bar, "⌨", Mode.NORMAL);
        addModeButton(bar, "Ȝ", Mode.GLYPH);
        addModeButton(bar, "↯", Mode.NULLFRACTURE);
        addModeButton(bar, "𝓕", Mode.STYLED);
        addModeButton(bar, "#+=", Mode.SYMBOLS);
        addModeButton(bar, "123", Mode.NUMBERS);
        addModeButton(bar, "¤", Mode.NULLS);
        addModeButton(bar, "✦", Mode.CUSTOM);

        Button settings = makeSmallKey("⚙", false);
        attachClick(settings, this::openSettings);
        bar.addView(settings, toolbarLayoutParams());
        return bar;
    }

    private void addModeButton(LinearLayout bar, String label, Mode target) {
        Button button = makeSmallKey(label, mode == target);
        attachClick(button, () -> {
            if (target == Mode.STYLED && mode == Mode.STYLED) {
                TextStyle[] styles = TextStyle.values();
                textStyle = styles[(textStyle.ordinal() + 1) % styles.length];
                renderKeyboard();
                return;
            }
            mode = target;
            shifted = false;
            resetCipherState();
            renderKeyboard();
        });
        bar.addView(button, toolbarLayoutParams());
    }

    private Button makeSmallKey(String label, boolean active) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(label.length() > 2 ? 10f : 16f);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setPadding(0, 0, 0, 0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        int bg = active ? accentColor : blend(surfaceColor, keyColor, 0.72f);
        int fg = active ? accentTextColor : mutedColor;
        b.setTextColor(fg);
        b.setBackground(rippleBackground(bg, dp(18)));
        return b;
    }

    private LinearLayout.LayoutParams toolbarLayoutParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(38), 1f);
        p.setMargins(dp(2), dp(2), dp(2), dp(2));
        return p;
    }

    private TextView makeModeHeader() {
        TextView header = new TextView(this);
        String text;
        switch (mode) {
            case NORMAL: text = "QWERTY  •  long-press = glyph"; break;
            case GLYPH: text = "GLYPH  •  English labels  •  long-press = letter"; break;
            case NULLFRACTURE: text = "NULLFRACTURE  •  live chained cipher"; break;
            case STYLED: text = textStyle.label + "  •  tap 𝓕 again to change style"; break;
            case SYMBOLS: text = "SYMBOLS"; break;
            case NUMBERS: text = "NUMBERS"; break;
            case NULLS: text = "NULL / DECOY"; break;
            default: text = "CUSTOM"; break;
        }
        header.setText(text);
        header.setTextColor(mutedColor);
        header.setTextSize(11.5f);
        header.setGravity(Gravity.CENTER);
        header.setPadding(0, dp(1), 0, dp(1));
        return header;
    }

    private void addLetterRows(boolean glyphMode, boolean cipherMode, boolean styledMode) {
        for (int r = 0; r < LETTER_ROWS.length; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);

            if (r == 1) {
                row.addView(new View(this), new LinearLayout.LayoutParams(0, dp(54), 0.45f));
            } else if (r == 2) {
                Button shift = makeKey("⇧", 20f, shifted);
                attachClick(shift, () -> {
                    shifted = !shifted;
                    renderKeyboard();
                });
                row.addView(shift, keyLayoutParams(1.3f));
            }

            for (int i = 0; i < LETTER_ROWS[r].length; i++) {
                final String letter = LETTER_ROWS[r][i];
                final String glyph = GLYPH_ROWS[r][i];

                String label;
                float size;
                if (glyphMode) {
                    label = letter + "\n" + glyph;
                    size = 15.5f;
                } else if (cipherMode) {
                    label = letter + "\n↯";
                    size = 15.5f;
                } else if (styledMode) {
                    label = letter + "\n" + styledLetter(letter);
                    size = 14.5f;
                } else {
                    label = shifted ? letter : letter.toLowerCase();
                    size = 20f;
                }

                Button key = makeKey(label, size, false);
                if (glyphMode) {
                    attachClick(key, () -> commit(glyph));
                    attachLongClick(key, () -> commit(shifted ? letter : letter.toLowerCase()));
                } else if (cipherMode) {
                    attachClick(key, () -> commitNullfracture(letter));
                    attachLongClick(key, () -> commit(shifted ? letter : letter.toLowerCase()));
                } else if (styledMode) {
                    attachClick(key, () -> commit(styledLetter(letter)));
                    attachLongClick(key, () -> commit(shifted ? letter : letter.toLowerCase()));
                } else {
                    attachClick(key, () -> commit(shifted ? letter : letter.toLowerCase()));
                    attachLongClick(key, () -> commit(glyph));
                }
                row.addView(key, keyLayoutParams(1f));
            }

            if (r == 1) {
                row.addView(new View(this), new LinearLayout.LayoutParams(0, dp(54), 0.45f));
            } else if (r == 2) {
                Button backspace = makeKey("⌫", 21f, false);
                attachClick(backspace, this::handleBackspace);
                row.addView(backspace, keyLayoutParams(1.3f));
            }

            root.addView(row);
        }
    }

    private String styledLetter(String letter) {
        int index = letter.charAt(0) - 'A';
        boolean upper = shifted;
        switch (textStyle) {
            case FULLWIDTH:
                return String.valueOf((char) ((upper ? 'Ａ' : 'ａ') + index));
            case CIRCLED:
                return String.valueOf((char) ((upper ? 'Ⓐ' : 'ⓐ') + index));
            case MONO: {
                int base = upper ? 0x1D670 : 0x1D68A;
                return new String(Character.toChars(base + index));
            }
            case SMALLCAPS:
            default:
                return SMALL_CAPS[index];
        }
    }

    private void addLiteralRows(String[][] rows) {
        for (String[] values : rows) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);
            float spacer = Math.max(0f, (10f - values.length) / 2f);
            if (spacer > 0f) row.addView(new View(this), new LinearLayout.LayoutParams(0, dp(54), spacer));

            for (String value : values) {
                Button key = makeKey(value, value.length() > 2 ? 14f : 19f, false);
                attachClick(key, () -> commit(value));
                row.addView(key, keyLayoutParams(1f));
            }

            if (spacer > 0f) row.addView(new View(this), new LinearLayout.LayoutParams(0, dp(54), spacer));
            root.addView(row);
        }
    }

    private LinearLayout makeBottomRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);

        boolean letterMode = mode == Mode.NORMAL || mode == Mode.GLYPH
                || mode == Mode.NULLFRACTURE || mode == Mode.STYLED;

        Button left = makeKey(letterMode ? "?123" : "ABC", 12f, false);
        attachClick(left, () -> {
            mode = letterMode ? Mode.NUMBERS : Mode.NORMAL;
            shifted = false;
            resetCipherState();
            renderKeyboard();
        });
        row.addView(left, keyLayoutParams(1.35f));

        Button comma = makeKey(",", 19f, false);
        attachClick(comma, () -> commit(","));
        row.addView(comma, keyLayoutParams(0.8f));

        Button space = makeKey(spaceLabel(), 12f, false);
        attachClick(space, () -> {
            if (mode == Mode.NULLFRACTURE) commitCipherSpace();
            else commit(" ");
        });
        space.setOnLongClickListener(v -> {
            haptic(v);
            showImePicker();
            return true;
        });
        row.addView(space, keyLayoutParams(4.3f));

        Button period = makeKey(".", 19f, false);
        attachClick(period, () -> commit("."));
        row.addView(period, keyLayoutParams(0.8f));

        Button enter = makeKey("↵", 21f, true);
        attachClick(enter, this::handleEnter);
        row.addView(enter, keyLayoutParams(1.35f));

        Button ime = makeKey("⌨", 17f, false);
        attachClick(ime, this::showImePicker);
        row.addView(ime, keyLayoutParams(1.0f));

        return row;
    }

    private String spaceLabel() {
        switch (mode) {
            case GLYPH: return "Ȝ  GLYPH";
            case NULLFRACTURE: return "↯  NULLFX";
            case STYLED: return textStyle.label;
            case NULLS: return "¤  NULL";
            case CUSTOM: return "✦  CUSTOM";
            case SYMBOLS: return "#+=";
            case NUMBERS: return "123";
            default: return "space";
        }
    }

    private Button makeKey(String label, float textSizeSp, boolean accent) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(accent ? accentTextColor : textColor);
        button.setTextSize(textSizeSp);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setPadding(0, 0, 0, 0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setBackground(rippleBackground(accent ? accentColor : keyColor, dp(9)));
        return button;
    }

    private RippleDrawable rippleBackground(int baseColor, int radius) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(baseColor);
        bg.setCornerRadius(radius);
        int ripple = withAlpha(accentColor, 90);
        return new RippleDrawable(ColorStateList.valueOf(ripple), bg, null);
    }

    private int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private int blend(int a, int b, float amount) {
        amount = Math.max(0f, Math.min(1f, amount));
        int r = Math.round(Color.red(a) * (1f - amount) + Color.red(b) * amount);
        int g = Math.round(Color.green(a) * (1f - amount) + Color.green(b) * amount);
        int bl = Math.round(Color.blue(a) * (1f - amount) + Color.blue(b) * amount);
        return Color.rgb(r, g, bl);
    }

    private LinearLayout.LayoutParams keyLayoutParams(float weight) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(54), weight);
        int h = dp(2);
        int v = dp(3);
        p.setMargins(h, v, h, v);
        return p;
    }

    private void attachClick(View view, Runnable action) {
        view.setOnClickListener(v -> {
            haptic(v);
            action.run();
        });
    }

    private void attachLongClick(View view, Runnable action) {
        view.setOnLongClickListener(v -> {
            haptic(v);
            action.run();
            return true;
        });
    }

    private void haptic(View view) {
        if (hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    private void showImePicker() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showInputMethodPicker();
    }

    private void openSettings() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void commitNullfracture(String letter) {
        InputConnection c = getCurrentInputConnection();
        if (c == null) return;

        int oldPosition = cipherPosition;
        int oldPrevious = previousCipherValue;
        int p = letter.charAt(0) - 'A';
        int i = cipherPosition + 1;
        int keyValue = CIPHER_KEY.charAt(cipherPosition % CIPHER_KEY.length()) - 'A';
        int fractured = Math.floorMod(p + keyValue + (i * i), 26);
        if ((i & 1) == 0) fractured = 25 - fractured;
        fractured = Math.floorMod(fractured + previousCipherValue, 26);

        StringBuilder out = new StringBuilder(CIPHER_GLYPHS[fractured]);
        int decoyCount = (fractured + i) % 3;
        for (int d = 0; d < decoyCount; d++) {
            out.append(DECOYS[(fractured + i + d * 3) % DECOYS.length]);
        }

        String output = out.toString();
        c.commitText(output, 1);
        cipherHistory.push(new CipherSnapshot(oldPosition, oldPrevious, output.length()));
        cipherPosition = i;
        previousCipherValue = fractured;
    }

    private void commitCipherSpace() {
        InputConnection c = getCurrentInputConnection();
        if (c == null) return;
        String output = SPACE_MARKERS[(cipherPosition + previousCipherValue) % SPACE_MARKERS.length];
        c.commitText(output, 1);
        cipherHistory.push(new CipherSnapshot(cipherPosition, previousCipherValue, output.length()));
    }

    private void handleBackspace() {
        InputConnection c = getCurrentInputConnection();
        if (c == null) return;
        if (mode == Mode.NULLFRACTURE && !cipherHistory.isEmpty()) {
            CipherSnapshot snapshot = cipherHistory.pop();
            cipherPosition = snapshot.position;
            previousCipherValue = snapshot.previous;
            c.deleteSurroundingText(snapshot.deleteUnits, 0);
        } else {
            c.deleteSurroundingText(1, 0);
        }
    }

    private void resetCipherState() {
        cipherPosition = 0;
        previousCipherValue = 0;
        cipherHistory.clear();
    }

    private void commit(String text) {
        InputConnection c = getCurrentInputConnection();
        if (c != null) c.commitText(text, 1);
        if (shifted && (mode == Mode.NORMAL || mode == Mode.STYLED)) {
            shifted = false;
            renderKeyboard();
        }
    }

    private void handleEnter() {
        InputConnection c = getCurrentInputConnection();
        EditorInfo info = getCurrentInputEditorInfo();
        if (c == null) return;
        if (info != null) {
            int action = info.imeOptions & EditorInfo.IME_MASK_ACTION;
            boolean noEnterAction = (info.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0;
            if (!noEnterAction && action != EditorInfo.IME_ACTION_NONE
                    && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
                c.performEditorAction(action);
                return;
            }
        }
        c.commitText("\n", 1);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
