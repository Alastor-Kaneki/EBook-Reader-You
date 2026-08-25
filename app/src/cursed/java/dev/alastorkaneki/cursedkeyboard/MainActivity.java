package dev.alastorkaneki.cursedkeyboard;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(32), dp(24), dp(24));
        root.setBackgroundColor(Color.rgb(10, 10, 13));

        TextView title = new TextView(this);
        title.setText("Cursed Keyboard");
        title.setTextColor(Color.WHITE);
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView sample = new TextView(this);
        sample.setText("҂ Ш ⊗ Я Ŧ ¥ µ Ʌ ⌁ Φ\nȜ § ʭ Ϟ Δ ⟁ ☍ Ж Ȣ\nƵ × Ψ V † Ѫ ∴");
        sample.setTextColor(Color.rgb(205, 205, 215));
        sample.setTextSize(20);
        sample.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sampleLp = new LinearLayout.LayoutParams(-1, -2);
        sampleLp.topMargin = dp(22);
        root.addView(sample, sampleLp);

        TextView instructions = new TextView(this);
        instructions.setText("Enable the keyboard, then choose Cursed Keyboard as your input method.");
        instructions.setTextColor(Color.rgb(170, 170, 182));
        instructions.setTextSize(16);
        instructions.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams instructionsLp = new LinearLayout.LayoutParams(-1, -2);
        instructionsLp.topMargin = dp(28);
        root.addView(instructions, instructionsLp);

        Button enable = makeButton("ENABLE KEYBOARD");
        enable.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        LinearLayout.LayoutParams enableLp = new LinearLayout.LayoutParams(-1, dp(54));
        enableLp.topMargin = dp(28);
        root.addView(enable, enableLp);

        Button choose = makeButton("CHOOSE KEYBOARD");
        choose.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showInputMethodPicker();
        });
        LinearLayout.LayoutParams chooseLp = new LinearLayout.LayoutParams(-1, dp(54));
        chooseLp.topMargin = dp(12);
        root.addView(choose, chooseLp);

        setContentView(root);
    }

    private Button makeButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(39, 39, 46));
        bg.setCornerRadius(dp(14));
        button.setBackground(bg);
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
