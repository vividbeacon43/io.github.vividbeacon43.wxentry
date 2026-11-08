package com.zafiro.wxentry.kernel;
import java.io.*;import java.net.*;import javax.xml.parsers.*;import org.w3c.dom.*;import org.xml.sax.InputSource;
/** Native share-card metadata. Never resolves a post into an unrelated plain-text draft. */
final class SnsForwardCard {
    static String preview(String xml,String expected)throws Exception{
        if(xml==null||xml.length()>524288||xml.toUpperCase(java.util.Locale.ROOT).contains("<!DOCTYPE")||xml.toUpperCase(java.util.Locale.ROOT).contains("<!ENTITY"))throw new IOException("SNS_CARD_INVALID");
        DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setExpandEntityReferences(false);
        try{f.setFeature("http://xml.org/sax/features/external-general-entities",false);}catch(Exception ignored){}
        try{f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);}catch(Exception ignored){}
        DocumentBuilder b=f.newDocumentBuilder();b.setErrorHandler(new org.xml.sax.helpers.DefaultHandler());
        Element root=b.parse(new InputSource(new StringReader(xml))).getDocumentElement();if(!expected.equals(root.getTagName()))throw new IOException("SNS_CARD_INVALID");
        NodeList all=root.getElementsByTagName("*");for(int i=0;i<all.getLength();i++){Node n=all.item(i);String name=n.getNodeName().toLowerCase(java.util.Locale.ROOT);if(name.contains("thumb")||name.contains("cover")){String value=n.getTextContent().trim();if(SnsForwardDraft.http(value))return value;}}
        return "";
    }
    static boolean valid(String xml,String expected){try{preview(xml,expected);return true;}catch(Exception invalid){return false;}}
    static File preload(String url,File directory,SnsForwardFiles.Guard guard)throws Exception{
        if(url==null||url.isEmpty())return null;
        String address=url;File part=new File(directory,"card-cover.part"),file=new File(directory,"card-cover.jpg");
        try{
            for(int hop=0;hop<4;hop++){
                if(!guard.valid()||!SnsForwardDraft.http(address))throw new IOException("SNS_CANCELLED");
                HttpURLConnection connection=(HttpURLConnection)new URL(address).openConnection();connection.setConnectTimeout(10000);connection.setReadTimeout(10000);connection.setInstanceFollowRedirects(false);
                try{
                    int code=connection.getResponseCode();if(code>=300&&code<400){String next=connection.getHeaderField("Location");if(next==null)throw new IOException("SNS_CARD_PRELOAD");address=new URL(new URL(address),next).toString();continue;}
                    if(code!=200||connection.getContentLengthLong()>10485760L)throw new IOException("SNS_CARD_PRELOAD");
                    try(InputStream in=connection.getInputStream();FileOutputStream out=new FileOutputStream(part)){byte[] bytes=new byte[32768];int n;long count=0;while((n=in.read(bytes))!=-1){count+=n;if(!guard.valid()||count>10485760L)throw new IOException("SNS_CARD_PRELOAD");out.write(bytes,0,n);}out.getFD().sync();}
                    if(SnsForwardFiles.extension(part,false)==null||!guard.valid()||SnsForwardFiles.total(directory.getParentFile())>SnsForwardFiles.MAX_ROOT||!part.renameTo(file))throw new IOException("SNS_CARD_PRELOAD");return file;
                }finally{connection.disconnect();}
            }
            throw new IOException("SNS_CARD_PRELOAD");
        }finally{part.delete();}
    }
}
