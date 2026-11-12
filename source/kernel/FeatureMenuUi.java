package com.zafiro.wxentry.kernel;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.AbsListView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.concurrent.Callable;
import org.json.JSONArray;
import org.json.JSONObject;

final class FeatureMenuUi {
    static final int SURFACE = 0xFFFFF5F8;
    static final int WHITE = 0xFFFFFFFF;
    static final int INK = 0xFF38232E;
    static final int MUTED = 0xFF755466;
    static final int ROSE = SettingsUi.ROSE;
    static final int PINK = 0xFFFBE4ED;

    static TextView text(Context context, String value, int size, int color) {
        TextView text = new TextView(context);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        return text;
    }

    static LinearLayout column(Context context) {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }

    static GradientDrawable shape(Context context, int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(SettingsUi.dp(context, radius));
        return shape;
    }

    static void pad(View view, int horizontal, int vertical) {
        Context context = view.getContext();
        int x = SettingsUi.dp(context, horizontal), y = SettingsUi.dp(context, vertical);
        view.setPadding(x, y, x, y);
    }

    static void card(View view, boolean emphasis, boolean clickable) {
        Context context = view.getContext();
        GradientDrawable background = shape(context, emphasis ? PINK : WHITE, 14);
        view.setBackground(clickable
                ? new RippleDrawable(ColorStateList.valueOf(0x26A82D61), background, null)
                : background);
    }

    static View row(Context context, String label, boolean category, boolean update) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        pad(row, 14, 8);
        row.setMinimumHeight(SettingsUi.dp(context, SettingsUi.ROW_HEIGHT));
        card(row, update, true);
        TextView title = text(context, label, 16, update ? ROSE : INK);
        if (category || update) title.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView arrow = text(context, "›", 22, ROSE);
        arrow.setGravity(Gravity.CENTER);
        arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(arrow, SettingsUi.controlSlot(context));
        return row;
    }

    static SettingsUi.ToggleRow toggle(Context context, String label, String detail,
                                      boolean checked, SettingsUi.Change change) {
        SettingsUi.ToggleRow row = SettingsUi.toggle(context, label, detail, checked, change);
        row.title.setTextColor(INK);
        row.subtitle.setTextColor(MUTED);
        pad(row, 14, 8);
        card(row, false, true);
        return row;
    }

    static void styleDialog(AlertDialog dialog) {
        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawable(shape(dialog.getContext(), SURFACE, 24));
        for (int button : new int[]{AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL, AlertDialog.BUTTON_POSITIVE})
            if (dialog.getButton(button) != null) dialog.getButton(button).setTextColor(ROSE);
    }

    static void wireScrollHint(final ListView list, final TextView hint) {
        hint.setGravity(Gravity.CENTER);
        hint.setMinHeight(SettingsUi.dp(list.getContext(), 30));
        hint.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_NONE);
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            public void onScrollStateChanged(AbsListView view, int state) {}
            public void onScroll(AbsListView view, int first, int visible, int count) {
                String label = list.canScrollVertically(1) ? "⌄  上滑查看更多"
                        : list.canScrollVertically(-1) ? "⌃  下滑返回上方" : "";
                if (!label.contentEquals(hint.getText())) hint.setText(label);
            }
        });
    }

    static void showAnnouncements(final Activity activity, final Callable<String> fetch) {
        final ScrollView scroll = new ScrollView(activity);
        final LinearLayout content = column(activity);
        content.setBackgroundColor(SURFACE);
        pad(content, 16, 10);
        scroll.addView(content);
        final AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("更新公告")
                .setView(scroll).setPositiveButton("关闭", null).setNeutralButton("刷新", null).create();
        final Runnable load = new Runnable() {
            public void run() {
                content.removeAllViews();
                content.addView(text(activity, "正在加载更新公告…", 14, MUTED));
                scroll.scrollTo(0, 0);
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(false);
                new Thread(new Runnable() { public void run() {
                    JSONArray result = null;
                    try { result = new JSONObject(fetch.call()).getJSONArray("releases"); }
                    catch (Exception unavailable) {}
                    final JSONArray releases = result;
                    activity.runOnUiThread(new Runnable() { public void run() {
                        if (!dialog.isShowing() || activity.isFinishing() || activity.isDestroyed()) return;
                        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(true);
                        content.removeAllViews();
                        if (releases == null) {
                            content.addView(text(activity, "暂时无法加载公告，请点刷新重试。", 14, MUTED));
                            return;
                        }
                        for (int i = 0; i < releases.length(); i++) {
                            JSONObject release = releases.optJSONObject(i);
                            if (release == null) continue;
                            LinearLayout card = column(activity);
                            pad(card, 14, 12);
                            card(card, false, false);
                            String date = release.optString("date");
                            TextView heading = text(activity, "内核 v" + release.optString("version"), 17, INK);
                            heading.setTypeface(Typeface.DEFAULT_BOLD);
                            card.addView(heading);
                            if (!date.isEmpty()) card.addView(text(activity, date, 12, MUTED));
                            addNotes(card, "新增", release.optJSONArray("added"));
                            addNotes(card, "修复", release.optJSONArray("fixed"));
                            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
                            params.bottomMargin = SettingsUi.dp(activity, 8);
                            content.addView(card, params);
                        }
                        if (content.getChildCount() == 0)
                            content.addView(text(activity, "暂无更新公告。", 14, MUTED));
                    }});
                }}, "wxentry-announcements").start();
            }
        };
        dialog.show();
        styleDialog(dialog);
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) { load.run(); }
        });
        load.run();
    }

    private static void addNotes(LinearLayout card, String label, JSONArray notes) {
        if (notes == null || notes.length() == 0) return;
        Context context = card.getContext();
        TextView title = text(context, label, 14, ROSE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, SettingsUi.dp(context, 10), 0, SettingsUi.dp(context, 3));
        card.addView(title);
        for (int i = 0; i < notes.length(); i++) {
            TextView note = text(context, "•  " + notes.optString(i), 14, INK);
            note.setPadding(0, 0, 0, SettingsUi.dp(context, 4));
            card.addView(note);
        }
    }
}
