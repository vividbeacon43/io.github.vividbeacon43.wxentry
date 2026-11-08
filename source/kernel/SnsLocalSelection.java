package com.zafiro.wxentry.kernel;
import java.util.*;

/** Selection counts follow the available contacts; random replacement never repeats an ID. */
final class SnsLocalSelection {
    private static final Set<String> SYSTEM=new HashSet<String>(Arrays.asList(
        "filehelper","weixin","newsapp","fmessage","medianote","floatbottle","qmessage","tmessage","bottlemessage","feedsapp","shakeapp","voiceinputapp","meishiapp","readerapp","blogapp","notification_messages","notifymessage","officialaccounts","service_officialaccounts","brandsessionholder","appbrand_notify_message","appbrandcustomerservicemsg","holder_fold_chat","group_fold",
        "facebookapp","linkedinplugin","qqmail","downloaderapp","schedule_message","qqfriend","masssendapp","voipapp","voicevoipapp","lbsapp","qqsync","weibo","qqsafe","helper_entry"));
    static boolean selectable(String id){return SnsLocalData.friend(id)&&!SYSTEM.contains(id);}
    static LinkedHashSet<String> all(Collection<String> ids){
        LinkedHashSet<String> result=new LinkedHashSet<String>();
        for(String id:ids){if(!selectable(id))throw new IllegalArgumentException("Invalid selectable friend");result.add(id);}return result;
    }
    static LinkedHashSet<String> random(Collection<String> ids,int count,Random random){
        List<String> pool=new ArrayList<String>(all(ids));if(count<0||count>pool.size()||random==null)throw new IllegalArgumentException("Invalid random count");
        for(int i=0;i<count;i++){int at=i+random.nextInt(pool.size()-i);String value=pool.get(i);pool.set(i,pool.get(at));pool.set(at,value);}
        return new LinkedHashSet<String>(pool.subList(0,count));
    }
}
