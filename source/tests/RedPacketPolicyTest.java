package com.zafiro.wxentry.kernel;
public final class RedPacketPolicyTest {
    static int checks;
    static void check(boolean value,String why) { checks++;if(!value)throw new AssertionError(why); }
    public static void main(String[] args) {
        String[] editions={"free","pro","plus","vip","unknown",null};
        for(String running:editions)for(String authorized:editions) {
            boolean expected=("plus".equals(running) && ("plus".equals(authorized)||"vip".equals(authorized)))
                    || ("vip".equals(running)&&"vip".equals(authorized));
            check(RedPacketPolicy.premium(running,authorized,false)==expected,"edition grant "+running+"/"+authorized);
            check(!RedPacketPolicy.premium(running,authorized,true),"disabled module cannot use premium "+running+"/"+authorized);
        }
        check(RedPacketPolicy.configured(null,false)==RedPacketPolicy.Mode.FOREGROUND,"default is foreground");
        check(RedPacketPolicy.configured(null,true)==RedPacketPolicy.Mode.SILENT,"old silent flag migrates");
        check(RedPacketPolicy.configured("foreground\n",true)==RedPacketPolicy.Mode.FOREGROUND,"explicit foreground overrides old flag");
        check(RedPacketPolicy.configured("silent\n",false)==RedPacketPolicy.Mode.SILENT,"explicit silent stored as one value");
        for(String invalid:new String[]{"","both","foreground,silent","SILENT","0","1"})
            check(RedPacketPolicy.configured(invalid,true)==RedPacketPolicy.Mode.FOREGROUND,"invalid mode fails to foreground: "+invalid);
        for(RedPacketPolicy.Mode m:RedPacketPolicy.Mode.values()) {
            check(RedPacketPolicy.configured(RedPacketPolicy.value(m),false)==m,"single mode round trip "+m);
            check(RedPacketPolicy.effective(m,false)==RedPacketPolicy.Mode.FOREGROUND,"no premium grant never uses silent");
            check(RedPacketPolicy.effective(m,true)==m,"valid premium selection retained");
        }
        check(RedPacketPolicy.Mode.values().length==2,"exactly two receive choices");
        System.out.println("PASS "+checks+" premium entitlement and exclusive mode checks");
    }
}
