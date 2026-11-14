package com.zafiro.wxentry.kernel;

final class RedPacketMessage {
    private RedPacketMessage() {}

    static String messageUrl(int type,Long sent,String content) {
        if((type&0xffff)!=49||sent==null||sent.longValue()!=0||content==null)return null;
        try {
            int start=content.indexOf("<nativeurl>"),end=content.indexOf("</nativeurl>");
            if(start<0||end<=start)return null;
            String url=content.substring(start+11,end).trim();
            if(url.startsWith("<![CDATA["))url=url.substring(9);
            if(url.endsWith("]]>"))url=url.substring(0,url.length()-3);
            url=url.trim().replace("&amp;","&");java.net.URI uri=new java.net.URI(url);
            return "wxpay".equals(uri.getScheme())&&"c2cbizmessagehandler".equals(uri.getHost())&&"/hongbao/receivehongbao".equals(uri.getPath())?url:null;
        }catch(Exception invalid){return null;}
    }
}
