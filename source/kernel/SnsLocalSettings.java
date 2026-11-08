package com.zafiro.wxentry.kernel;

import android.app.*;
import android.content.DialogInterface;
import android.widget.*;
import java.util.*;

final class SnsLocalSettings {
    static void style(AlertDialog dialog){int color=SettingsUi.dark(dialog.getContext())?0xFFB9C9E6:SettingsUi.BLUE;for(int which:new int[]{AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL}){Button button=dialog.getButton(which);if(button!=null)button.setTextColor(color);}}
    static void toast(Activity act,String text){if(act!=null)Toast.makeText(act,text,Toast.LENGTH_SHORT).show();}
    static boolean access(Activity act){if(Kernel.momentsPremiumEnabled())return true;SnsLocalSettings.toast(act,"朋友圈本地互动仅限高级版和 VIP 版");return false;}
    static boolean same(Activity act,String owner){if(owner!=null&&owner.equals(Kernel.receiptAccount()))return true;SnsLocalSettings.toast(act,"账号已切换，请重新打开设置");return false;}
    static boolean toggle(final Activity act,final SnsLocalData.Feature feature,final boolean on){
        if(access(act)){String owner=Kernel.receiptAccount();if(!SnsLocalStore.edit(owner,null,new SnsLocalStore.Edit(){public SnsLocalData apply(SnsLocalData before){return before.enabled(feature,on);}}))SnsLocalSettings.toast(act,"本地互动设置保存失败，请稍后重试");}
        return SnsLocalStore.enabled(feature);
    }
    static void presets(final Activity act){
        if(!access(act))return;final String owner=Kernel.receiptAccount();
        LinearLayout box=SettingsUi.column(act);TextView hint=SettingsUi.text(act,"每行一条预设，最多50条；添加伪评论时仍可分别编辑。仅用于本机展示。",13);box.addView(hint);
        final EditText input=new EditText(act);input.setMinLines(4);input.setMaxLines(10);input.setGravity(android.view.Gravity.TOP);input.setHint("例如：\n拍得真好看\n不错，支持一下");input.setText(android.text.TextUtils.join("\n",SnsLocalStore.load(owner).presets));box.addView(input);
        final AlertDialog dialog=new AlertDialog.Builder(act).setTitle("伪评论内容预设").setView(box).setNegativeButton("取消",null).setPositiveButton("保存",null).create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener(){public void onShow(DialogInterface ignored){dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new android.view.View.OnClickListener(){public void onClick(android.view.View view){
            if(!access(act)||!same(act,owner))return;final List<String> lines=new ArrayList<String>();
            for(String line:input.getText().toString().split("\\r?\\n")){line=line.trim();if(line.isEmpty())continue;if(line.length()>SnsLocalData.MAX_TEXT){input.setError("每条预设最多500字");return;}if(!lines.contains(line))lines.add(line);}
            if(lines.size()>SnsLocalData.MAX_PRESETS){input.setError("最多保存50条预设");return;}
            if(SnsLocalStore.edit(owner,null,new SnsLocalStore.Edit(){public SnsLocalData apply(SnsLocalData before){return before.presets(lines);}})){dialog.dismiss();SnsLocalSettings.toast(act,"评论预设已保存");}else input.setError("保存失败，请稍后重试");
        }});}});dialog.show();SnsLocalSettings.style(dialog);
    }
    static void clear(final Activity act){
        if(!access(act))return;final String owner=Kernel.receiptAccount();
        AlertDialog created=new AlertDialog.Builder(act).setTitle("清空本地互动").setMessage("清空本机保存的伪集赞和伪评论，保留正常的功能开关与评论预设。").setNegativeButton("取消",null).setPositiveButton("清空",new DialogInterface.OnClickListener(){public void onClick(DialogInterface dialog,int which){
            if(access(act)&&same(act,owner))SnsLocalSettings.toast(act,SnsLocalStore.reset(owner)?"本地互动已清空":"清空失败，请稍后重试");
        }}).show();SnsLocalSettings.style(created);
    }
}
