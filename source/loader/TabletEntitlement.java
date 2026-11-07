package com.zafiro.wxentry;

/** Login-before-account access still requires this activation's verified paid kernel. */
public final class TabletEntitlement {
    private TabletEntitlement() {}
    public static boolean premium(String edition) {return "plus".equals(edition)||"vip".equals(edition);}
    public static boolean allowed(KernelDelivery delivery,String token,boolean licenseValid) {
        if(!licenseValid||delivery==null)return false;
        try {
            KernelDelivery.Bundle verified=delivery.cached(token);
            return verified!=null&&premium(verified.manifest.edition);
        }catch(Exception unavailable){return false;}
    }
}
