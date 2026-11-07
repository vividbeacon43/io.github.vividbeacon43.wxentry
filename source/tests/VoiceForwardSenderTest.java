package com.zafiro.wxentry.kernel;
import java.util.*;
public class VoiceForwardSenderTest {
    static int checks;
    static void check(boolean yes,String reason){checks++;if(!yes)throw new AssertionError(reason);}
    static final class Fake implements VoiceForwardSender.Backend {
        Map<String,Long> files=new HashMap<>();String fresh="new-voice",target="new-path",to,file;int ms,copies,deletes,submits;
        boolean copyFail,copyThrow,taskFail,submitFail,shortCopy;
        Fake(){files.put("source-path",12345L);}
        public String account(){return "self";}
        public String sourceName(Object m){return "original-voice";}
        public String freshName(String original){return fresh;}
        public String sourcePath(Object m,String f){return "source-path";}
        public String targetPath(String f){return target;}
        public long size(String p){return files.containsKey(p)?files.get(p):0;}
        public long copy(String f,String t){copies++;files.put(t,shortCopy?100L:size(f));if(copyThrow)throw new RuntimeException();return copyFail?-1:size(f);}
        public void remove(String p){deletes++;files.remove(p);}
        public Object task(String t,String f,int duration){if(taskFail)throw new RuntimeException();to=t;file=f;ms=duration;return this;}
        public void submit(Object task){submits++;if(submitFail)throw new RuntimeException();}
    }
    static void fails(Fake f,Object source,String to,int duration)throws Throwable {
        boolean failed=false;try{VoiceForwardSender.send(f,source,to,duration);}catch(Exception expected){failed=true;}
        check(failed,"expected refusal/failure");
        check(f.size("source-path")==12345,"original audio unchanged");
    }
    public static void main(String[] args)throws Throwable {
        Fake f=new Fake();VoiceForwardSender.send(f,new Object(),"chosen-friend",7350);
        check(f.submits==1 && f.copies==1,"copy then one native submission");
        check(f.size("source-path")==12345 && f.size("new-path")==12345,"independent equal-sized native copy");
        check("chosen-friend".equals(f.to)&&"new-voice".equals(f.file)&&f.ms==7350,"exact recipient/new file/duration");
        check(f.deletes==0,"native owns accepted file");
        f=new Fake();fails(f,null,"friend",1);check(f.copies==0,"null source does not copy");
        f=new Fake();fails(f,new Object(),"",1);check(f.copies==0,"no recipient cannot copy/send");
        f=new Fake();fails(f,new Object(),"friend",0);check(f.copies==0,"zero duration cannot copy/send");
        f=new Fake();f.fresh="original-voice";fails(f,new Object(),"friend",1);check(f.copies==0,"new filename required");
        f=new Fake();f.target="source-path";fails(f,new Object(),"friend",1);check(f.copies==0&&f.deletes==0,"never overwrite/delete source");
        f=new Fake();f.files.put("new-path",99L);fails(f,new Object(),"friend",1);check(f.copies==0&&f.deletes==0,"existing destination never overwritten");
        f=new Fake();f.copyFail=true;fails(f,new Object(),"friend",1);check(f.deletes==1&&f.submits==0,"failed copy cleans only destination");
        f=new Fake();f.copyThrow=true;fails(f,new Object(),"friend",1);check(f.deletes==1&&f.submits==0,"partial copy cleanup");
        f=new Fake();f.shortCopy=true;fails(f,new Object(),"friend",1);check(f.deletes==1&&f.submits==0,"truncated copy never submits");
        f=new Fake();f.taskFail=true;fails(f,new Object(),"friend",1);check(f.deletes==1&&f.submits==0,"task construction failure cleans owned copy");
        f=new Fake();f.submitFail=true;fails(f,new Object(),"friend",1);check(f.deletes==0&&f.size("new-path")>0,"native may own file once submission starts");
        System.out.println("PASS "+checks+" modern voice file/submission checks");
    }
}
