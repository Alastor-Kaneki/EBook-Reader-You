package com.alastorkaneki.ebookreaderyou;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

public final class Ui {
    private Ui() {
    }

    public static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static GradientDrawable rounded(int color, float radius, Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radius));
        return drawable;
    }

    public static GradientDrawable outlined(int color, int stroke, float radius, Context context) {
        GradientDrawable drawable = rounded(Color.TRANSPARENT, radius, context);
        drawable.setStroke(dp(context, 1), stroke);
        return drawable;
    }

    public static TextView button(Context context, String text, int foreground, int background, View.OnClickListener listener) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(foreground);
        view.setTextSize(15);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12));
        view.setBackground(rounded(background, 20, context));
        view.setClickable(true);
        view.setFocusable(true);
        view.setOnClickListener(listener);
        return view;
    }

    public static String size(long bytes) {
        if (bytes <= 0) return "";
        double value = bytes;
        String[] units = {"B", "KB", "MB", "GB"};
        int index = 0;
        while (value >= 1024 && index < units.length - 1) {
            value /= 1024;
            index++;
        }
        return index == 0 ? String.format("%.0f %s", value, units[index]) : String.format("%.1f %s", value, units[index]);
    }
}
