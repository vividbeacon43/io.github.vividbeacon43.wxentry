package com.zafiro.wxentry.kernel;

import java.util.LinkedHashMap;

/** Account-owned recall identity only; never stores or rewrites a message body. */
final class RecallMarkStore {
    static final int MAX=10000;
    static final class Entry {
        final String account,talker;final long local,server,created;final int type;
        Entry(String a,String t,long id,long svr,long time,int kind) { account=a;talker=t;local=id;server=svr;created=time;type=kind; }
        String key() { return talker+'\t'+local; }
        boolean sameRow(Entry other) { return other!=null&&java.util.Objects.equals(account,other.account)&&java.util.Objects.equals(talker,other.talker)&&local==other.local; }
        boolean rowValid() { return ReceiptConfig.validId(account)&&ReceiptConfig.validId(talker)&&local>0; }
        boolean matches(Entry other) { return other!=null && java.util.Objects.equals(account,other.account)&&java.util.Objects.equals(talker,other.talker)&&local==other.local&&server==other.server&&created==other.created; }
        boolean valid() { return ReceiptConfig.validId(account)&&ReceiptConfig.validId(talker)&&local>0&&server>=0&&created>0&&type>0; }
        String line() { return local+"\t"+server+"\t"+created+"\t"+type+"\t"+talker; }
    }
    static final class Snapshot {
        final String owner;final LinkedHashMap<String,Entry> entries=new LinkedHashMap<String,Entry>();
        Snapshot(String account) { owner=account; }
    }
    static Snapshot cached;
    static String readError="none";
    static String path(String account) { return Kernel.WXDIR+"recall-inline."+RedPacketRuntime.hash(account).substring(0,16); }
    static Snapshot load(String account) {
        if(!ReceiptConfig.validId(account))throw new IllegalArgumentException("Invalid recall account");
        if(cached!=null&&cached.owner.equals(account))return cached;
        Snapshot snapshot=new Snapshot(account);String data=RedPacketRuntime.read(path(account));
        if(data!=null) {
            if(data.length()>4000000)throw new IllegalStateException("Recall state too large");
            String[] lines=data.split("\n");
            if(lines.length<2||!lines[0].equals("format=1")||!lines[1].equals("owner="+RedPacketRuntime.hash(account)))throw new IllegalStateException("Invalid recall state");
            for(int i=2;i<lines.length;i++) {
                if(lines[i].isEmpty())continue;String[] p=lines[i].split("\t",-1);if(p.length!=5)throw new IllegalStateException("Invalid recall entry");
                Entry entry=new Entry(account,p[4],Long.parseLong(p[0]),Long.parseLong(p[1]),Long.parseLong(p[2]),Integer.parseInt(p[3]));
                if(!entry.valid()||snapshot.entries.put(entry.key(),entry)!=null||snapshot.entries.size()>MAX)throw new IllegalStateException("Invalid recall identity");
            }
        }
        cached=snapshot;return snapshot;
    }
    static synchronized Entry find(Entry identity) {
        if(identity==null||!identity.valid())return null;
        try { Entry entry=load(identity.account).entries.get(identity.key());return entry!=null&&entry.matches(identity)?entry:null; }
        catch(Throwable unavailable) { return null; }
    }
    static synchronized Entry candidate(Entry identity) {
        if(identity==null||!identity.rowValid())return null;
        try { Entry entry=load(identity.account).entries.get(identity.key());readError="none";return entry; }
        catch(Throwable unavailable) { readError=unavailable.getClass().getSimpleName();return null; }
    }
    static synchronized boolean mark(Entry entry) {
        if(entry==null||!entry.valid())return false;
        Snapshot snapshot=load(entry.account);Entry previous=snapshot.entries.get(entry.key());
        if(entry.matches(previous))return false;
        LinkedHashMap<String,Entry> next=new LinkedHashMap<String,Entry>(snapshot.entries);next.remove(entry.key());next.put(entry.key(),entry);
        while(next.size()>MAX)next.remove(next.keySet().iterator().next());
        StringBuilder text=new StringBuilder("format=1\nowner="+RedPacketRuntime.hash(entry.account)+"\n");
        for(Entry saved:next.values())text.append(saved.line()).append('\n');
        if(!RedPacketRuntime.write(path(entry.account),text.toString()))return false;
        snapshot.entries.clear();snapshot.entries.putAll(next);return true;
    }
}
