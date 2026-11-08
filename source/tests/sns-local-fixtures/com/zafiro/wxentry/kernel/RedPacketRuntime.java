package com.zafiro.wxentry.kernel;
import java.nio.file.*;import java.nio.charset.*;import java.security.*;
final class RedPacketRuntime {
 static boolean failWrite;
 static String hash(String value){try{byte[] b=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte v:b)out.append(String.format("%02x",v&255));return out.toString();}catch(Exception e){throw new RuntimeException(e);}}
 static String read(String path){try{return Files.exists(Paths.get(path))?new String(Files.readAllBytes(Paths.get(path)),StandardCharsets.UTF_8):null;}catch(Exception e){return null;}}
 static boolean write(String path,String text){if(failWrite)return false;try{Path p=Paths.get(path),tmp=Paths.get(path+".tmp");Files.createDirectories(p.getParent());Files.write(tmp,text.getBytes(StandardCharsets.UTF_8));Files.move(tmp,p,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);return true;}catch(Exception e){return false;}}
}
