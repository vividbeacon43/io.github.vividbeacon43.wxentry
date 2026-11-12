package com.zafiro.wxentry.kernel;
import java.util.*;

/** Bounded, account-owned outcome queues; a durable claim is retired only by an outgoing row. */
final class GameEmojiData {
    static final int MAX=256;
    enum Kind { DICE("dice","骰子",6),RPS("rps","石头剪刀布",3);final String key,label;final int max;Kind(String k,String l,int m){key=k;label=l;max=m;} }
    static final class Slot {
        int fallback,chosen;boolean queued;String ticket="";final List<Integer> next=new ArrayList<Integer>();
        Slot copy(){Slot s=new Slot();s.fallback=fallback;s.chosen=chosen;s.queued=queued;s.ticket=ticket;s.next.addAll(next);return s;}
        void clearClaim(){ticket="";chosen=0;queued=false;}
    }
    final String owner;boolean enabled;Slot dice=new Slot(),rps=new Slot();
    GameEmojiData(String hash){if(hash==null||!hash.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Invalid owner");owner=hash;}
    Slot slot(Kind k){if(k==null)throw new IllegalArgumentException("Missing kind");return k==Kind.DICE?dice:rps;}
    GameEmojiData copy(){GameEmojiData d=new GameEmojiData(owner);d.enabled=enabled;d.dice=dice.copy();d.rps=rps.copy();return d;}
    int peek(Kind k){Slot s=slot(k);return s.next.isEmpty()?s.fallback:s.next.get(0);}
    boolean claim(Kind k,String ticket){Slot s=slot(k);if(!enabled||!s.ticket.isEmpty()||ticket==null||!ticket.matches("[a-f0-9]{32}"))return false;if(s.next.isEmpty()&&s.fallback==0)return false;s.ticket=ticket;s.chosen=peek(k);s.queued=!s.next.isEmpty();return true;}
    boolean complete(Kind k,String ticket){Slot s=slot(k);if(ticket==null||ticket.isEmpty()||!ticket.equals(s.ticket))return false;if(s.queued){if(s.next.isEmpty()||s.next.get(0)!=s.chosen)return false;s.next.remove(0);}s.clearClaim();return true;}
    boolean abort(Kind k,String ticket){Slot s=slot(k);if(ticket==null||ticket.isEmpty()||!ticket.equals(s.ticket))return false;s.clearClaim();return true;}
    void configure(Kind k,int fallback,List<Integer> next){if(fallback<0||fallback>k.max||next==null||next.size()>MAX)throw new IllegalArgumentException("Invalid outcomes");for(Integer v:next)if(v==null||v<0||v>k.max)throw new IllegalArgumentException("Invalid outcome");Slot s=slot(k);s.fallback=fallback;s.next.clear();s.next.addAll(next);s.clearClaim();}
    static String label(Kind k,int v){if(v==0)return "随机";if(v<1||v>k.max)throw new IllegalArgumentException("Invalid outcome");return k==Kind.DICE?Integer.toString(v):v==1?"石头":v==2?"剪刀":"布";}
    static List<Integer> parse(Kind k,String text){if(text==null||text.length()>4096)throw new IllegalArgumentException("最多设置 256 次结果");List<Integer> values=new ArrayList<Integer>();for(String word:text.trim().split("[，,、;；\\s]+")){if(word.isEmpty())continue;int v;if(word.equals("随机"))v=0;else if(k==Kind.RPS&&word.equals("石头"))v=1;else if(k==Kind.RPS&&word.equals("剪刀"))v=2;else if(k==Kind.RPS&&word.equals("布"))v=3;else try{v=Integer.parseInt(word);}catch(NumberFormatException invalid){throw new IllegalArgumentException(k==Kind.DICE?"请填写 1～6，或随机":"请填写石头、剪刀、布，或随机");}if(v<0||v>k.max)throw new IllegalArgumentException("结果超出范围");values.add(v);if(values.size()>MAX)throw new IllegalArgumentException("最多设置 256 次结果");}return values;}
    static String display(Kind k,List<Integer> values){StringBuilder s=new StringBuilder();for(int v:values){if(s.length()>0)s.append(", ");s.append(label(k,v));}return s.toString();}
    String encode(){StringBuilder s=new StringBuilder("format=1\nowner="+owner+"\nenabled="+(enabled?1:0)+"\n");for(Kind k:Kind.values()){Slot v=slot(k);s.append(k.key).append(".default=").append(v.fallback).append('\n').append(k.key).append(".queue=");for(int i=0;i<v.next.size();i++){if(i>0)s.append(',');s.append(v.next.get(i));}s.append('\n').append(k.key).append(".ticket=").append(v.ticket).append('\n').append(k.key).append(".chosen=").append(v.chosen).append('\n').append(k.key).append(".queued=").append(v.queued?1:0).append('\n');}return s.toString();}
    static GameEmojiData decode(String raw,String owner){GameEmojiData d=new GameEmojiData(owner);if(raw==null)return d;if(raw.length()>16384)throw new IllegalArgumentException("Oversize outcomes");Map<String,String> fields=new HashMap<String,String>();for(String line:raw.split("\n")){int p=line.indexOf('=');if(p<1||fields.put(line.substring(0,p),line.substring(p+1))!=null)throw new IllegalArgumentException("Malformed outcomes");}if(fields.size()!=13||!"1".equals(fields.get("format"))||!owner.equals(fields.get("owner")))throw new IllegalArgumentException("Wrong owner or version");d.enabled=bit(fields.get("enabled"));for(Kind k:Kind.values()){String key=k.key+".";Slot s=d.slot(k);s.fallback=Integer.parseInt(fields.get(key+"default"));s.next.addAll(parse(k,fields.get(key+"queue")));s.ticket=fields.get(key+"ticket");s.chosen=Integer.parseInt(fields.get(key+"chosen"));s.queued=bit(fields.get(key+"queued"));if(s.fallback<0||s.fallback>k.max||s.chosen<0||s.chosen>k.max||s.ticket==null||!s.ticket.isEmpty()&&!s.ticket.matches("[a-f0-9]{32}")||s.ticket.isEmpty()&&(s.chosen!=0||s.queued)||s.queued&&(s.next.isEmpty()||s.next.get(0)!=s.chosen))throw new IllegalArgumentException("Invalid claim");}return d;}
    static boolean bit(String v){if(!"0".equals(v)&&!"1".equals(v))throw new IllegalArgumentException("Invalid switch");return "1".equals(v);}
}
