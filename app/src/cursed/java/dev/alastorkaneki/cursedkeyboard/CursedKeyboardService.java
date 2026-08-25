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
import android.widget.TextView;

import java.util.ArrayDeque;
import java.util.Deque;

public final class CursedKeyboardService extends InputMethodService {
    private enum Mode {
        NORMAL("NORMAL"), GLYPH("GLYPH"), NULLFRACTURE("NULLFX"),
        SYMBOLS("SYMBOLS"), NUMBERS("123"), NULLS("NULLS"), CUSTOM("CUSTOM");
        final String label;
        Mode(String label) { this.label = label; }
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
            {"{","}","_","-","+","=",";",":","\"","'"}
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

    private LinearLayout root;
    private Mode mode = Mode.GLYPH;
    private int cipherPosition;
    private int previousCipherValue;
    private final Deque<CipherSnapshot> cipherHistory = new ArrayDeque<>();

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
        root.setBackgroundColor(Color.rgb(10, 10, 13));
        renderKeyboard();
        return root;
    }

    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        resetCipherState();
    }

    private void renderKeyboard() {
        if (root == null) return;
        root.removeAllViews();
        root.addView(makeModeHeader());

        switch (mode) {
            case NORMAL: addLetterRows(false, false); break;
            case GLYPH: addLetterRows(true, false); break;
            case NULLFRACTURE: addLetterRows(false, true); break;
            case SYMBOLS: addLiteralRows(SYMBOL_ROWS); break;
            case NUMBERS: addLiteralRows(NUMBER_ROWS); break;
            case NULLS: addLiteralRows(NULL_ROWS); break;
            case CUSTOM: addLiteralRows(CUSTOM_ROWS); break;
        }

        root.addView(makeBottomRow());
    }

    private TextView makeModeHeader() {
        TextView header = new TextView(this);
        String extra = "";
        if (mode == Mode.GLYPH) extra = "  •  English + glyph";
        else if (mode == Mode.NULLFRACTURE) extra = "  •  live chained cipher";
        else if (mode == Mode.NULLS) extra = "  •  decoy characters";
        header.setText(mode.label + extra);
        header.setTextColor(Color.rgb(190, 190, 205));
        header.setTextSize(12f);
        header.setGravity(Gravity.CENTER);
        header.setPadding(0, dp(1), 0, dp(1));
        return header;
    }

    private void addLetterRows(boolean glyphMode, boolean cipherMode) {
        for (int r = 0; r < LETTER_ROWS.length; r++) {
            LinearLayout row = makeEmptyRow(LETTER_ROWS[r].length);
            for (int i = 0; i < LETTER_ROWS[r].length; i++) {
                final String letter = LETTER_ROWS[r][i];
                String label;
                float size;
                if (glyphMode) {
                    label = letter + "\n" + GLYPH_ROWS[r][i];
                    size = 16f;
                } else if (cipherMode) {
                    label = letter + "\n↯";
                    size = 16f;
                } else {
                    label = letter;
                    size = 20f;
                }

                Button key = makeKey(label, size);
                if (glyphMode) {
                    final String output = GLYPH_ROWS[r][i];
                    key.setOnClickListener(v -> commit(output));
                } else if (cipherMode) {
                    key.setOnClickListener(v -> commitNullfracture(letter));
                } else {
                    key.setOnClickListener(v -> commit(letter.toLowerCase()));
                }
                row.addView(key, keyLayoutParams(1f));
            }
            addTrailingSpacer(row, LETTER_ROWS[r].length);
            root.addView(row);
        }
    }

    private void addLiteralRows(String[][] rows) {
        for (String[] values : rows) {
            LinearLayout row = makeEmptyRow(values.length);
            for (String value : values) {
                Button key = makeKey(value, value.length() > 2 ? 15f : 20f);
                key.setOnClickListener(v -> commit(value));
                row.addView(key, keyLayoutParams(1f));
            }
            addTrailingSpacer(row, values.length);
            root.addView(row);
        }
    }

    private LinearLayout makeEmptyRow(int keyCount) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        float spacer = sideSpacerWeight(keyCount);
        if (spacer > 0f) {
            row.addView(new View(this), new LinearLayout.LayoutParams(0, dp(56), spacer));
        }
        return row;
    }

    private void addTrailingSpacer(LinearLayout row, int keyCount) {
        float spacer = sideSpacerWeight(keyCount);
        if (spacer > 0f) {
            row.addView(new View(this), new LinearLayout.LayoutParams(0, dp(56), spacer));
        }
    }

    private float sideSpacerWeight(int keyCount) {
        return Math.max(0f, (10f - keyCount) / 2f);
    }

    private LinearLayout makeBottomRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);

        Button modeKey = makeKey(mode.label, 11f);
        modeKey.setOnClickListener(v -> cycleMode());
        row.addView(modeKey, keyLayoutParams(1.7f));

        Button ime = makeKey("IME", 11f);
        ime.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showInputMethodPicker();
        });
        row.addView(ime, keyLayoutParams(1.2f));

        Button space = makeKey("SPACE", 12f);
        space.setOnClickListener(v -> {
            if (mode == Mode.NULLFRACTURE) commitCipherSpace();
            else commit(" ");
        });
        row.addView(space, keyLayoutParams(4.5f));

        Button backspace = makeKey("⌫", 21f);
        backspace.setOnClickListener(v -> handleBackspace());
        row.addView(backspace, keyLayoutParams(1.2f));

        Button enter = makeKey("↵", 21f);
        enter.setOnClickListener(v -> handleEnter());
        row.addView(enter, keyLayoutParams(1.4f));
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

    private void cycleMode() {
        Mode[] values = Mode.values();
        mode = values[(mode.ordinal() + 1) % values.length];
        resetCipherState();
        renderKeyboard();
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
