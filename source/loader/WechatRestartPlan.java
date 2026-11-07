package com.zafiro.wxentry;

/** Never terminate the host until a relaunch has actually been scheduled. */
final class WechatRestartPlan {
    interface Host {void schedule(boolean exact)throws Throwable;void terminate();void failed(Throwable failure);}
    static boolean request(Host host){
        try{host.schedule(true);}catch(Throwable exact){
            try{host.schedule(false);}catch(Throwable fallback){host.failed(fallback);return false;}
        }
        host.terminate();return true;
    }
}
