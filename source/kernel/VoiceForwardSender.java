package com.zafiro.wxentry.kernel;

import de.robv.android.xposed.XposedHelpers;
import java.lang.reflect.*;

/** Copies into a fresh native voice file, then lets the current voice task create/send its own row. */
final class VoiceForwardSender {
    interface Backend {
        String account() throws Throwable;
        String sourceName(Object msg) throws Throwable;
        String freshName(String original) throws Throwable;
        String sourcePath(Object msg,String file) throws Throwable;
        String targetPath(String file) throws Throwable;
        long size(String path) throws Throwable;
        long copy(String from,String to) throws Throwable;
        void remove(String path) throws Throwable;
        Object task(String recipient,String file,int duration) throws Throwable;
        void submit(Object task) throws Throwable;
    }
    private static Backend backend;
    static void start(ClassLoader loader) throws Throwable { backend=new NativeBackend(loader); }
    static String account() throws Throwable { return backend.account(); }
    static void requestDownload(Object msg) throws Throwable { ((NativeBackend)backend).download(msg); }
    static void send(Object source,String recipient,int duration) throws Throwable {
        send(backend,source,recipient,duration);
    }
    static VoiceForwardQueue.Ticket sendTracked(Object source,String recipient,int duration) throws Throwable {
        final Object task=send(backend,source,recipient,duration);
        final NativeBackend api=(NativeBackend)backend;
        return new VoiceForwardQueue.Ticket() { public int state() throws Throwable { return api.state(task); } };
    }
    static Object send(Backend api,Object source,String recipient,int duration) throws Throwable {
        if(source==null || recipient==null || recipient.length()==0 || duration<=0)
            throw new IllegalArgumentException("voice request");
        String old=api.sourceName(source),fresh=api.freshName(old);
        if(old==null || old.length()==0 || fresh==null || fresh.length()==0 || fresh.equals(old))
            throw new IllegalStateException("VF_FILENAME");
        String from=api.sourcePath(source,old),to=api.targetPath(fresh);
        if(from==null || to==null || from.length()==0 || to.length()==0 || from.equals(to))
            throw new IllegalStateException("VF_PATH");
        long originalSize=api.size(from);
        if(originalSize<=0)throw new IllegalStateException("VF_SOURCE_MISSING");
        if(api.size(to)>0)throw new IllegalStateException("VF_DEST_EXISTS");
        boolean copied=false,handedOff=false;
        try {
            copied=true; // Also clean a partial destination if the copy throws.
            long result=api.copy(from,to);
            if(result<0 || api.size(to)!=originalSize)throw new IllegalStateException("VF_COPY");
            Object task=api.task(recipient,fresh,duration);
            if(task==null)throw new IllegalStateException("VF_TASK");
            handedOff=true; // Once native submission starts, its own error handling owns the file.
            api.submit(task);
            return task;
        } finally {
            if(copied && !handedOff)try { api.remove(to); } catch(Throwable ignored) {}
        }
    }

    private static final class NativeBackend implements Backend {
        final Class<?> locator,fileInterface,voiceInterface,voiceContext;
        final Method currentAccount,codec,filename,sourcePath,targetPath,fileSize,copy,remove,submit,mkdir,download;
        final Constructor<?> parameters,task,downloadParameters;
        final Field duration,scene,taskParameters,initResult,uploadResult,sendResult,updateResult;
        final Field initCode,uploadCode,sendCode,updateCode,networkErrorType,networkErrorCode;
        NativeBackend(ClassLoader loader) throws Throwable {
            locator=Class.forName("fi5.n0",false,loader);
            fileInterface=Class.forName("ep3.u0",false,loader);
            voiceInterface=Class.forName("qj0.f",false,loader);
            voiceContext=Class.forName("jv5.x",false,loader);
            Class<?> files=Class.forName("mc0.b",false,loader);
            Class<?> message=Class.forName("com.tencent.mm.storage.f9",false,loader);
            Class<?> voice=Class.forName("q71.n1",false,loader);
            Class<?> formats=Class.forName("q71.c1",false,loader);
            Class<?> vfs=Class.forName("com.tencent.mm.vfs.z6",false,loader);
            Class<?> params=Class.forName("pj0.d",false,loader);
            Class<?> pipeline=Class.forName("xj0.x",false,loader);
            currentAccount=Class.forName("w41.z1",false,loader).getDeclaredMethod("u");
            codec=formats.getDeclaredMethod("c",String.class);
            filename=voice.getDeclaredMethod("t1",String.class,String.class);
            sourcePath=files.getDeclaredMethod("gk",message,String.class,boolean.class);
            targetPath=files.getDeclaredMethod("ik",voiceContext,String.class,boolean.class,boolean.class);
            fileSize=vfs.getDeclaredMethod("k",String.class); // st_size; l() is modification time.
            copy=vfs.getDeclaredMethod("d",String.class,String.class,boolean.class);
            remove=vfs.getDeclaredMethod("h",String.class); // File deletion, not recursive directory removal.
            mkdir=vfs.getDeclaredMethod("u",String.class);
            parameters=params.getDeclaredConstructor(String.class,String.class);
            task=pipeline.getDeclaredConstructor(params);
            duration=params.getDeclaredField("h");scene=params.getDeclaredField("i");
            taskParameters=pipeline.getDeclaredField("i");
            initResult=params.getDeclaredField("q");uploadResult=params.getDeclaredField("r");
            sendResult=params.getDeclaredField("s");updateResult=params.getDeclaredField("t");
            initCode=initResult.getType().getDeclaredField("a");uploadCode=uploadResult.getType().getDeclaredField("a");
            sendCode=sendResult.getType().getDeclaredField("a");updateCode=updateResult.getType().getDeclaredField("a");
            networkErrorType=sendResult.getType().getDeclaredField("c");networkErrorCode=sendResult.getType().getDeclaredField("d");
            submit=Class.forName("wj0.o",false,loader).getDeclaredMethod("Sj",Class.forName("hr3.f0",false,loader));
            Class<?> downloadParams=Class.forName("pj0.a",false,loader);
            downloadParameters=downloadParams.getDeclaredConstructor(String.class,String.class,long.class,long.class,long.class);
            download=Class.forName("wj0.o",false,loader).getDeclaredMethod("Mj",downloadParams);
            if(currentAccount.getReturnType()!=String.class || filename.getReturnType()!=String.class
                    || fileSize.getReturnType()!=long.class || copy.getReturnType()!=long.class
                    || remove.getReturnType()!=boolean.class || submit.getReturnType()!=void.class
                    || duration.getType()!=int.class || scene.getType()!=int.class)
                throw new NoSuchMethodException("modern voice send contract");
            for(AccessibleObject o:new AccessibleObject[]{currentAccount,codec,filename,sourcePath,targetPath,
                    fileSize,copy,remove,mkdir,parameters,task,duration,scene,submit,downloadParameters,download,
                    taskParameters,initResult,uploadResult,sendResult,updateResult,initCode,uploadCode,sendCode,updateCode,networkErrorType,networkErrorCode})o.setAccessible(true);
        }
        Object service(Class<?> type) { return XposedHelpers.callStaticMethod(locator,"c",type); }
        public String account() throws Throwable { return (String)currentAccount.invoke(null); }
        public String sourceName(Object msg) { return (String)XposedHelpers.callMethod(msg,"z0"); }
        public String freshName(String original) throws Throwable {
            return (String)filename.invoke(null,account(),(String)codec.invoke(null,original));
        }
        public String sourcePath(Object msg,String file) throws Throwable {
            return (String)sourcePath.invoke(service(fileInterface),msg,file,false);
        }
        public String targetPath(String file) throws Throwable {
            Object context=XposedHelpers.getStaticObjectField(voiceContext,"j");
            String path=(String)targetPath.invoke(service(fileInterface),context,file,true,true);
            int slash=path.lastIndexOf('/');
            if(slash>0 && !Boolean.TRUE.equals(mkdir.invoke(null,path.substring(0,slash))))
                throw new IllegalStateException("VF_DIRECTORY");
            return path;
        }
        public long size(String path) throws Throwable { return ((Number)fileSize.invoke(null,path)).longValue(); }
        public long copy(String from,String to) throws Throwable { return ((Number)copy.invoke(null,from,to,false)).longValue(); }
        public void remove(String path) throws Throwable { remove.invoke(null,path); }
        public Object task(String recipient,String file,int milliseconds) throws Throwable {
            Object params=parameters.newInstance(recipient,file);
            duration.setInt(params,milliseconds);scene.setInt(params,1);
            return task.newInstance(params);
        }
        public void submit(Object sendTask) throws Throwable { submit.invoke(service(voiceInterface),sendTask); }
        int state(Object sendTask) throws Throwable {
            Object params=taskParameters.get(sendTask);
            Object initialized=initResult.get(params),uploaded=uploadResult.get(params),sent=sendResult.get(params),updated=updateResult.get(params);
            if(initialized!=null && initCode.getInt(initialized)!=0 || uploaded!=null && uploadCode.getInt(uploaded)!=0
                    || sent!=null && (sendCode.getInt(sent)!=0 || networkErrorType.getInt(sent)!=0 || networkErrorCode.getInt(sent)!=0)
                    || updated!=null && updateCode.getInt(updated)!=0)return -1;
            // updateMsg is reached after the network send stage, not when the task is merely queued.
            return updated!=null?1:0;
        }
        void download(Object msg) throws Throwable {
            Object params=downloadParameters.newInstance(sourceName(msg),
                (String)XposedHelpers.callMethod(msg,"O0"),
                ((Number)XposedHelpers.callMethod(msg,"getMsgId")).longValue(),
                ((Number)XposedHelpers.callMethod(msg,"G0")).longValue(),
                ((Number)XposedHelpers.callMethod(msg,"getCreateTime")).longValue());
            download.invoke(service(voiceInterface),params);
        }
    }
}
