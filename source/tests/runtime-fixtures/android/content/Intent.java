package android.content;
public class Intent {
    public String url,talker="room@chatroom";
    public String getStringExtra(String key) { return key.equals("key_username")?talker:url; }
    public long messageId;
    public long getLongExtra(String key,long fallback) { return key.equals("key_msgid")?messageId:fallback; }
}
