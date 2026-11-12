package com.zafiro.wxentry.kernel;
import java.util.*;
public final class GameEmojiDataTest {
 static int checks;static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}static String owner="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",t1="11111111111111111111111111111111",t2="22222222222222222222222222222222";
 static void rejects(Runnable r,String name){boolean fail=false;try{r.run();}catch(RuntimeException expected){fail=true;}check(fail,name);}
 public static void main(String[]args){
  GameEmojiData d=GameEmojiData.decode(null,owner);check(!d.enabled&&d.peek(GameEmojiData.Kind.DICE)==0&&d.peek(GameEmojiData.Kind.RPS)==0,"default off and native random");
  d.configure(GameEmojiData.Kind.DICE,4,GameEmojiData.parse(GameEmojiData.Kind.DICE,"6,1，3\n随机"));d.configure(GameEmojiData.Kind.RPS,0,GameEmojiData.parse(GameEmojiData.Kind.RPS,"石头、剪刀,布"));
  check(!d.claim(GameEmojiData.Kind.DICE,t1)&&d.dice.next.size()==4,"off leaves sequence untouched");d.enabled=true;
  check(d.claim(GameEmojiData.Kind.DICE,t1)&&d.peek(GameEmojiData.Kind.DICE)==6&&d.dice.next.size()==4,"reservation does not consume before native submit");
  check(!d.claim(GameEmojiData.Kind.DICE,t2),"double click cannot reserve twice");check(d.claim(GameEmojiData.Kind.RPS,t2),"other kind is independent");
  GameEmojiData restored=GameEmojiData.decode(d.encode(),owner);check(restored.dice.ticket.equals(t1)&&restored.rps.ticket.equals(t2),"uncertain in-flight reservations survive restart");
  check(!restored.complete(GameEmojiData.Kind.DICE,t2)&&restored.dice.next.size()==4,"wrong native acknowledgement cannot consume");
  check(restored.complete(GameEmojiData.Kind.RPS,t2)&&restored.rps.next.equals(Arrays.asList(2,3))&&restored.dice.next.size()==4,"native ack consumes just its kind");
  check(restored.abort(GameEmojiData.Kind.DICE,t1)&&restored.dice.next.size()==4,"early cancelled send keeps first result");check(!restored.complete(GameEmojiData.Kind.DICE,t1),"aborted ticket cannot commit later");
  check(restored.claim(GameEmojiData.Kind.DICE,t2)&&restored.complete(GameEmojiData.Kind.DICE,t2)&&restored.peek(GameEmojiData.Kind.DICE)==1,"first actual outgoing row advances exactly one result");check(!restored.complete(GameEmojiData.Kind.DICE,t2)&&restored.dice.next.size()==3,"duplicate row callback is idempotent");
  for(int expected:new int[]{1,3,0}){check(restored.peek(GameEmojiData.Kind.DICE)==expected,"ordered remaining values");check(restored.claim(GameEmojiData.Kind.DICE,t1)&&restored.complete(GameEmojiData.Kind.DICE,t1),"each accepted send consumes once");}
  check(restored.peek(GameEmojiData.Kind.DICE)==4,"exhaustion returns chosen default");check(restored.claim(GameEmojiData.Kind.DICE,t1)&&restored.complete(GameEmojiData.Kind.DICE,t1)&&restored.peek(GameEmojiData.Kind.DICE)==4,"fixed default persists after use");
  restored.configure(GameEmojiData.Kind.DICE,0,Collections.<Integer>emptyList());check(!restored.claim(GameEmojiData.Kind.DICE,t1),"fully random default bypasses interception");
  check(restored.claim(GameEmojiData.Kind.RPS,t1),"pending RPS before edit");restored.configure(GameEmojiData.Kind.RPS,3,Arrays.asList(3,2,1));check(!restored.complete(GameEmojiData.Kind.RPS,t1)&&restored.rps.next.equals(Arrays.asList(3,2,1)),"old acknowledgements do not consume newly edited sequences");
  GameEmojiData copied=restored.copy();copied.rps.next.clear();check(restored.rps.next.size()==3,"UI draft copy is isolated");
  String raw=restored.encode();check(GameEmojiData.decode(raw,owner).encode().equals(raw),"durable round trip");
  rejects(()->GameEmojiData.decode(raw,"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"),"cross account rejected");
  for(String suffix:new String[]{"enabled=0\n","unexpected=1\n"})rejects(()->GameEmojiData.decode(raw+suffix,owner),"duplicate or unknown fields rejected");
  rejects(()->GameEmojiData.parse(GameEmojiData.Kind.DICE,"7"),"invalid dice");rejects(()->GameEmojiData.parse(GameEmojiData.Kind.RPS,"4"),"invalid RPS");rejects(()->GameEmojiData.parse(GameEmojiData.Kind.DICE,"-1"),"negative outcome");rejects(()->GameEmojiData.parse(GameEmojiData.Kind.DICE,"六"),"bad dice format");
  check(GameEmojiData.parse(GameEmojiData.Kind.RPS,"1,2,3").equals(Arrays.asList(1,2,3)),"numeric RPS form");check(GameEmojiData.parse(GameEmojiData.Kind.RPS," ").isEmpty(),"empty sequence selects fallback");
  StringBuilder many=new StringBuilder();for(int i=0;i<256;i++)many.append("6,");check(GameEmojiData.parse(GameEmojiData.Kind.DICE,many.toString()).size()==256,"256 outcomes accepted");String tooMany=many+"6";rejects(()->GameEmojiData.parse(GameEmojiData.Kind.DICE,tooMany),"bounded queue");
  rejects(()->GameEmojiData.decode(raw.replace("enabled=1","enabled=true"),owner),"corrupt switch");rejects(()->GameEmojiData.decode(raw.replace("dice.ticket=","dice.ticket=invalid"),owner),"corrupt claim");
  check(GameEmojiData.display(GameEmojiData.Kind.RPS,Arrays.asList(1,2,3,0)).equals("石头, 剪刀, 布, 随机"),"clear user labels");
  System.out.println("PASS "+checks+" game outcome queue/order/claim/restart/account checks");
 }
}
