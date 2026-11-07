package com.zafiro.wxentry.kernel;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.widget.EditText;

final class RedPacketSettings {
    static void toast(Activity act,String message) { android.widget.Toast.makeText(act,message,android.widget.Toast.LENGTH_SHORT).show(); }
    static boolean requirePremium(Activity act) {
        if(RedPacketRuntime.premiumEnabled())return true;
        toast(act,"此功能需要高级版或 VIP 版");return false;
    }
    static void mode(final Activity act,final Runnable refresh) {
        if(Kernel.freeRedPacketManualOnly()) {
            new AlertDialog.Builder(act).setTitle("免费版红包领取")
                .setMessage("手动点开红包后自动点击拆包；没有点开红包时不会打开或领取。")
                .setPositiveButton("知道了",null).show();return;
        }
        final int selected=RedPacketRuntime.silentSelected()?1:0;
        String[] labels={"前台自动点领取","后台静默领取（高级版）"};
        new AlertDialog.Builder(act).setTitle("红包领取方式（二选一）")
            .setSingleChoiceItems(labels,selected,new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog,int which) {
                    if(which==1 && !requirePremium(act)) { ((AlertDialog)dialog).getListView().setItemChecked(selected,true);return; }
                    if(which==1 && !RedPacketRuntime.ready()) {
                        ((AlertDialog)dialog).getListView().setItemChecked(selected,true);
                        toast(act,RedPacketRuntime.unavailable());return;
                    }
                    RedPacketPolicy.Mode choice=which==1?RedPacketPolicy.Mode.SILENT:RedPacketPolicy.Mode.FOREGROUND;
                    if(!RedPacketRuntime.selectMode(choice)) {
                        ((AlertDialog)dialog).getListView().setItemChecked(selected,true);toast(act,"领取方式保存失败");return;
                    }
                    dialog.dismiss();if(refresh!=null)refresh.run();
                }
            }).setNegativeButton("取消",null).show();
    }
    static void show(final Activity act) {
        if(!requirePremium(act))return;
        android.widget.LinearLayout box=SettingsUi.column(act);
        final String[] labels={"显示发送者和金额提示","领取成功后自动回复原会话","随机选择自定义感言"};
        final String[] paths={Kernel.RP_TOAST_OFF_PATH,RedPacketRuntime.REPLY,RedPacketRuntime.RANDOM};
        for(int i=0;i<labels.length;i++) {
            final int which=i;final String path=paths[i];
            box.addView(SettingsUi.toggle(act,labels[i],"",which==0?!RedPacketRuntime.flag(path):RedPacketRuntime.flag(path),new SettingsUi.Change() {
                public boolean set(boolean value) {
                    if(requirePremium(act)) {
                        if(which==1&&value&&!RedPacketRuntime.ready())toast(act,RedPacketRuntime.unavailable());
                        else if(!setFlag(path,which==0?!value:value))toast(act,"设置保存失败，已保持原状态");
                    }
                    return which==0?!RedPacketRuntime.flag(path):RedPacketRuntime.flag(path);
                }
            }));
        }
        new AlertDialog.Builder(act).setTitle("红包提示与自动回复（高级版）").setView(box).setPositiveButton("完成",null)
            .setNeutralButton("编辑感言",new DialogInterface.OnClickListener() { public void onClick(DialogInterface d,int w) { templates(act); } })
            .setNegativeButton("领取记录",new DialogInterface.OnClickListener() { public void onClick(DialogInterface d,int w) { history(act); } }).show();
    }
    static boolean setFlag(String path,boolean enabled) {
        try {
            java.io.File file=new java.io.File(path);
            if(enabled) { file.getParentFile().mkdirs();return file.exists() || file.createNewFile(); }
            return !file.exists() || file.delete();
        } catch(Throwable failure) { return false; }
    }
    static void templates(final Activity act) {
        if(!requirePremium(act))return;
        final EditText input=new EditText(act);
        input.setSingleLine(false);input.setMinLines(5);input.setMaxLines(12);
        input.setGravity(android.view.Gravity.TOP);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setText(RedPacketRuntime.text());
        final AlertDialog dialog=new AlertDialog.Builder(act).setTitle("自动回复感言")
            .setMessage("每行一条，最多 50 条，每条最多 300 字。\n固定回复使用第一条；开启随机后从全部感言中选择。\n仅确认领取成功后发送到红包所在会话。")
            .setView(input).setNegativeButton("取消",null).setPositiveButton("保存",null).create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            public void onShow(DialogInterface ignored) {
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(new android.view.View.OnClickListener() {
                    public void onClick(android.view.View ignored) {
                        if(!requirePremium(act))return;
                        String value=input.getText().toString();int count=0;
                        for(String line:value.split("\\r?\\n"))if(!line.trim().isEmpty()) {
                            if(line.trim().length()>300) { input.setError("每条感言最多 300 字");return; }count++;
                        }
                        if(count==0 || count>50) { input.setError("请填写 1～50 条感言");return; }
                        StringBuilder normalized=new StringBuilder();
                        for(String line:RedPacketEngine.templates(value))normalized.append(line).append('\n');
                        if(!RedPacketRuntime.write(RedPacketRuntime.TEXT,normalized.toString())) { input.setError("保存失败，请重试");return; }
                        toast(act,"感言已保存");dialog.dismiss();
                    }
                });
            }
        });
        dialog.show();
    }
    static void history(Activity act) {
        if(!requirePremium(act))return;
        android.widget.TextView text=new android.widget.TextView(act);
        text.setText(RedPacketRuntime.history());text.setTextSize(14);text.setTextIsSelectable(true);
        int padding=(int)(16*act.getResources().getDisplayMetrics().density);
        text.setPadding(padding,padding,padding,padding);
        android.widget.ScrollView scroll=new android.widget.ScrollView(act);scroll.addView(text);
        new AlertDialog.Builder(act).setTitle("红包记录（最近 200 条）").setView(scroll).setPositiveButton("关闭",null).show();
    }
}
