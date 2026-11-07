package com.tencent.mm.plugin.luckymoney.model;
public class h6 extends q5 {
    public e1 h=new e1();public Object[] arguments;
    public h6(int type,int channel,String id,String url,String head,String nick,String talker,String version,String timing) {
        this(type,channel,id,url,head,nick,talker,version,timing,"");
    }
    public h6(int type,int channel,String id,String url,String head,String nick,String talker,String version,String timing,String consent) {
        arguments=new Object[]{type,channel,id,url,head,nick,talker,version,timing,consent};
    }
}
