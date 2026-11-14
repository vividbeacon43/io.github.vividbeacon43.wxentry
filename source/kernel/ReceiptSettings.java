package com.zafiro.wxentry.kernel;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.widget.*;
import android.view.View;
import java.util.*;

final class ReceiptSettings {
    static void toast(Activity act,String message) { android.widget.Toast.makeText(act,message,android.widget.Toast.LENGTH_SHORT).show(); }
    static boolean setFlag(String path,boolean enabled) {
        try {
            java.io.File file=new java.io.File(path);
            if(enabled) { file.getParentFile().mkdirs();return file.exists() || file.createNewFile(); }
            return !file.exists() || file.delete();
        } catch(Throwable failure) { return false; }
    }

    static boolean access(Activity act,ReceiptConfig.Feature feature) {
        return feature!=ReceiptConfig.Feature.TRANSFER || Kernel.transferSettingsAllowed(act);
    }
    static boolean sameAccount(Activity act,String account) {
        if(account.equals(Kernel.receiptAccount()))return true;
        toast(act,"账号已切换，请重新打开设置");return false;
    }
    static boolean enabled(ReceiptConfig.Feature feature) { return ReceiptStore.enabled(feature); }
    static void toggle(Activity act,ReceiptConfig.Feature feature,Runnable refresh) {
        if(!access(act,feature))return;
        try {
            String account=ReceiptStore.account();ReceiptConfig config=ReceiptStore.load(feature,account);
            if(feature==ReceiptConfig.Feature.TRANSFER && !config.enabled && !Kernel.transferSettingsReady(act))return;
            config.enabled=!config.enabled;
            if(!ReceiptStore.save(feature,account,config))throw new IllegalStateException("保存失败");
            if(feature==ReceiptConfig.Feature.RED_PACKET)setFlag(Kernel.RP_ON_PATH,config.enabled);
            if(refresh!=null)refresh.run();
            toast(act,feature.label+"自动处理已"+(config.enabled?"开启":"关闭"));
        } catch(Throwable failure) { toast(act,"设置保存失败，请确认当前账号已就绪"); }
    }
    static void lists(final Activity act,final ReceiptConfig.Feature feature) {
        if(!access(act,feature))return;
        try {
            final String account=ReceiptStore.account();final LinearLayout box=SettingsUi.column(act);
            final Runnable[] render=new Runnable[1];
            render[0]=new Runnable() { public void run() {
                try {
                    ReceiptConfig config=ReceiptStore.load(feature,account);box.removeAllViews();
                    box.addView(SettingsUi.text(act,(config.enabled?"自动处理总开关已开启。":"自动处理总开关已关闭，开启后本页规则才生效。")+"分类关闭：不处理任何人。分类开启：空白名单处理全部，非空白名单只处理选中的；黑名单不处理选中的。黑白名单互斥，两项都关闭则处理全部。点击名单文字编辑。",12));
                    for(final ReceiptConfig.Scope scope:ReceiptConfig.Scope.values()) {
                        ReceiptConfig.ListRule state=config.scope(scope);SettingsUi.heading(box,scope.label);
                        box.addView(SettingsUi.toggle(act,"允许处理此类"+feature.label,"关闭后此分类不自动处理",state.rule!=ReceiptConfig.Rule.OFF,new SettingsUi.Change() {
                            public boolean set(boolean on) { return changeRule(act,feature,scope,account,0,on,render[0]); }
                        }));
                        for(final boolean white:new boolean[]{true,false}) {
                            int size=(white?state.white:state.black).size();
                            SettingsUi.ToggleRow row=SettingsUi.toggle(act,white?"白名单（只处理选中的）":"黑名单（不处理选中的）",state.rule==ReceiptConfig.Rule.OFF?"分类已关闭，本名单暂不生效":white&&size==0?(state.rule==ReceiptConfig.Rule.WHITE?"当前处理全部（白名单为空）":"空白名单启用后处理全部")+" · 点击文字添加":"已选 "+size+" 项 · 点击文字编辑",state.rule==(white?ReceiptConfig.Rule.WHITE:ReceiptConfig.Rule.BLACK),new SettingsUi.Change() {
                                public boolean set(boolean on) { return changeRule(act,feature,scope,account,white?1:2,on,render[0]); }
                            });
                            row.control.setEnabled(state.rule!=ReceiptConfig.Rule.OFF);
                            row.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                                if(sameAccount(act,account)&&access(act,feature))picker(act,feature,scope,white,account,render[0]);
                            } });box.addView(row);
                        }
                    }
                } catch(Throwable failure) { toast(act,"名单设置暂时无法读取"); }
            } };render[0].run();
            ScrollView scroll=new ScrollView(act);scroll.addView(box);
            new AlertDialog.Builder(act).setTitle(feature.label+"黑白名单").setView(scroll).setNegativeButton("关闭",null).show();
        } catch(Throwable failure) { toast(act,"名单设置暂时无法读取，自动处理保持停止"); }
    }
    static boolean changeRule(Activity act,ReceiptConfig.Feature feature,ReceiptConfig.Scope scope,String account,int kind,boolean on,Runnable render) {
        if(sameAccount(act,account)&&access(act,feature))try {
            ReceiptConfig config=ReceiptStore.load(feature,account);ReceiptConfig.ListRule rule=config.scope(scope);
            if(kind==0)rule.setEnabled(on);else if(rule.rule!=ReceiptConfig.Rule.OFF)rule.setList(kind==1,on);
            if(!ReceiptStore.save(feature,account,config))throw new IllegalStateException();
        } catch(Throwable failure) { toast(act,"规则保存失败，已保持原状态"); }
        if(render!=null)render.run();
        ReceiptConfig.Rule saved=ReceiptStore.load(feature,account).scope(scope).rule;
        return kind==0?saved!=ReceiptConfig.Rule.OFF:saved==(kind==1?ReceiptConfig.Rule.WHITE:ReceiptConfig.Rule.BLACK);
    }
    static void picker(final Activity act,final ReceiptConfig.Feature feature,final ReceiptConfig.Scope scope,final boolean white,final String account,final Runnable refresh) {
        toast(act,"正在按最近聊天时间读取列表…");
        new Thread(new Runnable() { public void run() {
            try {
                ReceiptConfig config=ReceiptStore.load(feature,account);
                final Set<String> selected=new LinkedHashSet<String>(white?config.scope(scope).white:config.scope(scope).black);
                final List<ReceiptTargets.Target> all=ReceiptTargets.withSaved(ReceiptTargets.load(account),selected,scope);
                act.runOnUiThread(new Runnable() { public void run() {
                    if(act.isFinishing() || act.isDestroyed() || !sameAccount(act,account) || !access(act,feature))return;
                    buildPicker(act,feature,scope,white,account,all,selected,refresh);
                } });
            } catch(Throwable failure) { act.runOnUiThread(new Runnable() { public void run() { toast(act,"无法读取聊天时间，请稍后重试"); } }); }
        } },"wxentry-receipt-list").start();
    }
    static void buildPicker(final Activity act,final ReceiptConfig.Feature feature,final ReceiptConfig.Scope scope,final boolean white,final String account,
            final List<ReceiptTargets.Target> all,final Set<String> selected,final Runnable refresh) {
        final List<ReceiptTargets.Target> visible=new ArrayList<ReceiptTargets.Target>(all);
        LinearLayout box=new LinearLayout(act);box.setOrientation(LinearLayout.VERTICAL);
        final EditText search=new EditText(act);search.setSingleLine(true);search.setHint("搜索"+scope.label+"名称或微信号");box.addView(search);
        TextView hint=new TextView(act);hint.setText("最近聊天在前；搜索时保留已勾选项目");hint.setTextSize(12);box.addView(hint);
        final ListView list=new ListView(act);list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        final ArrayAdapter<String> adapter=new ArrayAdapter<String>(act,android.R.layout.simple_list_item_multiple_choice,new ArrayList<String>());
        list.setAdapter(adapter);
        box.addView(list,new LinearLayout.LayoutParams(-1,(int)(act.getResources().getDisplayMetrics().heightPixels*0.48f)));
        final Runnable render=new Runnable() { public void run() {
            adapter.clear();java.text.DateFormat format=new java.text.SimpleDateFormat("MM-dd HH:mm",java.util.Locale.CHINA);
            for(ReceiptTargets.Target target:visible)adapter.add(target.name+"\n"+(target.lastChat>0?format.format(new Date(target.lastChat)):"暂无聊天时间"));
            adapter.notifyDataSetChanged();list.clearChoices();
            for(int i=0;i<visible.size();i++)list.setItemChecked(i,selected.contains(visible.get(i).id));
        } };
        list.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            public void onItemClick(android.widget.AdapterView<?> parent,View view,int position,long id) {
                if(position<0 || position>=visible.size())return;
                String key=visible.get(position).id;if(list.isItemChecked(position))selected.add(key);else selected.remove(key);
            }
        });
        search.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s,int st,int c,int a) { }
            public void onTextChanged(CharSequence s,int st,int before,int count) { }
            public void afterTextChanged(android.text.Editable s) { visible.clear();visible.addAll(ReceiptTargets.filter(all,s==null?"":s.toString()));render.run(); }
        });
        final String listName=white?"白名单":"黑名单";
        DialogInterface.OnClickListener saveAndUse=new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface ignored,int which) { saveList(act,feature,scope,white,account,selected,true,refresh); }
        };
        new AlertDialog.Builder(act).setTitle(feature.label+" · "+scope.label+listName).setView(box)
            .setPositiveButton("保存并使用"+listName,saveAndUse)
            .setNeutralButton("仅保存",new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface ignored,int which) { saveList(act,feature,scope,white,account,selected,false,refresh); }
            }).setNegativeButton("取消",null).show();render.run();
    }
    static void saveList(Activity act,ReceiptConfig.Feature feature,ReceiptConfig.Scope scope,boolean white,String account,Set<String> ids,boolean use,Runnable refresh) {
        if(!sameAccount(act,account) || !access(act,feature))return;
        try {
            ReceiptConfig config=ReceiptStore.load(feature,account);ReceiptConfig.ListRule rule=config.scope(scope);
            Set<String> list=white?rule.white:rule.black;list.clear();list.addAll(ids);
            if(use)rule.setList(white,true);
            if(!ReceiptStore.save(feature,account,config))throw new IllegalStateException();
            toast(act,"已保存 "+ids.size()+" 项");if(refresh!=null)refresh.run();
        } catch(Throwable failure) { toast(act,"名单保存失败"); }
    }
    static void delay(final Activity act,final ReceiptConfig.Feature feature,final Runnable refresh) {
        if(!access(act,feature))return;
        try {
            final String account=ReceiptStore.account();ReceiptConfig config=ReceiptStore.load(feature,account);
            LinearLayout box=SettingsUi.column(act);
            final SettingsUi.ToggleRow enabled=SettingsUi.toggle(act,"启用随机延时","保存后生效",config.delayEnabled,new SettingsUi.Change() { public boolean set(boolean on) { return on; } });box.addView(enabled);
            TextView hint=new TextView(act);hint.setText("收到后随机等待最短～最长秒数。关闭即立即处理；两值相同则固定等待。支持三位小数，范围 0～86400 秒。");box.addView(hint);
            final EditText min=new EditText(act),max=new EditText(act);
            min.setHint("最短延时（秒）");max.setHint("最长延时（秒）");
            min.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
            max.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
            min.setText(ReceiptConfig.seconds(config.minDelayMs));max.setText(ReceiptConfig.seconds(config.maxDelayMs));box.addView(min);box.addView(max);
            final AlertDialog dialog=new AlertDialog.Builder(act).setTitle(feature.label+"随机延时（秒）").setView(box).setNegativeButton("取消",null).setPositiveButton("保存",null).create();
            dialog.setOnShowListener(new DialogInterface.OnShowListener() {
                public void onShow(DialogInterface ignored) { dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    public void onClick(View view) {
                        if(!sameAccount(act,account) || !access(act,feature))return;
                        try {
                            long a=ReceiptConfig.seconds(min.getText().toString()),b=ReceiptConfig.seconds(max.getText().toString());
                            if(a>b)throw new IllegalArgumentException("最短延时不能大于最长延时");
                            ReceiptConfig config=ReceiptStore.load(feature,account);config.delayEnabled=enabled.control.isChecked();config.minDelayMs=a;config.maxDelayMs=b;
                            if(!ReceiptStore.save(feature,account,config))throw new IllegalStateException("延时保存失败");
                            dialog.dismiss();if(refresh!=null)refresh.run();
                        } catch(IllegalArgumentException invalid) { min.setError(invalid.getMessage()); }
                        catch(Throwable failure) { toast(act,"延时保存失败"); }
                    }
                }); }
            });dialog.show();
        } catch(Throwable failure) { toast(act,"延时设置暂时无法读取"); }
    }
    static boolean delayEnabled(ReceiptConfig.Feature feature) {
        try { return ReceiptStore.current(feature).delayEnabled; }catch(Throwable unavailable) { return false; }
    }
    static boolean setDelayEnabled(Activity act,ReceiptConfig.Feature feature,boolean on,Runnable refresh) {
        if(access(act,feature))try {
            String account=ReceiptStore.account();ReceiptConfig config=ReceiptStore.load(feature,account);config.delayEnabled=on;
            if(!ReceiptStore.save(feature,account,config))throw new IllegalStateException();
        } catch(Throwable failure) { toast(act,"延时开关保存失败，已保持原状态"); }
        if(refresh!=null)refresh.run();return delayEnabled(feature);
    }
    static String delayRangeLabel(ReceiptConfig.Feature feature) {
        try { ReceiptConfig c=ReceiptStore.current(feature);return ReceiptConfig.seconds(c.minDelayMs)+"～"+ReceiptConfig.seconds(c.maxDelayMs)+" 秒 · 点击设置"; }
        catch(Throwable unavailable) { return "暂未就绪 · 点击设置"; }
    }
    static String delayLabel(ReceiptConfig.Feature feature) {
        try { ReceiptConfig c=ReceiptStore.current(feature);return c.delayEnabled?ReceiptConfig.seconds(c.minDelayMs)+"～"+ReceiptConfig.seconds(c.maxDelayMs)+" 秒":"关"; }
        catch(Throwable unavailable) { return "未就绪"; }
    }
    static boolean keywordAccess(Activity act,ReceiptConfig.Feature feature) {
        if(feature!=ReceiptConfig.Feature.ORIGINAL_IMAGE && Kernel.receiptKeywordsEnabled())return true;
        toast(act,"关键词过滤仅限 VIP 版");return false;
    }
    static String keywordLabel(ReceiptConfig.Feature feature) {
        try {
            ReceiptConfig.KeywordRule k=ReceiptStore.current(feature).keywords;
            return feature.label+"关键词过滤（VIP）："+(k.action==ReceiptConfig.KeywordAction.OFF?"关":k.action==ReceiptConfig.KeywordAction.SKIP?"命中跳过":"仅领命中");
        }catch(Throwable unavailable){return feature.label+"关键词过滤（VIP）";}
    }
    static void keywords(final Activity act,final ReceiptConfig.Feature feature,final Runnable refresh) {
        if(!keywordAccess(act,feature))return;
        try {
            final String account=ReceiptStore.account();ReceiptConfig initial=ReceiptStore.load(feature,account);
            final ReceiptConfig.KeywordAction[] action={initial.keywords.action};
            LinearLayout box=SettingsUi.column(act);
            box.addView(SettingsUi.text(act,"独立于黑白名单，需同时符合两类规则。红包只匹配祝福语，转账只匹配备注；不匹配昵称、金额或会话内容。两项互斥，全部关闭则不限制关键词。",12));
            final LinearLayout switches=new LinearLayout(act);switches.setOrientation(LinearLayout.VERTICAL);box.addView(switches);
            final Runnable[] render={null};
            render[0]=new Runnable(){public void run(){
                switches.removeAllViews();
                for(final ReceiptConfig.KeywordAction selected:new ReceiptConfig.KeywordAction[]{ReceiptConfig.KeywordAction.SKIP,ReceiptConfig.KeywordAction.ONLY})
                    switches.addView(SettingsUi.toggle(act,selected==ReceiptConfig.KeywordAction.SKIP?"命中关键词就跳过":"仅领取命中关键词的","",action[0]==selected,new SettingsUi.Change(){public boolean set(boolean on){
                        if(on)action[0]=selected;else if(action[0]==selected)action[0]=ReceiptConfig.KeywordAction.OFF;
                        render[0].run();return action[0]==selected;
                    }}));
            }};render[0].run();
            SettingsUi.heading(box,"匹配方式（二选一）");
            final RadioGroup modes=new RadioGroup(act);
            for(int i=0;i<2;i++) {
                RadioButton row=new RadioButton(act);row.setId(0x75260001+i);row.setText(i==0?"模糊：包含任意一个关键词":"精确：整段文字等于任意一个关键词");row.setTextSize(14);row.setTextColor(SettingsUi.textColor(act));modes.addView(row);
            }
            modes.check(0x75260001+(initial.keywords.match==ReceiptConfig.KeywordMatch.EXACT?1:0));box.addView(modes);
            box.addView(SettingsUi.text(act,"每行一个关键词，多个词任意一个命中即生效；忽略首尾空格，区分英文大小写。最多100个词，每词200字。保存后生效，取消不修改。开启后无法读取祝福语／备注时暂停自动领取，可手动操作。",12));
            final EditText words=new EditText(act);words.setSingleLine(false);words.setMinLines(3);words.setMaxLines(6);words.setGravity(android.view.Gravity.TOP);
            words.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            words.setTextColor(SettingsUi.textColor(act));words.setHintTextColor(SettingsUi.dark(act)?0xFFB7BEC8:0xFF687180);
            words.setHint("每行一个关键词");words.setText(initial.keywords.text());box.addView(words);
            ScrollView scroll=new ScrollView(act);scroll.setFillViewport(false);scroll.addView(box);
            final AlertDialog dialog=new AlertDialog.Builder(act).setTitle(feature.label+"关键词过滤（VIP）").setView(scroll).setNegativeButton("取消",null).setPositiveButton("保存",null).create();
            dialog.setOnShowListener(new DialogInterface.OnShowListener(){public void onShow(DialogInterface ignored){
                int color=SettingsUi.dark(act)?0xFFB9C9E6:SettingsUi.BLUE;
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(color);dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setTextColor(color);
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener(){public void onClick(View v){
                    if(!sameAccount(act,account) || !keywordAccess(act,feature))return;
                    try {
                        ReceiptConfig c=ReceiptStore.load(feature,account);c.keywords.action=action[0];
                        c.keywords.match=modes.getCheckedRadioButtonId()==0x75260002?ReceiptConfig.KeywordMatch.EXACT:ReceiptConfig.KeywordMatch.FUZZY;
                        c.keywords.setWords(words.getText().toString());c.keywords.validate();
                        if(!ReceiptStore.save(feature,account,c))throw new IllegalStateException();
                        dialog.dismiss();if(refresh!=null)refresh.run();toast(act,"关键词过滤已保存");
                    }catch(IllegalArgumentException invalid){words.setError(invalid.getMessage());}
                    catch(Throwable failure){toast(act,"关键词设置保存失败，保持原状态");}
                }});
            }});dialog.show();
        }catch(Throwable unavailable){toast(act,"关键词设置暂时无法读取");}
    }
}
