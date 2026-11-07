package com.zafiro.wxentry.kernel;

import java.util.Random;

final class ReceiptStore {
    static final String LEGACY_OWNER=Kernel.WXDIR+"receipt.legacy.owner";
    private static final Random RANDOM=new Random();
    static String account() {
        String id=Kernel.receiptAccount();
        if(!ReceiptConfig.validId(id))throw new IllegalStateException("当前账号尚未就绪");return id;
    }
    static String path(ReceiptConfig.Feature feature,String account) {
        if(!ReceiptConfig.validId(account))throw new IllegalArgumentException("Invalid receipt account");
        return Kernel.WXDIR+feature.key+".receipt.settings."+RedPacketRuntime.hash(account).substring(0,16);
    }
    static ReceiptConfig current(ReceiptConfig.Feature feature) { return load(feature,account()); }
    static synchronized ReceiptConfig load(ReceiptConfig.Feature feature,String account) {
        String path=path(feature,account),text=RedPacketRuntime.read(path);
        if(text!=null)return ReceiptConfig.decode(text); // Corruption fails closed; never silently resets to allow-all.
        ReceiptConfig config=new ReceiptConfig();
        if(feature==ReceiptConfig.Feature.RED_PACKET) {
            String owner=RedPacketRuntime.read(LEGACY_OWNER),hash=RedPacketRuntime.hash(account);
            if(owner==null) {
                if(!RedPacketRuntime.write(LEGACY_OWNER,hash+"\n"))throw new IllegalStateException("旧设置迁移标记保存失败");owner=hash;
            }
            if(hash.equals(owner.trim()))migrate(config);
        }
        if(!save(feature,account,config))throw new IllegalStateException("领取设置保存失败");
        return config;
    }
    static void migrate(ReceiptConfig config) {
        config.enabled=RedPacketRuntime.flag(Kernel.RP_ON_PATH);
        String whitelist=Kernel.readText(Kernel.RP_WL_PATH);
        if(whitelist!=null && !whitelist.trim().isEmpty()) {
            config.group.rule=ReceiptConfig.Rule.WHITE;
            for(String line:whitelist.split("\n")) {
                line=line.trim();if(line.isEmpty() || line.startsWith("#") || line.equals("@none"))continue;
                if(ReceiptConfig.validMember(line,ReceiptConfig.Scope.GROUP))config.group.white.add(line);
            }
        }
        String mode=Kernel.readText(Kernel.RP_MODE_PATH);mode=mode==null?"":mode.trim();
        if(mode.equals("group"))config.person.rule=ReceiptConfig.Rule.OFF;
        if(mode.equals("c2c"))config.group.rule=ReceiptConfig.Rule.OFF;
        String delay=Kernel.readText(Kernel.RP_DELAY_PATH);
        if(delay!=null)try {
            String[] values=delay.trim().split("-",2);long a=Long.parseLong(values[0].trim()),b=values.length==1?a:Long.parseLong(values[1].trim());
            long lo=Math.min(a,b),hi=Math.max(a,b);
            if(lo>=0 && hi<=ReceiptConfig.MAX_DELAY_MS) { config.delayEnabled=true;config.minDelayMs=lo;config.maxDelayMs=hi; }
        } catch(RuntimeException malformed) { }
    }
    static synchronized boolean save(ReceiptConfig.Feature feature,String account,ReceiptConfig config) {
        return RedPacketRuntime.write(path(feature,account),config.encode());
    }
    static boolean enabled(ReceiptConfig.Feature feature) {
        try { return current(feature).enabled; }catch(Throwable unavailable) { return false; }
    }
    static boolean permits(ReceiptConfig.Feature feature,String account,String talker) {
        try { ReceiptConfig c=load(feature,account);return c.enabled && c.permits(talker); }
        catch(Throwable unavailable) { return false; }
    }
    static long delay(ReceiptConfig.Feature feature,String account) { return load(feature,account).delay(RANDOM); }
}
