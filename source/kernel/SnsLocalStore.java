package com.zafiro.wxentry.kernel;

import java.io.File;
import java.nio.charset.StandardCharsets;

/** One private, account-owned file; independent of all existing Moments and receipt settings. */
final class SnsLocalStore {
    interface Edit { SnsLocalData apply(SnsLocalData before)throws Exception; }
    static volatile Runnable changed;
    private static String cachedOwner,cachedPath;private static long stamp=-1,length=-1,revision;
    private static SnsLocalData cached=SnsLocalData.empty();private static boolean valid=true;
    static String path(String owner){if(!ReceiptConfig.validId(owner))throw new IllegalArgumentException("Account unavailable");return Kernel.WXDIR+"moments.local."+ModuleState.hash(owner).substring(0,16);}
    static synchronized SnsLocalData load(String owner){
        try{
            String p=path(owner);File file=new File(p);long modified=file.lastModified(),bytes=file.length();
            if(owner.equals(cachedOwner)&&p.equals(cachedPath)&&stamp==modified&&length==bytes)return cached;
            SnsLocalData value=SnsLocalData.empty();boolean okay=true;
            try{if(bytes>SnsLocalData.MAX_BYTES)throw new IllegalArgumentException();String raw=ModuleState.read(p);if(file.exists()&&raw==null)throw new IllegalArgumentException();value=SnsLocalData.decode(raw);}catch(Throwable invalid){okay=false;Kernel.T("ARSNSLOCAL load-failed bytes="+bytes+" error="+invalid.getClass().getSimpleName());}
            cachedOwner=owner;cachedPath=p;stamp=modified;length=bytes;cached=value;valid=okay;revision++;return cached;
        }catch(Throwable unavailable){return SnsLocalData.empty();}
    }
    static SnsLocalData current(){return load(Kernel.receiptAccount());}
    static boolean enabled(SnsLocalData.Feature feature){return Kernel.momentsPremiumEnabled()&&current().enabled(feature);}
    static synchronized long revision(){current();return revision;}
    static synchronized boolean edit(String owner,String keep,Edit edit){
        if(!ReceiptConfig.validId(owner)||!Kernel.momentsPremiumEnabled()||!owner.equals(Kernel.receiptAccount()))return false;
        try{
            SnsLocalData old=load(owner);if(!valid||!owner.equals(cachedOwner))return false;SnsLocalData next=edit.apply(old);String raw=next.encode();
            while(raw.getBytes(StandardCharsets.UTF_8).length>SnsLocalData.MAX_BYTES){next=next.evict(keep);raw=next.encode();}
            if(!Kernel.momentsPremiumEnabled()||!owner.equals(Kernel.receiptAccount())||!ModuleState.write(path(owner),raw))return false;
            cached=next;File file=new File(cachedPath);stamp=file.lastModified();length=file.length();revision++;notifyChanged();return true;
        }catch(Throwable failure){Kernel.T("ARSNSLOCAL save-failed "+failure.getClass().getSimpleName());return false;}
    }
    static synchronized boolean reset(String owner){
        if(!ReceiptConfig.validId(owner)||!Kernel.momentsPremiumEnabled()||!owner.equals(Kernel.receiptAccount()))return false;
        try{SnsLocalData before=load(owner),next=valid?before.clear():SnsLocalData.empty();if(!Kernel.momentsPremiumEnabled()||!owner.equals(Kernel.receiptAccount())||!ModuleState.write(path(owner),next.encode()))return false;cachedOwner=null;load(owner);notifyChanged();return true;}catch(Throwable failure){return false;}
    }
    private static void notifyChanged(){Runnable r=changed;if(r!=null)try{r.run();}catch(Throwable ignored){}}
}
