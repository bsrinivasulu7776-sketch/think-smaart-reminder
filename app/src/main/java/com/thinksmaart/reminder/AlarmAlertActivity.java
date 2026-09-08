package com.thinksmaart.reminder;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class AlarmAlertActivity extends Activity {
    private long id;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        id = getIntent().getLongExtra("id", -1L);
        String title = getIntent().getStringExtra("title");
        String body = getIntent().getStringExtra("body");
        String category = getIntent().getStringExtra("category");
        if (title == null) title = "Think Smaart Reminder";
        if (body == null) body = "Reminder is due now";
        if (category == null) category = "Reminder";

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(34), dp(24), dp(26));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(3, 133, 221), Color.rgb(10, 65, 170), Color.rgb(37, 45, 126)});
        root.setBackground(bg);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.mipmap.ic_launcher);
        root.addView(logo, new LinearLayout.LayoutParams(dp(92), dp(92)));

        TextView app = text("Think Smaart", 26, true, Color.WHITE);
        app.setGravity(Gravity.CENTER);
        root.addView(app, lpMatchWrap(8));

        TextView cat = text(category, 14, true, Color.rgb(116, 232, 255));
        cat.setGravity(Gravity.CENTER);
        root.addView(cat, lpMatchWrap(18));

        TextView t = text(title, 24, true, Color.WHITE);
        t.setGravity(Gravity.CENTER);
        root.addView(t, lpMatchWrap(12));

        TextView b = text(body, 16, false, Color.rgb(230, 241, 255));
        b.setGravity(Gravity.CENTER);
        root.addView(b, lpMatchWrap(30));

        Button snooze = button("Snooze 10 Minutes", Color.WHITE, Color.rgb(0, 105, 210));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        blp.setMargins(0, dp(14), 0, dp(10));
        root.addView(snooze, blp);
        snooze.setOnClickListener(v -> {
            if (id == -42L) AlarmScheduler.snoozeWater(this, 10);
            else if (AlarmScheduler.isHealthId(id)) AlarmScheduler.snoozeHealth(this, id, 10);
            else AlarmScheduler.snoozeReminder(this, id, 10);
            NotificationHelper.cancel(this, id);
            finish();
        });

        Button dismiss = button("Dismiss", Color.WHITE, Color.argb(55, 255, 255, 255));
        root.addView(dismiss, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));
        dismiss.setOnClickListener(v -> {
            NotificationHelper.cancel(this, id);
            finish();
        });

        setContentView(root);
    }

    private TextView text(String s, int sp, boolean bold, int color) {
        TextView tv = new TextView(this);
        tv.setText(s);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        if (bold) tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return tv;
    }

    private Button button(String s, int textColor, int bgColor) {
        Button btn = new Button(this);
        btn.setText(s);
        btn.setTextColor(textColor);
        btn.setTextSize(16);
        btn.setAllCaps(false);
        btn.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(bgColor);
        gd.setCornerRadius(dp(16));
        btn.setBackground(gd);
        return btn;
    }

    private LinearLayout.LayoutParams lpMatchWrap(int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(top), 0, 0);
        return lp;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
