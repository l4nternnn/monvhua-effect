package com.kuilunfuzhe.monvhua.gui.action;

import com.google.gson.Gson;
import icyllis.modernui.graphics.drawable.ColorDrawable;
import com.kuilunfuzhe.monvhua.features.action.ActionConfig;
import com.kuilunfuzhe.monvhua.features.action.TimelineClientState;
import com.kuilunfuzhe.monvhua.model.ModModelLayers;
import com.kuilunfuzhe.monvhua.network.action.*;
import icyllis.modernui.core.Context;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.text.Editable;
import icyllis.modernui.text.TextWatcher;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.*;
import icyllis.modernui.widget.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

import java.util.*;

/**
 * Modern UI 动作编辑器 Fragment。
 * 三栏布局：左栏动作列表 | 中栏时间轴+预览 | 右栏编辑表单。
 */
public class ActionEditorFragment extends Fragment {
    private static final Gson GSON = new Gson();
    private static final float PREVIEW_Y_PIVOT = 1.601F;
    private static final String[] ACTION_TYPES = {"CHAT", "COMMAND", "SOUND", "ATTRIBUTE", "TELEPORT", "GIVE_ITEM", "POTION_EFFECT", "TITLE", "BODY_POSE"};
    private static final String[] TRIGGER_TYPES = {"MANUAL", "CHAT_KEYWORD", "ATTACK", "TIMER_INTERVAL", "TIMER_DELAY", "PLAYER_JOIN", "PLAYER_DEATH"};
    private static final String[] BODY_PARTS = {"head", "torso", "left_arm", "right_arm", "left_leg", "right_leg"};
    private static final String[] BODY_PART_LABELS = {"头部", "躯干", "左臂", "右臂", "左腿", "右腿"};
    private static final String[] BODY_AXES = {"pitch", "yaw", "roll"};

    /** 当前活跃实例，用于网络回调 */
    public static ActionEditorFragment activeInstance;

    // ── 数据模型 ──
    private ActionConfig localConfig = new ActionConfig();
    private int selectedIndex = -1;
    private int selectedTrigger = -1;
    private int actionTypeIdx = 0;
    private boolean fileListOpen = false;
    private final List<String> availableFiles = new ArrayList<>();
    private final List<String> previewLog = new ArrayList<>();
    private int timelineAssignSecond = 0;
    private int selectedTimelineSecond = 0;
    private boolean dirty = false;
    private long lastChangeTime = 0;
    private static final long SAVE_DEBOUNCE = 1200;

    // ── 模型预览 ──
    private PlayerEntityModel previewModel;
    private Identifier previewTexture;
    private boolean previewSlimModel = false;
    private boolean timelineDragging = false;
    private int lastTimelineSeekSecond = -1;
    private long timelineClientLastSecondAt = 0L;
    private int timelineClientBaseSecond = 0;
    private int lastRenderedTimelineSecond = -1;
    private final float[] previewAngles = new float[18]; // 6 parts × pitch,yaw,roll

    // ── 视图引用 ──
    private View rootView;
    private ListView actionListView;
    private ActionListAdapter actionListAdapter;
    private LinearLayout rightPanelContainer;
    private LinearLayout timelineRowsContainer;
    private TextView logTextView;
    private Button pauseBtn;
    private Button loopBtn;
    private Button previewBtn;
    private EditText timelineAssignSecondField;
    private LinearLayout fileListContainer;
    private View previewSurfaceView;
    private View timelineScrubberView;
    private int timelineSurfaceWidth;
    private int timelineSurfaceHeight;

    // ── 自动保存 ──
    private final Runnable autoSaveRunnable = () -> {
        if (dirty && System.currentTimeMillis() - lastChangeTime >= SAVE_DEBOUNCE) {
            ClientPlayNetworking.send(new UpdateActionsConfigC2SPacket(localConfig.toJson()));
        }
    };

    // ══════════════════════════════════════════════════
    //  静态方法 —— 打开编辑器
    // ══════════════════════════════════════════════════

    public static void open() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        Screen screen = MuiModApi.get().createScreen(new ActionEditorFragment());
        client.setScreen(screen);
    }

    // ══════════════════════════════════════════════════
    //  Fragment 生命周期
    // ══════════════════════════════════════════════════

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, DataSet savedInstanceState) {
        Context ctx = getContext();

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackground(new ColorDrawable(0xCC000000));

        // 左栏：动作列表
        View left = createLeftPanel(ctx);
        root.addView(left, new LinearLayout.LayoutParams(160, -1));

        // 中栏：时间轴 + 预览
        View center = createCenterPanel(ctx);
        root.addView(center, new LinearLayout.LayoutParams(0, -1, 1f));

        // 右栏：编辑表单
        View right = createRightPanel(ctx);
        root.addView(right, new LinearLayout.LayoutParams(400, -1));

        rootView = root;
        return root;
    }

    @Override
    public void onViewCreated(View view, DataSet savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // 初始化预览模型
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            var skinTextures = mc.player.getSkinTextures();
            previewTexture = skinTextures.texture();
            boolean slim = skinTextures.model() == net.minecraft.client.util.SkinTextures.Model.SLIM;
            previewSlimModel = slim;
            ModelPart rootPart = mc.getLoadedEntityModels().getModelPart(
                    slim ? ModModelLayers.COMBINED_BODY_SLIM : ModModelLayers.COMBINED_BODY);
            previewModel = new PlayerEntityModel(rootPart, slim);
        }
        // 初始重建（空状态）
        rebuildTimelineRows();
    }

    @Override
    public void onResume() {
        super.onResume();
        activeInstance = this;
        ClientPlayNetworking.send(new RequestActionsConfigC2SPacket());
    }

    @Override
    public void onPause() {
        super.onPause();
        activeInstance = null;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        rootView = null;
        previewSurfaceView = null;
        timelineScrubberView = null;
        timelineSurfaceWidth = 0;
        timelineSurfaceHeight = 0;
        activeInstance = null;
    }

    // ══════════════════════════════════════════════════
    //  左栏 —— 动作列表
    // ══════════════════════════════════════════════════

    private View createLeftPanel(Context ctx) {
        LinearLayout panel = new LinearLayout(ctx);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(new ColorDrawable(0xDD1A1A2E));

        TextView header = new TextView(ctx);
        header.setText("§l动作列表");
        header.setTextSize(16);
        header.setPadding(8, 8, 4, 4);
        panel.addView(header, new LinearLayout.LayoutParams(-1, -2));

        actionListAdapter = new ActionListAdapter(ctx);
        actionListView = new ListView(ctx);
        actionListView.setAdapter(actionListAdapter);
        actionListView.setOnItemClickListener((parent, view, pos, id) -> select(pos));
        panel.addView(actionListView, new LinearLayout.LayoutParams(-1, -1, 1f));

        // 底部按钮栏
        LinearLayout btnBar = new LinearLayout(ctx);
        btnBar.setOrientation(LinearLayout.HORIZONTAL);
        btnBar.setPadding(4, 4, 4, 4);

        Button addBtn = new Button(ctx);
        addBtn.setText("＋");
        addBtn.setOnClickListener(v -> newAction());
        btnBar.addView(addBtn, new LinearLayout.LayoutParams(-2, -2));

        Button delBtn = new Button(ctx);
        delBtn.setText("×");
        delBtn.setOnClickListener(v -> delAction());
        btnBar.addView(delBtn, new LinearLayout.LayoutParams(-2, -2));

        Button fileBtn = new Button(ctx);
        fileBtn.setText("▼ 文件");
        fileBtn.setOnClickListener(v -> toggleFileList(fileBtn));
        btnBar.addView(fileBtn, new LinearLayout.LayoutParams(0, -2, 1f));

        panel.addView(btnBar, new LinearLayout.LayoutParams(-1, -2));

        // 文件列表容器
        fileListContainer = new LinearLayout(ctx);
        fileListContainer.setOrientation(LinearLayout.VERTICAL);
        fileListContainer.setVisibility(View.GONE);
        panel.addView(fileListContainer, new LinearLayout.LayoutParams(-1, -2));

        return panel;
    }

    private void toggleFileList(Button fileBtn) {
        fileListOpen = !fileListOpen;
        fileBtn.setText(fileListOpen ? "▲ 文件" : "▼ 文件");
        if (fileListOpen) {
            ClientPlayNetworking.send(new ListActionFilesC2SPacket());
        }
        rebuildFileList();
    }

    private void rebuildFileList() {
        fileListContainer.removeAllViews();
        if (!fileListOpen || availableFiles.isEmpty()) {
            fileListContainer.setVisibility(View.GONE);
            return;
        }
        fileListContainer.setVisibility(View.VISIBLE);
        Context ctx = getContext();
        if (ctx == null) return;
        for (String fn : availableFiles) {
            Button b = new Button(ctx);
            b.setText(trunc(fn, 18));
            b.setOnClickListener(v -> {
                ClientPlayNetworking.send(new LoadActionFileC2SPacket(fn));
                fileListOpen = false;
            });
            fileListContainer.addView(b, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    // ══════════════════════════════════════════════════
    //  中栏 —— 时间轴 + 预览
    // ══════════════════════════════════════════════════

    private View createCenterPanel(Context ctx) {
        LinearLayout panel = new LinearLayout(ctx);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(4, 0, 4, 0);
        panel.setBackground(new ColorDrawable(0xDD0D0D1A));

        // 信息栏
        TextView infoBar = new TextView(ctx);
        infoBar.setId(View.generateViewId());
        panel.addView(infoBar, new LinearLayout.LayoutParams(-1, -2));
        timelineScrubberView = createTimelineScrubber(ctx);
        panel.addView(timelineScrubberView, new LinearLayout.LayoutParams(-1, 36));

        // 时间轴滚动区域
        ScrollView timelineScroll = new ScrollView(ctx);
        timelineRowsContainer = new LinearLayout(ctx);
        timelineRowsContainer.setOrientation(LinearLayout.VERTICAL);
        timelineScroll.addView(timelineRowsContainer, new FrameLayout.LayoutParams(-1, -2));
        panel.addView(timelineScroll, new LinearLayout.LayoutParams(-1, 0, 0.85f));

        // 模型预览（通过 MinecraftSurfaceView 在 Modern UI 渲染管线中绘制）
        // Actual model rendering is handled by renderVanillaPreview().
        FrameLayout previewHost = new FrameLayout(ctx);
        previewHost.setBackground(new ColorDrawable(0x33000000));
        previewSurfaceView = previewHost;
        panel.addView(previewHost, new LinearLayout.LayoutParams(-1, 200));
        panel.removeView(timelineScroll);
        panel.removeView(timelineScrubberView);
        panel.addView(timelineScroll, new LinearLayout.LayoutParams(-1, 0, 0.85f));
        panel.addView(timelineScrubberView, new LinearLayout.LayoutParams(-1, 36));

        // 控制按钮
        LinearLayout ctrlBar = new LinearLayout(ctx);
        ctrlBar.setOrientation(LinearLayout.HORIZONTAL);

        Button startBtn = new Button(ctx);
        startBtn.setText("▶开始");
        startBtn.setOnClickListener(v -> ClientPlayNetworking.send(new TimelineControlC2SPacket("START", 0, "")));
        ctrlBar.addView(startBtn, new LinearLayout.LayoutParams(0, -2, 1f));

        Button stopBtn = new Button(ctx);
        stopBtn.setText("■停止");
        stopBtn.setOnClickListener(v -> ClientPlayNetworking.send(new TimelineControlC2SPacket("STOP", 0, "")));
        ctrlBar.addView(stopBtn, new LinearLayout.LayoutParams(0, -2, 1f));

        pauseBtn = new Button(ctx);
        pauseBtn.setText("⏸暂停");
        pauseBtn.setOnClickListener(v -> {
            if (TimelineClientState.paused)
                ClientPlayNetworking.send(new TimelineControlC2SPacket("RESUME", 0, ""));
            else
                ClientPlayNetworking.send(new TimelineControlC2SPacket("PAUSE", 0, ""));
        });
        ctrlBar.addView(pauseBtn, new LinearLayout.LayoutParams(0, -2, 1f));

        loopBtn = new Button(ctx);
        loopBtn.setText("循环:关");
        loopBtn.setOnClickListener(v -> {
            boolean newLoop = !TimelineClientState.loop;
            ClientPlayNetworking.send(new TimelineControlC2SPacket(newLoop ? "SET_LOOP" : "SET_LOOP_OFF", 0, ""));
        });
        ctrlBar.addView(loopBtn, new LinearLayout.LayoutParams(0, -2, 1f));

        panel.addView(ctrlBar, new LinearLayout.LayoutParams(-1, -2));

        // 状态文本
        timelineStatusBtn = new Button(ctx);
        timelineStatusBtn.setEnabled(false);
        panel.addView(timelineStatusBtn, new LinearLayout.LayoutParams(-1, -2));

        // 预览按钮栏
        LinearLayout previewBar = new LinearLayout(ctx);
        previewBar.setOrientation(LinearLayout.HORIZONTAL);

        previewBtn = new Button(ctx);
        previewBtn.setText("▶ 预览执行");
        previewBtn.setOnClickListener(v -> doPreview());
        previewBar.addView(previewBtn, new LinearLayout.LayoutParams(0, -2, 1f));

        Button timelinePreviewBtn = new Button(ctx);
        timelinePreviewBtn.setText("▶ 预览时间轴");
        timelinePreviewBtn.setOnClickListener(v -> {
            appendLog("Timeline preview requested...");
            ClientPlayNetworking.send(new PreviewTimelineC2SPacket());
        });
        previewBar.addView(timelinePreviewBtn, new LinearLayout.LayoutParams(0, -2, 1f));

        panel.addView(previewBar, new LinearLayout.LayoutParams(-1, -2));

        // 预览日志
        logScrollView = new ScrollView(ctx);
        logTextView = new TextView(ctx);
        logTextView.setTextColor(0xFFBBBBBB);
        logTextView.setTextSize(12);
        logTextView.setPadding(4, 4, 4, 4);
        logScrollView.addView(logTextView, new FrameLayout.LayoutParams(-1, -2));
        panel.addView(logScrollView, new LinearLayout.LayoutParams(-1, 0, 0.45f));

        return panel;
    }

    // ══════════════════════════════════════════════════
    //  右栏 —— 编辑表单
    // ══════════════════════════════════════════════════

    private View createTimelineScrubber(Context ctx) {
        View scrubber = new View(ctx);
        scrubber.setBackground(new ColorDrawable(0x33111118));
        scrubber.setFocusable(true);
        scrubber.setClickable(true);
        scrubber.setOnTouchListener(this::handleTimelineTouch);
        return scrubber;
    }

    private void renderTimelineScrubber(DrawContext dCtx, int w, int h) {
        renderTimelineScrubber(dCtx, 0, 0, w, h);
    }

    private void renderTimelineScrubber(DrawContext dCtx, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        dCtx.fill(x, y, x + w, y + h, 0x66111118);
        dCtx.drawBorder(x, y, w, h, 0x554466AA);

        MinecraftClient client = MinecraftClient.getInstance();
        int maxSecond = getTimelineMaxSecond();
        int left = x + 12;
        int right = Math.max(left + 1, x + w - 12);
        int railY = y + Math.max(24, h - 16);
        dCtx.fill(left, railY - 2, right, railY + 2, 0xFF3A3A46);

        for (Integer second : localConfig.timelineSchedule.keySet()) {
            if (second == null || second < 0) continue;
            int markX = left + Math.round((right - left) * (Math.min(second, maxSecond) / (float) maxSecond));
            dCtx.fill(markX - 1, railY - 7, markX + 1, railY + 7, 0xFF77AAFF);
        }

        int current = Math.max(0, Math.min(TimelineClientState.currentSecond, maxSecond));
        int thumbX = left + Math.round((right - left) * (current / (float) maxSecond));
        dCtx.fill(thumbX - 3, railY - 10, thumbX + 3, railY + 10, 0xFFFFDD55);
        dCtx.drawTextWithShadow(client.textRenderer, "Timeline " + current + " / " + maxSecond + "s", x + 8, y + 6, 0xFFE8E8E8);
    }

    private boolean handleTimelineTouch(View view, MotionEvent event) {
        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN, MotionEvent.ACTION_BUTTON_PRESS -> {
                timelineDragging = true;
                lastTimelineSeekSecond = -1;
                seekTimelineAt(view, event.getX());
                return true;
            }
            case MotionEvent.ACTION_MOVE -> {
                if (!timelineDragging) return false;
                seekTimelineAt(view, event.getX());
                return true;
            }
            case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_BUTTON_RELEASE -> {
                if (timelineDragging) seekTimelineAt(view, event.getX());
                timelineDragging = false;
                lastTimelineSeekSecond = -1;
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void seekTimelineAt(View view, float x) {
        int maxSecond = getTimelineMaxSecond();
        int left = 12;
        int right = Math.max(left + 1, view.getWidth() - 12);
        float progress = Math.max(0.0F, Math.min(1.0F, (x - left) / (float) (right - left)));
        int second = Math.round(progress * maxSecond);
        if (second == lastTimelineSeekSecond) return;
        lastTimelineSeekSecond = second;
        selectTimelineSecond(second, false);
        TimelineClientState.currentSecond = second;
        TimelineClientState.totalSeconds = Math.max(TimelineClientState.totalSeconds, maxSecond);
        ClientPlayNetworking.send(new TimelineControlC2SPacket("JUMP", second, ""));
        updateTimelineStatus();
    }

    private int getTimelineMaxSecond() {
        int max = Math.max(1, TimelineClientState.totalSeconds);
        for (Integer second : localConfig.timelineSchedule.keySet()) {
            if (second != null) max = Math.max(max, second);
        }
        return Math.max(1, max);
    }

    public static void renderVanillaPreview(Screen screen, DrawContext dCtx, int mouseX, int mouseY, float tickDelta) {
        ActionEditorFragment inst = activeInstance;
        MinecraftClient client = MinecraftClient.getInstance();
        if (inst == null || client.currentScreen != screen) return;
        inst.renderTimelineScrubber(screen, dCtx);
        inst.renderVanillaPreview(screen, dCtx);
    }

    private void renderTimelineScrubber(Screen screen, DrawContext dCtx) {
        if (timelineScrubberView == null || !timelineScrubberView.isShown()) return;
        int[] location = new int[2];
        timelineScrubberView.getLocationInWindow(location);
        double guiScale = Math.max(1.0D, MinecraftClient.getInstance().getWindow().getScaleFactor());
        int w = Math.max(1, (int) Math.round(timelineScrubberView.getWidth() / guiScale));
        int h = Math.max(1, (int) Math.round(timelineScrubberView.getHeight() / guiScale));
        if (w <= 0 || h <= 0) return;
        int x = (int) Math.round(location[0] / guiScale);
        int y = (int) Math.round(location[1] / guiScale);
        if (x < 0 || y < 0 || x >= screen.width || y >= screen.height) return;

        int drawW = Math.max(1, Math.min(w, screen.width - x));
        int drawH = Math.max(1, Math.min(h, screen.height - y));
        renderTimelineScrubber(dCtx, x, y, drawW, drawH);
    }

    private void renderVanillaPreview(Screen screen, DrawContext dCtx) {
        if (previewSurfaceView == null || !previewSurfaceView.isShown()) return;
        int[] location = new int[2];
        previewSurfaceView.getLocationInWindow(location);
        double guiScale = Math.max(1.0D, MinecraftClient.getInstance().getWindow().getScaleFactor());
        int w = Math.max(1, (int) Math.round(previewSurfaceView.getWidth() / guiScale));
        int h = Math.max(1, (int) Math.round(previewSurfaceView.getHeight() / guiScale));
        if (w <= 0 || h <= 0) return;
        int x = (int) Math.round(location[0] / guiScale);
        int y = (int) Math.round(location[1] / guiScale);
        if (x < 0 || y < 0 || x >= screen.width || y >= screen.height) {
            x = 164;
            y = 24;
            w = Math.max(80, screen.width - 364);
            h = 220;
        } else {
            w = Math.max(1, Math.min(w, screen.width - x));
            h = Math.max(1, Math.min(h, screen.height - y));
        }
        renderActionPreview(dCtx, x, y, w, h);
    }

    private void renderActionPreview(DrawContext dCtx, int w, int h) {
        renderActionPreview(dCtx, 0, 0, w, h);
    }

    private void renderActionPreview(DrawContext dCtx, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        dCtx.fill(x, y, x + w, y + h, 0x66000000);
        dCtx.drawBorder(x, y, w, h, 0x664466AA);
        MinecraftClient client = MinecraftClient.getInstance();
        if (previewModel == null || previewTexture == null) {
            dCtx.drawCenteredTextWithShadow(client.textRenderer, "No model", x + w / 2, y + h / 2 - 4, 0xFFAAAAAA);
            return;
        }

        setModelAngles(previewModel);
        int pad = 6;
        int x1 = x + pad;
        int y1 = y + pad;
        int x2 = Math.max(x1 + 1, x + w - pad);
        int y2 = Math.max(y1 + 1, y + h - pad);
        float scale = Math.max(24.0F, Math.min(x2 - x1, y2 - y1) * 0.46F);
        int renderBottom = Math.min(y2, Math.max(y1 + 1,
                Math.round((y1 + y2) * 0.5F + scale * PREVIEW_Y_PIVOT)));

        dCtx.addPlayerSkin(previewModel, previewTexture, scale, 0.0F, 0.0F, PREVIEW_Y_PIVOT,
                x1, y1, x2, renderBottom);
    }

    private void invalidatePreview() {
        if (previewSurfaceView != null) previewSurfaceView.invalidate();
    }

    /*
    private void updateTimelineStatusBroken() {
        if (timelineStatusBtn != null) {
            String s = String.format("褰撳墠: %d/%d绉?%s",
                    TimelineClientState.currentSecond,
                    TimelineClientState.totalSeconds,
                    TimelineClientState.running ? (TimelineClientState.paused ? "搂e鏆傚仠" : "搂a杩愯涓?) : "搂7宸插仠姝?);
            timelineStatusBtn.setText(s);
        }
        if (timelineScrubberView != null) timelineScrubberView.invalidate();
    }

    */

    private void updateTimelineStatus() {
        if (timelineStatusBtn != null) {
            String state = TimelineClientState.paused ? "paused"
                    : (TimelineClientState.running ? "running" : "stopped");
            timelineStatusBtn.setText(String.format("Current: %d/%ds %s",
                    TimelineClientState.currentSecond, TimelineClientState.totalSeconds, state));
        }
        if (timelineScrubberView != null) timelineScrubberView.invalidate();
    }

    private View createRightPanel(Context ctx) {
        ScrollView scrollView = new ScrollView(ctx);
        scrollView.setBackground(new ColorDrawable(0xDD1A1A2E));
        rightPanelContainer = new LinearLayout(ctx);
        rightPanelContainer.setOrientation(LinearLayout.VERTICAL);
        rightPanelContainer.setPadding(8, 8, 8, 8);
        scrollView.addView(rightPanelContainer, new FrameLayout.LayoutParams(-1, -2));
        return scrollView;
    }

    private void rebuildRightPanel() {
        Context ctx = getContext();
        if (ctx == null) return;
        rightPanelContainer.removeAllViews();

        if (selectedIndex < 0 || selectedIndex >= localConfig.actions.size()) {
            // 未选择时显示提示
            TextView hint = new TextView(ctx);
            hint.setText("§7选择一个动作编辑");
            hint.setTextColor(0xFF666666);
            rightPanelContainer.addView(hint, new LinearLayout.LayoutParams(-1, -2));
            return;
        }

        ActionConfig.ActionDef def = localConfig.actions.get(selectedIndex);
        String typeUp = def.actionType.toUpperCase();

        // ID
        addLabel(ctx, "ID");
        EditText idField = new EditText(ctx);
        idField.setText(def.id != null ? def.id : "");
        idField.addTextChangedListener(new STWatcher(s -> {
            def.id = s.toString();
            markDirty();
            actionListAdapter.notifyDataSetChanged();
        }));
        rightPanelContainer.addView(idField, new LinearLayout.LayoutParams(-1, -2));

        // Name
        addLabel(ctx, "名称");
        EditText nameField = new EditText(ctx);
        nameField.setText(def.name != null ? def.name : "");
        nameField.addTextChangedListener(new STWatcher(s -> {
            def.name = s.toString();
            markDirty();
        }));
        rightPanelContainer.addView(nameField, new LinearLayout.LayoutParams(-1, -2));

        // 时间轴分配
        addLabel(ctx, "时间轴");
        LinearLayout assignBar = new LinearLayout(ctx);
        assignBar.setOrientation(LinearLayout.HORIZONTAL);
        EditText secField = new EditText(ctx);
        timelineAssignSecondField = secField;
        secField.setText(String.valueOf(timelineAssignSecond));
        secField.addTextChangedListener(new STWatcher(s -> {
            try {
                timelineAssignSecond = Math.max(0, Integer.parseInt(s.toString()));
                selectedTimelineSecond = timelineAssignSecond;
                rebuildTimelineRows();
            } catch (Exception ignored) {}
        }));
        assignBar.addView(secField, new LinearLayout.LayoutParams(0, -2, 1f));
        Button addTimelineBtn = new Button(ctx);
        addTimelineBtn.setText("+ 时间轴");
        addTimelineBtn.setOnClickListener(v -> {
            if (def.id != null && !def.id.isEmpty()) {
                applyLocalTimelineAdd(timelineAssignSecond, def.id);
                ClientPlayNetworking.send(new TimelineControlC2SPacket("ADD", timelineAssignSecond, def.id));
            }
        });
        assignBar.addView(addTimelineBtn, new LinearLayout.LayoutParams(0, -2, 1f));
        Button saveTimelineSecondBtn = new Button(ctx);
        saveTimelineSecondBtn.setText("\u4fdd\u5b58\u5230\u79d2\u8282\u70b9");
        saveTimelineSecondBtn.setOnClickListener(v -> saveCurrentActionToSelectedTimelineSecond());
        assignBar.addView(saveTimelineSecondBtn, new LinearLayout.LayoutParams(0, -2, 1f));
        rightPanelContainer.addView(assignBar, new LinearLayout.LayoutParams(-1, -2));

        // 出现在哪些秒
        List<String> secs = new ArrayList<>();
        if (def.id != null) {
            for (Map.Entry<Integer, List<String>> e : localConfig.timelineSchedule.entrySet()) {
                for (String timelineId : e.getValue()) {
                    if (def.id.equals(timelineId) || def.id.equals(getTimelineSourceActionId(timelineId))) {
                        secs.add(String.valueOf(e.getKey()));
                        break;
                    }
                }
            }
        }
        addLabel(ctx, "出现在: " + (secs.isEmpty() ? "无" : String.join("s, ", secs) + "s"));

        // 动作类型循环
        actionTypeIdx = indexOf(ACTION_TYPES, typeUp);
        Button typeBtn = new Button(ctx);
        typeBtn.setText(typeUp);
        typeBtn.setOnClickListener(v -> {
            actionTypeIdx = (actionTypeIdx + 1) % ACTION_TYPES.length;
            def.actionType = ACTION_TYPES[actionTypeIdx];
            typeBtn.setText(def.actionType.toUpperCase());
            markDirty();
            rebuildRightPanel();
        });
        rightPanelContainer.addView(typeBtn, new LinearLayout.LayoutParams(-1, -2));

        // 启用 + 权限
        LinearLayout enableBar = new LinearLayout(ctx);
        enableBar.setOrientation(LinearLayout.HORIZONTAL);
        Button enabledBtn = new Button(ctx);
        enabledBtn.setText(def.enabled ? "§a启用" : "§c禁用");
        enabledBtn.setOnClickListener(v -> {
            def.enabled = !def.enabled;
            enabledBtn.setText(def.enabled ? "§a启用" : "§c禁用");
            markDirty();
            actionListAdapter.notifyDataSetChanged();
        });
        enableBar.addView(enabledBtn, new LinearLayout.LayoutParams(0, -2, 1f));

        EditText permField = new EditText(ctx);
        permField.setText(String.valueOf(def.requiredPermissionLevel));
        permField.addTextChangedListener(new STWatcher(s -> {
            try { def.requiredPermissionLevel = Integer.parseInt(s.toString()); } catch (Exception ignored) {}
            markDirty();
        }));
        enableBar.addView(permField, new LinearLayout.LayoutParams(0, -2, 1f));
        rightPanelContainer.addView(enableBar, new LinearLayout.LayoutParams(-1, -2));

        // BODY_POSE 特殊编辑器
        if ("BODY_POSE".equals(typeUp)) {
            addBodyPoseEditor(ctx, def);
        } else {
            addStandardParams(ctx, def, typeUp);
        }

        // ── 触发器区域 ──
        addLabel(ctx, "触发器");
        LinearLayout trigBtnBar = new LinearLayout(ctx);
        trigBtnBar.setOrientation(LinearLayout.HORIZONTAL);
        Button addTrigBtn = new Button(ctx);
        addTrigBtn.setText("+ 触发器");
        addTrigBtn.setOnClickListener(v -> {
            ActionConfig.TriggerDef t = new ActionConfig.TriggerDef();
            t.type = "MANUAL";
            def.triggers.add(t);
            selectedTrigger = def.triggers.size() - 1;
            markDirty();
            rebuildRightPanel();
        });
        trigBtnBar.addView(addTrigBtn, new LinearLayout.LayoutParams(0, -2, 1f));

        Button delTrigBtn = new Button(ctx);
        delTrigBtn.setText("× 触发器");
        delTrigBtn.setEnabled(selectedTrigger >= 0 && selectedTrigger < def.triggers.size());
        delTrigBtn.setOnClickListener(v -> {
            if (selectedTrigger >= 0 && selectedTrigger < def.triggers.size()) {
                def.triggers.remove(selectedTrigger);
                selectedTrigger = def.triggers.isEmpty() ? -1 : Math.min(selectedTrigger, def.triggers.size() - 1);
                markDirty();
                rebuildRightPanel();
            }
        });
        trigBtnBar.addView(delTrigBtn, new LinearLayout.LayoutParams(0, -2, 1f));
        rightPanelContainer.addView(trigBtnBar, new LinearLayout.LayoutParams(-1, -2));

        // 当前触发器编辑
        if (selectedTrigger >= 0 && selectedTrigger < def.triggers.size()) {
            ActionConfig.TriggerDef trig = def.triggers.get(selectedTrigger);
            int trigTypeIdx = indexOf(TRIGGER_TYPES, trig.type.toUpperCase());

            Button trigTypeBtn = new Button(ctx);
            trigTypeBtn.setText(trig.type.toUpperCase());
            trigTypeBtn.setOnClickListener(v -> {
                int idx = indexOf(TRIGGER_TYPES, trig.type.toUpperCase());
                int nextIdx = (idx + 1) % TRIGGER_TYPES.length;
                trig.type = TRIGGER_TYPES[nextIdx];
                trigTypeBtn.setText(trig.type.toUpperCase());
                markDirty();
                rebuildRightPanel();
            });
            rightPanelContainer.addView(trigTypeBtn, new LinearLayout.LayoutParams(-1, -2));

            addTriggerParams(ctx, trig, trig.type.toUpperCase());
        }

        // 保存按钮
        Button saveBtn = new Button(ctx);
        saveBtn.setText("💾 保存");
        saveBtn.setPadding(0, 8, 0, 8);
        saveBtn.setOnClickListener(v -> forceSave());
        rightPanelContainer.addView(saveBtn, new LinearLayout.LayoutParams(-1, -2));

        // 更新模型预览
        updatePreviewFromDef();
    }

    private void addLabel(Context ctx, String text) {
        TextView label = new TextView(ctx);
        label.setText(text);
        label.setTextSize(11);
        label.setTextColor(0xFF888888);
        label.setPadding(0, 6, 0, 1);
        rightPanelContainer.addView(label, new LinearLayout.LayoutParams(-1, -2));
    }

    private void addBodyPoseEditor(Context ctx, ActionConfig.ActionDef def) {
        addLabel(ctx, "皮肤");
        EditText skinField = new EditText(ctx);
        String skin = def.actionParams.getOrDefault("skin_name", "ema").toString();
        if (skin.isEmpty()) skin = "ema";
        skinField.setText(skin);
        skinField.addTextChangedListener(new STWatcher(s -> {
            def.actionParams.put("skin_name", s.toString().isEmpty() ? "ema" : s.toString());
            markDirty();
        }));
        rightPanelContainer.addView(skinField, new LinearLayout.LayoutParams(-1, -2));

        boolean slim = "true".equals(String.valueOf(def.actionParams.getOrDefault("slim_model", false)));
        Button slimBtn = new Button(ctx);
        slimBtn.setText("模型: " + (slim ? "Slim" : "Default"));
        slimBtn.setOnClickListener(v -> {
            boolean newVal = !"true".equals(String.valueOf(def.actionParams.getOrDefault("slim_model", false)));
            def.actionParams.put("slim_model", newVal);
            slimBtn.setText("模型: " + (newVal ? "Slim" : "Default"));
            markDirty();
        });
        rightPanelContainer.addView(slimBtn, new LinearLayout.LayoutParams(-1, -2));

        addLabel(ctx, "持续Tick");
        EditText durationField = new EditText(ctx);
        durationField.setText(formatFloat(def.actionParams.getOrDefault("durationTicks", 40)));
        durationField.addTextChangedListener(new STWatcher(s -> {
            String txt = s.toString();
            if (txt.isEmpty()) def.actionParams.remove("durationTicks");
            else { try { def.actionParams.put("durationTicks", Double.parseDouble(txt)); } catch (Exception ignored) {} }
            markDirty();
        }));
        rightPanelContainer.addView(durationField, new LinearLayout.LayoutParams(-1, -2));

        boolean spawnModel = "true".equals(String.valueOf(def.actionParams.getOrDefault("spawn_model", false)));
        Button spawnModelBtn = new Button(ctx);
        spawnModelBtn.setText("生成模型: " + (spawnModel ? "开" : "关"));
        spawnModelBtn.setOnClickListener(v -> {
            boolean newVal = !"true".equals(String.valueOf(def.actionParams.getOrDefault("spawn_model", false)));
            def.actionParams.put("spawn_model", newVal);
            spawnModelBtn.setText("生成模型: " + (newVal ? "开" : "关"));
            markDirty();
        });
        rightPanelContainer.addView(spawnModelBtn, new LinearLayout.LayoutParams(-1, -2));

        for (int i = 0; i < 6; i++) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            TextView label = new TextView(ctx);
            label.setText(BODY_PART_LABELS[i]);
            label.setTextColor(0xFFAAAAAA);
            label.setWidth(50);
            row.addView(label, new LinearLayout.LayoutParams(-2, -2));

            for (int j = 0; j < 3; j++) {
                String key = BODY_PARTS[i] + "_" + BODY_AXES[j];
                EditText f = new EditText(ctx);
                f.setHint(BODY_AXES[j].substring(0, 1).toUpperCase());
                Object val = def.actionParams.get(key);
                f.setText(val != null ? formatFloat(val) : "0");
                f.addTextChangedListener(new STWatcher(s -> {
                    String txt = s.toString();
                    if (txt.isEmpty() || "0".equals(txt) || "-0".equals(txt)) def.actionParams.remove(key);
                    else { try { def.actionParams.put(key, Double.parseDouble(txt)); } catch (Exception ignored) {} }
                    markDirty();
                }));
                installPoseFieldScroll(f, def, key, 1.0);
                row.addView(f, new LinearLayout.LayoutParams(0, -2, 1f));
            }
            rightPanelContainer.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private void addStandardParams(Context ctx, ActionConfig.ActionDef def, String typeUp) {
        String[][] spec = getParamSpec(typeUp);
        if (spec == null) return;
        for (String[] s : spec) {
            addLabel(ctx, s[1]);
            EditText f = new EditText(ctx);
            f.setHint(s[0]);
            Object val = def.actionParams.get(s[0]);
            if (val != null) {
                if (val instanceof List<?> l && !l.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (Object elem : l) {
                        if (sb.length() > 0) sb.append(',');
                        sb.append(elem);
                    }
                    f.setText(sb.toString());
                } else
                    f.setText(val.toString());
            }
            final String key = s[0];
            final String type = s[2];
            f.addTextChangedListener(new STWatcher(txt -> {
                updateParam(def, key, txt.toString(), type);
                markDirty();
            }));
            rightPanelContainer.addView(f, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private void addTriggerParams(Context ctx, ActionConfig.TriggerDef trig, String typeUp) {
        String[][] spec = getTriggerParamSpec(typeUp);
        if (spec == null) return;
        for (String[] s : spec) {
            addLabel(ctx, s[1]);
            EditText f = new EditText(ctx);
            f.setHint(s[0]);
            Object val = trig.params.get(s[0]);
            if (val != null) {
                if (val instanceof List<?> l && !l.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (Object elem : l) {
                        if (sb.length() > 0) sb.append(',');
                        sb.append(elem);
                    }
                    f.setText(sb.toString());
                } else
                    f.setText(val.toString());
            }
            final String key = s[0];
            final String type = s[2];
            f.addTextChangedListener(new STWatcher(txt -> {
                updateTParam(trig, key, txt.toString(), type);
                markDirty();
            }));
            rightPanelContainer.addView(f, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    // ══════════════════════════════════════════════════
    //  时间轴表格
    // ══════════════════════════════════════════════════

    private void rebuildTimelineRows() {
        if (useCompactTimelineRows()) {
            rebuildTimelineRowsCompact();
            return;
        }
        if (timelineRowsContainer == null || getContext() == null) return;
        Context ctx = getContext();
        timelineRowsContainer.removeAllViews();

        Map<Integer, List<String>> schedule = localConfig.timelineSchedule;
        List<Integer> sorted = new ArrayList<>(schedule.keySet());
        Collections.sort(sorted);

        int curSec = TimelineClientState.currentSecond;

        if (sorted.isEmpty()) {
            TextView empty = new TextView(ctx);
            empty.setText("§7时间轴为空 — 使用右侧面板添加动作");
            empty.setPadding(8, 8, 8, 8);
            timelineRowsContainer.addView(empty, new LinearLayout.LayoutParams(-1, -2));
        }

        for (int sec : sorted) {
            List<String> ids = schedule.get(sec);
            if (ids == null) ids = Collections.emptyList();

            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(4, 2, 4, 2);

            // 秒标签
            TextView secLabel = new TextView(ctx);
            secLabel.setText(sec + "s");
            secLabel.setTextColor(sec == curSec ? 0xFF55FF55 : 0xFFAAAAAA);
            secLabel.setWidth(36);
            row.addView(secLabel, new LinearLayout.LayoutParams(-2, -2));

            if (ids.isEmpty()) {
                TextView emptyLabel = new TextView(ctx);
                emptyLabel.setText("(empty)");
                emptyLabel.setTextColor(0xFF666666);
                row.addView(emptyLabel, new LinearLayout.LayoutParams(0, -2, 1f));
            }

            for (String aid : ids) {
                ActionConfig.ActionDef def = localConfig.findById(aid).orElse(null);

                // 动作药丸按钮
                Button pill = new Button(ctx);
                pill.setText(trunc(aid, 10));
                pill.setOnClickListener(v -> selectActionById(aid));
                row.addView(pill, new LinearLayout.LayoutParams(-2, -2));

                // × 移除
                Button rmBtn = new Button(ctx);
                rmBtn.setText("×");
                rmBtn.setOnClickListener(v -> {
                    applyLocalTimelineRemove(sec, aid);
                    ClientPlayNetworking.send(new TimelineControlC2SPacket("REMOVE", sec, aid));
                });
                row.addView(rmBtn, new LinearLayout.LayoutParams(-2, -2));

                // ↑ ↓ (仅当同一秒内有多个动作)
                if (ids.size() > 1) {
                    Button upBtn = new Button(ctx);
                    upBtn.setText("↑");
                    upBtn.setOnClickListener(v -> {
                        applyLocalTimelineMove(sec, aid, -1);
                        ClientPlayNetworking.send(new TimelineControlC2SPacket("MOVE_UP", sec, aid));
                    });
                    row.addView(upBtn, new LinearLayout.LayoutParams(-2, -2));
                    Button downBtn = new Button(ctx);
                    downBtn.setText("↓");
                    downBtn.setOnClickListener(v -> {
                        applyLocalTimelineMove(sec, aid, 1);
                        ClientPlayNetworking.send(new TimelineControlC2SPacket("MOVE_DOWN", sec, aid));
                    });
                    row.addView(downBtn, new LinearLayout.LayoutParams(-2, -2));
                }
            }

            // 删除整秒
            Button delSecBtn = new Button(ctx);
            delSecBtn.setText("⊗");
            delSecBtn.setOnClickListener(v -> {
                applyLocalTimelineRemoveSecond(sec);
                ClientPlayNetworking.send(new TimelineControlC2SPacket("REMOVE_SECOND", sec, ""));
            });
            row.addView(delSecBtn, new LinearLayout.LayoutParams(-2, -2));

            timelineRowsContainer.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }

        // 添加秒控件
        LinearLayout addBar = new LinearLayout(ctx);
        addBar.setOrientation(LinearLayout.HORIZONTAL);
        addBar.setPadding(4, 4, 4, 4);

        EditText addSecField = new EditText(ctx);
        addSecField.setHint("秒数");
        addSecField.setText("0");
        addBar.addView(addSecField, new LinearLayout.LayoutParams(0, -2, 1f));

        Button addBtn = new Button(ctx);
        addBtn.setText("添加秒");
        addBtn.setOnClickListener(v -> {
            try {
                int sec = Integer.parseInt(addSecField.getText().toString());
                applyLocalTimelineAdd(sec, "");
                ClientPlayNetworking.send(new TimelineControlC2SPacket("ADD", sec, ""));
                addSecField.getText().clear();
                addSecField.getText().append("0");
            } catch (Exception ignored) {}
        });
        addBar.addView(addBtn, new LinearLayout.LayoutParams(-2, -2));

        timelineRowsContainer.addView(addBar, new LinearLayout.LayoutParams(-1, -2));
    }

    private void applyLocalTimelineAdd(int second, String actionId) {
        int sec = Math.max(0, second);
        List<String> ids = localConfig.timelineSchedule.computeIfAbsent(sec, k -> new ArrayList<>());
        if (actionId != null && !actionId.isBlank()) {
            ids.add(actionId);
        }
        refreshTimelineUi();
    }

    private void applyLocalTimelineRemove(int second, String actionId) {
        List<String> ids = localConfig.timelineSchedule.get(second);
        if (ids != null) {
            ids.remove(actionId);
            if (ids.isEmpty()) localConfig.timelineSchedule.remove(second);
        }
        refreshTimelineUi();
    }

    private void applyLocalTimelineMove(int second, String actionId, int direction) {
        List<String> ids = localConfig.timelineSchedule.get(second);
        if (ids != null) {
            int idx = ids.indexOf(actionId);
            int next = idx + direction;
            if (idx >= 0 && next >= 0 && next < ids.size()) {
                Collections.swap(ids, idx, next);
            }
        }
        refreshTimelineUi();
    }

    private void applyLocalTimelineRemoveSecond(int second) {
        localConfig.timelineSchedule.remove(second);
        refreshTimelineUi();
    }

    private void refreshTimelineUi() {
        TimelineClientState.totalSeconds = Math.max(TimelineClientState.totalSeconds, getTimelineMaxSecond());
        rebuildTimelineRows();
        updateTimelineStatus();
    }

    // ══════════════════════════════════════════════════
    //  列表适配器
    // ══════════════════════════════════════════════════

    private void saveCurrentActionToSelectedTimelineSecond() {
        if (selectedIndex < 0 || selectedIndex >= localConfig.actions.size()) return;

        int sec = Math.max(0, selectedTimelineSecond);
        ActionConfig.ActionDef current = localConfig.actions.get(selectedIndex);
        ActionConfig.ActionDef snapshot = ActionConfig.copyActionDef(current);
        if (snapshot == null) return;

        String sourceId = getTimelineSourceActionId(current);
        if (sourceId == null || sourceId.isBlank()) {
            sourceId = current.id == null ? "" : current.id;
        }

        List<String> ids = localConfig.timelineSchedule.computeIfAbsent(sec, k -> new ArrayList<>());
        String targetId = null;
        int legacySourceIndex = -1;
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            ActionConfig.ActionDef item = localConfig.findById(id).orElse(null);
            if (item == null) continue;

            String itemSourceId = getTimelineSourceActionId(item);
            if (ActionConfig.isTimelineInstance(item)) {
                if (id.equals(current.id) || (!sourceId.isBlank() && sourceId.equals(itemSourceId))) {
                    targetId = id;
                    break;
                }
            } else if (!sourceId.isBlank() && sourceId.equals(id)) {
                legacySourceIndex = i;
            }
        }

        ActionConfig.ActionDef target;
        if (targetId == null) {
            target = ActionConfig.copyActionDef(snapshot);
            if (target == null) return;
            target.id = nextLocalTimelineInstanceId(sourceId, sec);
            target.name = (snapshot.name == null || snapshot.name.isBlank() ? sourceId : snapshot.name)
                    + " @ " + sec + "s";
            target.triggers = new ArrayList<>();
            markTimelineInstance(target, sourceId, sec);
            localConfig.actions.add(target);
            if (legacySourceIndex >= 0) {
                ids.set(legacySourceIndex, target.id);
            } else if (!ids.contains(target.id)) {
                ids.add(target.id);
            }
        } else {
            target = localConfig.findById(targetId).orElse(null);
            if (target == null) return;
            String keepId = target.id;
            String keepName = target.name;
            copyTimelineEditableFields(snapshot, target);
            target.id = keepId;
            if (keepName != null && !keepName.isBlank()) {
                target.name = keepName;
            }
            markTimelineInstance(target, sourceId, sec);
        }

        selectedIndex = localConfig.actions.indexOf(target);
        selectedTrigger = -1;
        forceSave();
        refreshTimelineUi();
        rebuildRightPanel();
        if (actionListAdapter != null) actionListAdapter.notifyDataSetChanged();
        updatePreviewFromDef();
        invalidatePreview();
    }

    private void copyTimelineEditableFields(ActionConfig.ActionDef source, ActionConfig.ActionDef target) {
        target.name = source.name;
        target.enabled = source.enabled;
        target.requiredPermissionLevel = source.requiredPermissionLevel;
        target.actionType = source.actionType;
        target.actionParams = source.actionParams == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source.actionParams);
        target.triggers = new ArrayList<>();
    }

    private void markTimelineInstance(ActionConfig.ActionDef target, String sourceId, int second) {
        if (target.actionParams == null) target.actionParams = new LinkedHashMap<>();
        target.actionParams.put("_timeline_instance", true);
        target.actionParams.put("_source_action_id", sourceId == null ? "" : sourceId);
        target.actionParams.put("_timeline_second", Math.max(0, second));
    }

    private String nextLocalTimelineInstanceId(String sourceId, int second) {
        String safeSource = sourceId == null || sourceId.isBlank()
                ? "action"
                : sourceId.replaceAll("[^A-Za-z0-9_\\-]", "_");
        String base = "__tl_" + Math.max(0, second) + "_" + safeSource;
        String id = base;
        int suffix = 1;
        while (localConfig.findById(id).isPresent()) {
            id = base + "_" + suffix++;
        }
        return id;
    }

    private boolean useCompactTimelineRows() {
        return true;
    }

    private void selectTimelineSecond(int second, boolean refresh) {
        int sec = Math.max(0, second);
        selectedTimelineSecond = sec;
        timelineAssignSecond = sec;
        if (timelineAssignSecondField != null) {
            timelineAssignSecondField.setText(String.valueOf(sec));
        }
        if (refresh) {
            rebuildTimelineRows();
            updateTimelineStatus();
        }
    }

    private int getDefaultNewTimelineSecond() {
        int sec = Math.max(0, selectedTimelineSecond);
        if (!localConfig.timelineSchedule.containsKey(sec)) return sec;
        do {
            sec++;
        } while (localConfig.timelineSchedule.containsKey(sec));
        return sec;
    }

    private TextView compactTimelineText(Context ctx, String text, int color) {
        TextView view = new TextView(ctx);
        view.setText(text);
        view.setTextColor(color);
        view.setTextSize(12);
        view.setPadding(3, 0, 3, 0);
        return view;
    }

    private String getTimelineActionLabel(String actionId) {
        ActionConfig.ActionDef def = localConfig.findById(actionId).orElse(null);
        if (def == null) return actionId;
        String sourceId = getTimelineSourceActionId(def);
        String label = def.name != null && !def.name.isBlank() ? def.name : def.id;
        if (sourceId != null && !sourceId.isBlank()) {
            return label + " [" + sourceId + "]";
        }
        return label;
    }

    private String getTimelineSourceActionId(String actionId) {
        return localConfig.findById(actionId).map(this::getTimelineSourceActionId).orElse("");
    }

    private String getTimelineSourceActionId(ActionConfig.ActionDef def) {
        Object source = def.actionParams == null ? null : def.actionParams.get("_source_action_id");
        return source == null ? "" : source.toString();
    }

    private void rebuildTimelineRowsCompact() {
        if (timelineRowsContainer == null || getContext() == null) return;
        Context ctx = getContext();
        timelineRowsContainer.removeAllViews();

        Map<Integer, List<String>> schedule = localConfig.timelineSchedule;
        List<Integer> sorted = new ArrayList<>(schedule.keySet());
        Collections.sort(sorted);
        int curSec = TimelineClientState.currentSecond;

        if (sorted.isEmpty()) {
            TextView empty = compactTimelineText(ctx, "Timeline empty", 0xFF777777);
            empty.setPadding(6, 4, 6, 4);
            timelineRowsContainer.addView(empty, new LinearLayout.LayoutParams(-1, -2));
        }

        for (int sec : sorted) {
            final int rowSec = sec;
            List<String> ids = schedule.get(sec);
            if (ids == null) ids = Collections.emptyList();

            LinearLayout block = new LinearLayout(ctx);
            block.setOrientation(LinearLayout.VERTICAL);
            block.setPadding(4, 1, 4, 1);
            if (sec == selectedTimelineSecond) {
                block.setBackground(new ColorDrawable(0x334466AA));
            } else if (sec == curSec) {
                block.setBackground(new ColorDrawable(0x22336644));
            } else {
                block.setBackground(null);
            }
            block.setOnClickListener(v -> selectTimelineSecond(rowSec, true));

            LinearLayout header = new LinearLayout(ctx);
            header.setOrientation(LinearLayout.HORIZONTAL);
            TextView secLabel = compactTimelineText(ctx,
                    (sec == selectedTimelineSecond ? "> " : "  ") + sec + "s",
                    sec == selectedTimelineSecond ? 0xFFFFFFAA : (sec == curSec ? 0xFF55FF55 : 0xFFAAAAAA));
            secLabel.setOnClickListener(v -> selectTimelineSecond(rowSec, true));
            header.addView(secLabel, new LinearLayout.LayoutParams(0, -2, 1f));

            TextView delSec = compactTimelineText(ctx, "x", 0xFFFF7777);
            delSec.setOnClickListener(v -> {
                applyLocalTimelineRemoveSecond(rowSec);
                ClientPlayNetworking.send(new TimelineControlC2SPacket("REMOVE_SECOND", rowSec, ""));
            });
            header.addView(delSec, new LinearLayout.LayoutParams(-2, -2));
            block.addView(header, new LinearLayout.LayoutParams(-1, -2));

            if (ids.isEmpty()) {
                TextView emptyLabel = compactTimelineText(ctx, "(empty)", 0xFF666666);
                emptyLabel.setPadding(14, 0, 3, 0);
                block.addView(emptyLabel, new LinearLayout.LayoutParams(-1, -2));
            }

            for (String aid : ids) {
                final String actionId = aid;
                LinearLayout actionRow = new LinearLayout(ctx);
                actionRow.setOrientation(LinearLayout.HORIZONTAL);
                actionRow.setPadding(14, 0, 0, 0);

                TextView actionLabel = compactTimelineText(ctx, trunc(getTimelineActionLabel(actionId), 18), 0xFFE0E0E0);
                actionLabel.setOnClickListener(v -> {
                    selectTimelineSecond(rowSec, false);
                    selectActionById(actionId);
                    rebuildTimelineRows();
                });
                actionRow.addView(actionLabel, new LinearLayout.LayoutParams(0, -2, 1f));

                if (ids.size() > 1) {
                    TextView up = compactTimelineText(ctx, "^", 0xFFB8D0FF);
                    up.setOnClickListener(v -> {
                        selectTimelineSecond(rowSec, false);
                        applyLocalTimelineMove(rowSec, actionId, -1);
                        ClientPlayNetworking.send(new TimelineControlC2SPacket("MOVE_UP", rowSec, actionId));
                    });
                    actionRow.addView(up, new LinearLayout.LayoutParams(-2, -2));

                    TextView down = compactTimelineText(ctx, "v", 0xFFB8D0FF);
                    down.setOnClickListener(v -> {
                        selectTimelineSecond(rowSec, false);
                        applyLocalTimelineMove(rowSec, actionId, 1);
                        ClientPlayNetworking.send(new TimelineControlC2SPacket("MOVE_DOWN", rowSec, actionId));
                    });
                    actionRow.addView(down, new LinearLayout.LayoutParams(-2, -2));
                }

                TextView remove = compactTimelineText(ctx, "x", 0xFFFF7777);
                remove.setOnClickListener(v -> {
                    selectTimelineSecond(rowSec, false);
                    applyLocalTimelineRemove(rowSec, actionId);
                    ClientPlayNetworking.send(new TimelineControlC2SPacket("REMOVE", rowSec, actionId));
                });
                actionRow.addView(remove, new LinearLayout.LayoutParams(-2, -2));
                block.addView(actionRow, new LinearLayout.LayoutParams(-1, -2));
            }

            timelineRowsContainer.addView(block, new LinearLayout.LayoutParams(-1, -2));
        }

        Button addBtn = new Button(ctx);
        addBtn.setText("+ 秒节点");
        addBtn.setOnClickListener(v -> {
            int sec = getDefaultNewTimelineSecond();
            selectTimelineSecond(sec, false);
            applyLocalTimelineAdd(sec, "");
            ClientPlayNetworking.send(new TimelineControlC2SPacket("ADD", sec, ""));
        });
        timelineRowsContainer.addView(addBtn, new LinearLayout.LayoutParams(-1, -2));
    }

    private class ActionListAdapter extends BaseAdapter {
        private final Context ctx;
        ActionListAdapter(Context ctx) { this.ctx = ctx; }

        @Override public int getCount() { return localConfig.actions.size(); }
        @Override public Object getItem(int i) { return localConfig.actions.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int pos, View convertView, ViewGroup parent) {
            TextView tv;
            if (convertView instanceof TextView) {
                tv = (TextView) convertView;
            } else {
                tv = new TextView(ctx);
                tv.setPadding(8, 5, 8, 5);
                tv.setTextSize(14);
            }
            ActionConfig.ActionDef d = localConfig.actions.get(pos);
            String prefix = (pos == selectedIndex ? "§e▸ " : "  ");
            String status = d.enabled ? "§a" : "§c";
            tv.setText(prefix + status + trunc(d.id, 20));
            if (pos == selectedIndex) {
                tv.setTextColor(0xFFFFFFAA);
                tv.setBackground(new ColorDrawable(0x334466AA));
            } else {
                tv.setTextColor(0xFFCCCCCC);
                tv.setBackground(null);
            }
            return tv;
        }
    }

    // ══════════════════════════════════════════════════
    //  交互方法
    // ══════════════════════════════════════════════════

    private void select(int idx) {
        if (idx < 0 || idx >= localConfig.actions.size()) return;
        selectedIndex = idx;
        selectedTrigger = localConfig.actions.get(idx).triggers.isEmpty() ? -1 : 0;
        if (previewBtn != null) previewBtn.setEnabled(true);
        rebuildRightPanel();
        updatePreviewFromDef();
        invalidatePreview();
        actionListAdapter.notifyDataSetChanged();
    }

    private void selectActionById(String id) {
        for (int i = 0; i < localConfig.actions.size(); i++) {
            if (id.equals(localConfig.actions.get(i).id)) {
                select(i);
                return;
            }
        }
    }

    private void newAction() {
        ActionConfig.ActionDef d = new ActionConfig.ActionDef();
        d.id = "new_" + (System.currentTimeMillis() % 100000);
        d.name = "新动作";
        localConfig.actions.add(d);
        select(localConfig.actions.size() - 1);
        markDirty();
    }

    private void delAction() {
        if (selectedIndex < 0) return;
        localConfig.actions.remove(selectedIndex);
        selectedIndex = localConfig.actions.isEmpty() ? -1 : Math.min(selectedIndex, localConfig.actions.size() - 1);
        selectedTrigger = -1;
        if (previewBtn != null) previewBtn.setEnabled(selectedIndex >= 0);
        markDirty();
        rebuildRightPanel();
        actionListAdapter.notifyDataSetChanged();
    }

    private void doPreview() {
        if (selectedIndex < 0) return;
        ActionConfig.ActionDef def = localConfig.actions.get(selectedIndex);
        ClientPlayNetworking.send(new PreviewActionC2SPacket(GSON.toJson(def)));
    }

    // ══════════════════════════════════════════════════
    //  保存逻辑
    // ══════════════════════════════════════════════════

    private void markDirty() {
        dirty = true;
        lastChangeTime = System.currentTimeMillis();
        updatePreviewFromDef();
        invalidatePreview();
        if (rootView != null) {
            rootView.removeCallbacks(autoSaveRunnable);
            rootView.postDelayed(autoSaveRunnable, SAVE_DEBOUNCE);
        }
    }

    private void forceSave() {
        dirty = false;
        lastChangeTime = 0;
        if (rootView != null) rootView.removeCallbacks(autoSaveRunnable);
        ClientPlayNetworking.send(new UpdateActionsConfigC2SPacket(localConfig.toJson()));
        if (MinecraftClient.getInstance().player != null)
            MinecraftClient.getInstance().player.sendMessage(Text.literal("§a已保存"), true);
    }

    // ══════════════════════════════════════════════════
    //  网络回调
    // ══════════════════════════════════════════════════

    public void receiveConfig(ActionConfig config) {
        if (config == null) return;
        if (dirty) return;
        this.localConfig = config;
        if (!config.actions.isEmpty() && selectedIndex < 0) selectedIndex = 0;
        if (selectedIndex >= config.actions.size()) selectedIndex = config.actions.isEmpty() ? -1 : config.actions.size() - 1;
        if (previewBtn != null) previewBtn.setEnabled(selectedIndex >= 0);
        // 在 UI 线程刷新
        if (rootView != null) {
            rootView.post(() -> {
                rebuildRightPanel();
                rebuildTimelineRows();
                updatePreviewFromDef();
                invalidatePreview();
                actionListAdapter.notifyDataSetChanged();
            });
        }
    }

    public void receiveFileList(List<String> files) {
        this.availableFiles.clear();
        this.availableFiles.addAll(files);
        if (rootView != null) {
            rootView.post(this::rebuildFileList);
        }
    }

    public void receivePreviewResult(String actionId, String text) {
        String timestamp = String.format("%02d:%02d:%02d",
                (System.currentTimeMillis() / 3600000 + 8) % 24,
                (System.currentTimeMillis() / 60000) % 60,
                (System.currentTimeMillis() / 1000) % 60);
        StringBuilder sb = new StringBuilder(logTextView.getText());
        sb.append("§8[").append(timestamp).append("] §e").append(actionId).append("\n");
        for (String line : text.split("\n")) {
            sb.append("  §7").append(line).append("\n");
        }
        sb.append("\n");
        if (rootView != null) {
            rootView.post(() -> {
                logTextView.setText(sb.toString());
                logScrollView.fullScroll(View.FOCUS_DOWN);
            });
        }
    }

    private void appendLog(String text) {
        String timestamp = String.format("%02d:%02d:%02d",
                (System.currentTimeMillis() / 3600000 + 8) % 24,
                (System.currentTimeMillis() / 60000) % 60,
                (System.currentTimeMillis() / 1000) % 60);
        StringBuilder sb = new StringBuilder(logTextView == null ? "" : logTextView.getText());
        sb.append("§8[").append(timestamp).append("]§r\n");
        for (String line : text.split("\n")) {
            sb.append("  §7").append(line).append("\n");
        }
        sb.append("\n");
        if (rootView != null) {
            rootView.post(() -> {
                logTextView.setText(sb.toString());
                logScrollView.fullScroll(View.FOCUS_DOWN);
            });
        }
    }

    public void receiveTimelinePreviewResult(List<PreviewTimelineResultS2CPacket.PreviewEntry> entries) {
        StringBuilder sb = new StringBuilder(logTextView.getText());
        sb.append("§6===== 时间轴预览 =====\n");
        int currentSec = -1;
        for (var e : entries) {
            if (e.second() != currentSec) {
                sb.append("§9--- 第").append(e.second()).append("秒 ---\n");
                currentSec = e.second();
            }
            sb.append("  §e[").append(e.actionId()).append("] §7")
                    .append(e.previewText().replace("\n", " | ")).append("\n");
        }
        sb.append("§6===== 预览结束 =====\n");
        if (rootView != null) {
            rootView.post(() -> {
                logTextView.setText(sb.toString());
                logScrollView.fullScroll(View.FOCUS_DOWN);
            });
        }
    }

    /** 由外部定时调用（ClientTickHandler），更新时间轴状态显示 */
    /*
    public static void tickActiveBroken() {
        ActionEditorFragment inst = activeInstance;
        if (inst == null || inst.rootView == null) return;
        inst.rootView.post(() -> {
            if (inst.timelineStatusBtn != null) {
                String s = String.format("当前: %d/%d秒 %s",
                        TimelineClientState.currentSecond,
                        TimelineClientState.totalSeconds,
                        TimelineClientState.running ? (TimelineClientState.paused ? "§e暂停" : "§a运行中") : "§7已停止");
                inst.timelineStatusBtn.setText(s);
            }
            if (inst.loopBtn != null) {
                inst.loopBtn.setText(TimelineClientState.loop ? "循环:开" : "循环:关");
            }
            if (inst.pauseBtn != null) {
                inst.pauseBtn.setText(TimelineClientState.paused ? "▶继续" : "⏸暂停");
            }
            inst.rebuildTimelineRows();
        });
    }
    */

    // ══════════════════════════════════════════════════
    //  模型预览
    // ══════════════════════════════════════════════════

    /** 将 previewAngles 数组应用到预览模型的各部位 */
    public static void tickActive() {
        ActionEditorFragment inst = activeInstance;
        if (inst == null || inst.rootView == null) return;
        inst.rootView.post(() -> {
            int beforeSecond = TimelineClientState.currentSecond;
            inst.advanceTimelineClientClock();
            inst.updateTimelineStatus();
            if (inst.loopBtn != null) {
                inst.loopBtn.setText(TimelineClientState.loop ? "Loop:on" : "Loop:off");
            }
            if (inst.pauseBtn != null) {
                inst.pauseBtn.setText(TimelineClientState.paused ? "Resume" : "Pause");
            }
            if (inst.lastRenderedTimelineSecond != TimelineClientState.currentSecond
                    || beforeSecond != TimelineClientState.currentSecond) {
                inst.lastRenderedTimelineSecond = TimelineClientState.currentSecond;
                inst.rebuildTimelineRows();
            }
        });
    }

    public void syncTimelineClock() {
        timelineClientBaseSecond = TimelineClientState.currentSecond;
        timelineClientLastSecondAt = System.currentTimeMillis();
    }

    private void advanceTimelineClientClock() {
        if (!TimelineClientState.running || TimelineClientState.paused || timelineClientLastSecondAt <= 0L) return;
        int maxSecond = getTimelineMaxSecond();
        long elapsedSeconds = Math.max(0L, (System.currentTimeMillis() - timelineClientLastSecondAt) / 1000L);
        int projected = timelineClientBaseSecond + (int) elapsedSeconds;
        if (TimelineClientState.loop && maxSecond > 0) {
            projected = projected % (maxSecond + 1);
        } else {
            projected = Math.min(projected, maxSecond);
        }
        if (projected != TimelineClientState.currentSecond) {
            TimelineClientState.currentSecond = projected;
        }
    }

    private void setModelAngles(PlayerEntityModel model) {
        for (ModelPart part : model.getRootPart().traverse()) {
            part.resetTransform();
            part.visible = true;
            part.hidden = false;
        }
        model.head.pitch = previewAngles[0];  model.head.yaw = previewAngles[1];  model.head.roll = previewAngles[2];
        model.body.pitch = previewAngles[3];  model.body.yaw = previewAngles[4];  model.body.roll = previewAngles[5];
        model.leftArm.pitch = previewAngles[6];  model.leftArm.yaw = previewAngles[7];  model.leftArm.roll = previewAngles[8];
        model.rightArm.pitch = previewAngles[9];  model.rightArm.yaw = previewAngles[10];  model.rightArm.roll = previewAngles[11];
        model.leftLeg.pitch = previewAngles[12];  model.leftLeg.yaw = previewAngles[13];  model.leftLeg.roll = previewAngles[14];
        model.rightLeg.pitch = previewAngles[15];  model.rightLeg.yaw = previewAngles[16];  model.rightLeg.roll = previewAngles[17];
        // 外套部分跟随
        if (model.hat != null) model.hat.visible = model.head.visible;
        if (model.jacket != null) model.jacket.visible = model.body.visible;
        if (model.leftSleeve != null) model.leftSleeve.visible = model.leftArm.visible;
        if (model.rightSleeve != null) model.rightSleeve.visible = model.rightArm.visible;
        if (model.leftPants != null) model.leftPants.visible = model.leftLeg.visible;
        if (model.rightPants != null) model.rightPants.visible = model.rightLeg.visible;
    }

    /** 从当前选中的动作定义更新预览角度 */
    private void updatePreviewFromDef() {
        Arrays.fill(previewAngles, 0f);
        if (selectedIndex < 0 || selectedIndex >= localConfig.actions.size() || previewModel == null) return;
        ActionConfig.ActionDef def = localConfig.actions.get(selectedIndex);
        if (!"BODY_POSE".equals(def.actionType.toUpperCase())) return;
        // 更新模型类型
        boolean slim = "true".equals(String.valueOf(def.actionParams.getOrDefault("slim_model", false)));
        if (slim != previewSlimModel) {
            recreatePreviewModel(slim);
        }
        String skinName = (String) def.actionParams.getOrDefault("skin_name", "");
        // 读取角度
        for (int i = 0; i < 6; i++) {
            String prefix = BODY_PARTS[i];
            float pitch = getFloatParam(def, prefix + "_pitch");
            float yaw = getFloatParam(def, prefix + "_yaw");
            float roll = getFloatParam(def, prefix + "_roll");
            previewAngles[i * 3] = (float) Math.toRadians(pitch);
            previewAngles[i * 3 + 1] = (float) Math.toRadians(yaw);
            previewAngles[i * 3 + 2] = (float) Math.toRadians(roll);
        }
        // 皮肤纹理：尝试加载自定义皮肤
        previewTexture = resolvePreviewTexture(skinName);
    }

    private static float getFloatParam(ActionConfig.ActionDef def, String key) {
        Object val = def.actionParams.get(key);
        if (val instanceof Number n) return n.floatValue();
        return 0f;
    }

    private void recreatePreviewModel(boolean slim) {
        MinecraftClient client = MinecraftClient.getInstance();
        ModelPart rootPart = client.getLoadedEntityModels().getModelPart(
                slim ? ModModelLayers.COMBINED_BODY_SLIM : ModModelLayers.COMBINED_BODY);
        previewModel = new PlayerEntityModel(rootPart, slim);
        previewSlimModel = slim;
    }

    private Identifier resolvePreviewTexture(String skinName) {
        String skin = skinName == null ? "" : skinName.trim();
        if (skin.isEmpty()) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null) return mc.player.getSkinTextures().texture();
            return Identifier.of("monvhua", "textures/local_skin/ema.png");
        }
        if (!skin.contains(":") && !skin.contains("/")) {
            return Identifier.of("monvhua", "textures/local_skin/" + skin + ".png");
        }
        Identifier parsed = Identifier.tryParse(skin);
        return parsed != null ? parsed : Identifier.of("monvhua", "textures/local_skin/ema.png");
    }

    // ══════════════════════════════════════════════════
    //  工具方法
    // ══════════════════════════════════════════════════

    private static String trunc(String s, int max) {
        if (s == null || s.length() <= max) return s == null ? "" : s;
        return s.substring(0, max - 1) + "…";
    }

    private static String formatFloat(Object val) {
        if (val instanceof Number n) {
            double d = n.doubleValue();
            if (d == (long) d) return String.valueOf((long) d);
            return String.valueOf(d);
        }
        return val.toString();
    }

    private void installPoseFieldScroll(EditText field, ActionConfig.ActionDef def, String key, double step) {
        field.setOnGenericMotionListener((view, event) -> {
            if (event.getActionMasked() != MotionEvent.ACTION_SCROLL) return false;
            float scroll = getScrollAmount(event);
            if (Math.abs(scroll) < 0.001F) return false;
            double current = getDoubleFieldValue(field, def, key);
            double next = current + (scroll > 0.0F ? step : -step);
            if (Math.abs(next) < 0.0001D) {
                def.actionParams.remove(key);
            } else {
                def.actionParams.put(key, next);
            }
            field.setText(formatFloat(next));
            markDirty();
            return true;
        });
    }

    private static float getScrollAmount(MotionEvent event) {
        float scroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
        if (Math.abs(scroll) < 0.001F) {
            scroll = -event.getAxisValue(MotionEvent.AXIS_Y);
        }
        return scroll;
    }

    private static double getDoubleFieldValue(EditText field, ActionConfig.ActionDef def, String key) {
        try {
            return Double.parseDouble(field.getText().toString());
        } catch (Exception ignored) {
            Object val = def.actionParams.get(key);
            if (val instanceof Number n) return n.doubleValue();
            return 0.0D;
        }
    }

    private static int indexOf(String[] arr, String val) {
        for (int i = 0; i < arr.length; i++) if (arr[i].equals(val)) return i;
        return 0;
    }

    private static void updateParam(ActionConfig.ActionDef def, String key, String val, String type) {
        if (val.isEmpty()) { def.actionParams.remove(key); return; }
        switch (type) {
            case "number" -> { try { def.actionParams.put(key, Double.parseDouble(val)); } catch (Exception ignored) {} }
            case "bool" -> def.actionParams.put(key, "true".equalsIgnoreCase(val));
            default -> def.actionParams.put(key, val);
        }
    }

    private static void updateTParam(ActionConfig.TriggerDef trig, String key, String val, String type) {
        if (val.isEmpty()) { trig.params.remove(key); return; }
        switch (type) {
            case "number" -> { try { trig.params.put(key, Double.parseDouble(val)); } catch (Exception ignored) {} }
            case "bool" -> trig.params.put(key, "true".equalsIgnoreCase(val));
            case "string" -> {
                if ("keywords".equals(key)) trig.params.put(key, Arrays.asList(val.split("\\s*,\\s*")));
                else trig.params.put(key, val);
            }
            default -> trig.params.put(key, val);
        }
    }

    private static String[][] getParamSpec(String type) {
        return switch (type) {
            case "COMMAND" -> new String[][]{{"command", "命令", "string"}, {"asConsole", "控制台", "bool"}};
            case "CHAT" -> new String[][]{{"message", "消息", "string"}, {"target", "目标", "dropdown:self,global"}};
            case "SOUND" -> new String[][]{{"sound", "声音ID", "string"}, {"volume", "音量", "number"}, {"pitch", "音高", "number"}};
            case "ATTRIBUTE" -> new String[][]{{"attribute", "属性", "string"}, {"operation", "操作", "dropdown:set,add"}, {"value", "值", "number"}};
            case "TELEPORT" -> new String[][]{{"x", "X", "number"}, {"y", "Y", "number"}, {"z", "Z", "number"}, {"world", "世界", "string"}};
            case "GIVE_ITEM" -> new String[][]{{"item", "物品ID", "string"}, {"count", "数量", "number"}};
            case "POTION_EFFECT" -> new String[][]{{"effect", "效果ID", "string"}, {"duration", "时长tick", "number"}, {"amplifier", "等级", "number"}, {"ambient", "环境", "bool"}, {"showParticles", "粒子", "bool"}};
            case "TITLE" -> new String[][]{{"title", "标题", "string"}, {"subtitle", "副标题", "string"}, {"fadeIn", "淡入", "number"}, {"stay", "停留", "number"}, {"fadeOut", "淡出", "number"}};
            default -> null;
        };
    }

    private static String[][] getTriggerParamSpec(String type) {
        return switch (type) {
            case "CHAT_KEYWORD" -> new String[][]{{"keywords", "关键词", "string"}, {"matchMode", "匹配", "dropdown:contains,equals"}, {"caseSensitive", "大小写", "bool"}};
            case "TIMER_INTERVAL" -> new String[][]{{"intervalTicks", "间隔tick", "number"}, {"maxExecutions", "最大次数", "number"}};
            case "TIMER_DELAY" -> new String[][]{{"delayTicks", "延迟tick", "number"}};
            default -> null;
        };
    }

    // 简化 TextWatcher
    private interface STListener { void onChange(CharSequence s); }
    private static class STWatcher implements TextWatcher {
        private final STListener l;
        STWatcher(STListener l) { this.l = l; }
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        @Override public void afterTextChanged(Editable s) { l.onChange(s); }
    }

    private Button timelineStatusBtn;
    private ScrollView logScrollView;
}
