package com.zafiro.wxentry.kernel;
import java.util.*;
public final class SnsLocalSelectionTest {
 static int checks;static void ok(boolean value){checks++;if(!value)throw new AssertionError("Selection check "+checks);}
 interface Run{void run();}static void bad(Run run){boolean rejected=false;try{run.run();}catch(IllegalArgumentException e){rejected=true;}ok(rejected);}
 public static void main(String[] args){
  List<String> ids=new ArrayList<>();for(int i=0;i<5000;i++)ids.add("wxid_friend_"+i);List<String> original=new ArrayList<>(ids);
  Set<String> all=SnsLocalSelection.all(ids);ok(all.size()==5000&&all.contains(ids.get(4999)));ok(new ArrayList<>(all).equals(original));ok(ids.equals(original));
  ok(SnsLocalSelection.random(ids,0,new Random(1)).isEmpty());ok(SnsLocalSelection.random(Collections.<String>emptyList(),0,new Random(1)).isEmpty());
  Set<String> complete=SnsLocalSelection.random(ids,5000,new Random(2));ok(complete.size()==5000&&complete.equals(all));
  for(int count:new int[]{1,4,100,101,999,4999})for(int seed=1;seed<=4;seed++){
   Set<String> selected=SnsLocalSelection.random(ids,count,new Random(seed));ok(selected.size()==count&&all.containsAll(selected));
  }
  ok(ids.equals(original));ok(SnsLocalSelection.random(ids,25,new Random(123)).equals(SnsLocalSelection.random(ids,25,new Random(123))));
  ok(!SnsLocalSelection.random(ids,25,new Random(123)).equals(SnsLocalSelection.random(ids,25,new Random(124))));
  List<String> dup=Arrays.asList("wxid_a","wxid_b","wxid_a");ok(SnsLocalSelection.all(dup).size()==2);ok(SnsLocalSelection.random(dup,2,new Random(1)).size()==2);
  bad(()->SnsLocalSelection.random(ids,-1,new Random(1)));bad(()->SnsLocalSelection.random(ids,5001,new Random(1)));bad(()->SnsLocalSelection.random(dup,3,new Random(1)));bad(()->SnsLocalSelection.random(ids,1,null));bad(()->SnsLocalSelection.all(Arrays.asList("group@chatroom")));
  for(String service:new String[]{"facebookapp","linkedinplugin","qqmail","downloaderapp","schedule_message","qqfriend","masssendapp","voipapp","voicevoipapp","lbsapp","newsapp","notification_messages"}){ok(!SnsLocalSelection.selectable(service));bad(()->SnsLocalSelection.all(Arrays.asList(service)));}
  ok(SnsLocalSelection.selectable("old_custom_friend"));ok(SnsLocalData.friend("qqmail"));
  Set<String> visited=new HashSet<>();List<String> small=ids.subList(0,7);Random rng=new Random(54321);for(int i=0;i<200;i++)visited.addAll(SnsLocalSelection.random(small,1,rng));ok(visited.equals(new HashSet<>(small)));
  System.out.println("PASS "+checks+" all-friend and random-count selection, uniqueness, range and non-mutating checks");
 }
}
