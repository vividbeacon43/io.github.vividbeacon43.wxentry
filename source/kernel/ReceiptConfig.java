package com.zafiro.wxentry.kernel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Separate, account-owned rules for receipts and automatic original-photo downloads. */
final class ReceiptConfig {
    enum Feature {
        RED_PACKET("redpacket","红包"), TRANSFER("transfer","转账"), ORIGINAL_IMAGE("originalimage","原图下载");
        final String key,label;
        Feature(String k,String l) { key=k;label=l; }
    }
    enum Scope {
        PERSON("person","个人"), GROUP("group","群聊");
        final String key,label;
        Scope(String k,String l) { key=k;label=l; }
        static Scope of(String id) { return id!=null && id.endsWith("@chatroom")?GROUP:PERSON; }
    }
    enum Rule {
        ALL("不限名单"), WHITE("仅白名单"), BLACK("排除黑名单"), OFF("此类关闭");
        final String label;Rule(String l) { label=l; }
    }
    static final long MAX_DELAY_MS=86400000L;
    static final class ListRule {
        Rule rule=Rule.ALL;
        final Set<String> white=new LinkedHashSet<String>(),black=new LinkedHashSet<String>();
        void setEnabled(boolean enabled) { if(!enabled)rule=Rule.OFF;else if(rule==Rule.OFF)rule=Rule.ALL; }
        void setList(boolean useWhite,boolean enabled) {
            Rule selected=useWhite?Rule.WHITE:Rule.BLACK;
            if(enabled)rule=selected;else if(rule==selected)rule=Rule.ALL;
        }
        boolean permits(String id) {
            return rule==Rule.ALL || rule==Rule.WHITE && white.contains(id) || rule==Rule.BLACK && !black.contains(id);
        }
    }
    boolean enabled;
    boolean delayEnabled;
    long minDelayMs=1000,maxDelayMs=3000;
    final ListRule person=new ListRule(),group=new ListRule();
    ListRule scope(Scope value) { return value==Scope.GROUP?group:person; }
    boolean permits(String id) { return validId(id) && scope(Scope.of(id)).permits(id); }
    long delay(Random random) {
        if(!delayEnabled)return 0;
        if(minDelayMs<0 || maxDelayMs<minDelayMs || maxDelayMs>MAX_DELAY_MS)throw new IllegalStateException("Invalid receipt delay");
        return minDelayMs+(maxDelayMs==minDelayMs?0:random.nextInt((int)(maxDelayMs-minDelayMs+1)));
    }
    static long seconds(String value) {
        if(value==null || !value.trim().matches("[0-9]+(?:\\.[0-9]{1,3})?"))throw new IllegalArgumentException("请填写秒数，最多三位小数");
        try {
            long ms=new BigDecimal(value.trim()).multiply(new BigDecimal("1000")).setScale(0,RoundingMode.UNNECESSARY).longValueExact();
            if(ms<0 || ms>MAX_DELAY_MS)throw new IllegalArgumentException("延时范围为 0～86400 秒");
            return ms;
        } catch(ArithmeticException tooLarge) { throw new IllegalArgumentException("秒数超出范围"); }
    }
    static String seconds(long millis) { return BigDecimal.valueOf(millis,3).stripTrailingZeros().toPlainString(); }
    static boolean validId(String id) { return id!=null && id.matches("[A-Za-z0-9_.@-]{1,256}"); }
    static boolean validMember(String id,Scope scope) { return validId(id) && Scope.of(id)==scope; }
    static Set<String> ids(String value,Scope scope) {
        Set<String> result=new LinkedHashSet<String>();
        if(value!=null && !value.isEmpty())for(String id:value.split(",")) {
            if(!validMember(id,scope))throw new IllegalArgumentException("Invalid list identity");result.add(id);
        }
        return result;
    }
    static String join(Set<String> ids,Scope scope) {
        List<String> sorted=new ArrayList<String>(ids);Collections.sort(sorted);StringBuilder out=new StringBuilder();
        for(String id:sorted) { if(!validMember(id,scope))throw new IllegalArgumentException("Wrong list category");if(out.length()>0)out.append(',');out.append(id); }
        return out.toString();
    }
    String encode() {
        if(minDelayMs<0 || maxDelayMs<minDelayMs || maxDelayMs>MAX_DELAY_MS)throw new IllegalArgumentException("最短延时不能大于最长延时");
        StringBuilder out=new StringBuilder("format=1\n");
        out.append("enabled=").append(enabled?"1":"0").append("\ndelay.enabled=").append(delayEnabled?"1":"0")
            .append("\ndelay.min.seconds=").append(seconds(minDelayMs)).append("\ndelay.max.seconds=").append(seconds(maxDelayMs)).append('\n');
        for(Scope s:Scope.values()) {
            ListRule r=scope(s);out.append(s.key).append(".rule=").append(r.rule.name()).append('\n');
            out.append(s.key).append(".white=").append(join(r.white,s)).append('\n');
            out.append(s.key).append(".black=").append(join(r.black,s)).append('\n');
        }
        return out.toString();
    }
    static ReceiptConfig decode(String source) {
        if(source==null)throw new IllegalArgumentException("Missing receipt settings");
        Map<String,String> fields=new HashMap<String,String>();
        for(String line:source.split("\\r?\\n")) {
            if(line.isEmpty())continue;String[] p=line.split("=",2);
            if(p.length!=2 || fields.put(p[0],p[1])!=null)throw new IllegalArgumentException("Invalid receipt settings");
        }
        if(!"1".equals(fields.get("format")))throw new IllegalArgumentException("Unknown settings version");
        ReceiptConfig c=new ReceiptConfig();
        c.enabled=bit(fields.get("enabled"));c.delayEnabled=bit(fields.get("delay.enabled"));
        c.minDelayMs=seconds(fields.get("delay.min.seconds"));c.maxDelayMs=seconds(fields.get("delay.max.seconds"));
        if(c.minDelayMs>c.maxDelayMs)throw new IllegalArgumentException("Invalid delay order");
        for(Scope s:Scope.values()) {
            ListRule r=c.scope(s);r.rule=Rule.valueOf(fields.get(s.key+".rule"));
            if(!fields.containsKey(s.key+".white") || !fields.containsKey(s.key+".black"))throw new IllegalArgumentException("Missing list");
            r.white.addAll(ids(fields.get(s.key+".white"),s));r.black.addAll(ids(fields.get(s.key+".black"),s));
        }
        return c;
    }
    static boolean bit(String value) {
        if(!"0".equals(value) && !"1".equals(value))throw new IllegalArgumentException("Invalid setting flag");return "1".equals(value);
    }
}
