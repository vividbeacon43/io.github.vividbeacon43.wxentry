package com.zafiro.wxentry.kernel;

import android.app.*;
import android.content.DialogInterface;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Explicit user choices create only private presentation records. No native like/comment submission. */
final class SnsLocalPicker {
    static boolean allowed(Activity act,SnsLocalNative.Target target,SnsLocalData.Feature feature){
        if(!SnsLocalSettings.access(act)||!SnsLocalSettings.same(act,target.owner))return false;
        if(!SnsLocalStore.enabled(feature)){SnsLocalSettings.toast(act,"对应开关已关闭，请重新开启后操作");return false;}return !act.isFinishing()&&!act.isDestroyed();
    }
    static void choose(final Activity act,final SnsLocalNative.Target target,final SnsLocalData.Feature feature){
        if(!allowed(act,target,feature))return;final boolean[] cancelled={false};
        final ProgressDialog progress=new ProgressDialog(act);progress.setMessage("正在加载好友…");progress.setCancelable(true);progress.setOnCancelListener(new DialogInterface.OnCancelListener(){public void onCancel(DialogInterface d){cancelled[0]=true;}});progress.show();
        SnsLocalRuntime.io.execute(new Runnable(){public void run(){try{
            final List<ReceiptTargets.Target> all=new ArrayList<ReceiptTargets.Target>();for(ReceiptTargets.Target friend:ReceiptTargets.load(target.owner))if(!friend.group()&&SnsLocalData.friend(friend.id))all.add(friend);
            SnsLocalRuntime.main.post(new Runnable(){public void run(){try{progress.dismiss();}catch(Throwable ignored){}if(cancelled[0]||!allowed(act,target,feature))return;if(all.isEmpty()){SnsLocalSettings.toast(act,"暂无可选好友，请先确认通讯录已加载");return;}show(act,target,feature,all);}});
        }catch(final Throwable failure){SnsLocalRuntime.main.post(new Runnable(){public void run(){try{progress.dismiss();}catch(Throwable ignored){}if(!cancelled[0])SnsLocalSettings.toast(act,"好友列表暂未就绪，请稍后重试");SnsLocalRuntime.log("friends-error",failure);}});}}});
    }
    static void show(final Activity act,final SnsLocalNative.Target target,final SnsLocalData.Feature feature,final List<ReceiptTargets.Target> all){
        final boolean likes=feature==SnsLocalData.Feature.LIKES;final Set<String> selected=new LinkedHashSet<String>();
        if(likes){Set<String> existing=new HashSet<String>();for(SnsLocalData.Person p:SnsLocalStore.load(target.owner).post(target.key).likes)existing.add(p.id);for(ReceiptTargets.Target p:all)if(existing.contains(p.id))selected.add(p.id);}
        final int limit=likes?SnsLocalData.MAX_LIKES:Math.min(20,SnsLocalData.MAX_COMMENTS-SnsLocalStore.load(target.owner).post(target.key).comments.size());
        if(limit<=0){SnsLocalSettings.toast(act,"本条已有100条本地评论，请先长按评论删除一些");return;}
        LinearLayout box=SettingsUi.column(act);final EditText search=new EditText(act);search.setSingleLine(true);search.setHint("搜索好友昵称、备注或微信号");box.addView(search);
        final TextView hint=SettingsUi.text(act,"",12);box.addView(hint);
        final List<ReceiptTargets.Target> visible=new ArrayList<ReceiptTargets.Target>(all);
        final ListView list=new ListView(act);list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);final ArrayAdapter<String> adapter=new ArrayAdapter<String>(act,android.R.layout.simple_list_item_multiple_choice,new ArrayList<String>());list.setAdapter(adapter);box.addView(list,new LinearLayout.LayoutParams(-1,(int)(act.getResources().getDisplayMetrics().heightPixels*0.43f)));
        final Runnable render=new Runnable(){public void run(){adapter.clear();for(ReceiptTargets.Target p:visible)adapter.add(p.name);adapter.notifyDataSetChanged();list.clearChoices();for(int i=0;i<visible.size();i++)list.setItemChecked(i,selected.contains(visible.get(i).id));hint.setText("仅本机展示 · 最近聊天在前 · 已选"+selected.size()+"位（最多"+limit+"位）");}};
        list.setOnItemClickListener(new AdapterView.OnItemClickListener(){public void onItemClick(AdapterView<?> parent,View view,int position,long id){if(position<0||position>=visible.size())return;String key=visible.get(position).id;
            if(list.isItemChecked(position)){if(selected.size()>=limit){list.setItemChecked(position,false);SnsLocalSettings.toast(act,"本次最多选择"+limit+"位好友");}else selected.add(key);}else selected.remove(key);render.run();}});
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){}public void afterTextChanged(Editable text){visible.clear();visible.addAll(ReceiptTargets.filter(all,text==null?"":text.toString()));render.run();}});
        final AlertDialog dialog=new AlertDialog.Builder(act).setTitle(likes?"伪集赞 · 选择好友":"伪评论 · 选择好友").setView(box).setNegativeButton("取消",null).setNeutralButton("清空选择",null).setPositiveButton(likes?"保存本地点赞":"下一步",null).create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener(){public void onShow(DialogInterface ignored){
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener(){public void onClick(View view){selected.clear();render.run();}});
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener(){public void onClick(View view){
                if(!allowed(act,target,feature))return;final List<SnsLocalData.Person> people=new ArrayList<SnsLocalData.Person>();for(ReceiptTargets.Target p:all)if(selected.contains(p.id))people.add(new SnsLocalData.Person(p.id,p.name));
                if(likes){if(SnsLocalStore.edit(target.owner,target.key,new SnsLocalStore.Edit(){public SnsLocalData apply(SnsLocalData before){return before.likes(target.key,people,System.currentTimeMillis());}})){dialog.dismiss();SnsLocalSettings.toast(act,"本地点赞已更新");}else SnsLocalSettings.toast(act,"本地记录保存失败，请稍后重试");}
                else{if(people.isEmpty()){SnsLocalSettings.toast(act,"请先选择评论好友");return;}dialog.dismiss();comments(act,target,people);}
            }});
        }});dialog.show();render.run();
    }
    static void preset(final Activity act,final EditText input,String owner){
        final List<String> presets=SnsLocalStore.load(owner).presets;if(presets.isEmpty()){SnsLocalSettings.toast(act,"可先在朋友圈菜单设置评论预设，也可直接输入");return;}
        new AlertDialog.Builder(act).setTitle("选择评论预设").setItems(presets.toArray(new String[0]),new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){if(which>=0&&which<presets.size())input.setText(presets.get(which));}}).show();
    }
    static void comments(final Activity act,final SnsLocalNative.Target target,final List<SnsLocalData.Person> people){
        if(!allowed(act,target,SnsLocalData.Feature.COMMENTS))return;LinearLayout box=SettingsUi.column(act);TextView hint=SettingsUi.text(act,"分别填写每位好友的评论，头像和姓名来自所选好友；仅本机展示。",13);box.addView(hint);
        final List<EditText> inputs=new ArrayList<EditText>();List<String> presets=SnsLocalStore.load(target.owner).presets;
        for(int i=0;i<people.size();i++){final SnsLocalData.Person person=people.get(i);LinearLayout head=new LinearLayout(act);head.setGravity(Gravity.CENTER_VERTICAL);TextView name=SettingsUi.text(act,person.name,15);head.addView(name,new LinearLayout.LayoutParams(0,-2,1));final EditText input=new EditText(act);input.setHint("为"+person.name+"填写评论");input.setMinLines(2);input.setMaxLines(4);if(!presets.isEmpty())input.setText(presets.get(i%presets.size()));Button choose=new Button(act);choose.setText("选预设");choose.setTextSize(12);choose.setOnClickListener(new View.OnClickListener(){public void onClick(View view){preset(act,input,target.owner);}});head.addView(choose);box.addView(head);box.addView(input);inputs.add(input);}
        ScrollView scroll=new ScrollView(act);scroll.addView(box);LinearLayout frame=new LinearLayout(act);frame.addView(scroll,new LinearLayout.LayoutParams(-1,(int)(act.getResources().getDisplayMetrics().heightPixels*0.5f)));
        final AlertDialog dialog=new AlertDialog.Builder(act).setTitle("编辑伪评论（"+people.size()+"位好友）").setView(frame).setNegativeButton("取消",null).setPositiveButton("保存本地评论",null).create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener(){public void onShow(DialogInterface ignored){dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener(){public void onClick(View view){
            if(!allowed(act,target,SnsLocalData.Feature.COMMENTS))return;final List<SnsLocalData.Comment> entries=new ArrayList<SnsLocalData.Comment>();
            for(int i=0;i<inputs.size();i++){EditText input=inputs.get(i);String value=input.getText().toString().trim();if(value.isEmpty()||value.length()>SnsLocalData.MAX_TEXT){input.setError("请填写1～500字的评论");input.requestFocus();return;}entries.add(new SnsLocalData.Comment("local_"+UUID.randomUUID().toString().replace("-",""),people.get(i),value));}
            if(SnsLocalStore.edit(target.owner,target.key,new SnsLocalStore.Edit(){public SnsLocalData apply(SnsLocalData before){return before.addComments(target.key,entries,System.currentTimeMillis());}})){dialog.dismiss();SnsLocalSettings.toast(act,"本地评论已添加");}else SnsLocalSettings.toast(act,"记录已变化或达到上限，请重新打开后操作");
        }});}});dialog.show();if(dialog.getWindow()!=null)dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }
    static void edit(final Activity act,final SnsLocalNative.Target target,final SnsLocalData.Comment comment){
        if(!allowed(act,target,SnsLocalData.Feature.COMMENTS))return;LinearLayout box=SettingsUi.column(act);final EditText input=new EditText(act);input.setText(comment.text);input.setMinLines(2);input.setMaxLines(6);box.addView(input);Button preset=new Button(act);preset.setText("选择预设");preset.setOnClickListener(new View.OnClickListener(){public void onClick(View view){preset(act,input,target.owner);}});box.addView(preset);
        final AlertDialog dialog=new AlertDialog.Builder(act).setTitle("编辑 · "+comment.person.name).setView(box).setNegativeButton("取消",null).setNeutralButton("删除这条",null).setPositiveButton("保存",null).create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener(){public void onShow(DialogInterface ignored){
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener(){public void onClick(View view){if(!allowed(act,target,SnsLocalData.Feature.COMMENTS))return;final String text=input.getText().toString().trim();if(text.isEmpty()||text.length()>SnsLocalData.MAX_TEXT){input.setError("请填写1～500字的评论");return;}if(saveEdit(target,comment.localId,text))dialog.dismiss();else SnsLocalSettings.toast(act,"记录已变化，请重新打开后编辑");}});
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener(){public void onClick(View view){if(allowed(act,target,SnsLocalData.Feature.COMMENTS)&&saveEdit(target,comment.localId,null))dialog.dismiss();}});
        }});dialog.show();
    }
    static boolean saveEdit(final SnsLocalNative.Target target,final String id,final String text){return SnsLocalStore.edit(target.owner,target.key,new SnsLocalStore.Edit(){public SnsLocalData apply(SnsLocalData before){return before.edit(target.key,id,text,System.currentTimeMillis());}});}
    static void clearPost(final Activity act,final SnsLocalNative.Target target){
        if(!SnsLocalSettings.access(act)||!SnsLocalSettings.same(act,target.owner))return;
        new AlertDialog.Builder(act).setTitle("清除本条本地互动").setMessage("移除这条朋友圈的伪集赞和伪评论。").setNegativeButton("取消",null).setPositiveButton("清除",new DialogInterface.OnClickListener(){public void onClick(DialogInterface dialog,int which){if(!SnsLocalSettings.access(act)||!SnsLocalSettings.same(act,target.owner))return;boolean okay=SnsLocalStore.edit(target.owner,target.key,new SnsLocalStore.Edit(){public SnsLocalData apply(SnsLocalData before){return before.clear(target.key);}});SnsLocalSettings.toast(act,okay?"本条本地互动已清除":"清除失败，请稍后重试");}}).show();
    }
}
