package com.zafiro.wxentry.kernel;

/** Pure journal transitions shared by the real timer and recovery path. */
final class SnsScheduleEngine {
    static long wakeAt(long due,long now){return Math.max(now+50,due-2000);}
    static boolean sameClock(boolean sameOwner,long lastWall,long lastElapsed,long wall,long elapsed){return sameOwner&&lastElapsed>0&&elapsed>=lastElapsed&&Math.abs((wall-lastWall)-(elapsed-lastElapsed))<30000;}
    static boolean commitTimely(long due,long claimedWall,long claimedElapsed,long wall,long elapsed){return wall>=due&&wall-due<=45000&&elapsed>=claimedElapsed&&elapsed-claimedElapsed<=60000&&Math.abs((wall-claimedWall)-(elapsed-claimedElapsed))<30000;}
    static boolean recover(SnsScheduleData d,long now,boolean timer,boolean continuous,boolean busy){return recover(d,now,timer,continuous,busy,java.util.Collections.<String>emptySet());}
    static boolean recover(SnsScheduleData d,long now,boolean timer,boolean continuous,boolean busy,java.util.Set<String> editing){
        boolean changed=false;for(SnsScheduleData.Task t:d.tasks){
            if(t.state==SnsScheduleData.State.CLAIMED&&!busy){t.state=SnsScheduleData.State.UNKNOWN;t.message="上次提交结果不明，请核对自己的朋友圈；不会自动重发";t.updated=now;changed=true;}
            else if(SnsScheduleData.due(t,now)&&(editing.contains(t.id)||!SnsScheduleData.eligible(t,now,d.enabled,true,timer,continuous))){t.state=SnsScheduleData.State.MISSED;t.message="已错过计划时间，请确认后补发";t.updated=now;changed=true;}
        }return changed;
    }
    static void claim(SnsScheduleData d,String id,long now,boolean manual){
        SnsScheduleData.Task t=d.find(id);if(t==null||(!manual&&!d.enabled)||(manual?t.state!=SnsScheduleData.State.MISSED:t.state!=SnsScheduleData.State.WAITING||!SnsScheduleData.due(t,now)||now-t.due>45000))throw new IllegalStateException("任务状态已变更");SnsScheduleData.claim(t,now);
    }
    static void attach(SnsScheduleData d,String id,String client,boolean manual){SnsScheduleData.Task t=d.find(id);if(t==null||t.state!=SnsScheduleData.State.CLAIMED||!manual&&!d.enabled||client==null||client.isEmpty())throw new IllegalStateException("提交记录无效");t.client=client;}
    static void queued(SnsScheduleData d,String id,String client,int local,long now){SnsScheduleData.Task t=d.find(id);if(t==null||t.state!=SnsScheduleData.State.CLAIMED||!t.client.equals(client)||local<=0)throw new IllegalStateException("原生结果归属无效");t.localId=local;t.state=SnsScheduleData.State.UPLOADING;t.updated=now;t.message="已交给微信上传，正在核对结果";}
    static void result(SnsScheduleData d,String id,String client,int local,long status,long now){SnsScheduleData.Task t=d.find(id);if(t==null||(t.state!=SnsScheduleData.State.UPLOADING&&t.state!=SnsScheduleData.State.UNKNOWN)||t.localId!=local||!t.client.equals(client)||status==0)throw new IllegalStateException("原生结果归属无效");t.state=status==-1?SnsScheduleData.State.FAILED:SnsScheduleData.State.PUBLISHED;t.serverId=status==-1?0:status;t.updated=now;t.message=status==-1?"微信报告上传失败；请在朋友圈核对，不会自动重发":"已核对发布成功";}
}
