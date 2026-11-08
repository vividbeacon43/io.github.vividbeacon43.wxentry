package com.zafiro.wxentry.kernel;
import java.io.File;
import java.net.URI;
import java.util.*;

/** An immutable editor draft. The only handoff operation is opening an editor. */
final class SnsForwardDraft {
    enum Kind {TEXT,PHOTOS,VIDEO,LINK,FINDER,MPVIDEO}
    final Kind kind;final String description,title,url;final int expected;
    final List<String> media;final String thumbnail,videoMd5,cardXml;
    SnsForwardDraft(Kind k,String text,String heading,String link,int count,List<String> paths,String thumb,String md5){this(k,text,heading,link,count,paths,thumb,md5,null);}
    SnsForwardDraft(Kind k,String text,String heading,String link,int count,List<String> paths,String thumb,String md5,String xml){cardXml=xml;kind=k;description=text==null?"":text;title=heading==null?"":heading;url=link==null?"":link;expected=count;media=Collections.unmodifiableList(new ArrayList<String>(paths));thumbnail=thumb;videoMd5=md5;}
    static boolean premium(String edition){return "plus".equals(edition)||"vip".equals(edition);}
    static Long serverId(String value){try{if(value==null||!value.matches("[0-9]{1,20}"))return null;java.math.BigInteger id=new java.math.BigInteger(value);return id.signum()>0&&id.bitLength()<=64?id.longValue():null;}catch(Exception invalid){return null;}}
    static Kind kind(int style,int count,String description,String url){
        if(style==2&&description!=null&&!description.isEmpty())return Kind.TEXT;
        if((style==1||style==54)&&count>0&&count<=20)return Kind.PHOTOS;
        if(style==15&&count==1)return Kind.VIDEO;
        if((style==3||style==7)&&http(url))return Kind.LINK;
        if(style==28)return Kind.FINDER;
        if(style==42)return Kind.MPVIDEO;
        return null;
    }
    static boolean http(String s){try{URI u=new URI(s);return ("https".equalsIgnoreCase(u.getScheme())||"http".equalsIgnoreCase(u.getScheme()))&&u.getHost()!=null;}catch(Exception invalid){return false;}}
    int uploadType(){switch(kind){case TEXT:return 9;case PHOTOS:return 0;case VIDEO:return 14;case LINK:return 1;case FINDER:return 17;case MPVIDEO:return 19;default:throw new IllegalStateException();}}
    boolean complete(){
        if(description.length()>100000)return false;
        if(kind==Kind.FINDER&&!SnsForwardCard.valid(cardXml,"finderFeed")||kind==Kind.MPVIDEO&&!SnsForwardCard.valid(cardXml,"mmbrandmpvideo"))return false;
        int n=kind==Kind.PHOTOS?expected:kind==Kind.VIDEO?1:0;if(media.size()!=n)return false;
        if(kind==Kind.PHOTOS&&(n<1||n>20))return false;
        if(kind==Kind.LINK&&!http(url))return false;
        if(kind==Kind.TEXT&&description.isEmpty())return false;
        for(String s:media){if(s==null)return false;File f=new File(s);if(!f.isAbsolute()||!f.isFile()||f.length()<=0)return false;}
        return kind!=Kind.VIDEO||(thumbnail!=null&&new File(thumbnail).isFile()&&new File(thumbnail).length()>0&&videoMd5!=null&&videoMd5.matches("[a-f0-9]{32}"));
    }
    interface Editor {boolean permitted();String account();void open(SnsForwardDraft draft)throws Exception;}
    static final class Handoff {
        final String owner;boolean cancelled,opened,failed;
        Handoff(String account){owner=account;}
        synchronized void cancel(){cancelled=true;}
        synchronized boolean open(Editor editor,SnsForwardDraft draft)throws Exception{
            if(cancelled||opened||failed)return false;
            if(owner==null||!owner.equals(editor.account())||!editor.permitted()){cancelled=true;return false;}
            if(draft==null||!draft.complete())return false;
            opened=true;
            try{editor.open(draft);return true;}catch(Exception failure){opened=false;failed=true;throw failure;}
        }
    }
}
