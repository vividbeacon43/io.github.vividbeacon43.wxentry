package com.zafiro.wxentry.kernel;
public class RedPacketRuntime {
 public static final java.util.Map<String,String> files=new java.util.HashMap<>();public static int writes;public static boolean fail;
 public static String hash(String value){try{byte[] bytes=java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes("UTF-8"));StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format("%02x",b&255));return out.toString();}catch(Exception e){throw new RuntimeException(e);}}
 public static String read(String path){return files.get(path);}public static boolean write(String path,String text){if(fail)return false;files.put(path,text);writes++;return true;}
}
