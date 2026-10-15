package com.kuilunfuzhe.monvhua.features.daynight;

import icyllis.modernui.R;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.drawable.ColorDrawable;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.graphics.drawable.StateListDrawable;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.mc.ScreenCallback;
import icyllis.modernui.text.Editable;
import icyllis.modernui.text.TextWatcher;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.*;
import icyllis.modernui.widget.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import java.util.Locale;

/** UI-thread confined multi-segment editor with separate server draft and active configuration. */
public final class DayNightConfigScreen extends Fragment {
    private static final int TEXT=0xFFE8EDF5,MUTED=0xFF9BAAC0,ACCENT=0xFF8FAEFF;
    private static DayNightConfigScreen active;
    private final Screen parent;
    private FrameLayout root;
    private ScrollView scroll,listScroll;
    private LinearLayout content,columns,list,editor;
    private TextView status,diagnostic,total,actual,hint,dirtyLabel,interval,error;
    private Button packButton,modButton,save,apply,resume,reload,defaults,split,merge,recheck,barMode;
    private EditText nameField,startField,endField,secondsField,colorField;
    private Timeline timeline;
    private boolean editing,ready,pending,editable,bridgeReady,daylight,replaceOnRead=true,solarView;
    private long revision;
    private int selected,requestSerial;
    private DayNightConfig model=DayNightConfig.defaults(),candidate=model,saved=model,applied=model;

    private DayNightConfigScreen(Screen parent) { this.parent=parent; }
    public static void open(Screen parent) {
        MinecraftClient client=MinecraftClient.getInstance();
        client.execute(() -> client.setScreen(MuiModApi.get().createScreen(new DayNightConfigScreen(parent),new ScreenCallback() {
            public boolean isPauseScreen() { return false; }
            public boolean hasDefaultBackground() { return false; }
            public boolean shouldBlurBackground() { return false; }
        },parent)));
    }
    @Override public View onCreateView(LayoutInflater inflater,ViewGroup container,DataSet savedInstanceState) {
        active=this;ready=false;pending=false;replaceOnRead=true;
        root=new FrameLayout(getContext()) {
            @Override protected void onSizeChanged(int w,int h,int oldw,int oldh) {
                super.onSizeChanged(w,h,oldw,oldh);post(() -> fit(w,h));
            }
        };
        root.setBackground(new ColorDrawable(0xE0090D16));
        scroll=new ScrollView(getContext());scroll.setBackground(shape(0xFF131B2B,0xFF33415B,16));
        root.addView(scroll,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
        content=vertical();content.setPadding(d(24),d(20),d(24),d(20));
        scroll.addView(content,new ViewGroup.LayoutParams(-1,-2));
        label(content,"世界时间 / WORLD TIME",11,ACCENT);
        label(content,"自定义昼夜时段",25,TEXT);
        actual=label(content,"正在连接服务器…",12,MUTED);gap(content,12);
        LinearLayout modes=row(content,40);
        packButton=button(modes,"数据包模式",() -> { model=model.withEnabled(false);validate(); },false);
        modButton=button(modes,"模组自定义模式",() -> { model=model.withEnabled(true);validate(); },false);
        hint=label(content,"",12,MUTED);hint.setPadding(0,d(8),0,d(10));
        total=label(content,"",20,TEXT);
        timeline=new Timeline();content.addView(timeline,block(18));
        LinearLayout barRow=row(content,30);
        barMode=button(barRow,"时间轴：持续时长比例",() -> {
            solarView=!solarView;barMode.setText(solarView?"时间轴：游戏区间比例":"时间轴：持续时长比例");timeline.invalidate();
        },false);
        gap(content,12);
        columns=horizontal();content.addView(columns,block(-2));
        listScroll=new ScrollView(getContext());list=vertical();listScroll.addView(list,new ViewGroup.LayoutParams(-1,-2));
        columns.addView(listScroll,new LinearLayout.LayoutParams(d(240),d(344)));
        editor=vertical();editor.setPadding(d(16),d(12),d(16),d(12));
        editor.setBackground(shape(0xFF1B263A,0xFF33415B,10));
        columns.addView(editor,new LinearLayout.LayoutParams(0,-2,1));
        nameField=field(editor,"时段名称",false);
        LinearLayout boundaries=horizontal();editor.addView(boundaries,block(-2));
        LinearLayout startBox=vertical(),endBox=vertical();
        LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,-2,1);left.setMargins(0,0,d(10),0);
        boundaries.addView(startBox,left);boundaries.addView(endBox,new LinearLayout.LayoutParams(0,-2,1));
        startField=field(startBox,"起点",true);endField=field(endBox,"终点",true);
        interval=label(editor,"",12,ACCENT);
        label(editor,"可填游戏刻 0～24000 或 HH:mm；相邻边界自动联动",11,MUTED);
        secondsField=field(editor,"持续时间（现实秒，按 20 TPS）",true);
        colorField=field(editor,"颜色：自动 或 #RRGGBB",false);
        error=label(editor,"",12,0xFFFFA5A5);
        gap(content,10);
        LinearLayout segmentActions=row(content,34);
        split=button(segmentActions,"拆分选中时段",() -> transform(true),false);
        merge=button(segmentActions,"合并至上一段",() -> transform(false),false);
        dirtyLabel=label(content,"",12,ACCENT);dirtyLabel.setPadding(0,d(12),0,d(4));
        diagnostic=label(content,"",12,0xFFFFCB8E);
        status=label(content,"正在读取服务器配置…",12,MUTED);
        resume=new Button(getContext());resume.setText("恢复昼夜循环规则");style(resume,false);
        resume.setOnClickListener(v -> send(2));content.addView(resume,block(32));resume.setVisibility(View.GONE);
        gap(content,12);
        LinearLayout secondary=row(content,34);
        defaults=button(secondary,"恢复默认四段",() -> { model=DayNightConfig.defaults().withEnabled(model.enabled());selected=0;fillEditor(); },false);
        reload=button(secondary,"重新读取",() -> send(0),false);
        recheck=button(secondary,"重新检测适配",() -> send(4),false);
        gap(content,8);
        LinearLayout actions=row(content,40);
        button(actions,"取消 / 返回",() -> MinecraftClient.getInstance().execute(() -> MinecraftClient.getInstance().setScreen(parent)),false);
        save=button(actions,"保存配置",() -> send(3),false);
        apply=button(actions,"应用并切换",() -> send(1),true);
        fillEditor();send(0);return root;
    }
    private EditText field(LinearLayout parent,String title,boolean numeric) {
        TextView caption=label(parent,title,12,MUTED);caption.setPadding(0,d(6),0,d(4));
        EditText field=new EditText(getContext());field.setSingleLine(true);field.setTextSize(numeric?18:15);
        field.setTextColor(TEXT);field.setPadding(d(10),0,d(10),0);field.setBackground(fieldBackground());
        parent.addView(field,block(36));
        field.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            public void onTextChanged(CharSequence s,int start,int before,int count) {}
            public void afterTextChanged(Editable text) { if(!editing&&save!=null) validate(); }
        });
        return field;
    }
    private int d(float n) { return root.dp(n); }
    private void fit(int w,int h) {
        if(w<=0||h<=0) return;
        int width=Math.max(1,Math.min(w-d(24),d(880)));
        scroll.setLayoutParams(new FrameLayout.LayoutParams(width,Math.max(1,Math.min(h-d(24),d(800))),Gravity.CENTER));
        boolean narrow=width<d(640);columns.setOrientation(narrow?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams listParams=new LinearLayout.LayoutParams(narrow?-1:d(240),d(narrow?150:344));
        listParams.setMargins(0,0,narrow?0:d(12),narrow?d(10):0);listScroll.setLayoutParams(listParams);
        editor.setLayoutParams(new LinearLayout.LayoutParams(narrow?-1:0,-2,narrow?0:1));
    }
    private void refreshList() {
        list.removeAllViews();
        for(int i=0;i<model.segments().size();i++) {
            final int index=i;DayNightSegment s=model.segments().get(i);
            Button b=new Button(getContext());
            b.setText((i+1)+". "+s.name()+"\n"+DayNightConfig.timeText(s.start())+" → "+DayNightConfig.timeText(s.end())+"  ·  "+s.seconds()+" 秒");
            b.setTextSize(12);b.setTextColor(TEXT);b.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);
            b.setPadding(d(12),d(4),d(6),d(4));b.setMinHeight(0);
            b.setBackground(shape(i==selected?0xFF304364:0xFF1C293E,i==selected?s.displayColor():0xFF33415B,7));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,d(58));lp.setMargins(0,0,0,d(6));list.addView(b,lp);
            b.setEnabled(!pending);
            b.setOnClickListener(v -> { if(candidate==null) { status.setText("请先修正当前时段的输入");return; }selected=index;fillEditor(); });
        }
    }
    private void fillEditor() {
        editing=true;selected=Math.max(0,Math.min(selected,model.segments().size()-1));
        DayNightSegment s=model.segments().get(selected);
        nameField.setText(s.name());startField.setText(Integer.toString(s.start()));endField.setText(Integer.toString(s.end()));
        secondsField.setText(Integer.toString(s.seconds()));colorField.setText(s.color()==null?"自动":String.format(Locale.ROOT,"#%06X",s.color()));
        editing=false;validate();
    }
    private DayNightConfig readEditor() {
        String seconds=secondsField.getText().toString().trim();
        if(!seconds.matches("[0-9]{1,5}")) throw new IllegalArgumentException("持续时间须为 1～86400 整数秒");
        String raw=colorField.getText().toString().trim();Integer color=null;
        if(!raw.isEmpty()&&!raw.equals("自动")&&!raw.equalsIgnoreCase("auto")) {
            if(!raw.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException("颜色请输入 自动 或 #RRGGBB");
            color=Integer.parseInt(raw.substring(1),16);
        }
        return model.edit(selected,nameField.getText().toString().trim(),DayNightConfig.parseTime(startField.getText().toString()),
                DayNightConfig.parseTime(endField.getText().toString()),Integer.parseInt(seconds),color);
    }
    private boolean dirty() { return candidate==null||!saved.equals(candidate); }
    private void validate() {
        try { candidate=readEditor();model=candidate;error.setText(""); }
        catch(IllegalArgumentException e) { candidate=null;error.setText(e.getMessage()); }
        boolean canEdit=ready&&editable&&!pending;
        for(EditText field:new EditText[]{nameField,startField,endField,secondsField,colorField}) field.setEnabled(canEdit);
        style(packButton,!model.enabled());style(modButton,model.enabled());
        packButton.setEnabled(canEdit);modButton.setEnabled(canEdit);
        hint.setText(model.enabled()?"模组按下方区间推进太阳；日程由适配数据包处理。":"数据包主导运算。可编辑并保存模组配置，应用时才交接控制权。");
        total.setText(candidate==null?"请检查当前输入":model.segments().size()+" 个时段  ·  全天 "+format(model.totalSeconds()));
        DayNightSegment segment=model.segments().get(selected);
        interval.setText(DayNightConfig.timeText(segment.start())+" → "+DayNightConfig.timeText(segment.end())+
                (segment.span()==24000?"（完整一天）":Math.floorMod(segment.start()+6000,24000)+segment.span()>=24000?"（跨午夜）":"")+"  ·  "+segment.span()+" 游戏刻");
        timeline.config=candidate;timeline.invalidate();
        dirtyLabel.setText(!ready?"等待服务器配置":pending?"正在提交…":dirty()?"有未保存修改":!saved.equals(applied)?"配置已保存，尚未应用":"配置已保存并生效");
        save.setText(dirty()?"保存配置":"已保存");save.setEnabled(canEdit&&candidate!=null&&dirty());
        apply.setEnabled(canEdit&&bridgeReady&&candidate!=null&&(!candidate.equals(applied)||dirty()));
        split.setEnabled(canEdit&&candidate!=null&&model.segments().size()<32&&segment.span()>=2&&segment.seconds()>=2);
        merge.setEnabled(canEdit&&candidate!=null&&model.segments().size()>1);
        defaults.setEnabled(canEdit);reload.setEnabled(!pending);recheck.setEnabled(!pending);
        resume.setVisibility(ready&&applied.enabled()&&!daylight&&bridgeReady?View.VISIBLE:View.GONE);resume.setEnabled(canEdit);
        refreshList();
    }
    private void transform(boolean splitting) {
        if(candidate==null) return;
        try {
            if(splitting) model=model.split(selected);
            else { int old=selected;model=model.mergePrevious(selected);selected=old==0?model.segments().size()-1:old-1; }
            fillEditor();
        } catch(IllegalArgumentException e) { status.setText(e.getMessage()); }
    }
    private void send(int action) {
        if(pending) return;
        if((action==1||action==3)&&(!ready||!editable||candidate==null)) return;
        replaceOnRead=action==0;pending=true;int token=++requestSerial;
        status.setText("正在等待服务器确认…");validate();
        DayNightClient.send(new DayNightPackets.Request(action,revision,candidate==null?saved:candidate));
        root.postDelayed(() -> {
            if(active==this&&pending&&requestSerial==token) {pending=false;status.setText("服务器暂未响应，输入已保留；可重新读取确认状态");validate();}
        },8000);
    }
    public static void receive(DayNightPackets.State packet) {
        DayNightConfigScreen v=active;if(v==null||v.status==null) return;
        boolean replace=!v.ready||(v.replaceOnRead&&packet.message().equals("已读取服务器配置"))||packet.result()==1||packet.result()==4;
        v.editable=packet.editable();v.bridgeReady=packet.bridgeReady();v.daylight=packet.daylight();v.applied=packet.config();
        if(replace) {
            v.model=packet.draft();v.saved=packet.draft();v.revision=packet.revision();v.ready=true;v.pending=false;v.replaceOnRead=false;v.fillEditor();
        } else if(packet.result()==2||packet.result()==3) v.pending=false;
        else if(!v.dirty()&&!v.pending) {v.saved=packet.draft();v.model=packet.draft();v.revision=packet.revision();v.fillEditor();}
        v.actual.setText("已应用："+(packet.config().enabled()?"模组自定义模式":"数据包模式")+
                (packet.bridgeReady()?"  ·  适配已连接":"  ·  适配未就绪")+(packet.paused()?"  ·  已暂停":"")+(packet.editable()?"":"  ·  只读"));
        v.diagnostic.setText(packet.diagnostic().isEmpty()?"":packet.diagnostic()+"；仍可保存配置，应用需完成适配。");
        if(!packet.message().isEmpty()) v.status.setText(packet.message());
        else if(v.dirty()&&packet.revision()!=v.revision) v.status.setText("服务器配置已更新，草稿已保留；请重新读取后编辑");
        v.status.setTextColor(packet.result()==2?0xFFFFA5A5:MUTED);v.validate();
    }
    public static void disconnect() {
        if(active!=null&&active.status!=null) {active.ready=false;active.pending=false;active.status.setText("未连接世界，或服务器未安装第三版昼夜功能");active.validate();}
    }
    @Override public void onDestroyView() {if(active==this) active=null;super.onDestroyView();}
    private LinearLayout vertical() {LinearLayout l=new LinearLayout(getContext());l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout horizontal() {LinearLayout l=new LinearLayout(getContext());l.setOrientation(LinearLayout.HORIZONTAL);return l;}
    private LinearLayout.LayoutParams block(int h) {return new LinearLayout.LayoutParams(-1,h<0?h:d(h));}
    private LinearLayout row(LinearLayout parent,int h) {LinearLayout l=horizontal();parent.addView(l,block(h));return l;}
    private void gap(LinearLayout parent,int h) {parent.addView(new View(getContext()),block(h));}
    private TextView label(LinearLayout parent,String text,int size,int color) {
        TextView v=new TextView(getContext());v.setText(text);v.setTextSize(size);v.setTextColor(color);parent.addView(v,block(-2));return v;
    }
    private ShapeDrawable shape(int fill,int border,int radius) {
        ShapeDrawable s=new ShapeDrawable();s.setShape(ShapeDrawable.RECTANGLE);s.setColor(fill);s.setStroke(d(1),border);s.setCornerRadius(d(radius));return s;
    }
    private StateListDrawable fieldBackground() {
        StateListDrawable s=new StateListDrawable();s.addState(new int[]{R.attr.state_focused},shape(0xFF101A2A,ACCENT,6));
        s.addState(new int[0],shape(0xFF111A29,0xFF43516A,6));return s;
    }
    private void style(Button b,boolean primary) {
        StateListDrawable s=new StateListDrawable();
        s.addState(new int[]{-R.attr.state_enabled},shape(0xFF202A3A,0xFF2F3A4D,7));
        s.addState(new int[]{R.attr.state_pressed},shape(0xFF48639A,0xFFB1C7FF,7));
        s.addState(new int[]{R.attr.state_hovered},shape(0xFF354D78,0xFF819CCD,7));
        s.addState(new int[0],shape(primary?0xFF3E609D:0xFF233149,primary?ACCENT:0xFF405271,7));
        b.setBackground(s);b.setTextColor(TEXT);b.setTextSize(12);b.setMinHeight(0);b.setPadding(d(6),0,d(6),0);
    }
    private Button button(LinearLayout parent,String text,Runnable action,boolean primary) {
        Button b=new Button(getContext());b.setText(text);style(b,primary);b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);lp.setMargins(d(3),0,d(3),0);parent.addView(b,lp);return b;
    }
    private static String format(int seconds) {
        return (seconds>=3600?seconds/3600+" 小时 ":"")+seconds/60%60+" 分 "+seconds%60+" 秒";
    }
    private final class Timeline extends View {
        private DayNightConfig config;
        Timeline() {super(DayNightConfigScreen.this.getContext());}
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);if(config==null) return;
            float sum=solarView?24000:config.totalSeconds(),x=0;Paint p=Paint.obtain();
            for(DayNightSegment s:config.segments()) {
                float next=x+getWidth()*(solarView?s.span():s.seconds())/sum;p.setColor(s.displayColor());
                canvas.drawRect(x,d(3),next,getHeight()-d(3),p);x=next;
            }
            p.recycle();
        }
    }
}
