package com.zafiro.wxentry.kernel;

import android.content.Context;

/** Colors for the module menu, selected from the host Activity's effective theme. */
final class MenuPalette {
    static final MenuPalette LIGHT = new MenuPalette(
            0xFFFFF5F8, 0xFFFFFFFF, 0xFFFBE4ED, 0xFF38232E, 0xFF755466,
            0xFFA82D61, 0x26A82D61, 0xFFA82D61, 0xFF8B244F, 0xFFE4D3DB, 0xFF9C7C8B);
    static final MenuPalette DARK = new MenuPalette(
            0xFF141416, 0xFF232125, 0xFF3A2632, 0xFFF8F1F4, 0xFFC4B6C0,
            0xFFFF9BC4, 0x33FF9BC4, 0xFFC04D82, 0xFFDD74A4, 0xFF574956, 0xFFA48C9D);

    final int surface, card, highlight, text, muted, accent, ripple;
    final int switchOn, switchOnEdge, switchOff, switchOffEdge;

    private MenuPalette(int surface, int card, int highlight, int text, int muted,
                        int accent, int ripple, int switchOn, int switchOnEdge, int switchOff, int switchOffEdge) {
        this.surface = surface;
        this.card = card;
        this.highlight = highlight;
        this.text = text;
        this.muted = muted;
        this.accent = accent;
        this.ripple = ripple;
        this.switchOn = switchOn;
        this.switchOnEdge = switchOnEdge;
        this.switchOff = switchOff;
        this.switchOffEdge = switchOffEdge;
    }

    static MenuPalette current(Context context) {
        return WechatAppearance.isDark(context) ? DARK : LIGHT;
    }
}
