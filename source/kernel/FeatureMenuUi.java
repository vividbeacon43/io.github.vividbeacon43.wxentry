package com.zafiro.wxentry.kernel;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.concurrent.Callable;
import org.json.JSONArray;
import org.json.JSONObject;

final class FeatureMenuUi {
    static final int ROW_HEIGHT = SettingsUi.CONTROL_SIZE + 4;
    static final int ROW_GAP = 4;

    enum Tone {
        INK, MUTED, ROSE;

        int color(MenuPalette palette) {
            switch (this) {
                case MUTED: return palette.muted;
                case ROSE: return palette.accent;
                default: return palette.text;
            }
        }
    }

    private static final class MenuText extends TextView {
        final Tone tone;
        MenuText(Context context, String value, int size, Tone tone) {
            super(context);
            this.tone = tone;
            setText(value);
            setTextSize(size);
            setTextColor(tone.color(MenuPalette.current(context)));
        }
    }

    static TextView text(Context context, String value, int size, Tone tone) {
        return new MenuText(context, value, size, tone);
    }

    static AlertDialog.Builder builder(Activity host) {
        int style = MenuPalette.current(host) == MenuPalette.DARK ? android.R.style.Theme_Material_Dialog_Alert
                : android.R.style.Theme_Material_Light_Dialog_Alert;
        return new AlertDialog.Builder(host, style);
    }

    /** Keep the open menu in sync when the host updates its Activity resources in place. */
    static final class ThemeLayout extends LinearLayout {
        private MenuPalette current;
        private AlertDialog dialog;
        private View customTitle;

        ThemeLayout(Activity host) {
            super(host);
            setOrientation(VERTICAL);
            setBackgroundColor(MenuPalette.current(host).surface);
        }

        void bind(AlertDialog dialog, View customTitle) {
            this.dialog = dialog;
            this.customTitle = customTitle;
            updateColors();
        }

        @Override protected void onConfigurationChanged(Configuration configuration) {
            super.onConfigurationChanged(configuration);
            updateColors();
        }

        @Override public void onWindowFocusChanged(boolean hasFocus) {
            super.onWindowFocusChanged(hasFocus);
            if (hasFocus) updateColors();
        }

        private void updateColors() {
            if (dialog == null) return;
            MenuPalette next = MenuPalette.current(getContext());
            if (next == current) return;
            current = next;
            setBackgroundColor(next.surface);
            applyPalette(this, next);
            if (customTitle != null) applyPalette(customTitle, next);
            styleDialog(dialog, next);
        }
    }

    private enum CardStyle { PLAIN, ACTION, EMPHASIS }

    private static void applyPalette(View view, MenuPalette palette) {
        if (view.getTag() instanceof CardStyle)
            paintCard(view, (CardStyle) view.getTag(), palette);
        if (view instanceof MenuText) {
            MenuText text = (MenuText) view;
            text.setTextColor(text.tone.color(palette));
        } else if (view instanceof SettingsUi.ToggleRow) {
            SettingsUi.ToggleRow row = (SettingsUi.ToggleRow) view;
            row.title.setTextColor(palette.text);
            row.subtitle.setTextColor(palette.muted);
            row.control.setMenuPalette(palette);
        } else if (view instanceof ListView) {
            styleList((ListView) view, palette);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) applyPalette(group.getChildAt(i), palette);
        }
    }

    static void styleList(ListView list, MenuPalette palette) {
        list.setDivider(new ColorDrawable(palette.surface));
        list.setDividerHeight(SettingsUi.dp(list.getContext(), ROW_GAP));
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
        CardStyle style = emphasis ? CardStyle.EMPHASIS : clickable ? CardStyle.ACTION : CardStyle.PLAIN;
        view.setTag(style);
        paintCard(view, style, MenuPalette.current(view.getContext()));
    }

    private static void paintCard(View view, CardStyle style, MenuPalette palette) {
        Context context = view.getContext();
        GradientDrawable background = shape(context, style == CardStyle.EMPHASIS ? palette.highlight : palette.card, 11);
        view.setBackground(style == CardStyle.PLAIN ? background
                : new RippleDrawable(ColorStateList.valueOf(palette.ripple), background, null));
    }

    static View row(Context context, String label, boolean category, boolean update) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        pad(row, 13, 2);
        row.setMinimumHeight(SettingsUi.dp(context, ROW_HEIGHT));
        card(row, update, true);
        TextView title = text(context, label, 15, update ? Tone.ROSE : Tone.INK);
        if (category || update) title.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView arrow = text(context, "›", 19, Tone.ROSE);
        arrow.setGravity(Gravity.CENTER);
        arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(arrow, SettingsUi.controlSlot(context));
        return row;
    }

    static SettingsUi.ToggleRow toggle(Context context, String label, String detail,
                                      boolean checked, SettingsUi.Change change) {
        SettingsUi.ToggleRow row = SettingsUi.toggle(context, label, detail, checked, change);
        MenuPalette palette = MenuPalette.current(context);
        row.setMinimumHeight(SettingsUi.dp(context, ROW_HEIGHT));
        row.title.setTextSize(15);
        row.title.setTextColor(palette.text);
        row.subtitle.setTextSize(11);
        row.subtitle.setTextColor(palette.muted);
        row.control.setMenuPalette(palette);
        pad(row, 13, 2);
        card(row, false, true);
        return row;
    }

    private static void styleDialog(AlertDialog dialog, MenuPalette palette) {
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(shape(dialog.getContext(), palette.surface, 24));
            if (android.os.Build.VERSION.SDK_INT >= 29)
                dialog.getWindow().getDecorView().setForceDarkAllowed(false);
        }
        for (int button : new int[]{AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL, AlertDialog.BUTTON_POSITIVE})
            if (dialog.getButton(button) != null) dialog.getButton(button).setTextColor(palette.accent);
    }

    static void wireScrollHint(final ListView list, final TextView hint) {
        hint.setGravity(Gravity.CENTER);
        hint.setMinHeight(SettingsUi.dp(list.getContext(), 24));
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
        final ThemeLayout content = new ThemeLayout(activity);
        pad(content, 16, 10);
        scroll.addView(content);
        final TextView title = text(activity, "更新公告", 22, Tone.INK);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        pad(title, 16, 12);
        final AlertDialog dialog = builder(activity).setCustomTitle(title)
                .setView(scroll).setPositiveButton("关闭", null).setNeutralButton("刷新", null).create();
        final Runnable load = new Runnable() {
            public void run() {
                content.removeAllViews();
                content.addView(text(activity, "正在加载更新公告…", 14, Tone.MUTED));
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
                            content.addView(text(activity, "暂时无法加载公告，请点刷新重试。", 14, Tone.MUTED));
                            return;
                        }
                        for (int i = 0; i < releases.length(); i++) {
                            JSONObject release = releases.optJSONObject(i);
                            if (release == null) continue;
                            LinearLayout card = column(activity);
                            pad(card, 14, 12);
                            card(card, false, false);
                            String date = release.optString("date");
                            TextView heading = text(activity, "内核 v" + release.optString("version"), 17, Tone.INK);
                            heading.setTypeface(Typeface.DEFAULT_BOLD);
                            card.addView(heading);
                            if (!date.isEmpty()) card.addView(text(activity, date, 12, Tone.MUTED));
                            addNotes(card, "新增", release.optJSONArray("added"));
                            addNotes(card, "修复", release.optJSONArray("fixed"));
                            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
                            params.bottomMargin = SettingsUi.dp(activity, 8);
                            content.addView(card, params);
                        }
                        if (content.getChildCount() == 0)
                            content.addView(text(activity, "暂无更新公告。", 14, Tone.MUTED));
                    }});
                }}, "wxentry-announcements").start();
            }
        };
        dialog.show();
        content.bind(dialog, title);
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) { load.run(); }
        });
        load.run();
    }

    private static void addNotes(LinearLayout card, String label, JSONArray notes) {
        if (notes == null || notes.length() == 0) return;
        Context context = card.getContext();
        TextView title = text(context, label, 14, Tone.ROSE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, SettingsUi.dp(context, 10), 0, SettingsUi.dp(context, 3));
        card.addView(title);
        for (int i = 0; i < notes.length(); i++) {
            TextView note = text(context, "•  " + notes.optString(i), 14, Tone.INK);
            note.setPadding(0, 0, 0, SettingsUi.dp(context, 4));
            card.addView(note);
        }
    }
}
