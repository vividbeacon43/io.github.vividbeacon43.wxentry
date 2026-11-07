package com.zafiro.wxentry;

/** Common cumulative edition rules used by the loader and release builder. */
public final class EditionPolicy {
    private EditionPolicy() {}
    public static int rank(String edition) {
        if("free".equals(edition)) return 0;
        if("pro".equals(edition)) return 1;
        if("plus".equals(edition)) return 2;
        if("vip".equals(edition)) return 3;
        return -1;
    }
    public static boolean valid(String edition) { return rank(edition)>=0; }
    public static boolean includesImages(String edition) { return rank(edition)>=2; }
    public static String name(String edition) {
        if("pro".equals(edition)) return "专业版";
        if("plus".equals(edition)) return "高级版";
        if("vip".equals(edition)) return "VIP 版";
        return "免费版";
    }
    public static String manifestData(String edition,String version,String sha) {
        if(!valid(edition) || version==null || !version.matches("[1-9][0-9]{0,8}")
                || sha==null || !sha.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Invalid kernel manifest");
        return "kernel-v2|"+edition+"|"+version+"|"+sha;
    }
}
