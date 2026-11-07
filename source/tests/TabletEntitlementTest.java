package com.zafiro.wxentry;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

public final class TabletEntitlementTest {
    static int checks;
    static void ok(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
    static class Transport implements KernelDelivery.Transport {
        KeyPair key;String edition="plus",current="synthetic-tablet-entitlement-token";boolean denied;
        byte[] dex=new byte[1400];
        Transport()throws Exception {KeyPairGenerator g=KeyPairGenerator.getInstance("RSA");g.initialize(2048);key=g.generateKeyPair();dex[0]='d';dex[1]='e';dex[2]='x';dex[3]='\n';}
        String sign(String data)throws Exception {Signature s=Signature.getInstance("SHA256withRSA");s.initSign(key.getPrivate());s.update(data.getBytes("UTF-8"));return Base64.getEncoder().encodeToString(s.sign());}
        public KernelDelivery.Manifest manifest(String token)throws Exception {
            if(denied)throw new KernelDelivery.AuthorizationDenied();
            String hash=KernelDelivery.hash(dex),data=EditionPolicy.manifestData(edition,"238",hash);
            return new KernelDelivery.Manifest(edition,"238",hash,sign(data),sign("kernel-grant-v1|"+KernelDelivery.scope(token)+"|"+edition+"|238|"+hash));
        }
        public byte[] download(String token,KernelDelivery.Manifest m){return dex.clone();}
        public byte[] decrypt(byte[] encrypted){return encrypted;}
        public boolean verify(String data,String signature)throws Exception {Signature s=Signature.getInstance("SHA256withRSA");s.initVerify(key.getPublic());s.update(data.getBytes("UTF-8"));return s.verify(Base64.getDecoder().decode(signature));}
        public boolean isCurrent(String token){return current.equals(token);}
        public boolean blocked(String sha){return false;}
    }
    public static void main(String[] args)throws Exception {
        File dir=Files.createTempDirectory(Paths.get(args[0]),"tablet-grant-").toFile();Transport t=new Transport();String token=t.current;
        KernelDelivery d=new KernelDelivery(dir,t);
        ok(!TabletEntitlement.premium("free"),"free excluded");ok(TabletEntitlement.premium("plus"),"advanced included");ok(TabletEntitlement.premium("vip"),"VIP included");
        ok(!TabletEntitlement.premium("pro")&&!TabletEntitlement.premium(null)&&!TabletEntitlement.premium("VIP"),"unknown/old labels do not grant access");
        ok(!TabletEntitlement.allowed(d,token,true),"missing signed cache is not a paid entitlement");
        d.refresh(token);ok(TabletEntitlement.allowed(d,token,true),"signed advanced cache permits before wxid lookup");
        ok(!TabletEntitlement.allowed(d,token,false),"invalid activation signature cannot reuse paid cache");
        ok(!TabletEntitlement.allowed(d,"different-synthetic-activation-token",true),"another activation cannot reuse grant");
        t.edition="vip";d.refresh(token);ok(TabletEntitlement.allowed(d,token,true),"signed VIP permits");
        t.edition="free";d.probe(token);ok(!TabletEntitlement.allowed(d,token,true),"online free decision denies stale VIP cache before download");
        d.refresh(token);ok(!TabletEntitlement.allowed(d,token,true),"signed free cache denied");
        Properties p=new Properties();File channel=new File(dir,"kernel.channel");try(InputStream in=new FileInputStream(channel)){p.load(in);}p.setProperty("edition","plus");try(OutputStream out=new FileOutputStream(channel)){p.store(out,"tampered");}
        ok(!TabletEntitlement.allowed(new KernelDelivery(dir,t),token,true),"editing edition text cannot forge paid signature");
        t.edition="plus";d.refresh(token);Files.write(new File(dir,"kernel.enc").toPath(),new byte[1400]);
        ok(!TabletEntitlement.allowed(new KernelDelivery(dir,t),token,true),"modified paid kernel bytes denied");
        d.refresh(token);t.denied=true;try{d.probe(token);}catch(KernelDelivery.AuthorizationDenied expected){}
        ok(!TabletEntitlement.allowed(d,token,true),"revoked activation denies previous paid cache");
        ok(!TabletEntitlement.allowed(null,token,true)&&!TabletEntitlement.allowed(d,null,true),"missing loader or activation denied");
        System.out.println("PASS "+checks+" signed paid tablet entitlement, downgrade and tampering checks");
    }
}
