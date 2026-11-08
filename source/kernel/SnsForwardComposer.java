package com.zafiro.wxentry.kernel;
import android.content.Intent;
import java.util.ArrayList;

/** Whitelisted fresh-composer extras only. No retry/resend/publish action is copied. */
final class SnsForwardComposer {
    static Intent intent(SnsForwardDraft draft){
        Intent out=new Intent().setClassName("com.tencent.mm","com.tencent.mm.plugin.sns.ui.SnsUploadUI");
        out.putExtra("Ksnsupload_type",draft.uploadType());out.putExtra("Kdescription",draft.description);out.putExtra("KSnsPostManu",true);out.putExtra("Kis_take_photo",false);
        if(draft.kind==SnsForwardDraft.Kind.TEXT)out.putExtra("sns_comment_type",1);
        if(draft.kind==SnsForwardDraft.Kind.PHOTOS){out.putStringArrayListExtra("sns_kemdia_path_list",new ArrayList<String>(draft.media));}
        if(draft.kind==SnsForwardDraft.Kind.VIDEO){out.putExtra("KSightPath",draft.media.get(0));out.putExtra("KSightThumbPath",draft.thumbnail);out.putExtra("sight_md5",draft.videoMd5);}
        if(draft.kind==SnsForwardDraft.Kind.LINK){out.putExtra("Ksnsupload_link",draft.url);out.putExtra("Ksnsupload_title",draft.title);if(draft.thumbnail!=null)out.putExtra("KsnsUpload_imgPath",draft.thumbnail);}
        if(draft.kind==SnsForwardDraft.Kind.FINDER)out.putExtra("ksnsupload_finder_object_xml",draft.cardXml);
        if(draft.kind==SnsForwardDraft.Kind.MPVIDEO)out.putExtra("ksnsupload_mp_video_xml",draft.cardXml);
        return out;
    }
}
