package com.zafiro.wxentry.kernel;

import android.database.Cursor;
import java.util.*;

/** Only selectable friend/group conversations already present in the user's chat list. */
final class VoiceForwardTargets {
    static final class Target {
        final String id,name;
        final boolean group;
        Target(String id,String name) { this.id=id;this.name=name;group=id.endsWith("@chatroom"); }
        String label() { return (group?"[群聊] ":"[好友] ")+name; }
    }
    private static final Set<String> SYSTEM=new HashSet<String>(Arrays.asList(
        "weixin","newsapp","fmessage","medianote","floatbottle","qmessage","tmessage",
        "bottlemessage","feedsapp","shakeapp","voiceinputapp","meishiapp","readerapp","blogapp",
        "notification_messages","notifymessage","officialaccounts","service_officialaccounts",
        "brandsessionholder","appbrand_notify_message","appbrandcustomerservicemsg",
        "holder_fold_chat","group_fold"));
    static boolean eligible(String id,int type,int verify) {
        if(id==null || id.length()==0 || SYSTEM.contains(id) || id.startsWith("gh_"))return false;
        if(id.endsWith("@chatroom") || id.equals("filehelper"))return true;
        return id.indexOf('@')<0 && (type&1)!=0 && (verify&8)==0;
    }
    static List<Target> load() throws Throwable {
        Object db=Kernel.db0();Cursor cursor=null;
        List<Target> result=new ArrayList<Target>();Set<String> seen=new HashSet<String>();
        String sql="select c.username,r.conRemark,r.nickname,coalesce(r.type,0),coalesce(r.verifyFlag,0) "
            +"from rconversation c left join rcontact r on r.username=c.username ";
        try {
            try { cursor=Kernel.rawQuery(db,sql+"order by c.conversationTime desc",null); }
            catch(Throwable oldSchema) { cursor=Kernel.rawQuery(db,sql+"order by c.rowid desc",null); }
            while(cursor!=null && cursor.moveToNext()) {
                String id=cursor.getString(0);
                if(!eligible(id,cursor.getInt(3),cursor.getInt(4)) || !seen.add(id))continue;
                String remark=cursor.getString(1),nick=cursor.getString(2);
                String name=remark!=null && remark.trim().length()>0?remark.trim():
                    nick!=null && nick.trim().length()>0?nick.trim():id.equals("filehelper")?"文件传输助手":id;
                result.add(new Target(id,name));
            }
        } finally { if(cursor!=null)cursor.close(); }
        return result;
    }
    static List<Target> filter(List<Target> targets,int kind,String query) {
        String text=query==null?"":query.trim().toLowerCase(Locale.ROOT);
        List<Target> result=new ArrayList<Target>();
        for(Target target:targets) {
            if(kind==1 && target.group || kind==2 && !target.group)continue;
            if(text.length()>0 && !target.name.toLowerCase(Locale.ROOT).contains(text)
                    && !target.id.toLowerCase(Locale.ROOT).contains(text))continue;
            result.add(target);
        }
        return result;
    }
}
