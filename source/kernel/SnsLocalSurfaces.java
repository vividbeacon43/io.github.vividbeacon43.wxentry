package com.zafiro.wxentry.kernel;

import android.app.*;
import android.content.*;
import android.database.DataSetObserver;
import android.os.Looper;
import android.text.*;
import android.text.style.*;
import android.view.*;
import android.widget.*;
import de.robv.android.xposed.*;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.*;

/** Mirrors private interactions into album, detail and history views. Host models remain untouched. */
final class SnsLocalSurfaces {
    static final String DETAIL="com.tencent.mm.plugin.sns.ui.SnsCommentDetailUI",MSG="com.tencent.mm.plugin.sns.ui.SnsMsgUI",ALBUM="com.tencent.mm.plugin.sns.ui.SnsUserUI";
    static final int WRAP=0x57585331;static boolean ready;static Class<?> messageType,messageAdapterType;
    static final Map<Activity,WeakReference<Surface>> screens=new WeakHashMap<Activity,WeakReference<Surface>>();
    static final Map<Activity,Boolean> albums=new WeakHashMap<Activity,Boolean>();
    static final class Meta {final String key,local,text;final long published;Meta(String key,Object info)throws Throwable{this.key=key;published=((Number)XposedHelpers.callMethod(info,"getCreateTime")).longValue()*1000L;local=(String)XposedHelpers.callMethod(info,"getLocalid");Object timeline=XposedHelpers.callMethod(info,"getTimeLine");Object value=XposedHelpers.getObjectField(timeline,"ContentDesc");text=value instanceof String?(String)value:"";}}
    static boolean isMessage(Activity act){return messageType!=null&&messageType.isInstance(act);}
    static boolean access(String owner){return Kernel.momentsPremiumEnabled()&&owner!=null&&owner.equals(Kernel.receiptAccount());}
    static Object store()throws Throwable{return XposedHelpers.callStaticMethod(SnsLocalNative.cls("com.tencent.mm.plugin.sns.model.p4"),"mk");}
    static Object lookup(String key)throws Throwable{int at=key.indexOf('~');Object info=XposedHelpers.callMethod(store(),"V1",new java.math.BigInteger(key.substring(at+1),16).longValue());return key.equals(SnsLocalNative.key(info))?info:null;}
    static Object social(Object info)throws Throwable{return XposedHelpers.callStaticMethod(SnsLocalNative.cls("com.tencent.mm.plugin.sns.model.w5"),"f",info);}
    static Object detailInfo(Activity act)throws Throwable{String local=(String)XposedHelpers.getObjectField(act,"H");if(local!=null&&!local.isEmpty())return XposedHelpers.callStaticMethod(SnsLocalNative.cls("com.tencent.mm.plugin.sns.storage.l1"),"b",local);String id=(String)XposedHelpers.getObjectField(act,"G");return id==null?null:XposedHelpers.callMethod(store(),"X1",id);}
    static Set<String> genuine(Object info){Set<String> out=new HashSet<String>();try{Object list=XposedHelpers.getObjectField(social(info),"LikeUserList");if(list instanceof List)for(Object p:(List<?>)list){Object id=XposedHelpers.getObjectField(p,"d");if(id instanceof String)out.add((String)id);}}catch(Throwable ignored){}return out;}
    static void start()throws Throwable{
        if(ready)return;
        Class<?> detail=SnsLocalNative.cls(DETAIL),msg=SnsLocalNative.cls(MSG),album=SnsLocalNative.cls(ALBUM),rq=SnsLocalNative.cls("com.tencent.mm.plugin.sns.ui.rq");
        messageType=msg;messageAdapterType=SnsLocalNative.cls("com.tencent.mm.plugin.sns.ui.yn");
        detail.getDeclaredMethod("m8",boolean.class);detail.getDeclaredField("s");msg.getDeclaredField("d");msg.getDeclaredField("n");rq.getDeclaredMethod("getItem",int.class);
        SnsLocalNative.cls("com.tencent.mm.plugin.sns.model.w5").getDeclaredMethod("f",SnsLocalNative.infoType);
        SnsLocalNative.cls("com.tencent.mm.plugin.sns.storage.l1").getDeclaredMethod("b",String.class);
        SnsLocalNative.cls("com.tencent.mm.plugin.sns.ui.y0").getDeclaredMethod("setLineMaxCounte",int.class);
        for(Class<?> cls:new Class<?>[]{detail,msg}){
            XposedBridge.hookMethod(cls.getDeclaredMethod("initView"),new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam p){resumed((Activity)p.thisObject);}});
            XposedBridge.hookMethod(cls.getDeclaredMethod("onDestroy"),new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){WeakReference<Surface> ref=screens.remove((Activity)p.thisObject);Surface surface=ref==null?null:ref.get();if(surface!=null)surface.close();}});
        }
        XposedBridge.hookMethod(ListView.class.getDeclaredMethod("setAdapter",ListAdapter.class),new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){
            if(p.args[0]==null||p.args[0] instanceof Surface)return;ListView list=(ListView)p.thisObject;Activity act=SnsLocalNative.activity(list.getContext());if(act==null)return;
            String name=act.getClass().getName();boolean messages=isMessage(act);boolean matches=messages?messageAdapterType.isInstance(p.args[0]):name.equals(DETAIL)&&p.args[0].getClass().getName().equals("com.tencent.mm.plugin.sns.ui.SnsCommentDetailUI$CommentListAdapter");
            if(!matches)return;Surface surface=new Surface(act,list,(ListAdapter)p.args[0],messages);WeakReference<Surface> old=screens.put(act,new WeakReference<Surface>(surface));if(old!=null&&old.get()!=null)old.get().close();p.args[0]=surface;surface.request();
        }});
        XposedBridge.hookMethod(rq.getDeclaredMethod("getView",int.class,View.class,ViewGroup.class),new XC_MethodHook(){
            protected void beforeHookedMethod(MethodHookParam p){if(p.args[1] instanceof View){Object nativeView=((View)p.args[1]).getTag(WRAP);if(nativeView instanceof View){View view=(View)nativeView;if(view.getParent() instanceof ViewGroup)((ViewGroup)view.getParent()).removeView(view);p.args[1]=view;}}}
            protected void afterHookedMethod(MethodHookParam p){if(p.hasThrowable()||!(p.getResult() instanceof View))return;try{p.setResult(albumRow((View)p.getResult()));}catch(Throwable failure){SnsLocalRuntime.log("album-error",failure);}}
        });
        XposedBridge.hookMethod(detail.getDeclaredMethod("O8",List.class,boolean.class),new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam p){WeakReference<Surface> ref=screens.get((Activity)p.thisObject);Surface surface=ref==null?null:ref.get();if(surface!=null)surface.request();}});
        ready=true;Kernel.T("ARSNSLOCAL surfaces-ready album+detail+messages localOnly=true");
    }
    static void resumed(final Activity act){
        if(!ready||act==null)return;String name=act.getClass().getName();if(!name.equals(DETAIL)&&!isMessage(act)&&!name.equals(ALBUM))return;
        SnsLocalRuntime.main.post(new Runnable(){public void run(){try{
            if(act.isFinishing()||act.isDestroyed())return;
            if(act.getClass().getName().equals(ALBUM)){albums.put(act,true);refreshAlbum(act);return;}
            ListView list=(ListView)XposedHelpers.getObjectField(act,isMessage(act)?"d":"s");if(list==null)return;
            ListAdapter adapter=list.getAdapter();if(adapter instanceof HeaderViewListAdapter)adapter=((HeaderViewListAdapter)adapter).getWrappedAdapter();
            if(adapter!=null&&!(adapter instanceof Surface))list.setAdapter(adapter);
            WeakReference<Surface> ref=screens.get(act);Surface surface=ref==null?null:ref.get();if(surface!=null){surface.listeners();surface.request();}
        }catch(Throwable failure){SnsLocalRuntime.log("surface-open-error",failure);}}});
    }
    static void refresh(){if(!ready)return;for(WeakReference<Surface> ref:new ArrayList<WeakReference<Surface>>(screens.values())){Surface s=ref.get();if(s!=null)s.request();}for(Activity a:new ArrayList<Activity>(albums.keySet()))if(a!=null)refreshAlbum(a);}
    static void refreshAlbum(Activity act){try{Object adapter=XposedHelpers.getObjectField(act,"d");if(adapter instanceof BaseAdapter)((BaseAdapter)adapter).notifyDataSetChanged();}catch(Throwable ignored){}}
    static void open(Activity act,String owner,Meta meta){if(!access(owner)||act==null||act.isFinishing()||act.isDestroyed())return;try{Object info=lookup(meta.key);if(info==null){SnsLocalSettings.toast(act,"这条朋友圈已不在本机，请刷新后重试");return;}Intent intent=new Intent();intent.setClassName(act,DETAIL);intent.putExtra("INTENT_SNS_LOCAL_ID",(String)XposedHelpers.callMethod(info,"getLocalid"));intent.putExtra("INTENT_FROMSUI",true);act.startActivity(intent);}catch(Throwable failure){SnsLocalSettings.toast(act,"暂时无法打开这条朋友圈");}}
    static void targets(View view,Set<String> keys,int[] budget)throws Throwable{
        if(--budget[0]<0||keys.size()>=16)return;Object tag=view.getTag();if(tag!=null&&tag.getClass().getName().equals("xj4.v")){int id=((Number)XposedHelpers.getObjectField(tag,"b")).intValue();if(id>0){Object info=XposedHelpers.callMethod(store(),"I2","sns_table_"+id);String key=SnsLocalNative.key(info);if(key!=null)keys.add(key);}}
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)targets(((ViewGroup)view).getChildAt(i),keys,budget);
    }
    static View albumRow(View nativeView)throws Throwable{
        final Activity act=SnsLocalNative.activity(nativeView.getContext());if(act==null||!act.getClass().getName().equals(ALBUM)||!Kernel.momentsPremiumEnabled())return nativeView;final String owner=Kernel.receiptAccount();SnsLocalData data=SnsLocalStore.load(owner);if((!data.likesOn&&!data.commentsOn)||data.posts.isEmpty())return nativeView;
        Set<String> keys=new LinkedHashSet<String>();targets(nativeView,keys,new int[]{160});LinearLayout wrapper=null;
        for(String key:keys){SnsLocalData.Post post=data.posts.get(key);if(post==null||!SnsLocalProjection.own(owner,key))continue;Object info=lookup(key);if(info==null)continue;Set<String> real=genuine(info);int extra=0;if(data.likesOn)for(SnsLocalData.Person person:post.likes)if(!real.contains(person.id))extra++;int comments=data.commentsOn?post.comments.size():0;if(extra==0&&comments==0)continue;
            int likes=real.size(),nativeComments=0;try{Object sns=social(info);likes=Math.max(likes,((Number)XposedHelpers.getObjectField(sns,"LikeCount")).intValue());nativeComments=((Number)XposedHelpers.getObjectField(sns,"CommentCount")).intValue();}catch(Throwable ignored){}
            if(wrapper==null){wrapper=new LinearLayout(act);wrapper.setOrientation(LinearLayout.VERTICAL);wrapper.setTag(WRAP,nativeView);wrapper.setTag(nativeView.getTag());wrapper.addView(nativeView,new LinearLayout.LayoutParams(-1,-2));}
            final Meta meta=new Meta(key,info);TextView line=SettingsUi.text(act,"♡ "+(likes+extra)+"    评论 "+(nativeComments+comments),12);line.setTextColor(SnsLocalNative.linkColor(act));line.setPadding(SettingsUi.dp(act,80),SettingsUi.dp(act,4),SettingsUi.dp(act,12),SettingsUi.dp(act,8));line.setOnClickListener(new View.OnClickListener(){public void onClick(View v){open(act,owner,meta);}});wrapper.addView(line);
        }
        return wrapper==null?nativeView:wrapper;
    }
    static final class Surface extends BaseAdapter {
        final WeakReference<Activity> activity;final WeakReference<ListView> view;final ListAdapter nativeAdapter;final boolean messages;final String owner;
        List<SnsLocalProjection.Entry> entries=Collections.emptyList();List<SnsLocalData.Person> likes=Collections.emptyList();List<SnsLocalData.Comment> comments=Collections.emptyList();Map<String,Meta> metas=Collections.emptyMap();Map<String,Long> times=Collections.emptyMap();String key="";int[] order=new int[0];int orderNative=-1;List<SnsLocalProjection.Entry> orderEntries;int limit=100,total;long request;boolean closed,queued,applied,merged;final List<WeakReference<View>> additions=new ArrayList<WeakReference<View>>();WeakReference<ViewGroup> mergedFlow=new WeakReference<ViewGroup>(null);WeakReference<View> originalLine=new WeakReference<View>(null);int originalRows,originalCount;AdapterView.OnItemClickListener click;AdapterView.OnItemLongClickListener longClick;AbsListView.OnScrollListener scroll;boolean scrollBound;
        final DataSetObserver observer=new DataSetObserver(){public void onChanged(){Surface.this.request();Surface.this.notifyDataSetChanged();}public void onInvalidated(){Surface.this.request();Surface.this.notifyDataSetChanged();}};
        Surface(Activity act,ListView list,ListAdapter nativeAdapter,boolean messages){activity=new WeakReference<Activity>(act);view=new WeakReference<ListView>(list);this.nativeAdapter=nativeAdapter;this.messages=messages;owner=Kernel.receiptAccount();nativeAdapter.registerDataSetObserver(observer);}
        boolean allowed(){return !closed&&access(owner)&&activity.get()!=null;}
        int prefix(){return !allowed()?0:messages?entries.size()+(entries.size()<total?1:0):likes.isEmpty()||merged?0:1;}
        int suffix(){return allowed()&&!messages?comments.size():0;}
        SnsLocalProjection.Layout layout(){return new SnsLocalProjection.Layout(prefix(),nativeAdapter.getCount(),suffix());}
        void order(){if(!messages||!allowed())return;int count=nativeAdapter.getCount();if(orderNative==count&&orderEntries==entries&&order.length==count+prefix())return;long[] genuine=new long[count];for(int i=0;i<count;i++)try{Object item=nativeAdapter.getItem(i);genuine[i]=((Number)XposedHelpers.getObjectField(item,"field_createTime")).longValue()*1000L;}catch(Throwable unavailable){genuine[i]=Long.MAX_VALUE;}order=SnsLocalProjection.merge(genuine,entries,entries.size()<total);orderNative=count;orderEntries=entries;}
        int nativePosition(int position){if(!messages||!allowed())return layout().nativePosition(position);order();return position>=0&&position<order.length&&order[position]>=0?order[position]:-1;}
        int localPosition(int position){order();return position>=0&&position<order.length&&order[position]<0?-1-order[position]:-1;}
        int delegated(int position){if(position<0)return position;int n=nativePosition(position);return n>=0?n:position>=getCount()?position-prefix()-suffix():-1;}
        int nativeBefore(int position){order();int count=0;for(int i=0;i<Math.min(position,order.length);i++)if(order[i]>=0)count++;return count;}
        public int getCount(){return layout().count();}public boolean isEmpty(){return getCount()==0;}public boolean hasStableIds(){return false;}
        public int getViewTypeCount(){return nativeAdapter.getViewTypeCount()+1;}
        public int getItemViewType(int position){int n=nativePosition(position);return n>=0?nativeAdapter.getItemViewType(n):nativeAdapter.getViewTypeCount();}
        public boolean areAllItemsEnabled(){return false;}public boolean isEnabled(int position){int n=nativePosition(position);return n<0||nativeAdapter.isEnabled(n);}
        public Object getItem(int position){int n=nativePosition(position);return n>=0?nativeAdapter.getItem(n):null;}
        public long getItemId(int position){int n=nativePosition(position);return n>=0?nativeAdapter.getItemId(n):Long.MIN_VALUE+position;}
        public View getView(int position,View convert,ViewGroup parent){int n=nativePosition(position);if(n>=0)return nativeAdapter.getView(n,convert,parent);Activity act=activity.get();if(act==null)return new View(parent.getContext());try{
            if(messages){int index=localPosition(position);if(index>=entries.size()){TextView more=SettingsUi.text(act,"加载更多互动",14);more.setPadding(30,24,30,24);more.setGravity(Gravity.CENTER);more.setOnClickListener(v->{limit+=100;request();});return more;}return messageRow(act,entries.get(index));}
            return position<prefix()?likeRow(act):commentRow(act,comments.get(position-prefix()-nativeAdapter.getCount()));
        }catch(Throwable failure){SnsLocalRuntime.log("surface-row-error",failure);return new View(parent.getContext());}}
        void clearAdded(){for(WeakReference<View> ref:additions){View v=ref.get();if(v!=null&&SnsLocalRuntime.OWN_TAG.equals(v.getTag())&&v.getParent() instanceof ViewGroup)((ViewGroup)v.getParent()).removeView(v);}additions.clear();ViewGroup flow=mergedFlow.get();if(flow!=null)try{while(flow.getChildCount()>originalRows)flow.removeViewAt(flow.getChildCount()-1);XposedHelpers.setIntField(flow,"d",originalCount);XposedHelpers.setObjectField(flow,"g",originalLine.get());}catch(Throwable ignored){}mergedFlow=new WeakReference<ViewGroup>(null);originalLine=new WeakReference<View>(null);merged=false;}
        void close(){closed=true;request++;clearAdded();try{nativeAdapter.unregisterDataSetObserver(observer);}catch(Throwable ignored){}}
        void mergeLikes(){clearAdded();Activity act=activity.get();if(messages||!allowed()||likes.isEmpty()||act==null)return;try{Object info=detailInfo(act);if(genuine(info).isEmpty())return;ViewGroup host=(ViewGroup)XposedHelpers.getObjectField(act,"o");if(host==null||host.getVisibility()!=View.VISIBLE)return;ViewGroup flow=findFlow(host);if(flow==null||flow.getChildCount()==0)return;View line=flow.getChildAt(0);if(!(line instanceof ViewGroup)||((ViewGroup)line).getChildCount()==0)return;View sample=((ViewGroup)line).getChildAt(0);ViewGroup.LayoutParams source=sample.getLayoutParams();int count=Math.min(limit,likes.size());mergedFlow=new WeakReference<ViewGroup>(flow);originalRows=flow.getChildCount();originalCount=XposedHelpers.getIntField(flow,"d");originalLine=new WeakReference<View>((View)XposedHelpers.getObjectField(flow,"g"));
            for(int i=0;i<count;i++){final SnsLocalData.Person person=likes.get(i);ImageView image=new ImageView(act);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setContentDescription(person.name+"的头像");SnsLocalNative.avatar(image,person.id);LinearLayout.LayoutParams params=source instanceof ViewGroup.MarginLayoutParams?new LinearLayout.LayoutParams((ViewGroup.MarginLayoutParams)source):new LinearLayout.LayoutParams(source.width,source.height);image.setLayoutParams(params);image.setTag(SnsLocalRuntime.OWN_TAG);image.setOnClickListener(v->profile(person,SnsLocalData.Feature.LIKES));flow.addView(image);additions.add(new WeakReference<View>(image));}
            if(count<likes.size()){TextView more=SettingsUi.text(act,"更多",12);more.setTag(SnsLocalRuntime.OWN_TAG);more.setOnClickListener(v->{limit+=100;mergeLikes();notifyDataSetChanged();});more.setLayoutParams(new LinearLayout.LayoutParams(SettingsUi.dp(act,40),SettingsUi.dp(act,32)));flow.addView(more);additions.add(new WeakReference<View>(more));}merged=true;flow.requestLayout();
        }catch(Throwable failure){clearAdded();SnsLocalRuntime.log("detail-likes-error",failure);}}
        ViewGroup findFlow(ViewGroup host){if(host.getClass().getName().equals("com.tencent.mm.plugin.sns.ui.y0"))return host;for(int i=0;i<host.getChildCount();i++)if(host.getChildAt(i) instanceof ViewGroup){ViewGroup found=findFlow((ViewGroup)host.getChildAt(i));if(found!=null)return found;}return null;}
        void request(){if(Looper.myLooper()!=Looper.getMainLooper()){SnsLocalRuntime.main.post(()->request());return;}if(closed)return;
            if(!allowed()){clearAdded();entries=Collections.emptyList();likes=Collections.emptyList();comments=Collections.emptyList();metas=Collections.emptyMap();total=0;notifyDataSetChanged();empty();return;}
            if(queued){request++;return;}queued=true;final long generation=++request,revision=SnsLocalStore.revision();final SnsLocalData data=SnsLocalStore.load(owner);final Activity act=activity.get();
            Object found=null;try{if(!messages&&act!=null)found=detailInfo(act);}catch(Throwable ignored){}final Object detailInfo=found;final int pageLimit=limit;
            SnsLocalRuntime.io.execute(()->{final Map<String,Meta> next=new LinkedHashMap<String,Meta>();final List<SnsLocalData.Person> nextLikes=new ArrayList<SnsLocalData.Person>();final List<SnsLocalData.Comment> nextComments=new ArrayList<SnsLocalData.Comment>();String nextKey="";SnsLocalProjection.Page page=null;
                try{if(access(owner)){if(messages){for(SnsLocalData.Post p:data.posts.values())if(SnsLocalProjection.own(owner,p.key)){Object info=lookup(p.key);if(info!=null)next.put(p.key,new Meta(p.key,info));}}else{nextKey=SnsLocalNative.key(detailInfo);if(nextKey!=null){next.put(nextKey,new Meta(nextKey,detailInfo));SnsLocalData.Post post=data.posts.get(nextKey);if(post!=null){Set<String> real=genuine(detailInfo);if(data.likesOn)for(SnsLocalData.Person person:post.likes)if(!real.contains(person.id))nextLikes.add(person);if(data.commentsOn)nextComments.addAll(post.comments);}}}}}catch(Throwable failure){SnsLocalRuntime.log("surface-data-error",failure);}
                final Map<String,Long> published=new HashMap<String,Long>();for(Meta meta:next.values())if(meta.published>0)published.put(meta.key,meta.published);final Map<String,Long> nextTimes=SnsLocalTimesStore.resolve(owner,data,published);if(messages)page=SnsLocalProjection.messages(owner,data,next.keySet(),pageLimit,nextTimes);final String resolved=nextKey;final SnsLocalProjection.Page result=page;
                SnsLocalRuntime.main.post(()->{queued=false;if(closed)return;if(generation!=request||revision!=SnsLocalStore.revision()){request();return;}if(!allowed()){request();return;}metas=next;times=nextTimes;key=resolved;likes=nextLikes;comments=nextComments;entries=result==null?Collections.emptyList():result.entries;total=result==null?0:result.total;mergeLikes();notifyDataSetChanged();empty();Kernel.T("ARSNSLOCAL surface-rendered page="+(messages?"messages":"detail")+" likes="+likes.size()+" comments="+comments.size()+" messages="+entries.size()+" stored="+data.posts.size()+" owned="+data.posts.keySet().stream().filter(k->SnsLocalProjection.own(owner,k)).count()+" available="+next.size()+" likesOn="+data.likesOn+" commentsOn="+data.commentsOn+" type="+(activity.get()==null?"gone":activity.get().getClass().getSimpleName()));});
            });
        }
        void empty(){if(!messages)return;Activity act=activity.get();ListView list=view.get();if(act==null||list==null)return;try{View empty=(View)XposedHelpers.getObjectField(act,"n");if(prefix()>0){list.setVisibility(View.VISIBLE);if(empty!=null)empty.setVisibility(View.GONE);applied=true;}else if(applied){boolean has=nativeAdapter.getCount()>0;list.setVisibility(has?View.VISIBLE:View.GONE);if(empty!=null)empty.setVisibility(has?View.GONE:View.VISIBLE);applied=false;}}catch(Throwable ignored){}}
        void listeners(){final ListView list=view.get();if(list==null)return;
            AdapterView.OnItemClickListener current=list.getOnItemClickListener();if(current!=null&&current!=clickProxy){click=current;list.setOnItemClickListener(clickProxy);}
            AdapterView.OnItemLongClickListener currentLong=list.getOnItemLongClickListener();if(currentLong!=null&&currentLong!=longProxy){longClick=currentLong;list.setOnItemLongClickListener(longProxy);}
            if(messages&&!scrollBound)try{scroll=(AbsListView.OnScrollListener)XposedHelpers.getObjectField(list,"mOnScrollListener");if(scroll!=null){scrollBound=true;list.setOnScrollListener(new AbsListView.OnScrollListener(){public void onScrollStateChanged(AbsListView v,int state){scroll.onScrollStateChanged(v,state);}public void onScroll(AbsListView v,int first,int visible,int count){SnsLocalProjection.Layout map=layout();int start=nativeBefore(first),end=nativeBefore(first+visible);scroll.onScroll(v,start,end-start,Math.max(0,count-map.before-map.after));}});}}catch(Throwable ignored){}
        }
        final AdapterView.OnItemClickListener clickProxy=new AdapterView.OnItemClickListener(){public void onItemClick(AdapterView<?> parent,View child,int position,long id){ListView list=view.get();if(list==null)return;int headers=list.getHeaderViewsCount(),p=position-headers;SnsLocalProjection.Layout map=layout();int mapped=delegated(p);if(mapped<0){if(messages&&localPosition(p)>=0&&localPosition(p)<entries.size()){SnsLocalProjection.Entry entry=entries.get(localPosition(p));Meta meta=metas.get(entry.key);if(meta!=null)open(activity.get(),owner,meta);}return;}if(click!=null)click.onItemClick(parent,child,mapped+headers,id);}};
        final AdapterView.OnItemLongClickListener longProxy=new AdapterView.OnItemLongClickListener(){public boolean onItemLongClick(AdapterView<?> parent,View child,int position,long id){ListView list=view.get();if(list==null)return true;int headers=list.getHeaderViewsCount();int mapped=delegated(position-headers);if(mapped<0)return true;return longClick!=null&&longClick.onItemLongClick(parent,child,mapped+headers,id);}};
        void profile(SnsLocalData.Person person,SnsLocalData.Feature feature){if(allowed()&&SnsLocalStore.enabled(feature))SnsLocalNative.profile(activity.get(),owner,person.id);}
        View likeRow(Activity act)throws Throwable{
            LinearLayout row=new LinearLayout(act);row.setPadding(SettingsUi.dp(act,16),SettingsUi.dp(act,8),SettingsUi.dp(act,16),SettingsUi.dp(act,8));TextView heart=SettingsUi.text(act," ",18);SpannableString text=new SpannableString(" ");text.setSpan(SnsLocalNative.heart(act),0,1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);heart.setText(text);row.addView(heart,new LinearLayout.LayoutParams(SettingsUi.dp(act,32),-2));
            ViewGroup flow=(ViewGroup)SnsLocalNative.cls("com.tencent.mm.plugin.sns.ui.y0").getConstructor(Context.class).newInstance(act);int size=SettingsUi.dp(act,32),gap=SettingsUi.dp(act,6),width=act.getResources().getDisplayMetrics().widthPixels-SettingsUi.dp(act,68);XposedHelpers.callMethod(flow,"setLineMaxCounte",Math.max(1,width/(size+gap)));int count=Math.min(limit,likes.size());
            for(int i=0;i<count;i++){final SnsLocalData.Person person=likes.get(i);ImageView image=new ImageView(act);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setContentDescription(person.name+"的头像");SnsLocalNative.avatar(image,person.id);LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(size,size);params.setMargins(0,0,gap,gap);image.setLayoutParams(params);flow.addView(image);image.setOnClickListener(v->profile(person,SnsLocalData.Feature.LIKES));}
            LinearLayout column=new LinearLayout(act);column.setOrientation(LinearLayout.VERTICAL);column.addView(flow,new LinearLayout.LayoutParams(-1,-2));if(count<likes.size()){TextView more=SettingsUi.text(act,"展开更多（共"+likes.size()+"人）",12);more.setOnClickListener(v->{limit+=100;notifyDataSetChanged();});column.addView(more);}row.addView(column,new LinearLayout.LayoutParams(0,-2,1));row.setBackgroundResource(2131233980);return row;
        }
        long commentTime(SnsLocalData.Comment comment){Long at=times.get(SnsLocalTimes.event(key,comment.person.id,comment.localId));return at==null?SnsLocalStore.load(owner).post(key).updated:at;}
        View commentRow(Activity act,final SnsLocalData.Comment comment)throws Throwable{
            View row=LayoutInflater.from(act).inflate(2131630817,null);row.setBackgroundResource(2131233980);ImageView avatar=(ImageView)row.findViewById(2131362523);SnsLocalNative.avatar(avatar,comment.person.id);avatar.setContentDescription(comment.person.name+"的头像");avatar.setOnClickListener(v->profile(comment.person,SnsLocalData.Feature.COMMENTS));
            TextView name=(TextView)row.findViewById(2131362535),body=(TextView)row.findViewById(2131362528),time=(TextView)row.findViewById(2131362539);name.setText(comment.person.name);name.setTextColor(SnsLocalNative.linkColor(act));name.setOnClickListener(v->profile(comment.person,SnsLocalData.Feature.COMMENTS));body.setText(comment.text);body.setTextColor(SettingsUi.textColor(act));body.setVisibility(View.VISIBLE);time.setText(date(commentTime(comment)));row.findViewById(2131362527).setVisibility(View.VISIBLE);for(int id:new int[]{2131362524,2131387113}){View unused=row.findViewById(id);if(unused!=null)unused.setVisibility(View.GONE);}View.OnLongClickListener edit=v->{if(allowed()&&SnsLocalStore.enabled(SnsLocalData.Feature.COMMENTS))SnsLocalPicker.edit(act,new SnsLocalNative.Target(owner,key),comment);return true;};row.setOnLongClickListener(edit);body.setOnLongClickListener(edit);return row;
        }
        View messageRow(Activity act,final SnsLocalProjection.Entry entry)throws Throwable{
            LinearLayout row=new LinearLayout(act);row.setOrientation(LinearLayout.HORIZONTAL);row.setPadding(SettingsUi.dp(act,16),SettingsUi.dp(act,14),SettingsUi.dp(act,16),SettingsUi.dp(act,14));row.setGravity(Gravity.TOP);row.setBackgroundColor(SettingsUi.dark(act)?0xFF191919:0xFFFFFFFF);final SnsLocalData.Feature feature=entry.comment==null?SnsLocalData.Feature.LIKES:SnsLocalData.Feature.COMMENTS;
            ImageView avatar=new ImageView(act);avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);SnsLocalNative.avatar(avatar,entry.person.id);avatar.setContentDescription(entry.person.name+"的头像");avatar.setOnClickListener(v->profile(entry.person,feature));row.addView(avatar,new LinearLayout.LayoutParams(SettingsUi.dp(act,40),SettingsUi.dp(act,40)));
            LinearLayout column=new LinearLayout(act);column.setOrientation(LinearLayout.VERTICAL);column.setPadding(SettingsUi.dp(act,12),0,0,0);TextView name=SettingsUi.text(act,entry.person.name,15);name.setTextColor(SnsLocalNative.linkColor(act));name.setOnClickListener(v->profile(entry.person,feature));column.addView(name);TextView content=SettingsUi.text(act,entry.comment==null?"♡ 赞了这条朋友圈":entry.comment.text,14);column.addView(content);TextView time=SettingsUi.text(act,date(entry.time),12);time.setTextColor(SettingsUi.dark(act)?0xFF888888:0xFF999999);column.addView(time);row.addView(column,new LinearLayout.LayoutParams(0,-2,1));
            final Meta meta=metas.get(entry.key);if(meta!=null){TextView preview=SettingsUi.text(act,meta.text.isEmpty()?"朋友圈":meta.text,12);preview.setMaxLines(3);preview.setEllipsize(TextUtils.TruncateAt.END);preview.setGravity(Gravity.CENTER);preview.setBackgroundColor(SettingsUi.dark(act)?0xFF303030:0xFFF3F3F3);preview.setPadding(6,6,6,6);row.addView(preview,new LinearLayout.LayoutParams(SettingsUi.dp(act,56),SettingsUi.dp(act,56)));row.setOnClickListener(v->open(act,owner,meta));}
            row.setOnLongClickListener(v->{if(!allowed()||!SnsLocalStore.enabled(feature))return true;if(entry.comment!=null)SnsLocalPicker.edit(act,new SnsLocalNative.Target(owner,entry.key),entry.comment);else SnsLocalPicker.clearPost(act,new SnsLocalNative.Target(owner,entry.key));return true;});return row;
        }
    }
    static String date(long when){long ago=System.currentTimeMillis()-when;if(ago>=0&&ago<60000)return "刚刚";if(ago>=0&&ago<3600000)return (ago/60000)+"分钟前";return new java.text.SimpleDateFormat("yyyy年M月d日 HH:mm",java.util.Locale.CHINA).format(new java.util.Date(when));}
}

