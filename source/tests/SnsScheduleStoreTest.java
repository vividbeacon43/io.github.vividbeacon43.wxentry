package com.zafiro.wxentry.kernel;
import java.nio.file.*;import java.io.*;import java.util.*;import android.util.AtomicFile;

public final class SnsScheduleStoreTest {
 static int checks;static final String OWNER="wxid_schedule_owner",ID="1234567890abcdef1234567890abcdef";static long now=1800000000000L;
 static void ok(boolean v){checks++;if(!v)throw new AssertionError("store check "+checks);}
 static byte[] bytes()throws Exception{return Files.readAllBytes(SnsScheduleStore.child(SnsScheduleStore.account(OWNER),"queue.bin").toPath());}
 public static void main(String[] args)throws Exception{
  Kernel.WXDIR=Paths.get(args[0],"schedule-store-test").toAbsolutePath().toString()+File.separator;Files.createDirectories(Paths.get(Kernel.WXDIR));ok(SnsScheduleStore.load(OWNER).tasks.isEmpty());
  ok(SnsScheduleStore.edit(OWNER,d->{d.enabled=true;d.tasks.add(new SnsScheduleData.Task(ID,OWNER,"exact text",now,true,now));}));byte[] initial=bytes();ok(SnsScheduleStore.load(OWNER).tasks.size()==1);
  for(String edition:new String[]{"free","plus","unactivated"}){SnsScheduleRuntime.edition=edition;ok(!SnsScheduleStore.edit(OWNER,d->SnsScheduleEngine.claim(d,ID,now,false)));ok(Arrays.equals(initial,bytes()));}SnsScheduleRuntime.edition="vip";
  SnsScheduleRuntime.verified=false;ok(!SnsScheduleStore.edit(OWNER,d->d.enabled=false));SnsScheduleRuntime.verified=true;SnsScheduleRuntime.moduleOn=false;ok(!SnsScheduleStore.edit(OWNER,d->d.enabled=false));SnsScheduleRuntime.moduleOn=true;
  SnsScheduleRuntime.account="wxid_second_owner";ok(!SnsScheduleStore.edit(OWNER,d->d.enabled=false));ok(SnsScheduleStore.load(SnsScheduleRuntime.account).tasks.isEmpty());ok(SnsScheduleStore.edit(SnsScheduleRuntime.account,d->d.enabled=true));SnsScheduleRuntime.account=OWNER;ok(Arrays.equals(initial,bytes()));
  AtomicFile.fail=true;int nativeCalls=0;if(SnsScheduleStore.edit(OWNER,d->SnsScheduleEngine.claim(d,ID,now,false)))nativeCalls++;ok(nativeCalls==0);ok(Arrays.equals(initial,bytes()));AtomicFile.fail=false;
  ok(!SnsScheduleStore.edit(OWNER,d->{d.enabled=false;throw new IOException("cancelled");}));ok(Arrays.equals(initial,bytes()));
  ok(!SnsScheduleStore.edit(OWNER,d->{d.enabled=false;SnsScheduleRuntime.verified=false;}));SnsScheduleRuntime.verified=true;ok(Arrays.equals(initial,bytes()));
  for(int i=0;i<10;i++)if(SnsScheduleStore.edit(OWNER,d->SnsScheduleEngine.claim(d,ID,now,false)))nativeCalls++;ok(nativeCalls==1);ok(SnsScheduleStore.load(OWNER).find(ID).state==SnsScheduleData.State.CLAIMED);
  ok(SnsScheduleStore.edit(OWNER,d->SnsScheduleEngine.recover(d,now+1,false,false,false)));ok(SnsScheduleStore.load(OWNER).find(ID).state==SnsScheduleData.State.UNKNOWN);ok(!SnsScheduleStore.edit(OWNER,d->SnsScheduleEngine.claim(d,ID,now,true)));
  Path file=SnsScheduleStore.child(SnsScheduleStore.account(OWNER),"queue.bin").toPath();byte[] valid=bytes();Files.write(Paths.get(file+".bak"),valid);Files.write(file,new byte[]{1,2,3});ok(SnsScheduleStore.load(OWNER).find(ID).state==SnsScheduleData.State.UNKNOWN);ok(Arrays.equals(valid,bytes()));
  Files.write(file,new byte[]{0,0,0,0});boolean corrupt=false;try{SnsScheduleStore.load(OWNER);}catch(IOException e){corrupt=true;}ok(corrupt);ok(!SnsScheduleStore.edit(OWNER,d->d.enabled=true));ok(Arrays.equals(new byte[]{0,0,0,0},bytes()));Files.write(file,valid);
  boolean escape=false;try{SnsScheduleStore.child(SnsScheduleStore.account(OWNER),"../outside");}catch(IOException e){escape=true;}ok(escape);
  System.out.println("PASS "+checks+" scheduled Moments account/VIP isolation, fsynced atomic failure, crash recovery and single-submit store checks");
 }
}
