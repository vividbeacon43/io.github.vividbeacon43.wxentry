package com.zafiro.wxentry.kernel;

/** Two independent, account-owned Moments switches. */
final class MomentsConfig {
    enum Feature { FORWARD, AD_BLOCK }
    final boolean forward,adBlock;
    MomentsConfig(boolean forward,boolean adBlock){this.forward=forward;this.adBlock=adBlock;}
    static MomentsConfig defaults(){return new MomentsConfig(true,false);}
    static MomentsConfig disabled(){return new MomentsConfig(false,false);}
    boolean get(Feature feature){return feature==Feature.FORWARD?forward:adBlock;}
    MomentsConfig with(Feature feature,boolean on){return new MomentsConfig(feature==Feature.FORWARD?on:forward,feature==Feature.AD_BLOCK?on:adBlock);}
    String encode(){return "format=1\nforward="+(forward?"1":"0")+"\nads="+(adBlock?"1":"0")+"\n";}
    static MomentsConfig decode(String text){
        if(text==null)return defaults();
        if(text.length()>1024)return disabled();
        java.util.Map<String,String> fields=new java.util.HashMap<String,String>();
        for(String line:text.split("\\r?\\n")){
            if(line.isEmpty())continue;int at=line.indexOf('=');if(at<=0||fields.put(line.substring(0,at),line.substring(at+1))!=null)return disabled();
        }
        if(fields.size()!=3||!"1".equals(fields.get("format")))return disabled();
        for(String key:new String[]{"forward","ads"})if(!"0".equals(fields.get(key))&&!"1".equals(fields.get(key)))return disabled();
        return new MomentsConfig("1".equals(fields.get("forward")),"1".equals(fields.get("ads")));
    }
}
