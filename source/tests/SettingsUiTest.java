package com.zafiro.wxentry.kernel;
import android.content.Context;import android.widget.*;import android.graphics.drawable.*;
public final class SettingsUiTest {
 static int checks;static void check(boolean v,String message){checks++;if(!v)throw new AssertionError(message);}
 public static void main(String[] args){
  Context c=new Context();final int[] writes={0};final boolean[] saved={false};
  SettingsUi.Change change=new SettingsUi.Change(){public boolean set(boolean on){writes[0]++;saved[0]=on;return saved[0];}};
  SettingsUi.ToggleRow row=SettingsUi.toggle(c,"随机延时","1～3 秒 · 点击设置",false,change);
  check(row.children.size()==2&&row.children.get(1)==row.control,"switch occupies rightmost child");
  LinearLayout.LayoutParams label=(LinearLayout.LayoutParams)row.children.get(0).params,right=(LinearLayout.LayoutParams)row.control.params;
  check(label.width==0&&label.weight==1&&right.width==48&&right.height==48&&right.leftMargin==12,"label receives remaining space; switch keeps its own width");
  check(row.minimumHeight==64&&!row.control.showText,"touch row is large enough; switch has no overlapping ON/OFF label");
  StateListDrawable track=(StateListDrawable)row.control.track;
  check(((GradientDrawable)track.drawables.get(0)).color==SettingsUi.ROSE&&((GradientDrawable)track.drawables.get(1)).color==SettingsUi.GRAY,"explicit rose checked and muted unchecked drawables");
  check(row.control.thumbTint==null&&row.control.trackTint==null,"host theme tint cleared");
  check(writes[0]==0,"initial display never writes settings");row.control.performClick();check(writes[0]==1&&saved[0]&&row.control.isChecked(),"switch click writes once");
  row.performClick();check(writes[0]==2&&!saved[0]&&!row.control.isChecked(),"row click triggers switch exactly once");
  SettingsUi.bind(row.control,true,change);check(writes[0]==2&&row.control.isChecked(),"refresh/rebind does not call stale listener");
  SettingsUi.bind(row.control,false,new SettingsUi.Change(){public boolean set(boolean requested){return false;}});row.control.performClick();check(!row.control.isChecked(),"failed persistence rolls visual state back without recursion");
  SettingsUi.bind(row.control,false,new SettingsUi.Change(){public boolean set(boolean requested){throw new IllegalStateException();}});row.control.performClick();check(!row.control.isChecked(),"callback failure restores prior state");
  row.control.setEnabled(false);row.performClick();check(!row.control.isChecked(),"disabled row cannot toggle");
  SettingsUi.CompactSwitch compact=(SettingsUi.CompactSwitch)SettingsUi.control(c);
  android.graphics.Canvas off=new android.graphics.Canvas();compact.onDraw(off);
  check(off.nativeLayerAlpha==0,"native theme painter cannot show an unfilled or oversized track");
  check(off.draws.size()==4&&off.draws.get(0).w==36&&off.draws.get(0).h==20,"visible switch is 36 by 20 dp with explicit track and thumb passes");
  check(off.draws.get(0).color==SettingsUi.GRAY&&off.draws.get(1).style==android.graphics.Paint.Style.STROKE,"off state always has gray fill and a separate outline");
  check(off.draws.get(1).stroke>0&&off.draws.get(1).color!=off.draws.get(0).color,"outline stays distinguishable from gray fill");
  check(off.draws.get(2).r==8&&off.draws.get(2).shadow>0&&compact.layerType==android.view.View.LAYER_TYPE_SOFTWARE,"small white thumb has rendered shadow");
  compact.setChecked(true);android.graphics.Canvas on=new android.graphics.Canvas();compact.onDraw(on);
  check(on.draws.get(0).color==SettingsUi.ROSE&&on.draws.get(2).x>off.draws.get(2).x,"checked track is rose and thumb moves right");
  compact.visualPosition=0.5f;android.graphics.Canvas middle=new android.graphics.Canvas();compact.onDraw(middle);
  check(middle.draws.get(2).x>off.draws.get(2).x&&middle.draws.get(2).x<on.draws.get(2).x,"native drag/animation position is preserved by custom painting");
  compact.setEnabled(false);android.graphics.Canvas disabled=new android.graphics.Canvas();compact.onDraw(disabled);check(disabled.draws.get(0).color!=0xFFFFFFFF&&disabled.draws.get(1).stroke>0,"disabled track remains visible on white background");
  c.resources.config.uiMode=0x20;check(SettingsUi.textColor(c)==0xFFEEEEEE,"dark theme uses readable label color");
  compact.setChecked(false);compact.setEnabled(true);android.graphics.Canvas night=new android.graphics.Canvas();compact.onDraw(night);check(night.draws.get(0).color!=SettingsUi.GRAY&&night.draws.get(1).stroke>0,"night mode has its own visible gray track and border");
  com.tencent.mm.ui.ok.mode=true;c.resources.config.uiMode=0x10;
  check(MenuPalette.current(c)==MenuPalette.DARK,"WeChat dark selection overrides light system");
  compact.setMenuPalette(MenuPalette.current(c));compact.setChecked(true);
  android.graphics.Canvas hostDark=new android.graphics.Canvas();compact.onDraw(hostDark);
  check(hostDark.draws.get(0).color==MenuPalette.DARK.switchOn,"switch uses WeChat's dark palette");
  com.tencent.mm.ui.ok.mode=false;c.resources.config.uiMode=0x20;
  check(MenuPalette.current(c)==MenuPalette.LIGHT,"WeChat light selection overrides dark system");
  compact.setMenuPalette(MenuPalette.current(c));android.graphics.Canvas hostLight=new android.graphics.Canvas();compact.onDraw(hostLight);
  check(hostLight.draws.get(0).color==MenuPalette.LIGHT.switchOn,"switch recolors when host selection changes");
  com.tencent.mm.ui.ok.mode=null;
  check(MenuPalette.current(c)==MenuPalette.DARK,"unavailable host reader falls back to Activity resources");
  c.resources.config.uiMode=0x10;
  check(MenuPalette.current(c)==MenuPalette.LIGHT,"fallback observes configuration changes in either direction");
  c.resources.metrics.density=3;Switch scaled=SettingsUi.control(c);check(scaled.switchWidth==108&&((GradientDrawable)scaled.thumb).h==48,"drawable and switch geometry scale together");
  System.out.println("PASS "+checks+" switch layout, binding and host theme checks");
 }
}
