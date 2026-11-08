package com.zafiro.wxentry.kernel;
import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.security.MessageDigest;
final class RedPacketRuntime {
 static String hash(String value){try{byte[] d=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(byte b:d)s.append(String.format("%02x",b&255));return s.toString();}catch(Exception e){throw new RuntimeException(e);}}
 static String read(String path){try{return new String(Files.readAllBytes(Paths.get(path)),StandardCharsets.UTF_8);}catch(Exception e){return null;}}
 static boolean write(String path,String text){try{Files.write(Paths.get(path),text.getBytes(StandardCharsets.UTF_8));return true;}catch(Exception e){return false;}}
}
