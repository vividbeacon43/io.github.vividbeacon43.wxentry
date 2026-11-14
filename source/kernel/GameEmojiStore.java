package com.zafiro.wxentry.kernel;
import java.io.File;
import java.util.List;

final class GameEmojiStore {
    static String owner(){String account=Kernel.receiptAccount();if(!ReceiptConfig.validId(account))throw new IllegalStateException("请先登录微信");return ModuleState.hash(account);}
    static String path(String owner){if(owner==null||!owner.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Invalid owner");return Kernel.WXDIR+"gameemoji.settings."+owner;}
    static GameEmojiData read(String owner){String p=path(owner);File f=new File(p);if(f.length()>16384)throw new IllegalStateException("结果设置过大");String raw=ModuleState.read(p);if(f.exists()&&raw==null)throw new IllegalStateException("结果设置读取失败");return GameEmojiData.decode(raw,owner);}
    static boolean save(GameEmojiData data){return data.owner.equals(owner())&&ModuleState.write(path(data.owner),data.encode());}
    static synchronized GameEmojiData current(){return read(owner());}
    static synchronized boolean enabled(){try{return !Kernel.MODULE_OFF&&current().enabled;}catch(Throwable unavailable){return false;}}
    static synchronized boolean setEnabled(boolean on){try{if(Kernel.MODULE_OFF)return false;GameEmojiData d=current();boolean before=d.enabled;d.enabled=on;return save(d)?on:before;}catch(Throwable failure){return enabled();}}
    static synchronized boolean configure(String owner,GameEmojiData.Kind kind,int fallback,List<Integer> next){if(Kernel.MODULE_OFF||!owner.equals(owner()))return false;GameEmojiData d=read(owner);d.configure(kind,fallback,next);return save(d);}
    static synchronized String claim(String owner,GameEmojiData.Kind kind,String expected){if(Kernel.MODULE_OFF||!owner.equals(owner()))return null;GameEmojiData d=read(owner);if(!d.encode().equals(expected))return null;String ticket=java.util.UUID.randomUUID().toString().replace("-","");return d.claim(kind,ticket)&&save(d)?ticket:null;}
    static synchronized boolean complete(String owner,GameEmojiData.Kind kind,String ticket){try{if(!owner.equals(owner()))return false;GameEmojiData d=read(owner);return d.complete(kind,ticket)&&save(d);}catch(Throwable failure){return false;}}
    static synchronized void abort(String owner,GameEmojiData.Kind kind,String ticket){try{if(!owner.equals(owner()))return;GameEmojiData d=read(owner);if(d.abort(kind,ticket))save(d);}catch(Throwable ignored){}}
}
