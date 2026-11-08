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
    enum KeywordAction { OFF, SKIP, ONLY }
    enum KeywordMatch { FUZZY, EXACT }
    static final class KeywordRule {
        KeywordAction action=KeywordAction.OFF;
        KeywordMatch match=KeywordMatch.FUZZY;
        final List<String> words=new ArrayList<String>();
        void select(KeywordAction selected,boolean on) {
            if(selected==null || selected==KeywordAction.OFF)throw new IllegalArgumentException("Invalid keyword action");
            if(on)action=selected;else if(action==selected)action=KeywordAction.OFF;
        }
        void setWords(String input) {
            List<String> clean=new ArrayList<String>();
            if(input==null || input.length()>25000)throw new IllegalArgumentException("关键词内容过长");
            for(String line:input.split("\\r?\\n",-1)) {
                String word=line.trim();if(word.isEmpty())continue;
                if(word.length()>200 || word.indexOf('\0')>=0 || word.indexOf('\r')>=0)throw new IllegalArgumentException("每个关键词最多200字，不能含控制字符");
                if(!clean.contains(word))clean.add(word);
                if(clean.size()>100)throw new IllegalArgumentException("最多填写100个不同关键词");
            }
            words.clear();words.addAll(clean);
        }
        String text() { StringBuilder out=new StringBuilder();for(String w:words){if(out.length()>0)out.append('\n');out.append(w);}return out.toString(); }
        void validate() {
            if(action==null || match==null)throw new IllegalArgumentException("Invalid keyword mode");
            KeywordRule normalized=new KeywordRule();normalized.setWords(text());
            if(!normalized.words.equals(words))throw new IllegalArgumentException("Invalid keywords");
            if(action!=KeywordAction.OFF && words.isEmpty())throw new IllegalArgumentException("开启过滤前请至少填写一个关键词");
        }
        boolean permits(String text) {
            if(action==KeywordAction.OFF)return true;
            validate();if(text==null || text.length()>16384)return false;
            text=text.trim();boolean hit=false;
            for(String word:words)if(match==KeywordMatch.EXACT?text.equals(word):text.contains(word)){hit=true;break;}
            return action==KeywordAction.SKIP?!hit:hit;
        }
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
            return rule==Rule.ALL || rule==Rule.WHITE && (white.isEmpty() || white.contains(id)) || rule==Rule.BLACK && !black.contains(id);
        }
    }
    boolean enabled;
    boolean delayEnabled;
    long minDelayMs=1000,maxDelayMs=3000;
    final ListRule person=new ListRule(),group=new ListRule();
    final KeywordRule keywords=new KeywordRule();
    ListRule scope(Scope value) { return value==Scope.GROUP?group:person; }
    boolean permits(String id) { return validId(id) && scope(Scope.of(id)).permits(id); }
    boolean permits(String id,String text) { return permits(id) && keywords.permits(text); }
    // Only the actual card greeting/memo is searchable: never names, amount, IDs or the raw XML.
    static String messageText(Feature feature,String content) {
        if(feature==Feature.ORIGINAL_IMAGE || content==null || content.length()>262144)return null;
        String upper=content.toUpperCase(Locale.ROOT);
        if(upper.contains("<!DOCTYPE") || upper.contains("<!ENTITY"))return null;
        try {
            int start=content.indexOf('<');if(start<0)return null;
            javax.xml.parsers.DocumentBuilderFactory f=javax.xml.parsers.DocumentBuilderFactory.newInstance();f.setExpandEntityReferences(false);
            try{f.setFeature("http://xml.org/sax/features/external-general-entities",false);}catch(Exception ignored){}
            try{f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);}catch(Exception ignored){}
            javax.xml.parsers.DocumentBuilder b=f.newDocumentBuilder();b.setErrorHandler(new org.xml.sax.helpers.DefaultHandler());
            org.w3c.dom.Element root=b.parse(new org.xml.sax.InputSource(new java.io.StringReader(content.substring(start)))).getDocumentElement();
            org.w3c.dom.Element app=root.getTagName().equals("appmsg")?root:field(root,"appmsg");
            if(!field(app,"type").getTextContent().trim().equals(feature==Feature.RED_PACKET?"2001":"2000"))return null;
            org.w3c.dom.Element pay=field(app,"wcpayinfo");
            String key=feature==Feature.RED_PACKET?"sendertitle":"pay_memo";
            org.w3c.dom.Element value=null;
            for(org.w3c.dom.Node n=pay.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof org.w3c.dom.Element && key.equals(n.getNodeName())) {
                if(value!=null)throw new IllegalArgumentException("Duplicate receipt text");value=(org.w3c.dom.Element)n;
            }
            if(value==null)return feature==Feature.TRANSFER?"":null;
            for(org.w3c.dom.Node n=value.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof org.w3c.dom.Element)return null;
            return value.getTextContent().trim();
        }catch(Exception invalid){return null;}
    }
    private static org.w3c.dom.Element field(org.w3c.dom.Element parent,String name) {
        org.w3c.dom.Element value=null;
        for(org.w3c.dom.Node n=parent.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof org.w3c.dom.Element && name.equals(n.getNodeName())) {
            if(value!=null)throw new IllegalArgumentException("Duplicate receipt field");value=(org.w3c.dom.Element)n;
        }
        if(value==null)throw new IllegalArgumentException("Missing receipt field");return value;
    }
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
        keywords.validate();
        if(minDelayMs<0 || maxDelayMs<minDelayMs || maxDelayMs>MAX_DELAY_MS)throw new IllegalArgumentException("最短延时不能大于最长延时");
        StringBuilder out=new StringBuilder("format=1\n");
        out.append("enabled=").append(enabled?"1":"0").append("\ndelay.enabled=").append(delayEnabled?"1":"0")
            .append("\ndelay.min.seconds=").append(seconds(minDelayMs)).append("\ndelay.max.seconds=").append(seconds(maxDelayMs)).append('\n');
        for(Scope s:Scope.values()) {
            ListRule r=scope(s);out.append(s.key).append(".rule=").append(r.rule.name()).append('\n');
            out.append(s.key).append(".white=").append(join(r.white,s)).append('\n');
            out.append(s.key).append(".black=").append(join(r.black,s)).append('\n');
        }
        out.append("keyword.rule=").append(keywords.action.name()).append("\nkeyword.match=").append(keywords.match.name()).append("\nkeyword.words=");
        try { out.append(java.net.URLEncoder.encode(keywords.text(),"UTF-8")).append('\n'); }
        catch(java.io.UnsupportedEncodingException impossible) { throw new IllegalStateException(impossible); }
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
        if(fields.containsKey("keyword.rule") || fields.containsKey("keyword.match") || fields.containsKey("keyword.words")) {
            if(!fields.containsKey("keyword.rule") || !fields.containsKey("keyword.match") || !fields.containsKey("keyword.words"))throw new IllegalArgumentException("Incomplete keyword settings");
            c.keywords.action=KeywordAction.valueOf(fields.get("keyword.rule"));c.keywords.match=KeywordMatch.valueOf(fields.get("keyword.match"));
            try { c.keywords.setWords(java.net.URLDecoder.decode(fields.get("keyword.words"),"UTF-8")); }
            catch(java.io.UnsupportedEncodingException impossible) { throw new IllegalStateException(impossible); }
            c.keywords.validate();
        }
        return c;
    }
    static boolean bit(String value) {
        if(!"0".equals(value) && !"1".equals(value))throw new IllegalArgumentException("Invalid setting flag");return "1".equals(value);
    }
}
