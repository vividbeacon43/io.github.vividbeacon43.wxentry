package com.zafiro.wxentry.kernel;
import java.io.File;

final class MomentsSettings {
    static volatile Runnable forwardChanged;
    private static String cachedOwner,cachedPath;
    private static long cachedTime=-1,cachedLength=-1;
    private static MomentsConfig cached;
    static String path(String owner){
        if(!ReceiptConfig.validId(owner))throw new IllegalArgumentException("Moments account unavailable");
        return Kernel.WXDIR+"moments.settings."+RedPacketRuntime.hash(owner).substring(0,16);
    }
    static synchronized MomentsConfig current(){
        try{
            String owner=Kernel.receiptAccount(),path=path(owner);File file=new File(path);long time=file.lastModified(),length=file.length();
            if(cached!=null&&owner.equals(cachedOwner)&&path.equals(cachedPath)&&time==cachedTime&&length==cachedLength)return cached;
            String raw=file.exists()&&length<=1024?RedPacketRuntime.read(path):null;
            MomentsConfig value=length>1024||file.exists()&&raw==null?MomentsConfig.disabled():MomentsConfig.decode(raw);
            cachedOwner=owner;cachedPath=path;cachedTime=time;cachedLength=length;cached=value;return value;
        }catch(Throwable unavailable){return MomentsConfig.disabled();}
    }
    static boolean configured(MomentsConfig.Feature feature){return current().get(feature);}
    static boolean set(MomentsConfig.Feature feature,boolean on){
        Runnable changed=null;
        synchronized(MomentsSettings.class){
            if(!Kernel.momentsPremiumEnabled())return false;
            try{
                String owner=Kernel.receiptAccount(),path=path(owner);MomentsConfig value=current().with(feature,on);
                if(!owner.equals(Kernel.receiptAccount())||!RedPacketRuntime.write(path,value.encode()))return false;
                File file=new File(path);cachedOwner=owner;cachedPath=path;cachedTime=file.lastModified();cachedLength=file.length();cached=value;
                if(feature==MomentsConfig.Feature.FORWARD)changed=forwardChanged;
            }catch(Throwable unavailable){return false;}
        }
        if(changed!=null)try{changed.run();}catch(Throwable failure){Kernel.T("ARSNSSETTINGS listener-error "+failure.getClass().getSimpleName());}
        return true;
    }
}
