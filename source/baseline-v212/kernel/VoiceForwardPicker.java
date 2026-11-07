package com.zafiro.wxentry.kernel;

import android.app.*;
import android.content.DialogInterface;
import android.content.Context;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.lang.ref.WeakReference;

/** A module-owned chat-list selector. Confirmation creates exactly one sequential batch. */
final class VoiceForwardPicker {
    private static BatchSession active;
    private VoiceForwardPicker() {}
    private static boolean allowed(Activity activity,String owner) {
        try { return !activity.isFinishing() && !Kernel.MODULE_OFF && owner.equals(VoiceForwardSender.account()); }
        catch(Throwable failure) { return false; }
    }
    private static void notice(Activity activity,String text) {
        Toast.makeText(activity,text,Toast.LENGTH_LONG).show();
    }
    private static int dp(Activity activity,int value) {
        return Math.round(value*activity.getResources().getDisplayMetrics().density);
    }
    static void show(final Activity activity,final Object message,final String owner,final int duration) {
        if(showActive(activity))return;
        final ProgressDialog loading=new ProgressDialog(activity);
        loading.setMessage("正在读取聊天列表…");loading.setCancelable(true);loading.show();
        new Thread(new Runnable() { public void run() {
            List<VoiceForwardTargets.Target> targets=null;Throwable failure=null;
            try { targets=VoiceForwardTargets.load(); } catch(Throwable t) { failure=t; }
            final List<VoiceForwardTargets.Target> result=targets;final Throwable error=failure;
            activity.runOnUiThread(new Runnable() { public void run() {
                if(!loading.isShowing())return;
                loading.dismiss();
                if(!allowed(activity,owner))return;
                if(error!=null) { Kernel.T("ARVF list-error "+error.getClass().getSimpleName());notice(activity,"聊天列表读取失败，请稍后重试");return; }
                if(result==null || result.isEmpty()) { notice(activity,"聊天列表里暂无可选择的好友或群聊");return; }
                int groups=0;for(VoiceForwardTargets.Target target:result)if(target.group)groups++;
                Kernel.T("ARVF list-ready friends="+(result.size()-groups)+" groups="+groups);
                build(activity,message,owner,duration,result);
            }});
        }},"wxentry-voice-chats").start();
    }
    private static void build(final Activity activity,final Object message,final String owner,final int duration,
                              final List<VoiceForwardTargets.Target> targets) {
        final Set<String> selected=new LinkedHashSet<String>();
        final List<VoiceForwardTargets.Target> visible=new ArrayList<VoiceForwardTargets.Target>();
        final int[] kind={0};
        LinearLayout root=new LinearLayout(activity);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(activity,16),dp(activity,4),dp(activity,16),0);
        final EditText search=new EditText(activity);search.setSingleLine(true);search.setHint("搜索聊天名称");
        root.addView(search,new LinearLayout.LayoutParams(-1,dp(activity,48)));
        LinearLayout tabs=new LinearLayout(activity);
        final Button[] filters=new Button[3];
        String[] titles={"全部","好友","群聊"};
        for(int i=0;i<3;i++) {
            filters[i]=new Button(activity);filters[i].setText(titles[i]);
            tabs.addView(filters[i],new LinearLayout.LayoutParams(0,dp(activity,44),1));
        }
        root.addView(tabs);
        final TextView count=new TextView(activity);count.setPadding(0,dp(activity,8),0,dp(activity,8));root.addView(count);
        final ListView list=new ListView(activity);list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        final ArrayAdapter<String> adapter=new ArrayAdapter<String>(activity,android.R.layout.simple_list_item_multiple_choice,new ArrayList<String>());
        list.setAdapter(adapter);root.addView(list,new LinearLayout.LayoutParams(-1,dp(activity,300)));
        LinearLayout selection=new LinearLayout(activity);
        Button all=new Button(activity);all.setText("全选当前");
        Button clear=new Button(activity);clear.setText("清空已选");
        selection.addView(all,new LinearLayout.LayoutParams(0,dp(activity,44),1));
        selection.addView(clear,new LinearLayout.LayoutParams(0,dp(activity,44),1));root.addView(selection);
        final AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("转发语音")
            .setView(root).setPositiveButton("发送",null).setNegativeButton("取消",null).create();
        final Runnable updateCount=new Runnable(){public void run(){
            count.setText("已选 "+selected.size()+" 个 · 当前显示 "+visible.size()+" 个"
                +(selected.size()>5?"\n每条语音间隔随机 1～5 秒":""));
            Button send=dialog.getButton(DialogInterface.BUTTON_POSITIVE);
            if(send!=null) { send.setText("发送（"+selected.size()+"）");send.setEnabled(!selected.isEmpty()); }
        }};
        final Runnable refresh=new Runnable(){public void run(){
            visible.clear();visible.addAll(VoiceForwardTargets.filter(targets,kind[0],search.getText().toString()));
            adapter.setNotifyOnChange(false);adapter.clear();for(VoiceForwardTargets.Target target:visible)adapter.add(target.label());adapter.notifyDataSetChanged();
            list.clearChoices();for(int i=0;i<visible.size();i++)list.setItemChecked(i,selected.contains(visible.get(i).id));
            for(int i=0;i<filters.length;i++)filters[i].setEnabled(i!=kind[0]);
            updateCount.run();
        }};
        for(int i=0;i<3;i++) { final int value=i;filters[i].setOnClickListener(new View.OnClickListener(){public void onClick(View v){kind[0]=value;refresh.run();}}); }
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){refresh.run();}public void afterTextChanged(Editable text){} });
        list.setOnItemClickListener(new AdapterView.OnItemClickListener(){public void onItemClick(AdapterView<?> parent,View view,int position,long id){
            String recipient=visible.get(position).id;
            if(list.isItemChecked(position))selected.add(recipient);else selected.remove(recipient);
            updateCount.run();
        }});
        all.setOnClickListener(new View.OnClickListener(){public void onClick(View v){for(VoiceForwardTargets.Target t:visible)selected.add(t.id);refresh.run();}});
        clear.setOnClickListener(new View.OnClickListener(){public void onClick(View v){selected.clear();refresh.run();}});
        final AtomicBoolean confirmed=new AtomicBoolean();
        dialog.setOnShowListener(new DialogInterface.OnShowListener(){public void onShow(DialogInterface ignored){
            refresh.run();dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener(){public void onClick(View v){
                if(selected.isEmpty() || !allowed(activity,owner) || !confirmed.compareAndSet(false,true))return;
                dialog.dismiss();send(activity,message,owner,duration,new ArrayList<String>(selected));
            }});
        }});
        dialog.show();
        if(dialog.getWindow()!=null)dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }
    static boolean showActive(Activity activity) {
        BatchSession current=active;
        if(current==null || current.queue.isFinished())return false;
        current.showProgress(activity);return true;
    }
    private static void send(Activity activity,Object message,String owner,int duration,List<String> chosen) {
        if(showActive(activity))return;
        BatchSession session=new BatchSession(activity,message,owner,duration,chosen);
        active=session;session.showProgress(activity);session.queue.start();
    }
    private static final class BatchSession implements VoiceForwardQueue.Environment {
        final Context context;
        final Object message;
        final String owner;
        final int duration,total;
        final Handler main=new Handler(Looper.getMainLooper());
        final Random random=new Random();
        final VoiceForwardQueue queue;
        WeakReference<Activity> activity=new WeakReference<Activity>(null);
        WeakReference<ProgressDialog> progress=new WeakReference<ProgressDialog>(null);
        VoiceForwardQueue.State state;
        boolean background;
        String lastMessage,lastToast;
        long lastToastAt;
        Toast toast;
        BatchSession(Activity activity,Object message,String owner,int duration,List<String> chosen) {
            Context app=activity.getApplicationContext();context=app!=null?app:activity;
            this.message=message;this.owner=owner;this.duration=duration;total=chosen.size();
            queue=new VoiceForwardQueue(chosen,this);
        }
        public long now(){return android.os.SystemClock.elapsedRealtime();}
        public void post(Runnable action,long delay){main.postDelayed(action,delay);}
        public int randomDelaySeconds(){return random.nextInt(5)+1;}
        public boolean allowed(){
            try { return !Kernel.MODULE_OFF && owner.equals(VoiceForwardSender.account()); }
            catch(Throwable failure) { return false; }
        }
        public VoiceForwardQueue.Ticket send(String recipient)throws Throwable {
            Kernel.T("ARVF batch-submit");return VoiceForwardSender.sendTracked(message,recipient,duration);
        }
        private boolean valid(Activity value){return value!=null && !value.isFinishing() && !value.isDestroyed();}
        private String text(VoiceForwardQueue.State value) {
            if(value==null)return "正在发送，剩余 "+total+" 个";
            String text="已发送 "+value.sent+" / "+value.total+" 个\n剩余 "+value.remaining+" 个，失败 "+value.failed+" 个";
            if(value.delaySeconds>0)text+="\n下一条将在 "+value.delaySeconds+" 秒后发送";
            if(value.stopping && !value.finished)text+="\n已停止后续，正在等待当前语音结果";
            if(value.unknown>0)text+="\n另有 "+value.unknown+" 个发送结果暂未确认";
            return text;
        }
        void showProgress(Activity current) {
            if(!valid(current))return;
            ProgressDialog shown=progress.get();if(shown!=null && shown.isShowing())return;
            final ProgressDialog dialog=new ProgressDialog(current);dialog.setTitle("语音转发进度");dialog.setCancelable(true);
            dialog.setMessage(text(state));lastMessage=text(state);
            dialog.setButton(DialogInterface.BUTTON_POSITIVE,"后台发送",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){toBackground();}});
            dialog.setButton(DialogInterface.BUTTON_NEGATIVE,"停止后续发送",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){stopFromUi();}});
            dialog.setOnCancelListener(new DialogInterface.OnCancelListener(){public void onCancel(DialogInterface d){stopFromUi();}});
            activity=new WeakReference<Activity>(current);progress=new WeakReference<ProgressDialog>(dialog);background=false;
            dialog.show();
        }
        private void releaseDialog() {
            ProgressDialog shown=progress.get();if(shown!=null)try{shown.dismiss();}catch(Throwable ignored){}
            progress.clear();activity.clear();
        }
        private void toBackground() {
            background=true;releaseDialog();Kernel.T("ARVF batch-background");
            int remaining=state==null?total:state.remaining;
            brief("正在后台发送语音，剩余 "+remaining+" 个",true);
        }
        private void stopFromUi() {
            background=true;releaseDialog();queue.stop();
            if(!queue.isFinished())brief("已停止后续发送，正在等待当前结果",true);
        }
        private void brief(String message,boolean force) {
            if(!force && (message.equals(lastToast) || now()-lastToastAt<1800))return;
            lastToast=message;lastToastAt=now();
            if(toast!=null)toast.cancel();toast=Toast.makeText(context,message,Toast.LENGTH_SHORT);toast.show();
        }
        public void changed(VoiceForwardQueue.State value) {
            state=value;String message=text(value);
            if(value.finished) {
                if(active==this)active=null;
                Activity current=activity.get();boolean showResult=!background && valid(current);
                releaseDialog();
                Kernel.T("ARVF batch-finished total="+value.total+" sent="+value.sent+" failed="+value.failed+" remaining="+value.remaining+" unknown="+value.unknown);
                if(showResult)new AlertDialog.Builder(current).setTitle(value.stopping?"转发已停止":"转发完成").setMessage(message).setPositiveButton("关闭",null).show();
                else brief((value.stopping?"转发已停止":"转发完成")+"：已发送 "+value.sent+" 个，剩余 "+value.remaining+" 个，失败 "+value.failed+" 个",true);
                return;
            }
            ProgressDialog shown=progress.get();
            if(!background && (!valid(activity.get()) || shown==null || !shown.isShowing())) { background=true;releaseDialog(); }
            if(background)brief((value.stopping?"已停止后续":"正在后台发送语音")+"，剩余 "+value.remaining+" 个",false);
            else if(!message.equals(lastMessage)) { lastMessage=message;shown.setMessage(message); }
            Kernel.T("ARVF batch-progress sent="+value.sent+" remaining="+value.remaining+" failed="+value.failed+" delay="+value.delaySeconds);
        }
    }
}
