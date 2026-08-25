package dev.alastorkaneki.cursedkeyboard;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;

public final class CursedKeyboardService extends InputMethodService {
    private static final String[] ROW_1 = {"҂","Ш","⊗","Я","Ŧ","¥","µ","Ʌ","⌁","Φ"};
    private static final String[] ROW_2 = {"Ȝ","§","ʭ","Ϟ","Δ","⟁","☍","Ж","Ȣ"};
    private static final String[] ROW_3 = {"Ƶ","×","Ψ","V","†","Ѫ","∴"};

    @Override
    public View onCreateInputView() {
        LinearLayout keyboard = new LinearLayout(this);
        keyboard.setOrientation(LinearLayout.VERTICAL);
        keyboard.setPadding(dp(4), dp(6), dp(4), dp(6));
        keyboard.setBackgroundColor(Color.rgb(10, 10, 13));
        keyboard.addView(makeGlyphRow(ROW_1, 0f));
        keyboard.addView(makeGlyphRow(ROW_2, 0.5f));
        keyboard.addView(makeGlyphRow(ROW_3, 1.5f));
        keyboard.addView(makeBottomRow());
        return keyboard;
    }

    private LinearLayout makeGlyphRow(String[] glyphs, float sideSpacerWeight) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        if (sideSpacerWeight > 0f) row.addView(new View(this), new LinearLayout.LayoutParams(0, dp(56), sideSpacerWeight));
        for (String glyph : glyphs) {
            Button key = makeKey(glyph, 23f);
            key.setOnClickListener(v -> commit(glyph));
            row.addView(key, keyLayoutParams(1f));
        }
        if (sideSpacerWeight > 0f) row.addView(new View(this), new LinearLayout.LayoutParams(0, dp(56), sideSpacerWeight));
        return row;
    }

    private LinearLayout makeBottomRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);

        Button ime = makeKey("IME", 13f);
        ime.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showInputMethodPicker();
        });
        row.addView(ime, keyLayoutParams(1.4f));

        Button space = makeKey("SPACE", 13f);
        space.setOnClickListener(v -> commit(" "));
        row.addView(space, keyLayoutParams(5.2f));

        Button backspace = makeKey("⌫", 22f);
        backspace.setOnClickListener(v -> {
            InputConnection c = getCurrentInputConnection();
            if (c != null) c.deleteSurroundingText(1, 0);
        });
        row.addView(backspace, keyLayoutParams(1.4f));

        Button enter = makeKey("↵", 22f);
        enter.setOnClickListener(v -> handleEnter());
        row.addView(enter, keyLayoutParams(2.0f));
        return row;
    }

    private Button makeKey(String label, float textSizeSp) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(textSizeSp);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setPadding(0, 0, 0, 0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(38, 38, 45));
        bg.setCornerRadius(dp(10));
        button.setBackground(bg);
        return button;
    }

    private LinearLayout.LayoutParams keyLayoutParams(float weight) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(56), weight);
        int m = dp(3);
        p.setMargins(m, m, m, m);
        return p;
    }

    private void commit(String text) {
        InputConnection c = getCurrentInputConnection();
        if (c != null) c.commitText(text, 1);
    }

    private void handleEnter() {
        InputConnection c = getCurrentInputConnection();
        EditorInfo info = getCurrentInputEditorInfo();
        if (c == null) return;
        if (info != null) {
            int action = info.imeOptions & EditorInfo.IME_MASK_ACTION;
            boolean noEnterAction = (info.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0;
            if (!noEnterAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
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
