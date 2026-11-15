package com.zafiro.wxentry.kernel;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.StateListAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.PathInterpolator;
import android.widget.AbsListView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.concurrent.Callable;
import org.json.JSONArray;
import org.json.JSONObject;

final class FeatureMenuUi {
    static final int ROW_HEIGHT = SettingsUi.CONTROL_SIZE;
    private static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private static final PathInterpolator QUICK = new PathInterpolator(0.2f, 0f, 0.25f, 1f);

    enum Tone {
        INK, MUTED, ACCENT;

        int color(MenuPalette palette) {
            switch (this) {
                case MUTED: return palette.muted;
                case ACCENT: return palette.accent;
                default: return palette.text;
            }
        }
    }

    private enum Surface { CHROME, RULE, BUTTON, ROW, EMPHASIS }

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
        int style = MenuPalette.current(host) == MenuPalette.DARK
                ? android.R.style.Theme_Material_Dialog_Alert
                : android.R.style.Theme_Material_Light_Dialog_Alert;
        return new AlertDialog.Builder(host, style);
    }

    /** Repaint the existing native views when the WeChat theme changes. */
    static final class ThemeLayout extends LinearLayout {
        private MenuPalette current;
        private AlertDialog dialog;

        ThemeLayout(Activity host) {
            super(host);
            setOrientation(VERTICAL);
            setClipToOutline(true);
            setBackground(windowShape(host, MenuPalette.current(host)));
        }

        void bind(AlertDialog dialog) {
            this.dialog = dialog;
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
            if (next != current) {
                current = next;
                setBackground(windowShape(getContext(), next));
                applyPalette(this, next);
            }
            Window window = dialog.getWindow();
            if (window == null) return;
            window.setBackgroundDrawable(windowShape(getContext(), next));
            window.setDimAmount(0.55f);
            if (android.os.Build.VERSION.SDK_INT >= 29) window.getDecorView().setForceDarkAllowed(false);
            int available = getResources().getDisplayMetrics().widthPixels - dp(getContext(), 38);
            if (available > 0)
                window.setLayout(Math.min(available, dp(getContext(), 448)), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private static void applyPalette(View view, MenuPalette palette) {
        if (view.getTag() instanceof Surface) paint(view, (Surface) view.getTag(), palette);
        if (view instanceof MenuText) {
            MenuText label = (MenuText) view;
            label.setTextColor(label.tone.color(palette));
        } else if (view instanceof Glyph) {
            ((Glyph) view).recolor(palette);
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
        list.setDivider(new ColorDrawable(palette.line));
        list.setDividerHeight(dp(list.getContext(), 1));
        list.setCacheColorHint(palette.surface);
        fadeEdges(list);
    }

    private static void fadeEdges(View scrolling) {
        scrolling.setVerticalFadingEdgeEnabled(true);
        scrolling.setFadingEdgeLength(dp(scrolling.getContext(), 19));
    }

    static void slidePage(View title, View list, boolean forward) {
        reveal(title, forward ? 8 : -8, 190);
        reveal(list, forward ? 16 : -12, 230);
    }

    private static void reveal(View view, int offset, int duration) {
        view.animate().cancel();
        if (android.os.Build.VERSION.SDK_INT >= 26 && !ValueAnimator.areAnimatorsEnabled()) {
            view.setTranslationX(0);
            view.setAlpha(1);
            return;
        }
        int direction = view.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL ? -1 : 1;
        view.setTranslationX(direction * dp(view.getContext(), offset));
        view.setAlpha(0.24f);
        view.animate().translationX(0).alpha(1).setDuration(duration).setInterpolator(QUICK).start();
    }

    private static void pressMotion(View view, float pressedScale) {
        StateListAnimator states = new StateListAnimator();
        states.addState(new int[]{android.R.attr.state_enabled, android.R.attr.state_pressed},
                scale(view, pressedScale, 85));
        states.addState(new int[0], scale(view, 1f, 145));
        view.setStateListAnimator(states);
    }

    private static ObjectAnimator scale(View view, float amount, int duration) {
        ObjectAnimator animator = ObjectAnimator.ofPropertyValuesHolder(view,
                PropertyValuesHolder.ofFloat(View.SCALE_X, amount),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, amount));
        animator.setDuration(duration);
        animator.setInterpolator(QUICK);
        return animator;
    }

    private static int dp(Context context, int size) {
        return SettingsUi.dp(context, size);
    }

    private static LinearLayout column(Context context) {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }

    private static LinearLayout horizontal(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private static GradientDrawable shape(Context context, int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(context, radius));
        return shape;
    }

    private static GradientDrawable windowShape(Context context, MenuPalette palette) {
        GradientDrawable shape = shape(context, palette.surface, 18);
        shape.setStroke(dp(context, 1), palette.line);
        return shape;
    }

    private static void surface(View view, Surface style) {
        view.setTag(style);
        paint(view, style, MenuPalette.current(view.getContext()));
    }

    private static void paint(View view, Surface style, MenuPalette palette) {
        if (style == Surface.CHROME || style == Surface.RULE) {
            view.setBackgroundColor(style == Surface.CHROME ? palette.card : palette.line);
            return;
        }
        Context context = view.getContext();
        int color = style == Surface.EMPHASIS ? palette.highlight
                : style == Surface.BUTTON ? palette.surface : 0x00000000;
        GradientDrawable background = shape(context, color, style == Surface.ROW ? 0 : 8);
        if (style == Surface.BUTTON || style == Surface.EMPHASIS) background.setStroke(dp(context, 1), palette.line);
        GradientDrawable mask = shape(context, 0xFFFFFFFF, style == Surface.ROW ? 0 : 8);
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(palette.ripple), background, mask));
    }

    private static View rule(Context context) {
        View rule = new View(context);
        surface(rule, Surface.RULE);
        rule.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(context, 1)));
        return rule;
    }

    static final class MenuHeader extends LinearLayout {
        final TextView title;
        final TextView update;
        final View close;
        final View back;
        private final Glyph mark;

        MenuHeader(Activity activity, String version, String updateLabel, String reply) {
            super(activity);
            setOrientation(VERTICAL);

            LinearLayout nav = horizontal(activity);
            nav.setPadding(dp(activity, 9), 0, dp(activity, 7), 0);
            surface(nav, Surface.CHROME);
            LinearLayout brand = horizontal(activity);
            brand.setPadding(dp(activity, 8), 0, dp(activity, 10), 0);
            surface(brand, Surface.ROW);
            pressMotion(brand, 0.965f);
            mark = new Glyph(activity, Icon.BRAND, Tone.ACCENT, 20);
            brand.addView(mark, new LinearLayout.LayoutParams(dp(activity, 22), dp(activity, 30)));
            TextView name = text(activity, "WXEntry", 14, Tone.INK);
            name.setTypeface(MEDIUM);
            LinearLayout.LayoutParams brandLabel = new LinearLayout.LayoutParams(-2, -2);
            brandLabel.leftMargin = dp(activity, 8);
            brand.addView(name, brandLabel);
            brand.setMinimumHeight(dp(activity, 46));
            nav.addView(brand, new LinearLayout.LayoutParams(-2, -1));
            nav.addView(new View(activity), new LinearLayout.LayoutParams(0, 1, 1));
            back = brand;

            Glyph x = new Glyph(activity, Icon.CLOSE, Tone.MUTED, 18);
            x.setContentDescription("关闭");
            x.setFocusable(true);
            x.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            surface(x, Surface.ROW);
            pressMotion(x, 0.93f);
            nav.addView(x, new LinearLayout.LayoutParams(dp(activity, 48), -1));
            close = x;
            addView(nav, new LinearLayout.LayoutParams(-1, dp(activity, 48)));
            addView(rule(activity));

            LinearLayout heading = column(activity);
            heading.setPadding(dp(activity, 18), dp(activity, 13), dp(activity, 18), dp(activity, 13));
            LinearLayout titleRow = horizontal(activity);
            title = text(activity, "模块设置", 24, Tone.INK);
            title.setTypeface(MEDIUM);
            titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
            update = text(activity, updateLabel, 13, Tone.ACCENT);
            update.setTypeface(MEDIUM);
            update.setGravity(Gravity.CENTER);
            update.setPadding(dp(activity, 10), 0, dp(activity, 10), 0);
            update.setMinimumHeight(dp(activity, 44));
            update.setMaxLines(2);
            update.setMaxWidth(dp(activity, 145));
            update.setEllipsize(TextUtils.TruncateAt.END);
            update.setFocusable(true);
            surface(update, Surface.BUTTON);
            pressMotion(update, 0.96f);
            LinearLayout.LayoutParams updateParams = new LinearLayout.LayoutParams(-2, -2);
            updateParams.leftMargin = dp(activity, 9);
            titleRow.addView(update, updateParams);
            heading.addView(titleRow);
            if (version != null && !version.isEmpty()) {
                TextView versionText = text(activity, version, 12, Tone.MUTED);
                versionText.setPadding(0, dp(activity, 2), 0, 0);
                heading.addView(versionText);
            }
            if (reply != null && !reply.isEmpty()) {
                TextView replyText = text(activity, "官方回复：" + reply, 12, Tone.MUTED);
                replyText.setPadding(0, dp(activity, 8), 0, 0);
                replyText.setMaxLines(2);
                replyText.setEllipsize(TextUtils.TruncateAt.END);
                heading.addView(replyText);
            }
            addView(heading);
            addView(rule(activity));
        }

        void showSection(String label, boolean nested) {
            title.setText(label);
            mark.kind = nested ? Icon.BACK : Icon.BRAND;
            mark.invalidate();
            back.setClickable(nested);
            back.setFocusable(nested);
            back.setContentDescription(nested ? "返回模块设置" : null);
        }
    }

    static View row(Context context, String label, String kind, boolean update) {
        LinearLayout row = horizontal(context);
        row.setPadding(dp(context, 18), 0, dp(context, 12), 0);
        row.setMinimumHeight(dp(context, ROW_HEIGHT));
        surface(row, update ? Surface.EMPHASIS : Surface.ROW);
        pressMotion(row, 0.986f);
        Icon icon = menuIcon(kind);
        if (icon != null) {
            Glyph glyph = new Glyph(context, icon, Tone.MUTED, 18);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(context, 20), dp(context, 36));
            iconParams.rightMargin = dp(context, 11);
            row.addView(glyph, iconParams);
        }
        TextView labelView = text(context, label, 15, update ? Tone.ACCENT : Tone.INK);
        if (kind != null || update) labelView.setTypeface(MEDIUM);
        row.addView(labelView, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(new Glyph(context, Icon.NEXT, Tone.MUTED, 16), SettingsUi.controlSlot(context));
        return row;
    }

    static SettingsUi.ToggleRow toggle(Context context, String label, String detail,
                                      boolean checked, SettingsUi.Change change) {
        SettingsUi.ToggleRow row = SettingsUi.toggle(context, label, detail, checked, change);
        MenuPalette palette = MenuPalette.current(context);
        row.setMinimumHeight(dp(context, ROW_HEIGHT));
        row.title.setTextSize(15);
        row.title.setTextColor(palette.text);
        row.subtitle.setTextSize(12);
        row.subtitle.setTextColor(palette.muted);
        row.control.setMenuPalette(palette);
        row.setPadding(dp(context, 18), dp(context, 4), dp(context, 12), dp(context, 4));
        surface(row, Surface.ROW);
        pressMotion(row, 0.986f);
        pressMotion(row.control, 0.955f);
        return row;
    }

    static void wireScrollHint(final ListView list, final TextView hint) {
        hint.setGravity(Gravity.CENTER);
        hint.setMinHeight(dp(list.getContext(), 30));
        hint.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_NONE);
        surface(hint, Surface.CHROME);
        hint.setVisibility(View.GONE);
        Runnable refresh = () -> {
            int last = list.getLastVisiblePosition();
            boolean below = last >= 0 && last < list.getCount() - 1 || list.canScrollVertically(1);
            boolean above = list.getFirstVisiblePosition() > 0 || list.canScrollVertically(-1);
            String label = below ? "⌄  上滑查看更多" : above ? "⌃  下滑返回上方" : "";
            if (!label.contentEquals(hint.getText())) hint.setText(label);
            int visibility = label.isEmpty() ? View.GONE : View.VISIBLE;
            if (hint.getVisibility() != visibility) hint.setVisibility(visibility);
        };
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            public void onScrollStateChanged(AbsListView view, int state) {}
            public void onScroll(AbsListView view, int first, int visible, int count) {
                list.removeCallbacks(refresh);
                list.post(refresh);
            }
        });
        list.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            list.removeCallbacks(refresh);
            list.post(refresh);
        });
    }

    static void showAnnouncements(final Activity activity, final Callable<String> fetch) {
        final ThemeLayout panel = new ThemeLayout(activity);
        final MenuHeader header = new MenuHeader(activity, "", "刷新", null);
        header.showSection("更新公告", false);
        panel.addView(header);

        final ScrollView scroll = new ScrollView(activity) {
            @Override public int getSolidColor() {
                return MenuPalette.current(getContext()).surface;
            }
        };
        fadeEdges(scroll);
        scroll.setVerticalScrollBarEnabled(true);
        final LinearLayout content = column(activity);
        scroll.addView(content);
        int screen = activity.getResources().getDisplayMetrics().heightPixels;
        int height = Math.min(dp(activity, 530),
                Math.min((int) (screen * 0.61f), Math.max(dp(activity, 110), screen - dp(activity, 175))));
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, height));
        final AlertDialog dialog = builder(activity).setView(panel).create();
        header.close.setOnClickListener(view -> dialog.dismiss());
        final Runnable load = new Runnable() {
            @Override public void run() {
                content.removeAllViews();
                TextView status = message("正在加载更新公告…");
                content.addView(status);
                scroll.scrollTo(0, 0);
                header.update.setEnabled(false);
                new Thread(() -> {
                    JSONArray result = null;
                    try { result = new JSONObject(fetch.call()).getJSONArray("releases"); }
                    catch (Exception ignored) {}
                    final JSONArray releases = result;
                    activity.runOnUiThread(() -> {
                        if (!dialog.isShowing() || activity.isFinishing() || activity.isDestroyed()) return;
                        header.update.setEnabled(true);
                        content.removeAllViews();
                        if (releases == null) {
                            content.addView(message("暂时无法加载公告，请点刷新重试。"));
                            return;
                        }
                        for (int i = 0; i < releases.length(); i++) {
                            JSONObject release = releases.optJSONObject(i);
                            if (release == null) continue;
                            if (content.getChildCount() != 0) content.addView(rule(activity));
                            addRelease(content, release);
                        }
                        if (content.getChildCount() == 0) content.addView(message("暂无更新公告。"));
                    });
                }, "wxentry-announcements").start();
            }

            private TextView message(String value) {
                TextView status = text(activity, value, 14, Tone.MUTED);
                status.setPadding(dp(activity, 18), dp(activity, 16), dp(activity, 18), dp(activity, 16));
                return status;
            }
        };
        dialog.show();
        panel.bind(dialog);
        header.update.setOnClickListener(view -> load.run());
        load.run();
    }

    private static void addRelease(LinearLayout content, JSONObject release) {
        Context context = content.getContext();
        LinearLayout section = column(context);
        section.setPadding(dp(context, 18), dp(context, 15), dp(context, 18), dp(context, 14));
        LinearLayout heading = horizontal(context);
        TextView version = text(context, "内核 v" + release.optString("version"), 16, Tone.INK);
        version.setTypeface(MEDIUM);
        heading.addView(version, new LinearLayout.LayoutParams(0, -2, 1));
        String date = release.optString("date");
        if (!date.isEmpty()) heading.addView(text(context, date, 12, Tone.MUTED));
        section.addView(heading);
        addNotes(section, "新增", release.optJSONArray("added"));
        addNotes(section, "修复", release.optJSONArray("fixed"));
        content.addView(section);
    }

    private static void addNotes(LinearLayout section, String label, JSONArray notes) {
        if (notes == null || notes.length() == 0) return;
        Context context = section.getContext();
        TextView title = text(context, label, 13, Tone.ACCENT);
        title.setTypeface(MEDIUM);
        title.setPadding(0, dp(context, 10), 0, dp(context, 3));
        section.addView(title);
        for (int i = 0; i < notes.length(); i++) {
            TextView note = text(context, notes.optString(i), 14, Tone.INK);
            note.setLineSpacing(dp(context, 2), 1);
            note.setPadding(0, 0, 0, dp(context, 7));
            section.addView(note);
        }
    }

    private enum Icon {
        BRAND, BACK, CLOSE, NEXT, DONATE, ANNOUNCEMENTS, CHAT, RED_PACKET, MOMENTS,
        ANTI_RECALL, SUPPORT, EXPERIMENTAL, RESTART
    }

    private static Icon menuIcon(String name) {
        if (name == null) return null;
        try { return Icon.valueOf(name); }
        catch (IllegalArgumentException unknown) { return null; }
    }

    /** Native line icons in one 24-unit coordinate grid. */
    private static final class Glyph extends View {
        Icon kind;
        final Tone tone;
        final int size;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path path = new Path();

        Glyph(Context context, Icon kind, Tone tone, int size) {
            super(context);
            this.kind = kind;
            this.tone = tone;
            this.size = size;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.65f);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeCap(Paint.Cap.ROUND);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            recolor(MenuPalette.current(context));
        }

        void recolor(MenuPalette palette) {
            paint.setColor(tone.color(palette));
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            float diameter = dp(getContext(), size);
            int save = canvas.save();
            canvas.translate((getWidth() - diameter) / 2, (getHeight() - diameter) / 2);
            canvas.scale(diameter / 24, diameter / 24);
            switch (kind) {
                case BRAND:
                    canvas.drawRoundRect(2.8f, 2.8f, 21.2f, 21.2f, 5, 5, paint);
                    line(canvas, 6, 8, 9, 16, 12, 11, 15, 16, 18, 8);
                    break;
                case BACK:
                    line(canvas, 14, 5, 7, 12, 14, 19);
                    break;
                case NEXT:
                    line(canvas, 9, 6, 15, 12, 9, 18);
                    break;
                case CLOSE:
                    line(canvas, 6, 6, 18, 18);
                    line(canvas, 18, 6, 6, 18);
                    break;
                case DONATE:
                    path.rewind();
                    path.moveTo(12, 20);
                    path.cubicTo(10, 18, 3, 14, 3, 9);
                    path.cubicTo(3, 4, 9, 3, 12, 8);
                    path.cubicTo(15, 3, 21, 4, 21, 9);
                    path.cubicTo(21, 14, 14, 18, 12, 20);
                    canvas.drawPath(path, paint);
                    break;
                case ANNOUNCEMENTS:
                    canvas.drawRoundRect(5, 3, 19, 21, 2, 2, paint);
                    line(canvas, 8, 8, 16, 8);
                    line(canvas, 8, 12, 16, 12);
                    line(canvas, 8, 16, 13, 16);
                    break;
                case CHAT:
                    line(canvas, 5, 4, 19, 4, 21, 6, 21, 15, 19, 17, 10, 17, 5, 21, 5, 17, 3, 15, 3, 6, 5, 4);
                    line(canvas, 7, 9, 17, 9);
                    line(canvas, 7, 12, 14, 12);
                    break;
                case RED_PACKET:
                    canvas.drawRoundRect(5, 3, 19, 21, 3, 3, paint);
                    line(canvas, 5, 8, 12, 13, 19, 8);
                    canvas.drawCircle(12, 13, 2.1f, paint);
                    break;
                case MOMENTS:
                    canvas.drawCircle(12, 12, 9, paint);
                    line(canvas, 12, 3, 15, 10, 21, 12);
                    line(canvas, 21, 12, 14, 15, 12, 21);
                    line(canvas, 12, 21, 9, 14, 3, 12);
                    line(canvas, 3, 12, 10, 9, 12, 3);
                    break;
                case ANTI_RECALL:
                    line(canvas, 12, 3, 20, 6, 20, 13, 17, 18, 12, 21, 7, 18, 4, 13, 4, 6, 12, 3);
                    line(canvas, 8.5f, 12, 11, 14.5f, 15.5f, 10);
                    break;
                case SUPPORT:
                    line(canvas, 4, 6, 20, 6);
                    line(canvas, 4, 12, 20, 12);
                    line(canvas, 4, 18, 20, 18);
                    canvas.drawCircle(8, 6, 2, paint);
                    canvas.drawCircle(15, 12, 2, paint);
                    canvas.drawCircle(10, 18, 2, paint);
                    break;
                case EXPERIMENTAL:
                    line(canvas, 9, 3, 15, 3);
                    line(canvas, 10, 3, 10, 9, 4.5f, 18, 5.5f, 21, 18.5f, 21, 19.5f, 18, 14, 9, 14, 3);
                    line(canvas, 8, 14, 16, 14);
                    break;
                case RESTART:
                    canvas.drawArc(4, 4, 20, 20, 5, 290, false, paint);
                    line(canvas, 20, 4, 20, 11, 13, 11);
                    break;
            }
            canvas.restoreToCount(save);
        }

        private void line(Canvas canvas, float... points) {
            path.rewind();
            path.moveTo(points[0], points[1]);
            for (int i = 2; i < points.length; i += 2) path.lineTo(points[i], points[i + 1]);
            canvas.drawPath(path, paint);
        }
    }
}

