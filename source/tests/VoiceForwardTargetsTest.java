package com.zafiro.wxentry.kernel;
import java.util.*;
public class VoiceForwardTargetsTest {
    static int checks;static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    public static void main(String[] args){
        check(VoiceForwardTargets.eligible("friend",1,0),"friend conversation");
        check(VoiceForwardTargets.eligible("group@chatroom",0,0),"group does not depend on friend bit");
        check(VoiceForwardTargets.eligible("filehelper",0,0),"file helper test/self destination");
        for(String id:new String[]{"gh_account","weixin","officialaccounts","holder_fold_chat","notifymessage","stranger@openim"})
            check(!VoiceForwardTargets.eligible(id,1,0),"non-friend/system conversation excluded "+id);
        check(!VoiceForwardTargets.eligible("stranger",0,0),"non-friend excluded");
        check(!VoiceForwardTargets.eligible("verified-public",1,8),"public account verify flag");
        List<VoiceForwardTargets.Target> all=Arrays.asList(new VoiceForwardTargets.Target("friend","备注Alice"),new VoiceForwardTargets.Target("group@chatroom","测试群"),new VoiceForwardTargets.Target("filehelper","文件传输助手"));
        check(VoiceForwardTargets.filter(all,0,"").size()==3,"all conversations");
        check(VoiceForwardTargets.filter(all,1,"").size()==2,"friends filter");
        check(VoiceForwardTargets.filter(all,2,"").size()==1,"groups filter");
        check(VoiceForwardTargets.filter(all,0,"ALICE").size()==1,"case-insensitive remark search");
        check(VoiceForwardTargets.filter(all,0,"测试").get(0).group,"group name search");
        check(VoiceForwardTargets.filter(all,1,"测试").isEmpty(),"search and category combine");
        check(VoiceForwardTargets.filter(all,0,"missing").isEmpty(),"empty search result");
        check(all.size()==3,"filtering never changes source list");
        System.out.println("PASS "+checks+" conversation eligibility/filter checks");
    }
}
