package com.tencent.mm.ui;

import android.content.res.Resources;

public class ok {
    public static Boolean mode;
    public static boolean F(Resources resources) {
        if (mode == null) throw new IllegalStateException("Host appearance unavailable");
        return mode;
    }
}
