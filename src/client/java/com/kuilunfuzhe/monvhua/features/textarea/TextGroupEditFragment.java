package com.kuilunfuzhe.monvhua.features.textarea;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.kuilunfuzhe.monvhua.features.area_tip.AreaTipClient;
import com.kuilunfuzhe.monvhua.item.config.AreaTipConfig;
import icyllis.modernui.core.Context;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.drawable.ColorDrawable;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.mc.MinecraftSurfaceView;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.mc.ScreenCallback;
import icyllis.modernui.text.Editable;
import icyllis.modernui.text.TextWatcher;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

public class TextGroupEditFragment extends Fragment {
    private static final Identifier COLOR_MAP_TEXTURE_ID = Identifier.of(MonvhuaMod.MOD_ID, "dynamic/text_area_color_map_modernui");
    private static final int LEFT_WIDTH = 220;
    private static final int RIGHT_WIDTH = 380;
    private static final int COLOR_MAP_SIZE = 132;
    private static final int HUE_WIDTH = 12;

    private static final List<String> FALLBACK_FONTS = List.of(
            "minecraft:default",
            "minecraft:uniform",
            "minecraft:alt",
            "modernui:source-han-sans-cn-medium",
            "modernui:mui-i18n-compat",
            "modernui:inter-frozen-medium",
            "modernui:jetbrains-mono-medium"
    );

    public static TextGroupEditFragment activeInstance;

    private final Screen parent;
    private AreaTipConfig original;
    private final AreaTipConfig working;
    private int selectedGroupIndex;
    private int selectedTextIndex;
    private boolean dirty;
    private boolean updatingFields;
    private boolean leftHidden;
    private boolean rightHidden;
    private boolean fontListOpen;
    private boolean importListOpen;
    private boolean colorPickerOpen;
    private boolean colorPicking;
    private boolean draggingUsesGuiCoordinates;
    private float hue = 0.13F;
    private float saturation = 1.0F;
    private float value = 1.0F;
    private float uploadedHue = -1.0F;
    private int pendingColor = 0xFFFFFFFF;
    private int surfaceWidth = 854;
    private int surfaceHeight = 480;

    private FrameLayout root;
    private LinearLayout leftPanel;
    private LinearLayout rightPanel;
    private LinearLayout groupList;
    private LinearLayout textList;
    private LinearLayout inspectorPanel;
    private LinearLayout fontListPanel;
    private LinearLayout importListPanel;
    private TextView statusLabel;
    private TextView selectedGroupLabel;
    private EditText textField;
    private EditText fontField;
    private EditText sizeField;
    private EditText wrapField;
    private EditText offsetXField;
    private EditText offsetYField;
    private EditText delayField;
    private EditText displayField;
    private EditText fadeField;
    private EditText passDelayField;
    private EditText playLimitField;
    private EditText priorityField;
    private EditText widthField;
    private EditText heightField;
    private EditText colorField;
    private Button visibleButton;
    private Button alignButton;
    private Button fontToggleButton;
    private Button importToggleButton;
    private MinecraftSurfaceView surfaceView;
    private NativeImageBackedTexture colorMapTexture;
    private final GenericDragger dragger = new GenericDragger();
    private final List<ImageEntry> imageEntries = new ArrayList<>();

    public TextGroupEditFragment(Screen parent) {
        this.parent = parent;
        this.original = AreaTipConfig.getInstance();
        this.working = AreaTipConfig.fromJson(original.toJson());
        this.selectedGroupIndex = selectedIndexFromConfig();
        this.dragger.enableDragging(true);
    }

    public static void open(Screen parent) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.execute(() -> client.setScreen(MuiModApi.get().createScreen(new TextGroupEditFragment(parent), NON_PAUSING_CALLBACK, parent)));
        }
    }

    private static final ScreenCallback NON_PAUSING_CALLBACK = new ScreenCallback() {
        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public boolean hasDefaultBackground() {
            return false;
        }

        @Override
        public boolean shouldBlurBackground() {
            return false;
        }
    };

    public static boolean isActive() {
        return activeInstance != null;
    }

    public static void receiveActiveConfig(AreaTipConfig config) {
        if (activeInstance != null) {
            activeInstance.receiveConfig(config);
        }
    }

    public void receiveConfig(AreaTipConfig config) {
        if (dirty || config == null) {
            return;
        }
        AreaTipConfig refreshed = AreaTipConfig.fromJson(config.toJson());
        original = AreaTipConfig.fromJson(config.toJson());
        working.groups = refreshed.groups;
        working.selectedGroupId = refreshed.selectedGroupId;
        selectedGroupIndex = selectedIndexFromConfig();
        selectedTextIndex = 0;
        rebuildAllPanels();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, icyllis.modernui.util.DataSet savedInstanceState) {
        Context ctx = getContext();
        root = new FrameLayout(ctx);
        root.setBackground(new ColorDrawable(0xFF0F1115));

        surfaceView = new MinecraftSurfaceView(ctx);
        surfaceView.setRenderer(new PreviewRenderer());
        surfaceView.setFocusable(true);
        surfaceView.setFocusableInTouchMode(true);
        surfaceView.setOnTouchListener(this::handlePreviewTouch);
        root.addView(surfaceView, new FrameLayout.LayoutParams(-1, -1));

        leftPanel = createLeftPanel(ctx);
        FrameLayout.LayoutParams leftParams = new FrameLayout.LayoutParams(LEFT_WIDTH, -1, Gravity.LEFT | Gravity.TOP);
        root.addView(leftPanel, leftParams);

        rightPanel = createRightPanel(ctx);
        FrameLayout.LayoutParams rightParams = new FrameLayout.LayoutParams(RIGHT_WIDTH, -1, Gravity.RIGHT | Gravity.TOP);
        root.addView(rightPanel, rightParams);

        Button leftToggle = miniButton(ctx, "<");
        leftToggle.setOnClickListener(v -> toggleLeftPanel(leftToggle));
        FrameLayout.LayoutParams leftToggleParams = new FrameLayout.LayoutParams(24, 40, Gravity.LEFT | Gravity.CENTER_VERTICAL);
        root.addView(leftToggle, leftToggleParams);

        Button rightToggle = miniButton(ctx, ">");
        rightToggle.setOnClickListener(v -> toggleRightPanel(rightToggle));
        FrameLayout.LayoutParams rightToggleParams = new FrameLayout.LayoutParams(24, 40, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        root.addView(rightToggle, rightToggleParams);

        rebuildAllPanels();
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        activeInstance = this;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (activeInstance == this) {
            activeInstance = null;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (activeInstance == this) {
            activeInstance = null;
        }
        MinecraftClient.getInstance().execute(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (colorMapTexture != null) {
                client.getTextureManager().destroyTexture(COLOR_MAP_TEXTURE_ID);
                colorMapTexture = null;
            }
        });
    }

    private LinearLayout createLeftPanel(Context ctx) {
        LinearLayout panel = new LinearLayout(ctx);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(8, 8, 8, 8);
        panel.setBackground(panelShape(0xE0181B22, 0xFF343B46));

        TextView title = label(ctx, "文字区域组", 15, 0xFFE8EDF4);
        panel.addView(title, blockParams());

        ScrollView groupScroll = new ScrollView(ctx);
        groupScroll.setBackground(panelShape(0x441A2028, 0x553A4350));
        groupList = vertical(ctx);
        groupList.setPadding(4, 4, 4, 4);
        groupScroll.addView(groupList, new FrameLayout.LayoutParams(-1, -2));
        panel.addView(groupScroll, new LinearLayout.LayoutParams(-1, 0, 1.0F));

        statusLabel = label(ctx, "", 11, 0xFFB6C0CC);
        panel.addView(statusLabel, blockParams());
        return panel;
    }

    private LinearLayout createRightPanel(Context ctx) {
        LinearLayout panel = new LinearLayout(ctx);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(8, 8, 8, 8);
        panel.setBackground(panelShape(0xE0181B22, 0xFF343B46));

        selectedGroupLabel = label(ctx, "", 15, 0xFFE8EDF4);
        panel.addView(selectedGroupLabel, blockParams());

        LinearLayout actionRow = new LinearLayout(ctx);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        Button save = button(ctx, "保存");
        save.setOnClickListener(v -> saveAndClose());
        actionRow.addView(save, new LinearLayout.LayoutParams(0, -2, 1.0F));
        Button cancel = button(ctx, "取消");
        cancel.setOnClickListener(v -> cancelAndClose());
        actionRow.addView(cancel, new LinearLayout.LayoutParams(0, -2, 1.0F));
        Button back = button(ctx, "返回");
        back.setOnClickListener(v -> closeToParent());
        actionRow.addView(back, new LinearLayout.LayoutParams(0, -2, 1.0F));
        panel.addView(actionRow, blockParams());

        ScrollView textScroll = new ScrollView(ctx);
        textScroll.setBackground(panelShape(0x441A2028, 0x553A4350));
        textList = vertical(ctx);
        textList.setPadding(4, 4, 4, 4);
        textScroll.addView(textList, new FrameLayout.LayoutParams(-1, -2));
        panel.addView(textScroll, new LinearLayout.LayoutParams(-1, 130));

        ScrollView inspectorScroll = new ScrollView(ctx);
        inspectorScroll.setBackground(panelShape(0x441A2028, 0x553A4350));
        inspectorPanel = vertical(ctx);
        inspectorPanel.setPadding(6, 6, 6, 6);
        inspectorScroll.addView(inspectorPanel, new FrameLayout.LayoutParams(-1, -2));
        panel.addView(inspectorScroll, new LinearLayout.LayoutParams(-1, 0, 1.0F));
        return panel;
    }

    private void rebuildAllPanels() {
        rebuildGroupList();
        rebuildTextList();
        rebuildInspector();
        updateStatus();
        if (surfaceView != null) {
            surfaceView.invalidate();
        }
    }

    private void rebuildGroupList() {
        if (groupList == null) {
            return;
        }
        Context ctx = getContext();
        groupList.removeAllViews();
        for (int i = 0; i < working.groups.size(); i++) {
            AreaTipConfig.GroupConfig group = working.groups.get(i);
            Button row = button(ctx, group.name);
            row.setTextColor(i == selectedGroupIndex ? 0xFFFFD36A : 0xFFE8EDF4);
            row.setBackground(panelShape(i == selectedGroupIndex ? 0xAA3A5268 : 0x332A3038, 0x553A4350));
            int index = i;
            row.setOnClickListener(v -> selectGroup(index));
            groupList.addView(row, blockParams());
        }
    }

    private void rebuildTextList() {
        if (textList == null) {
            return;
        }
        Context ctx = getContext();
        textList.removeAllViews();
        LinearLayout tools = new LinearLayout(ctx);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        Button add = button(ctx, "新增文本框");
        add.setOnClickListener(v -> addTextEntry());
        tools.addView(add, new LinearLayout.LayoutParams(0, -2, 1.0F));
        Button delete = button(ctx, "删除当前");
        delete.setOnClickListener(v -> deleteSelectedTextEntry());
        tools.addView(delete, new LinearLayout.LayoutParams(0, -2, 1.0F));
        Button copy = button(ctx, "复制上一组");
        copy.setOnClickListener(v -> copyStyleFromPreviousGroup());
        tools.addView(copy, new LinearLayout.LayoutParams(0, -2, 1.0F));
        textList.addView(tools, blockParams());

        List<AreaTipConfig.HudTextEntry> entries = currentGroup().hudTexts;
        for (int i = 0; i < entries.size(); i++) {
            AreaTipConfig.HudTextEntry entry = entries.get(i);
            String name = (i + 1) + ". " + previewText(entry.text);
            Button row = button(ctx, name);
            row.setTextColor(i == selectedTextIndex ? 0xFFFFD36A : 0xFFE8EDF4);
            row.setBackground(panelShape(i == selectedTextIndex ? 0xAA3A5268 : 0x332A3038, 0x553A4350));
            int index = i;
            row.setOnClickListener(v -> selectText(index));
            textList.addView(row, blockParams());
        }
    }

    private void rebuildInspector() {
        if (inspectorPanel == null) {
            return;
        }
        Context ctx = getContext();
        inspectorPanel.removeAllViews();
        AreaTipConfig.GroupConfig group = currentGroup();
        AreaTipConfig.HudTextEntry entry = currentEntry();
        if (selectedGroupLabel != null) {
            selectedGroupLabel.setText("当前组: " + group.name);
        }

        visibleButton = button(ctx, group.hudVisible ? "进入区域显示: 开" : "进入区域显示: 关");
        visibleButton.setOnClickListener(v -> {
            group.hudVisible = !group.hudVisible;
            markDirty();
            rebuildInspector();
        });
        inspectorPanel.addView(visibleButton, blockParams());

        addSection(ctx, "文本内容");
        textField = edit(ctx, entry.text, s -> {
            currentEntry().text = s.toString();
            markDirty();
            rebuildTextList();
        });
        textField.setMinLines(2);
        textField.setMaxLines(6);
        inspectorPanel.addView(textField, blockParams());

        addSection(ctx, "字体");
        LinearLayout fontRow = new LinearLayout(ctx);
        fontRow.setOrientation(LinearLayout.HORIZONTAL);
        fontField = edit(ctx, entry.font, s -> {
            currentEntry().font = s.toString();
            markDirty();
        });
        fontRow.addView(fontField, new LinearLayout.LayoutParams(0, -2, 1.0F));
        fontToggleButton = button(ctx, fontListOpen ? "▲" : "▼");
        fontToggleButton.setOnClickListener(v -> {
            fontListOpen = !fontListOpen;
            rebuildInspector();
        });
        fontRow.addView(fontToggleButton, new LinearLayout.LayoutParams(42, -2));
        inspectorPanel.addView(fontRow, blockParams());
        if (fontListOpen) {
            addFontList(ctx);
        }

        addSection(ctx, "颜色");
        LinearLayout colorRow = new LinearLayout(ctx);
        colorRow.setOrientation(LinearLayout.HORIZONTAL);
        colorField = edit(ctx, String.format(Locale.ROOT, "#%06X", entry.color & 0xFFFFFF), s -> {
            Integer parsed = parseColor(s.toString());
            if (parsed != null) {
                AreaTipConfig.HudTextEntry selected = currentEntry();
                selected.color = 0xFF000000 | parsed;
                selected.useGroupColor = false;
                markDirty();
            }
        });
        colorRow.addView(colorField, new LinearLayout.LayoutParams(0, -2, 1.0F));
        Button picker = button(ctx, "取色");
        picker.setOnClickListener(v -> openColorPicker());
        colorRow.addView(picker, new LinearLayout.LayoutParams(82, -2));
        inspectorPanel.addView(colorRow, blockParams());

        addSection(ctx, "字号 / 换行 / 优先级");
        LinearLayout sizeRow = new LinearLayout(ctx);
        sizeRow.setOrientation(LinearLayout.HORIZONTAL);
        sizeField = numeric(ctx, String.format(Locale.ROOT, "%.2f", entry.fontSize), value -> currentEntry().fontSize = Math.clamp(parseFloat(value, currentEntry().fontSize), 0.25F, 8.0F));
        sizeRow.addView(sizeField, new LinearLayout.LayoutParams(0, -2, 1.0F));
        wrapField = numeric(ctx, Integer.toString(entry.wrapWidth), value -> currentEntry().wrapWidth = clampInt(parseInt(value, currentEntry().wrapWidth), 16, 4096));
        sizeRow.addView(wrapField, new LinearLayout.LayoutParams(0, -2, 1.0F));
        priorityField = numeric(ctx, Integer.toString(entry.priority), value -> currentEntry().priority = clampInt(parseInt(value, currentEntry().priority), -100000, 100000));
        sizeRow.addView(priorityField, new LinearLayout.LayoutParams(0, -2, 1.0F));
        inspectorPanel.addView(sizeRow, blockParams());

        addSection(ctx, "文本偏移 X / Y");
        LinearLayout offsetRow = new LinearLayout(ctx);
        offsetRow.setOrientation(LinearLayout.HORIZONTAL);
        offsetXField = numeric(ctx, Integer.toString(entry.offsetX), value -> currentEntry().offsetX = clampInt(parseInt(value, currentEntry().offsetX), -4096, 4096));
        offsetRow.addView(offsetXField, new LinearLayout.LayoutParams(0, -2, 1.0F));
        offsetYField = numeric(ctx, Integer.toString(entry.offsetY), value -> currentEntry().offsetY = clampInt(parseInt(value, currentEntry().offsetY), -4096, 4096));
        offsetRow.addView(offsetYField, new LinearLayout.LayoutParams(0, -2, 1.0F));
        inspectorPanel.addView(offsetRow, blockParams());

        addSection(ctx, "宽 / 高");
        LinearLayout sizeBoxRow = new LinearLayout(ctx);
        sizeBoxRow.setOrientation(LinearLayout.HORIZONTAL);
        widthField = numeric(ctx, Integer.toString(entry.width), value -> currentEntry().width = clampInt(parseInt(value, currentEntry().width), 32, 4096));
        sizeBoxRow.addView(widthField, new LinearLayout.LayoutParams(0, -2, 1.0F));
        heightField = numeric(ctx, Integer.toString(entry.height), value -> currentEntry().height = clampInt(parseInt(value, currentEntry().height), 16, 4096));
        sizeBoxRow.addView(heightField, new LinearLayout.LayoutParams(0, -2, 1.0F));
        inspectorPanel.addView(sizeBoxRow, blockParams());

        addSection(ctx, "延迟 / 显示 / 消失 tick");
        LinearLayout timingRow = new LinearLayout(ctx);
        timingRow.setOrientation(LinearLayout.HORIZONTAL);
        delayField = numeric(ctx, Integer.toString(entry.delayTicks), value -> currentEntry().delayTicks = clampInt(parseInt(value, currentEntry().delayTicks), 0, 72000));
        timingRow.addView(delayField, new LinearLayout.LayoutParams(0, -2, 1.0F));
        displayField = numeric(ctx, Integer.toString(entry.displayTicks), value -> currentEntry().displayTicks = clampInt(parseInt(value, currentEntry().displayTicks), 1, 72000));
        timingRow.addView(displayField, new LinearLayout.LayoutParams(0, -2, 1.0F));
        fadeField = numeric(ctx, Integer.toString(entry.fadeTicks), value -> currentEntry().fadeTicks = clampInt(parseInt(value, currentEntry().fadeTicks), 0, 72000));
        timingRow.addView(fadeField, new LinearLayout.LayoutParams(0, -2, 1.0F));
        inspectorPanel.addView(timingRow, blockParams());

        addSection(ctx, "\u7ecf\u8fc7\u6b21\u6570\u5ef6\u8fdf / \u64ad\u653e\u4e0a\u9650");
        LinearLayout playRuleRow = new LinearLayout(ctx);
        playRuleRow.setOrientation(LinearLayout.HORIZONTAL);
        passDelayField = numeric(ctx, Integer.toString(entry.passDelayCount), value -> currentEntry().passDelayCount = clampInt(parseInt(value, currentEntry().passDelayCount), 0, 100000));
        playRuleRow.addView(passDelayField, new LinearLayout.LayoutParams(0, -2, 1.0F));
        playLimitField = numeric(ctx, Integer.toString(entry.playLimit), value -> currentEntry().playLimit = clampInt(parseInt(value, currentEntry().playLimit), 0, 100000));
        playRuleRow.addView(playLimitField, new LinearLayout.LayoutParams(0, -2, 1.0F));
        inspectorPanel.addView(playRuleRow, blockParams());

        Button playMode = button(ctx, entry.playOncePerPlayer ? "\u6a21\u5f0f: \u8be5\u73a9\u5bb6\u53ea\u67091\u6b21" : "\u6a21\u5f0f: \u6bcf\u6b21\u7ecf\u8fc7\u90fd\u64ad\u653e");
        playMode.setOnClickListener(v -> {
            AreaTipConfig.HudTextEntry selected = currentEntry();
            selected.playOncePerPlayer = !selected.playOncePerPlayer;
            markDirty();
            rebuildInspector();
        });
        inspectorPanel.addView(playMode, blockParams());

        alignButton = button(ctx, "对齐: " + alignName(entry.align));
        alignButton.setOnClickListener(v -> {
            AreaTipConfig.HudTextEntry selected = currentEntry();
            selected.align = switch (selected.align) {
                case "left" -> "center";
                case "center" -> "right";
                default -> "left";
            };
            markDirty();
            rebuildInspector();
        });
        inspectorPanel.addView(alignButton, blockParams());

        importToggleButton = button(ctx, importListOpen ? "导入背景 ▲" : "导入背景 ▼");
        importToggleButton.setOnClickListener(v -> {
            importListOpen = !importListOpen;
            if (importListOpen) {
                reloadImages();
            }
            rebuildInspector();
        });
        inspectorPanel.addView(importToggleButton, blockParams());
        if (importListOpen) {
            addImportList(ctx);
        }
    }

    private void addFontList(Context ctx) {
        ScrollView scroll = new ScrollView(ctx);
        scroll.setBackground(panelShape(0xAA10141A, 0x663A4350));
        fontListPanel = vertical(ctx);
        fontListPanel.setPadding(4, 4, 4, 4);
        for (String font : availableFonts()) {
            Button row = button(ctx, font);
            row.setTextColor(font.equals(currentEntry().font) ? 0xFFFFD36A : 0xFFE8EDF4);
            row.setOnClickListener(v -> {
                currentEntry().font = font;
                markDirty();
                fontListOpen = false;
                rebuildInspector();
            });
            fontListPanel.addView(row, blockParams());
        }
        scroll.addView(fontListPanel, new FrameLayout.LayoutParams(-1, -2));
        inspectorPanel.addView(scroll, new LinearLayout.LayoutParams(-1, 150));
    }

    private void addImportList(Context ctx) {
        ScrollView scroll = new ScrollView(ctx);
        scroll.setBackground(panelShape(0xAA10141A, 0x663A4350));
        importListPanel = vertical(ctx);
        importListPanel.setPadding(4, 4, 4, 4);
        Button chooseFile = button(ctx, "在文件中选择");
        chooseFile.setOnClickListener(v -> chooseBackgroundFile());
        importListPanel.addView(chooseFile, blockParams());
        Button deleteBackground = button(ctx, "删除当前背景");
        deleteBackground.setOnClickListener(v -> deleteCurrentBackground());
        importListPanel.addView(deleteBackground, blockParams());
        if (imageEntries.isEmpty()) {
            TextView empty = label(ctx, "没有可导入图片。目录: " + TextGroupRenderer.textureDir(), 11, 0xFFB6C0CC);
            importListPanel.addView(empty, blockParams());
        }
        for (ImageEntry image : imageEntries) {
            Button row = button(ctx, image.name() + "  " + image.width() + "x" + image.height());
            row.setOnClickListener(v -> {
                enqueueExistingLocalUpload(image.background());
                applyImportedBackground(image.background(), image.width(), image.height());
            });
            importListPanel.addView(row, blockParams());
        }
        scroll.addView(importListPanel, new FrameLayout.LayoutParams(-1, -2));
        inspectorPanel.addView(scroll, new LinearLayout.LayoutParams(-1, 180));
    }

    private void addSection(Context ctx, String text) {
        TextView label = label(ctx, text, 11, 0xFF8D98A6);
        label.setPadding(2, 8, 0, 2);
        inspectorPanel.addView(label, new LinearLayout.LayoutParams(-1, -2));
    }

    private void selectGroup(int index) {
        selectedGroupIndex = Math.clamp(index, 0, Math.max(0, working.groups.size() - 1));
        selectedTextIndex = 0;
        working.selectedGroupId = currentGroup().id;
        fontListOpen = false;
        importListOpen = false;
        colorPickerOpen = false;
        rebuildAllPanels();
    }

    private void selectText(int index) {
        selectedTextIndex = Math.clamp(index, 0, Math.max(0, currentGroup().hudTexts.size() - 1));
        fontListOpen = false;
        importListOpen = false;
        colorPickerOpen = false;
        rebuildAllPanels();
    }

    private void addTextEntry() {
        AreaTipConfig.GroupConfig group = currentGroup();
        AreaTipConfig.HudTextEntry entry = AreaTipConfig.HudTextEntry.fromGroup(group.color, group.message);
        entry.text = "";
        entry.x = (screenWidth() - entry.width) * 0.5F + group.hudTexts.size() * 18.0F;
        entry.y = (screenHeight() - entry.height) * 0.5F + group.hudTexts.size() * 18.0F;
        entry.offsetX = 8;
        entry.offsetY = 8;
        group.hudTexts.add(entry);
        selectedTextIndex = group.hudTexts.size() - 1;
        markDirty();
        rebuildAllPanels();
    }

    private void deleteSelectedTextEntry() {
        AreaTipConfig.GroupConfig group = currentGroup();
        selectedTextIndex = Math.clamp(selectedTextIndex, 0, group.hudTexts.size() - 1);
        if (group.hudTexts.size() <= 1) {
            AreaTipConfig.HudTextEntry entry = group.hudTexts.get(selectedTextIndex);
            entry.text = "";
            entry.background = "";
        } else {
            group.hudTexts.remove(selectedTextIndex);
            selectedTextIndex = Math.clamp(selectedTextIndex, 0, group.hudTexts.size() - 1);
        }
        markDirty();
        rebuildAllPanels();
    }

    private void copyStyleFromPreviousGroup() {
        if (working.groups.size() < 2) {
            return;
        }
        AreaTipConfig.GroupConfig source = working.groups.get((selectedGroupIndex + working.groups.size() - 1) % working.groups.size()).copy();
        AreaTipConfig.GroupConfig target = currentGroup();
        target.hudVisible = source.hudVisible;
        target.hudTexts = new ArrayList<>();
        for (AreaTipConfig.HudTextEntry entry : source.hudTexts) {
            AreaTipConfig.HudTextEntry copy = entry.copy();
            copy.id = UUID.randomUUID().toString();
            copy.useGroupColor = true;
            copy.color = target.color;
            target.hudTexts.add(copy);
        }
        selectedTextIndex = 0;
        markDirty();
        rebuildAllPanels();
    }

    private void applyImportedBackground(String background, int imageWidth, int imageHeight) {
        AreaTipConfig.HudTextEntry entry = currentEntry();
        entry.background = background;
        int maxWidth = Math.max(64, screenWidth() / 3);
        int maxHeight = Math.max(32, screenHeight() / 3);
        double ratio = Math.min(maxWidth / (double) imageWidth, maxHeight / (double) imageHeight);
        entry.width = Math.max(32, (int) Math.round(imageWidth * ratio));
        entry.height = Math.max(16, (int) Math.round(imageHeight * ratio));
        entry.scale = 1.0F;
        entry.rotation = 0.0F;
        entry.x = (screenWidth() - entry.width) * 0.5F;
        entry.y = (screenHeight() - entry.height) * 0.5F;
        importListOpen = false;
        markDirty();
        TextGroupRenderer.cleanupTextures();
        rebuildAllPanels();
    }

    private void chooseBackgroundFile() {
        Path selected = openImageFileDialog();
        if (selected == null || !Files.isRegularFile(selected) || !isImageFile(selected)) {
            return;
        }
        try {
            NativeImage image = TextGroupRenderer.readNativeImage(selected);
            int imageWidth = image.getWidth();
            int imageHeight = image.getHeight();
            image.close();

            Path targetDir = TextGroupRenderer.textureDir().normalize();
            Files.createDirectories(targetDir);
            Path target = uniqueTexturePath(targetDir, selected.getFileName().toString());
            Files.copy(selected, target, StandardCopyOption.REPLACE_EXISTING);
            TextGroupRenderer.cleanupTextures();
            TextAreaResourceSyncClient.enqueueUpload(target.getFileName().toString(), target);
            applyImportedBackground(target.getFileName().toString(), imageWidth, imageHeight);
        } catch (Exception ignored) {
        }
    }

    private void deleteCurrentBackground() {
        AreaTipConfig.HudTextEntry entry = currentEntry();
        String background = entry.background;
        if (background == null || background.isBlank()) {
            return;
        }
        entry.background = "";
        Path root = TextGroupRenderer.textureDir().normalize();
        Path path = root.resolve(background).normalize();
        if (path.startsWith(root)) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
            }
        }
        TextAreaResourceSyncClient.deleteRemote(background);
        TextGroupRenderer.cleanupTextures();
        markDirty();
        rebuildAllPanels();
    }

    private void enqueueExistingLocalUpload(String background) {
        if (background == null || background.isBlank() || background.startsWith("gui/text_ui/")) {
            return;
        }
        Path root = TextGroupRenderer.textureDir().normalize();
        Path path = root.resolve(background).normalize();
        if (path.startsWith(root) && Files.isRegularFile(path)) {
            TextAreaResourceSyncClient.enqueueUpload(background, path);
        }
    }

    private Path openImageFileDialog() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(5);
            filters.put(stack.UTF8("*.png"));
            filters.put(stack.UTF8("*.jpg"));
            filters.put(stack.UTF8("*.jpeg"));
            filters.put(stack.UTF8("*.bmp"));
            filters.put(stack.UTF8("*.gif"));
            filters.flip();
            String selected = TinyFileDialogs.tinyfd_openFileDialog(
                    "选择背景图片",
                    null,
                    filters,
                    "图片文件 (*.png; *.jpg; *.jpeg; *.bmp; *.gif)",
                    false
            );
            return selected == null || selected.isBlank() ? null : Path.of(selected);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Path uniqueTexturePath(Path directory, String fileName) {
        String safeName = safeImageFileName(fileName);
        String base = baseName(safeName);
        String extension = extension(safeName);
        Path candidate = directory.resolve(safeName);
        int index = 1;
        while (Files.exists(candidate)) {
            candidate = directory.resolve(base + "_" + index + extension);
            index++;
        }
        return candidate;
    }

    private static String safeImageFileName(String fileName) {
        String clean = fileName == null ? "background.png" : Path.of(fileName).getFileName().toString();
        clean = clean.replaceAll("[\\\\/:*?\"<>|]+", "_").trim();
        if (clean.isBlank() || !isImageName(clean)) {
            clean = "background.png";
        }
        return clean;
    }

    private static String baseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot <= 0 ? fileName : fileName.substring(0, dot);
    }

    private static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot <= 0 ? ".png" : fileName.substring(dot);
    }

    private boolean handlePreviewTouch(View view, MotionEvent event) {
        int action = event.getActionMasked();
        int button = buttonFromEvent(event);
        double x = event.getX();
        double y = event.getY();
        if (colorPickerOpen) {
            return handleColorPickerTouch(event, action);
        }
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_BUTTON_PRESS) {
            Pointer pointer = pointerForHit(event);
            AreaTipConfig.HudTextEntry hit = hitEntry(pointer.x(), pointer.y());
            if (hit != null) {
                draggingUsesGuiCoordinates = pointer.guiCoordinates();
                selectedTextIndex = currentGroup().hudTexts.indexOf(hit);
                rebuildAllPanels();
                dragger.attachTo(() -> transformOf(hit));
                dragger.setTransformCallback(transform -> {
                    applyTransform(hit, transform);
                    markDirty();
                    updateStatus();
                    if (surfaceView != null) {
                        surfaceView.invalidate();
                    }
                });
                return dragger.mouseClicked(pointer.x(), pointer.y(), button);
            }
        }
        if (action == MotionEvent.ACTION_MOVE) {
            Pointer pointer = pointer(event, draggingUsesGuiCoordinates);
            return dragger.mouseDragged(pointer.x(), pointer.y(), button);
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_BUTTON_RELEASE) {
            Pointer pointer = pointer(event, draggingUsesGuiCoordinates);
            return dragger.mouseReleased(pointer.x(), pointer.y(), button);
        }
        return false;
    }

    private boolean handleColorPickerTouch(MotionEvent event, int action) {
        Pointer local = pointer(event, false);
        Pointer gui = pointer(event, true);
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_BUTTON_PRESS) {
            if (!clickColorPicker(local.x(), local.y())) {
                clickColorPicker(gui.x(), gui.y());
            }
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            if (colorPicking && !pickColor(local.x(), local.y())) {
                pickColor(gui.x(), gui.y());
            }
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_BUTTON_RELEASE) {
            colorPicking = false;
            return true;
        }
        return true;
    }

    private Pointer pointerForHit(MotionEvent event) {
        Pointer local = pointer(event, false);
        if (hitEntry(local.x(), local.y()) != null) {
            return local;
        }
        Pointer gui = pointer(event, true);
        return hitEntry(gui.x(), gui.y()) != null ? gui : local;
    }

    private Pointer pointer(MotionEvent event, boolean guiCoordinates) {
        if (!guiCoordinates) {
            return new Pointer(event.getX(), event.getY(), false);
        }
        double scale = Math.max(1.0D, MinecraftClient.getInstance().getWindow().getScaleFactor());
        return new Pointer(event.getRawX() / scale, event.getRawY() / scale, true);
    }

    private int buttonFromEvent(MotionEvent event) {
        if (event.isButtonPressed(MotionEvent.BUTTON_SECONDARY) || event.getActionButton() == MotionEvent.BUTTON_SECONDARY) {
            return 1;
        }
        return 0;
    }

    private void openColorPicker() {
        loadColor(currentEntry().color & 0xFFFFFF);
        pendingColor = 0xFF000000 | (currentEntry().color & 0xFFFFFF);
        colorPickerOpen = true;
        if (surfaceView != null) {
            surfaceView.invalidate();
        }
    }

    private boolean clickColorPicker(double mouseX, double mouseY) {
        ColorPickerBounds bounds = colorPickerBounds();
        if (pickColor(mouseX, mouseY)) {
            colorPicking = true;
            return true;
        }
        if (inside(bounds.applyX(), bounds.applyY(), 58, 22, mouseX, mouseY)) {
            AreaTipConfig.HudTextEntry entry = currentEntry();
            entry.color = pendingColor;
            entry.useGroupColor = false;
            colorPickerOpen = false;
            markDirty();
            rebuildInspector();
            return true;
        }
        if (inside(bounds.closeX(), bounds.applyY(), 58, 22, mouseX, mouseY)) {
            colorPickerOpen = false;
            if (surfaceView != null) {
                surfaceView.invalidate();
            }
            return true;
        }
        return inside(bounds.panelX(), bounds.panelY(), bounds.panelW(), bounds.panelH(), mouseX, mouseY);
    }

    private boolean pickColor(double mouseX, double mouseY) {
        ColorPickerBounds bounds = colorPickerBounds();
        if (inside(bounds.mapX(), bounds.mapY(), COLOR_MAP_SIZE, COLOR_MAP_SIZE, mouseX, mouseY)) {
            saturation = MathHelper.clamp((float) ((mouseX - bounds.mapX()) / (COLOR_MAP_SIZE - 1)), 0.0F, 1.0F);
            value = MathHelper.clamp(1.0F - (float) ((mouseY - bounds.mapY()) / (COLOR_MAP_SIZE - 1)), 0.0F, 1.0F);
            pendingColor = 0xFF000000 | hsvToRgb(hue, saturation, value);
            if (surfaceView != null) {
                surfaceView.invalidate();
            }
            return true;
        }
        if (inside(bounds.hueX(), bounds.mapY(), HUE_WIDTH, COLOR_MAP_SIZE, mouseX, mouseY)) {
            hue = MathHelper.clamp((float) ((mouseY - bounds.mapY()) / (COLOR_MAP_SIZE - 1)), 0.0F, 1.0F);
            uploadedHue = -1.0F;
            pendingColor = 0xFF000000 | hsvToRgb(hue, saturation, value);
            if (surfaceView != null) {
                surfaceView.invalidate();
            }
            return true;
        }
        return false;
    }

    private void saveAndClose() {
        working.selectedGroupId = currentGroup().id;
        AreaTipConfig.setInstance(working);
        AreaTipClient.sendConfigUpdate(AreaTipConfig.getInstance());
        TextAreaHudClient.resetPlayback();
        closeToParent();
    }

    private void cancelAndClose() {
        AreaTipConfig.syncInstance(original);
        closeToParent();
    }

    private void closeToParent() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.execute(() -> client.setScreen(parent));
        }
    }

    private void toggleLeftPanel(Button toggle) {
        leftHidden = !leftHidden;
        leftPanel.setVisibility(leftHidden ? View.GONE : View.VISIBLE);
        toggle.setText(leftHidden ? ">" : "<");
    }

    private void toggleRightPanel(Button toggle) {
        rightHidden = !rightHidden;
        rightPanel.setVisibility(rightHidden ? View.GONE : View.VISIBLE);
        toggle.setText(rightHidden ? "<" : ">");
    }

    private void markDirty() {
        if (!updatingFields) {
            dirty = true;
            if (surfaceView != null) {
                surfaceView.invalidate();
            }
        }
    }

    private void updateStatus() {
        if (statusLabel == null) {
            return;
        }
        AreaTipConfig.HudTextEntry entry = currentEntry();
        statusLabel.setText(String.format(Locale.ROOT, "选中 %d/%d  x %.0f y %.0f  scale %.2f  rot %.0f",
                selectedTextIndex + 1, currentGroup().hudTexts.size(), entry.x, entry.y, entry.scale, entry.rotation));
    }

    private AreaTipConfig.GroupConfig currentGroup() {
        if (working.groups.isEmpty()) {
            working.groups.add(new AreaTipConfig.GroupConfig());
        }
        selectedGroupIndex = Math.clamp(selectedGroupIndex, 0, working.groups.size() - 1);
        AreaTipConfig.GroupConfig group = working.groups.get(selectedGroupIndex);
        if (group.hudTexts == null || group.hudTexts.isEmpty()) {
            group.hudTexts = new ArrayList<>();
            group.hudTexts.add(AreaTipConfig.HudTextEntry.fromGroup(group.color, group.message));
        }
        return group;
    }

    private AreaTipConfig.HudTextEntry currentEntry() {
        AreaTipConfig.GroupConfig group = currentGroup();
        selectedTextIndex = Math.clamp(selectedTextIndex, 0, group.hudTexts.size() - 1);
        AreaTipConfig.HudTextEntry entry = group.hudTexts.get(selectedTextIndex);
        if (entry.useGroupColor) {
            entry.color = 0xFF000000 | (group.color & 0xFFFFFF);
        }
        return entry;
    }

    private AreaTipConfig.HudTextEntry hitEntry(double mouseX, double mouseY) {
        List<AreaTipConfig.HudTextEntry> entries = currentGroup().hudTexts.stream()
                .sorted(Comparator.comparingInt(entry -> -entry.priority))
                .toList();
        for (AreaTipConfig.HudTextEntry entry : entries) {
            if (mouseX >= entry.x && mouseX <= entry.x + entry.width * entry.scale
                    && mouseY >= entry.y && mouseY <= entry.y + entry.height * entry.scale) {
                return entry;
            }
        }
        return null;
    }

    private int selectedIndexFromConfig() {
        UUID selected = working.selectedGroup().map(AreaTipConfig.GroupConfig::uuid).orElse(null);
        if (selected == null) {
            return 0;
        }
        for (int i = 0; i < working.groups.size(); i++) {
            if (selected.equals(working.groups.get(i).uuid())) {
                return i;
            }
        }
        return 0;
    }

    private void reloadImages() {
        imageEntries.clear();
        Set<Path> seen = new LinkedHashSet<>();
        Path folder = TextGroupRenderer.textureDir().normalize();
        try {
            Files.createDirectories(folder);
            try (Stream<Path> stream = Files.list(folder)) {
                stream.filter(Files::isRegularFile)
                        .filter(TextGroupEditFragment::isImageFile)
                        .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                        .forEach(path -> addImage(path.normalize(), path.getFileName().toString(), path.getFileName().toString(), seen));
            }
        } catch (IOException ignored) {
        }
        Path bundled = TextGroupRenderer.bundledTextUiDir();
        if (bundled != null && Files.isDirectory(bundled)) {
            try (Stream<Path> stream = Files.list(bundled)) {
                stream.filter(Files::isRegularFile)
                        .filter(TextGroupEditFragment::isImageFile)
                        .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                        .forEach(path -> addImage(path.normalize(), "[内置] " + path.getFileName(), "gui/text_ui/" + path.getFileName(), seen));
            } catch (IOException ignored) {
            }
        }
    }

    private void addImage(Path path, String name, String background, Set<Path> seen) {
        if (!seen.add(path)) {
            return;
        }
        try {
            NativeImage image = TextGroupRenderer.readNativeImage(path);
            imageEntries.add(new ImageEntry(name, background, image.getWidth(), image.getHeight()));
            image.close();
        } catch (Exception ignored) {
        }
    }

    private static boolean isImageFile(Path path) {
        return isImageName(path.getFileName().toString());
    }

    private static boolean isImageName(String name) {
        name = name.toLowerCase(Locale.ROOT);
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                || name.endsWith(".bmp") || name.endsWith(".gif");
    }

    private List<String> availableFonts() {
        LinkedHashSet<String> fonts = new LinkedHashSet<>(FALLBACK_FONTS);
        try {
            icyllis.modernui.graphics.text.FontCollection collection =
                    icyllis.modernui.mc.text.TextLayoutEngine.getInstance().getRawDefaultFontCollection();
            for (icyllis.modernui.graphics.text.FontFamily family : collection.getFamilies()) {
                String name = family.getFamilyName(Locale.ROOT);
                if (name != null && !name.isBlank()) {
                    fonts.add(name);
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            fonts.addAll(icyllis.modernui.graphics.text.FontFamily.getSystemFontMap().keySet());
        } catch (Throwable ignored) {
        }
        return fonts.stream().limit(160).toList();
    }

    private void renderSnapshot(DrawContext context, int width, int height) {
    }

    private void renderColorPicker(DrawContext context) {
        if (!colorPickerOpen) {
            return;
        }
        uploadColorMapIfNeeded();
        ColorPickerBounds bounds = colorPickerBounds();
        context.fill(bounds.panelX(), bounds.panelY(), bounds.panelX() + bounds.panelW(), bounds.panelY() + bounds.panelH(), 0xF0101015);
        context.drawBorder(bounds.panelX(), bounds.panelY(), bounds.panelW(), bounds.panelH(), 0xFFB8B8C2);
        context.drawText(MinecraftClient.getInstance().textRenderer, "文本颜色", bounds.panelX() + 14, bounds.panelY() + 10, 0xFFFFFFFF, false);
        context.drawTexture(RenderPipelines.GUI_TEXTURED, COLOR_MAP_TEXTURE_ID, bounds.mapX(), bounds.mapY(), 0.0F, 0.0F,
                COLOR_MAP_SIZE, COLOR_MAP_SIZE, COLOR_MAP_SIZE, COLOR_MAP_SIZE);
        context.drawBorder(bounds.mapX(), bounds.mapY(), COLOR_MAP_SIZE, COLOR_MAP_SIZE, 0xFFB8B8C2);
        for (int y = 0; y < COLOR_MAP_SIZE; y++) {
            int color = 0xFF000000 | hsvToRgb((float) y / (COLOR_MAP_SIZE - 1), 1.0F, 1.0F);
            context.fill(bounds.hueX(), bounds.mapY() + y, bounds.hueX() + HUE_WIDTH, bounds.mapY() + y + 1, color);
        }
        context.drawBorder(bounds.hueX(), bounds.mapY(), HUE_WIDTH, COLOR_MAP_SIZE, 0xFFB8B8C2);
        context.fill(bounds.previewX(), bounds.mapY(), bounds.previewX() + 32, bounds.mapY() + 32, pendingColor);
        context.drawBorder(bounds.previewX(), bounds.mapY(), 32, 32, 0xFFB8B8C2);
        drawColorCursors(context, bounds);
        drawSurfaceButton(context, bounds.applyX(), bounds.applyY(), 58, 22, "应用");
        drawSurfaceButton(context, bounds.closeX(), bounds.applyY(), 58, 22, "关闭");
    }

    private void drawColorCursors(DrawContext context, ColorPickerBounds bounds) {
        int sx = bounds.mapX() + Math.round(saturation * (COLOR_MAP_SIZE - 1));
        int sy = bounds.mapY() + Math.round((1.0F - value) * (COLOR_MAP_SIZE - 1));
        context.fill(sx - 4, sy - 1, sx + 5, sy + 1, 0xFFFFFFFF);
        context.fill(sx - 1, sy - 4, sx + 1, sy + 5, 0xFFFFFFFF);
        int hy = bounds.mapY() + Math.round(hue * (COLOR_MAP_SIZE - 1));
        context.fill(bounds.hueX() - 3, hy - 2, bounds.hueX() + HUE_WIDTH + 3, hy + 2, 0xFFFFFFFF);
        context.fill(bounds.hueX() - 2, hy - 1, bounds.hueX() + HUE_WIDTH + 2, hy + 1, 0xFF000000);
    }

    private void drawSurfaceButton(DrawContext context, int x, int y, int width, int height, String label) {
        context.fill(x, y, x + width, y + height, 0xFF2F4E61);
        context.drawBorder(x, y, width, height, 0xFF88B8D8);
        context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, label, x + width / 2, y + 7, 0xFFFFFFFF);
    }

    private void uploadColorMapIfNeeded() {
        if (colorMapTexture != null && Math.abs(uploadedHue - hue) < 0.0001F) {
            return;
        }
        NativeImage image = new NativeImage(COLOR_MAP_SIZE, COLOR_MAP_SIZE, false);
        for (int y = 0; y < COLOR_MAP_SIZE; y++) {
            float v = 1.0F - (float) y / (COLOR_MAP_SIZE - 1);
            for (int x = 0; x < COLOR_MAP_SIZE; x++) {
                float s = (float) x / (COLOR_MAP_SIZE - 1);
                image.setColorArgb(x, y, 0xFF000000 | hsvToRgb(hue, s, v));
            }
        }
        if (colorMapTexture == null) {
            colorMapTexture = new NativeImageBackedTexture(() -> "monvhua text area color map", image);
            MinecraftClient.getInstance().getTextureManager().registerTexture(COLOR_MAP_TEXTURE_ID, colorMapTexture);
        } else {
            colorMapTexture.setImage(image);
            colorMapTexture.upload();
        }
        uploadedHue = hue;
    }

    private ColorPickerBounds colorPickerBounds() {
        int panelW = COLOR_MAP_SIZE + HUE_WIDTH + 126;
        int panelH = COLOR_MAP_SIZE + 64;
        int panelX = (screenWidth() - panelW) / 2;
        int panelY = (screenHeight() - panelH) / 2;
        int mapX = panelX + 14;
        int mapY = panelY + 34;
        int hueX = mapX + COLOR_MAP_SIZE + 8;
        int previewX = hueX + HUE_WIDTH + 16;
        return new ColorPickerBounds(panelX, panelY, panelW, panelH, mapX, mapY, hueX, previewX, previewX, mapY + 72, previewX + 64);
    }

    private void loadColor(int rgb) {
        float r = ((rgb >>> 16) & 0xFF) / 255.0F;
        float g = ((rgb >>> 8) & 0xFF) / 255.0F;
        float b = (rgb & 0xFF) / 255.0F;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        value = max;
        saturation = max == 0.0F ? 0.0F : delta / max;
        if (delta == 0.0F) {
            hue = 0.0F;
        } else if (max == r) {
            hue = ((g - b) / delta) / 6.0F;
        } else if (max == g) {
            hue = (2.0F + (b - r) / delta) / 6.0F;
        } else {
            hue = (4.0F + (r - g) / delta) / 6.0F;
        }
        if (hue < 0.0F) {
            hue += 1.0F;
        }
        uploadedHue = -1.0F;
    }

    private int screenWidth() {
        return Math.max(1, surfaceWidth);
    }

    private int screenHeight() {
        return Math.max(1, surfaceHeight);
    }

    private static GenericDragger.Transform transformOf(AreaTipConfig.HudTextEntry entry) {
        return new GenericDragger.Transform(entry.x, entry.y, entry.scale, entry.rotation, entry.width, entry.height);
    }

    private static void applyTransform(AreaTipConfig.HudTextEntry entry, GenericDragger.Transform transform) {
        entry.x = transform.x();
        entry.y = transform.y();
        entry.scale = transform.scale();
        entry.rotation = transform.rotation();
    }

    private static NativeImage copyImage(NativeImage image) {
        NativeImage copy = new NativeImage(image.getWidth(), image.getHeight(), false);
        copy.copyFrom(image);
        return copy;
    }

    private Button button(Context ctx, String text) {
        Button button = new Button(ctx);
        button.setText(text);
        button.setTextColor(0xFFE8EDF4);
        button.setTextSize(12);
        button.setPadding(6, 2, 6, 2);
        button.setMinHeight(24);
        button.setBackground(panelShape(0x55313A4A, 0xB4AAB7C8));
        return button;
    }

    private Button miniButton(Context ctx, String text) {
        Button button = button(ctx, text);
        button.setTextSize(14);
        return button;
    }

    private EditText edit(Context ctx, String value, TextConsumer consumer) {
        EditText field = new EditText(ctx);
        field.setText(value == null ? "" : value);
        field.setTextColor(0xFFE8EDF4);
        field.setTextSize(12);
        field.setSingleLine(false);
        field.setPadding(5, 2, 5, 2);
        field.setBackground(panelShape(0xDD151920, 0xFF3A4350));
        field.addTextChangedListener(new STWatcher(s -> {
            if (!updatingFields) {
                consumer.accept(s);
            }
        }));
        return field;
    }

    private EditText numeric(Context ctx, String value, TextConsumer consumer) {
        EditText field = edit(ctx, value, s -> {
            consumer.accept(s);
            markDirty();
        });
        field.setSingleLine(true);
        return field;
    }

    private TextView label(Context ctx, String text, int size, int color) {
        TextView label = new TextView(ctx);
        label.setText(text);
        label.setTextSize(size);
        label.setTextColor(color);
        return label;
    }

    private LinearLayout vertical(Context ctx) {
        LinearLayout layout = new LinearLayout(ctx);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private static LinearLayout.LayoutParams blockParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, 5);
        return params;
    }

    private static ShapeDrawable panelShape(int fill, int stroke) {
        ShapeDrawable shape = new ShapeDrawable();
        shape.setShape(ShapeDrawable.RECTANGLE);
        shape.setColor(fill);
        shape.setStroke(1, stroke);
        shape.setCornerRadius(3.0F);
        return shape;
    }

    private static String previewText(String text) {
        if (text == null || text.isBlank()) {
            return "(空文本)";
        }
        String single = text.replace('\n', ' ');
        return single.length() <= 18 ? single : single.substring(0, 18) + "...";
    }

    private static int parseInt(Editable value, int fallback) {
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static float parseFloat(Editable value, float fallback) {
        try {
            return Float.parseFloat(value.toString().trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static Integer parseColor(String value) {
        String clean = value.trim();
        if (clean.startsWith("#")) {
            clean = clean.substring(1);
        }
        try {
            return Integer.parseUnsignedInt(clean, 16) & 0xFFFFFF;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean inside(int x, int y, int width, int height, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static String alignName(String align) {
        return switch (align) {
            case "center" -> "居中";
            case "right" -> "右";
            default -> "左";
        };
    }

    private static int hsvToRgb(float h, float s, float v) {
        float hue = (h - (float) Math.floor(h)) * 6.0F;
        int sector = (int) Math.floor(hue);
        float f = hue - sector;
        float p = v * (1.0F - s);
        float q = v * (1.0F - f * s);
        float t = v * (1.0F - (1.0F - f) * s);
        float r;
        float g;
        float b;
        switch (sector) {
            case 0 -> {
                r = v;
                g = t;
                b = p;
            }
            case 1 -> {
                r = q;
                g = v;
                b = p;
            }
            case 2 -> {
                r = p;
                g = v;
                b = t;
            }
            case 3 -> {
                r = p;
                g = q;
                b = v;
            }
            case 4 -> {
                r = t;
                g = p;
                b = v;
            }
            default -> {
                r = v;
                g = p;
                b = q;
            }
        }
        return ((int) (r * 255.0F) << 16) | ((int) (g * 255.0F) << 8) | (int) (b * 255.0F);
    }

    private final class PreviewRenderer implements MinecraftSurfaceView.Renderer {
        @Override
        public void onSurfaceChanged(int w, int h) {
            double guiScale = Math.max(1.0D, MinecraftClient.getInstance().getWindow().getScaleFactor());
            surfaceWidth = Math.max(1, (int) Math.round(w / guiScale));
            surfaceHeight = Math.max(1, (int) Math.round(h / guiScale));
        }

        @Override
        public void onDraw(DrawContext context, int mouseX, int mouseY, float tick, double guiScale, float alpha) {
            surfaceWidth = Math.max(1, (int) Math.round(surfaceView.getWidth() / Math.max(1.0D, guiScale)));
            surfaceHeight = Math.max(1, (int) Math.round(surfaceView.getHeight() / Math.max(1.0D, guiScale)));
            renderSnapshot(context, screenWidth(), screenHeight());
            TextGroupRenderer.renderGroup(context, currentGroup(), true, selectedTextIndex, -1L);
            renderColorPicker(context);
        }
    }

    private interface TextConsumer {
        void accept(Editable text);
    }

    private static class STWatcher implements TextWatcher {
        private final TextConsumer listener;

        STWatcher(TextConsumer listener) {
            this.listener = listener;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            listener.accept(s);
        }
    }

    private record ImageEntry(String name, String background, int width, int height) {
    }

    private record Pointer(double x, double y, boolean guiCoordinates) {
    }

    private record ColorPickerBounds(int panelX, int panelY, int panelW, int panelH, int mapX, int mapY,
                                     int hueX, int previewX, int applyX, int applyY, int closeX) {
    }
}
