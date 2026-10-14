package com.kuilunfuzhe.monvhua.features.daynight;

import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.drawable.ColorDrawable;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.mc.ScreenCallback;
import icyllis.modernui.text.Editable;
import icyllis.modernui.text.TextWatcher;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.*;
import icyllis.modernui.widget.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

/** All widget state is confined to the ModernUI thread. */
public final class DayNightConfigScreen extends Fragment {
    private static DayNightConfigScreen active;
    private final Screen parent;
    private final EditText[] fields = new EditText[4];
    private LinearLayout layout;
    private TextView status, total;
    private Button toggle, save, resume;
    private boolean enabled, editing, ready, pending, editable;
    private long revision;

    private DayNightConfigScreen(Screen parent) { this.parent = parent; }
    public static void open(Screen parent) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> client.setScreen(MuiModApi.get().createScreen(new DayNightConfigScreen(parent), new ScreenCallback() {
            public boolean isPauseScreen() { return false; }
            public boolean hasDefaultBackground() { return false; }
            public boolean shouldBlurBackground() { return false; }
        }, parent)));
    }
    @Override public View onCreateView(LayoutInflater inflater, ViewGroup container, DataSet savedInstanceState) {
        active = this;
        ScrollView scroll = new ScrollView(getContext());
        scroll.setBackground(new ColorDrawable(0xF00F1115));
        layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 16, 24, 16);
        scroll.addView(layout, new ViewGroup.LayoutParams(-1, -2));
        label("昼夜时长设置");
        label("按服务器 20 TPS 计算；只调整主世界昼夜速度。");
        toggle = button("", () -> { enabled = !enabled; validate(); });
        String[] names = {"日出持续时间（秒）", "白天持续时间（秒）", "日落持续时间（秒）", "夜晚持续时间（秒）"};
        for (int i = 0; i < 4; i++) {
            label(names[i]);
            EditText field = new EditText(getContext());
            fields[i] = field;
            field.setSingleLine(true);
            field.setTextColor(0xFFE8EDF4);
            layout.addView(field, new LinearLayout.LayoutParams(-1, 38));
            field.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                public void onTextChanged(CharSequence s, int start, int before, int count) {}
                public void afterTextChanged(Editable text) { if (!editing) validate(); }
            });
        }
        total = label("");
        status = label("正在读取服务器配置……");
        button("恢复默认", () -> fill(DayNightConfig.defaults()));
        save = button("保存并应用", () -> submit(1));
        resume = button("恢复昼夜循环游戏规则", () -> submit(2));
        resume.setEnabled(false);
        button("重新读取服务器配置", () -> {
            ready = false; pending = false; validate();
            DayNightClient.send(new DayNightPackets.Request(0, 0, DayNightConfig.defaults()));
        });
        button("返回", () -> MinecraftClient.getInstance().execute(() -> MinecraftClient.getInstance().setScreen(parent)));
        fill(DayNightConfig.defaults());
        DayNightClient.send(new DayNightPackets.Request(0, 0, DayNightConfig.defaults()));
        return scroll;
    }
    private TextView label(String text) {
        TextView view = new TextView(getContext());
        view.setText(text); view.setTextSize(15); view.setTextColor(0xFFE8EDF4);
        layout.addView(view, new LinearLayout.LayoutParams(-1, -2));
        return view;
    }
    private Button button(String text, Runnable action) {
        Button button = new Button(getContext()); button.setText(text);
        button.setOnClickListener(v -> action.run());
        layout.addView(button, new LinearLayout.LayoutParams(-1, 38));
        return button;
    }
    private DayNightConfig draft() {
        try {
            int[] values = new int[4];
            for (int i = 0; i < 4; i++) {
                String text = fields[i].getText().toString().trim();
                if (!text.matches("[0-9]{1,5}")) return null;
                values[i] = Integer.parseInt(text);
            }
            DayNightConfig config = new DayNightConfig(enabled, values[0], values[1], values[2], values[3]);
            return config.valid() ? config : null;
        } catch (NumberFormatException e) { return null; }
    }
    private void fill(DayNightConfig config) {
        editing = true; enabled = config.enabled();
        for (int i = 0; i < 4; i++) fields[i].setText(Integer.toString(config.seconds(i)));
        editing = false; validate();
    }
    private void validate() {
        DayNightConfig config = draft();
        toggle.setText("自定义昼夜循环：" + (enabled ? "开启" : "关闭（原版速度）"));
        total.setText(config == null ? "每段请输入 1～86400 的整数秒" :
                "全天总时长：" + (config.dawn() + config.day() + config.dusk() + config.night()) / 60.0 + " 分钟");
        save.setEnabled(ready && editable && !pending && config != null);
        toggle.setEnabled(ready && editable && !pending);
        for (EditText field : fields) field.setEnabled(ready && editable && !pending);
    }
    private void submit(int action) {
        DayNightConfig config = draft();
        if (!ready || !editable || pending || config == null) return;
        pending = true; status.setText("正在等待服务器确认……"); resume.setEnabled(false); validate();
        DayNightClient.send(new DayNightPackets.Request(action, revision, config));
    }
    public static void receive(DayNightPackets.State packet) {
        DayNightConfigScreen view = active;
        if (view == null || view.status == null) return;
        view.revision = packet.revision(); view.ready = true; view.pending = false; view.editable = packet.editable();
        view.fill(packet.config());
        view.status.setText(packet.message() + (packet.editable() ? "" : "（只读）")
                + (packet.daylight() ? "" : "；游戏规则已暂停昼夜循环"));
        view.resume.setEnabled(packet.editable() && !packet.daylight());
    }
    public static void disconnect() {
        if (active != null && active.status != null) {
            active.ready = false; active.pending = false; active.validate(); active.resume.setEnabled(false);
            active.status.setText("未连接世界，或服务器未安装昼夜时长功能");
        }
    }
    @Override public void onDestroyView() {
        if (active == this) active = null;
        super.onDestroyView();
    }
}
