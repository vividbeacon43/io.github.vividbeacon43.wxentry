package com.zafiro.wxentry;
import android.app.*;
import android.content.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;

final class TabletLoginEntry {
    static final String TAG="wxentry.experimental.login";
    static boolean loginPage(String name){
        if("com.tencent.mm.plugin.login_exdevice.ui.LoginAsExDeviceUI".equals(name))return true;
        if(!name.startsWith("com.tencent.mm.plugin.account.ui."))return false;
        String simple=name.substring(name.lastIndexOf('.')+1);
        return simple.equals("WelcomeActivity")||simple.equals("MobileInputUI")||simple.equals("LoginUI")||simple.equals("LoginHistoryUI")||simple.equals("LoginSelectUI")||simple.equals("LoginPasswordUI")||simple.equals("LoginSmsUI")||simple.equals("SimpleLoginUI");
    }
    static void resumed(final Activity act){
        if(!loginPage(act.getClass().getName()))return;
        if(!TabletModeHooks.refreshAuthorization())return;
        TabletModeHooks.bind(act);
        final View decor=act.getWindow().getDecorView();decor.post(new Runnable(){public void run(){
            if(act.isFinishing()||act.isDestroyed()||decor.findViewWithTag(TAG)!=null)return;
            try{
                // Use the native account toolbar when available; repeated IDs replace, rather than duplicate, entries.
                java.lang.reflect.Method menu=act.getClass().getMethod("addTextOptionMenu",int.class,String.class,MenuItem.OnMenuItemClickListener.class);
                menu.invoke(act,0x574558,"实验功能",new MenuItem.OnMenuItemClickListener(){public boolean onMenuItemClick(MenuItem item){show(act);return true;}});
                Main.log("tablet login settings entry attached "+act.getClass().getSimpleName());
            }catch(Throwable absent){
                View root=act.findViewById(android.R.id.content);if(!(root instanceof FrameLayout))return;
                TextView entry=ExperimentalUi.text(act,"实验功能",13);entry.setTag(TAG);entry.setTextColor(ExperimentalUi.BLUE);
                int pad=ExperimentalUi.dp(act,10);entry.setPadding(pad,pad,pad,pad);
                GradientDrawable bg=new GradientDrawable();bg.setColor(ExperimentalUi.dark(act)?0xFF263243:0xFFF0F3F8);bg.setCornerRadius(pad);entry.setBackground(bg);
                int status=0;int dimension=act.getResources().getIdentifier("status_bar_height","dimen","android");if(dimension!=0)status=act.getResources().getDimensionPixelSize(dimension);
                int[] location=new int[2];root.getLocationOnScreen(location);
                FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.LEFT);lp.topMargin=ExperimentalUi.dp(act,12)+Math.max(0,status-location[1]);lp.leftMargin=ExperimentalUi.dp(act,12);
                entry.setOnClickListener(new View.OnClickListener(){public void onClick(View v){show(act);}});((FrameLayout)root).addView(entry,lp);
            }
        }});
    }
    static boolean set(Activity act,boolean on){
        if(on&&!TabletModeHooks.refreshAuthorization()){
            Toast.makeText(act,"平板模式仅限高级版和 VIP 版，请先激活并下载对应内核",Toast.LENGTH_LONG).show();return false;
        }
        try{
            if(TabletModeHooks.store==null)throw new IllegalStateException("Not initialized");TabletModeHooks.store.write(on);
            Toast.makeText(act,(on?"已开启":"已关闭")+"平板模式，重启微信后生效",Toast.LENGTH_LONG).show();return on;
        }catch(Exception failure){Toast.makeText(act,"平板模式保存失败，已保持原设置",Toast.LENGTH_LONG).show();return TabletModeHooks.configured();}
    }
    static void show(final Activity act){
        if(!TabletModeHooks.refreshAuthorization()){Toast.makeText(act,"平板模式仅限高级版和 VIP 版",Toast.LENGTH_LONG).show();return;}
        LinearLayout box=ExperimentalUi.column(act);
        box.addView(ExperimentalUi.toggle(act,"平板模式","重启微信后生效，登录页支持平板扫码登录",TabletModeHooks.configured(),new ExperimentalUi.Change(){public boolean set(boolean on){return TabletLoginEntry.set(act,on);}}));
        TextView note=ExperimentalUi.text(act,"扫码后需在另一台已登录设备上确认。",13);box.addView(note);
        Button qr=new Button(act);qr.setText("平板扫码登录");qr.setOnClickListener(new View.OnClickListener(){public void onClick(View v){openQr(act);}});box.addView(qr);
        new AlertDialog.Builder(act).setTitle("实验功能").setView(box).setNegativeButton("关闭",null).setPositiveButton("重启微信",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){restart(act);}}).show();
    }
    static void openQr(Activity act){
        if(!TabletModeHooks.refreshAuthorization()){Toast.makeText(act,"平板扫码登录仅限高级版和 VIP 版",Toast.LENGTH_LONG).show();return;}
        if(!TabletModeHooks.configured()){Toast.makeText(act,"请先开启平板模式并重启微信",Toast.LENGTH_LONG).show();return;}
        TabletModeHooks.bind(act);
        if(!TabletModeHooks.active){Toast.makeText(act,"请重启微信后使用平板扫码登录；当前微信需为 8.0.80",Toast.LENGTH_LONG).show();return;}
        try{act.startActivity(new Intent().setClassName("com.tencent.mm","com.tencent.mm.plugin.login_exdevice.ui.LoginAsExDeviceUI"));Main.log("tablet native QR page opened");}
        catch(Throwable failure){Main.log("tablet QR page unavailable "+failure.getClass().getSimpleName());Toast.makeText(act,"当前微信无法打开平板扫码登录",Toast.LENGTH_LONG).show();}
    }
    static void restart(final Activity act){
        Main.log("tablet restart requested");
        try{
            final Intent launch=new Intent().setClassName("com.tencent.mm","com.tencent.mm.ui.LauncherUI").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
            final PendingIntent pending=PendingIntent.getActivity(act,0x575450,launch,PendingIntent.FLAG_CANCEL_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            final AlarmManager alarms=(AlarmManager)act.getSystemService(Context.ALARM_SERVICE);
            if(alarms==null)throw new IllegalStateException("Alarm service unavailable");
            WechatRestartPlan.request(new WechatRestartPlan.Host(){
                public void schedule(boolean exact)throws Throwable{
                    long at=System.currentTimeMillis()+1200;
                    if(exact)alarms.setExact(AlarmManager.RTC,at,pending);else alarms.set(AlarmManager.RTC,at,pending);
                    Main.log("tablet restart scheduled strategy="+(exact?"exact":"fallback"));
                }
                public void terminate(){
                    Toast.makeText(act,"正在重启微信…",Toast.LENGTH_SHORT).show();
                    new Thread(new Runnable(){public void run(){try{Thread.sleep(350);}catch(InterruptedException ignored){}Main.log("tablet restart terminating current process");android.os.Process.killProcess(android.os.Process.myPid());System.exit(0);}},"wxentry-tablet-restart").start();
                }
                public void failed(Throwable error){Main.log("tablet restart failed "+error.getClass().getSimpleName());Toast.makeText(act,"无法自动重启，请关闭并重新打开微信",Toast.LENGTH_LONG).show();}
            });
        }catch(Throwable failure){Main.log("tablet restart setup failed "+failure.getClass().getSimpleName());Toast.makeText(act,"无法自动重启，请关闭并重新打开微信",Toast.LENGTH_LONG).show();}
    }
}
