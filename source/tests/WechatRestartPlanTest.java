package com.zafiro.wxentry;
import java.util.*;
public class WechatRestartPlanTest {
 static int checks;static void ok(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
 static class Host implements WechatRestartPlan.Host {
  List<String> events=new ArrayList<>();boolean exactFails,fallbackFails;int killed,failed;
  public void schedule(boolean exact){events.add(exact?"exact":"fallback");if(exact?exactFails:fallbackFails)throw new IllegalStateException();}
  public void terminate(){events.add("terminate");killed++;}public void failed(Throwable e){events.add("failed");failed++;}
 }
 public static void main(String[] args){
  Host h=new Host();ok(WechatRestartPlan.request(h),"exact scheduling accepted");ok(h.events.equals(Arrays.asList("exact","terminate")),"terminate only after scheduling succeeds");ok(h.killed==1&&h.failed==0,"successful restart terminates once");
  h=new Host();h.exactFails=true;ok(WechatRestartPlan.request(h),"fallback after exact permission rejection");ok(h.events.equals(Arrays.asList("exact","fallback","terminate")),"fallback completed before termination");ok(h.killed==1&&h.failed==0,"fallback still terminates once");
  h=new Host();h.exactFails=h.fallbackFails=true;ok(!WechatRestartPlan.request(h),"both scheduling failures reported");ok(h.killed==0&&h.failed==1,"host remains alive when relaunch unavailable");ok(h.events.equals(Arrays.asList("exact","fallback","failed")),"failure cannot silently masquerade as a restart");
  System.out.println("PASS "+checks+" explicit restart scheduling/fallback/termination checks");
 }
}
