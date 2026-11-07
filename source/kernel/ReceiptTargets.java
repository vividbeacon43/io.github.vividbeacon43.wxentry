package com.zafiro.wxentry.kernel;

import android.database.Cursor;
import java.util.*;

final class ReceiptTargets {
    static final class Target {
        final String id,name;final long lastChat;
        Target(String id,String name,long time) { this.id=id;this.name=name;lastChat=time; }
        boolean group() { return ReceiptConfig.Scope.of(id)==ReceiptConfig.Scope.GROUP; }
    }
    static final Comparator<Target> RECENT=new Comparator<Target>() {
        public int compare(Target a,Target b) {
            int n=Long.compare(b.lastChat,a.lastChat);if(n!=0)return n;
            n=a.name.compareToIgnoreCase(b.name);return n!=0?n:a.id.compareTo(b.id);
        }
    };
    static boolean eligible(String id,int type,int verify,String account) {
        return ReceiptConfig.validId(id) && !id.equals(account) && !id.equals("filehelper") && VoiceForwardTargets.eligible(id,type,verify);
    }
    static List<Target> load(String account) throws Throwable {
        Object db=Kernel.db0();Cursor cursor=null;Map<String,Target> unique=new HashMap<String,Target>();
        String sql="select r.username,r.conRemark,r.nickname,coalesce(r.type,0),coalesce(r.verifyFlag,0),coalesce(c.conversationTime,0) "
            +"from rcontact r left join rconversation c on c.username=r.username order by c.conversationTime desc";
        try {
            cursor=Kernel.rawQuery(db,sql,null);
            if(cursor==null)throw new IllegalStateException("聊天列表尚未就绪");
            while(cursor!=null && cursor.moveToNext()) {
                String id=cursor.getString(0);if(!eligible(id,cursor.getInt(3),cursor.getInt(4),account))continue;
                String remark=cursor.getString(1),nick=cursor.getString(2);
                String name=!RedPacketEngine.empty(remark)?remark.trim():!RedPacketEngine.empty(nick)?nick.trim():id;
                long time=Math.max(0,cursor.getLong(5));Target old=unique.get(id);
                if(old==null || old.lastChat<time)unique.put(id,new Target(id,name,time));
            }
        } finally { if(cursor!=null)cursor.close(); }
        List<Target> result=new ArrayList<Target>(unique.values());Collections.sort(result,RECENT);return result;
    }
    static List<Target> withSaved(List<Target> all,Set<String> saved,ReceiptConfig.Scope scope) {
        List<Target> result=new ArrayList<Target>();Set<String> seen=new HashSet<String>();
        for(Target t:all)if(ReceiptConfig.Scope.of(t.id)==scope && seen.add(t.id))result.add(t);
        for(String id:saved)if(ReceiptConfig.validMember(id,scope) && seen.add(id))result.add(new Target(id,id+"（已保存）",0));
        Collections.sort(result,RECENT);return result;
    }
    static List<Target> filter(List<Target> sorted,String query) {
        String q=query==null?"":query.trim().toLowerCase(Locale.ROOT);List<Target> result=new ArrayList<Target>();
        for(Target t:sorted)if(q.isEmpty() || t.name.toLowerCase(Locale.ROOT).contains(q) || t.id.toLowerCase(Locale.ROOT).contains(q))result.add(t);
        return result;
    }
}
