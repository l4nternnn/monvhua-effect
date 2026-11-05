package com.kuilunfuzhe.monvhua.gui.commandpanel;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.kuilunfuzhe.monvhua.network.commandpanel.CommandPanelPackets;
import java.util.*;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.attribute.BasicFileAttributes;

public final class CommandPanelScreen extends Screen {
    private static final Gson GSON = new Gson();
    private static final Type POPUP_TYPE = new TypeToken<List<Popup>>() {}.getType();
    private static final int CONFIG_VERSION = 3;
    private final Map<Popup, ButtonWidget> popupButtons = new HashMap<>();
    private final Deque<QueuedCommand> commandQueue = new ArrayDeque<>();
    private final List<Panel> panels = new ArrayList<>();
    private final List<ButtonWidget> toolbarButtons = new ArrayList<>();
    private List<Popup> popups = new ArrayList<>();
    private Panel activePanel;
    private boolean externalConfigChanged;
    private long loadedFileModified = Long.MIN_VALUE;
    private long loadedFileSize = Long.MIN_VALUE;
    private Object loadedFileKey;
    private int previewWidth = 854;
    private int previewHeight = 480;
    private int configPollTicks;
    private int commandWaitTicks;
    private int toolbarRight;
    private int pageLabelX;
    private int pageLabelWidth;
    private String pageLabel = "";
    private int pageListOffset;
    private static final int CONTENT_TOP = 36;
    private static final int PAGE_ROW_HEIGHT = 24;
    private boolean dirty;

    public static void receiveData(long revision, String json) { MinecraftClient c=MinecraftClient.getInstance(); if(c.currentScreen instanceof CommandPanelScreen s) try { s.loadDocument(json); s.revision = Math.max(s.revision, revision); s.captureFileStamp(); s.clearAndInit(); } catch(Exception ignored) {} }
    public static void receivePermission(boolean value) { if (MinecraftClient.getInstance().currentScreen instanceof CommandPanelScreen screen) { screen.editable = value; screen.permissionReceived = true; screen.clearAndInit(); } }
    private Popup selected;
    private Operation operation = Operation.NONE;
    private double grabX, grabY, downX, downY;
    private float oldW, oldH, oldRotation;
    private boolean editable;
    private boolean permissionReceived;
    private long revision;

    public CommandPanelScreen() {
        super(Text.translatable("item.monvhua.command_panel"));
        loadLocal();
        if (panels.isEmpty()) {
            Panel panel = new Panel("面板 1");
            panel.popups.add(new Popup("示例按钮", "/say hello", 120, 90, 160, 52));
            panels.add(panel);
            setActivePanel(panel);
            dirty = true;
            save();
        }
        if (MinecraftClient.getInstance().player != null) ClientPlayNetworking.send(new CommandPanelPackets.RequestC2S());
    }
    private void loadLocal() {
        try {
            Path f = configFile();
            if (!Files.exists(f)) return;
            String json = Files.readString(f);
            loadDocument(json);
            captureFileStamp();
        } catch (Exception exception) {
            externalConfigChanged = true;
            notifyPlayer("命令面板配置读取失败：" + exception.getMessage() + "；请检查文件或先修复 JSON。默认配置不会覆盖该文件。");
        }
    }

    private void loadDocument(String json) {
        JsonElement root = JsonParser.parseString(json);
        panels.clear();
        if (root.isJsonArray()) {
            List<Popup> legacy = GSON.fromJson(root, POPUP_TYPE);
            Panel panel = new Panel("面板 1");
            if (legacy != null) panel.popups.addAll(legacy);
            normalizePopups(panel.popups);
            panels.add(panel);
            revision = 0;
            setActivePanel(panel);
            return;
        }
        if (!root.isJsonObject()) throw new IllegalArgumentException("配置根节点不是对象或旧版弹窗数组");
        Config config = GSON.fromJson(root, Config.class);
        if (config == null) throw new IllegalArgumentException("配置为空");
        revision = Math.max(0, config.revision);
        if (config.preview != null) {
            previewWidth = Math.max(160, config.preview.width);
            previewHeight = Math.max(120, config.preview.height);
        }
        if (config.panels != null && !config.panels.isEmpty()) {
            for (Panel panel : config.panels) {
                if (panel == null) continue;
                panel.normalize();
                panels.add(panel);
            }
        }
        if (panels.isEmpty()) {
            Panel panel = new Panel("面板 1");
            if (config.popups != null) panel.popups.addAll(config.popups);
            panels.add(panel);
        }
        Panel chosen = panels.stream().filter(panel -> Objects.equals(panel.id, config.activePanelId)).findFirst().orElse(panels.getFirst());
        setActivePanel(chosen);
        normalizePopups(popups);
    }

    private static void normalizePopups(List<Popup> values) {
        values.removeIf(Objects::isNull);
        for (Popup popup : values) popup.normalize();
    }

    private void setActivePanel(Panel panel) {
        activePanel = panel;
        if (!panels.contains(panel)) panels.add(panel);
        popups = panel.popups;
        selected = null;
    }

    static Path configFile() {
        return MinecraftClient.getInstance().runDirectory.toPath().resolve("config/monvhua_command_panel.json");
    }


    @Override protected void init() {
        popupButtons.clear();
        toolbarButtons.clear();
        if (permissionReceived) {
            for (Popup popup : popups) {
                ButtonWidget widget = ButtonWidget.builder(Text.literal(popup.name), b -> {
                    if (!editable) startExecution(popup);
                }).dimensions(screenX(popup.x), screenY(popup.y),
                        Math.max(20, (int) Math.round(popup.width * scaleX())),
                        Math.max(20, (int) Math.round(popup.height * scaleY()))).build();
                widget.active = true;
                popupButtons.put(popup, widget);
                addDrawableChild(widget);
            }
        }
        boolean compactToolbar = width < 400;
        int gap = compactToolbar ? 2 : 4;
        int x = 8;
        if (editable) {
            int buttonWidth = compactToolbar ? 36 : 58;
            addToolbarButton("New", x, buttonWidth, this::createPopup);
            x += buttonWidth + gap;
        }
        int panelButtonWidth = compactToolbar ? 36 : 66;
        addToolbarButton(compactToolbar ? "+页" : "＋面板", x, panelButtonWidth, this::createPanel);
        x += panelButtonWidth + gap;
        int deleteButtonWidth = compactToolbar ? 36 : 58;
        addToolbarButton(compactToolbar ? "删页" : "删除页", x, deleteButtonWidth, this::requestDeletePanel);
        x += deleteButtonWidth + gap;
        int arrowWidth = compactToolbar ? 18 : 22;
        addToolbarButton("◀", x, arrowWidth, () -> changePanel(-1));
        x += arrowWidth + gap;
        pageLabelX = x;
        pageLabelWidth = compactToolbar ? 48 : 74;
        x += pageLabelWidth + gap;
        addToolbarButton("▶", x, arrowWidth, () -> changePanel(1));
        x += arrowWidth + gap;
        int refreshX = x;
        int refreshWidth = compactToolbar ? 26 : 66;
        addToolbarButton(compactToolbar ? "↻" : "刷新", refreshX, refreshWidth, this::refreshFromDisk);
        toolbarRight = refreshX + refreshWidth;
        pageLabel = activePanel == null ? "" : activePanel.name;
        pageListOffset = Math.max(0, Math.min(pageListOffset, panels.size() - visiblePageRows()));
    }

    private ButtonWidget addToolbarButton(String label, int x, int buttonWidth, Runnable action) {
        ButtonWidget button = ButtonWidget.builder(Text.literal(label), widget -> action.run())
                .dimensions(x, 8, buttonWidth, 20).build();
        toolbarButtons.add(button);
        addDrawableChild(button);
        return button;
    }

    private void createPopup() {
        float popupWidth = 140;
        float popupHeight = 48;
        Popup popup = new Popup("New popup", "/say hello", (previewWidth - popupWidth) / 2F,
                (previewHeight - popupHeight) / 2F, popupWidth, popupHeight);
        popups.add(popup);
        select(popup);
        dirty = true;
        save();
        clearAndInit();
    }

    private void createPanel() {
        Panel panel = new Panel("面板 " + (panels.size() + 1));
        panels.add(panel);
        setActivePanel(panel);
        dirty = true;
        save();
        clearAndInit();
    }

    private void requestDeletePanel() {
        if (panels.size() <= 1) {
            notifyPlayer("至少需要保留一个面板。");
            return;
        }
        if (client != null) client.setScreen(new CommandPanelConfirmScreen(this,
                Text.literal("删除面板"), Text.literal("确定删除“" + activePanel.name + "”及其中所有弹窗吗？"),
                this::deleteActivePanel));
    }

    private void deleteActivePanel() {
        if (panels.size() <= 1 || activePanel == null) return;
        int index = panels.indexOf(activePanel);
        panels.remove(activePanel);
        Panel next = panels.get(Math.min(index, panels.size() - 1));
        setActivePanel(next);
        dirty = true;
        save();
        clearAndInit();
    }

    private void changePanel(int direction) {
        if (panels.size() <= 1 || activePanel == null) return;
        int index = panels.indexOf(activePanel);
        jumpToPanel(Math.floorMod(index + direction, panels.size()));
    }

    private void jumpToPanel(int index) {
        if (index < 0 || index >= panels.size() || panels.get(index) == activePanel) return;
        if (dirty) save();
        operation = Operation.NONE;
        setActivePanel(panels.get(index));
        if (index < pageListOffset) pageListOffset = index;
        if (index >= pageListOffset + visiblePageRows()) pageListOffset = index - visiblePageRows() + 1;
        dirty = true;
        save();
        clearAndInit();
    }

    void deletePopup(Popup popup) {
        if (popup == null || !popups.remove(popup)) return;
        if (selected == popup) selected = null;
        dirty = true;
        save();
        clearAndInit();
    }

    private void refreshFromDisk() {
        try {
            Path file = configFile();
            if (!Files.exists(file)) {
                notifyPlayer("命令面板配置文件不存在。");
                return;
            }
            loadDocument(Files.readString(file));
            dirty = false;
            externalConfigChanged = false;
            captureFileStamp();
            clearAndInit();
            notifyPlayer("已重新载入命令面板配置。");
        } catch (Exception exception) {
            notifyPlayer("命令面板配置刷新失败：" + exception.getMessage());
        }
    }

    private void notifyPlayer(String message) {
        if (client != null && client.player != null) client.player.sendMessage(Text.literal(message), false);
    }

    private void pollConfigFile() {
        try {
            Path file = configFile();
            BasicFileAttributes attributes = Files.exists(file) ? Files.readAttributes(file, BasicFileAttributes.class) : null;
            long modified = attributes == null ? -1 : attributes.lastModifiedTime().toMillis();
            long size = attributes == null ? -1 : attributes.size();
            Object fileKey = attributes == null ? null : attributes.fileKey();
            if (loadedFileModified != Long.MIN_VALUE && (modified != loadedFileModified || size != loadedFileSize
                    || !Objects.equals(fileKey, loadedFileKey))) {
                externalConfigChanged = true;
            }
        } catch (Exception ignored) {
            externalConfigChanged = true;
        }
    }

    private void captureFileStamp() {
        try {
            Path file = configFile();
            BasicFileAttributes attributes = Files.exists(file) ? Files.readAttributes(file, BasicFileAttributes.class) : null;
            loadedFileModified = attributes == null ? -1 : attributes.lastModifiedTime().toMillis();
            loadedFileSize = attributes == null ? -1 : attributes.size();
            loadedFileKey = attributes == null ? null : attributes.fileKey();
        } catch (Exception ignored) {
            loadedFileModified = -1;
            loadedFileSize = -1;
            loadedFileKey = null;
        }
    }

    private int sidebarWidth() { return Math.max(48, Math.min(120, width / 5)); }
    private int contentLeft() { return sidebarWidth() + 4; }
    private int contentRight() { return width - sidebarWidth() - 4; }
    private int visiblePageRows() { return Math.max(1, (height - CONTENT_TOP - 28) / PAGE_ROW_HEIGHT); }
    private boolean inContent(double x, double y) { return x >= contentLeft() && x < contentRight() && y >= CONTENT_TOP && y < height; }
    private double scaleX() { return Math.max(1, contentRight() - contentLeft()) / (double) Math.max(160, previewWidth); }
    private double scaleY() { return Math.max(1, height - CONTENT_TOP) / (double) Math.max(120, previewHeight); }
    private int screenX(double x) { return contentLeft() + (int) Math.round(x * scaleX()); }
    private int screenY(double y) { return CONTENT_TOP + (int) Math.round(y * scaleY()); }
    private double toConfigX(double screenX) { return (screenX - contentLeft()) / scaleX(); }
    private double toConfigY(double screenY) { return (screenY - CONTENT_TOP) / scaleY(); }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (modifiers == 0 && keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_6) {
            jumpToPanel(keyCode - GLFW.GLFW_KEY_1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (x < sidebarWidth() && y >= CONTENT_TOP) {
            pageListOffset = Math.max(0, Math.min(Math.max(0, panels.size() - visiblePageRows()),
                    pageListOffset - (int) Math.signum(vertical)));
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xCC101014);
        context.fill(0, CONTENT_TOP, sidebarWidth(), height, 0xEE202028);
        context.fill(width - sidebarWidth(), CONTENT_TOP, width, height, 0xEE202028);
        context.drawTextWithShadow(textRenderer, "页面  1–6", 6, CONTENT_TOP + 8, 0xFFFFFFFF);
        for (int row = 0; row < visiblePageRows() && row + pageListOffset < panels.size(); row++) {
            int index = row + pageListOffset;
            int y = CONTENT_TOP + 26 + row * PAGE_ROW_HEIGHT;
            Panel panel = panels.get(index);
            boolean hover = mouseX >= 4 && mouseX < sidebarWidth() - 4 && mouseY >= y && mouseY < y + 22;
            context.fill(4, y, sidebarWidth() - 4, y + 22,
                    panel == activePanel ? 0xFF42648B : hover ? 0xFF383844 : 0xFF292932);
            context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth((index + 1) + ". " + panel.name,
                    sidebarWidth() - 16), 8, y + 7, 0xFFFFFFFF);
        }
        context.enableScissor(contentLeft(), CONTENT_TOP, contentRight(), height);
        for (ButtonWidget widget : popupButtons.values()) widget.render(context,
                inContent(mouseX, mouseY) ? mouseX : -1, inContent(mouseX, mouseY) ? mouseY : -1, delta);
        String visiblePageLabel = textRenderer.trimToWidth(pageLabel, Math.max(0, pageLabelWidth - 4));
        for (Popup popup : popups) {
            int x = screenX(popup.x), y = screenY(popup.y);
            int w = (int) Math.round(popup.width * scaleX()), h = (int) Math.round(popup.height * scaleY());
            if (popup == selected && editable) {
                context.fill(x + w - 4, y + h - 4, x + w + 4, y + h + 4, 0xFFFFFFFF);
                context.fill(x + w / 2 - 4, y - 24, x + w / 2 + 4, y - 16, 0xFFFFFFFF);
            }
        }
        context.disableScissor();
        for (ButtonWidget widget : toolbarButtons) widget.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(visiblePageLabel),
                pageLabelX + pageLabelWidth / 2, 14, 0xFFFFFFFF);
    }

    @Override public boolean shouldPause() { return false; }

    @Override public void tick() {
        super.tick();
        if (++configPollTicks >= 10) {
            configPollTicks = 0;
            pollConfigFile();
        }
        if (commandWaitTicks > 0 && --commandWaitTicks > 0) return;
        QueuedCommand queued = commandQueue.pollFirst();
        if (queued != null) {
            ClientPlayNetworking.send(new CommandPanelPackets.ExecuteC2S(queued.command));
            commandWaitTicks = Math.max(0, queued.delay);
        }
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (x < sidebarWidth() && y >= CONTENT_TOP) {
            int row = (int) ((y - CONTENT_TOP - 26) / PAGE_ROW_HEIGHT);
            if (button == 0 && y >= CONTENT_TOP + 26 && row < visiblePageRows()) jumpToPanel(pageListOffset + row);
            return true;
        }
        if (button == 1) { Popup p = hit(x, y); if (p != null) { select(p); client.setScreen(new CommandPopupEditScreen(this, p)); return true; } }
        if (button == 0 && x >= 8 && x <= toolbarRight && y >= 8 && y <= 28) {
            for (ButtonWidget toolbarButton : toolbarButtons) {
                if (toolbarButton.isMouseOver(x, y)) {
                    setFocused(toolbarButton);
                    return toolbarButton.mouseClicked(x, y, button);
                }
            }
        }
        if (!inContent(x, y)) return false;
        if (!editable && super.mouseClicked(x, y, button)) return true;
        if (!editable) return false;
        if (button != 0) return super.mouseClicked(x, y, button);
        double configX = toConfigX(x), configY = toConfigY(y);
        if (selected != null && Math.abs(configX - (selected.x + selected.width / 2)) < 10 / scaleX()
                && Math.abs(configY - (selected.y - 20)) < 10 / scaleY()) {
            operation = Operation.ROTATE; downX = configX; downY = configY; oldRotation = selected.rotation; return true;
        }
        if (selected != null && Math.abs(configX - (selected.x + selected.width)) < 12 / scaleX()
                && Math.abs(configY - (selected.y + selected.height)) < 12 / scaleY()) {
            operation = Operation.RESIZE; downX = configX; downY = configY; oldW = selected.width; oldH = selected.height; return true;
        }
        Popup popup = hit(x, y);
        if (popup == null) { selected = null; return true; }
        select(popup); operation = Operation.MOVE; grabX = configX - popup.x; grabY = configY - popup.y; return true;
    }

    @Override public boolean mouseDragged(double x, double y, int button, double deltaX, double deltaY) {
        if (!editable || selected == null || operation == Operation.NONE) return super.mouseDragged(x, y, button, deltaX, deltaY);
        double configX = toConfigX(x), configY = toConfigY(y);
        if (operation == Operation.MOVE) { selected.x = (float)(configX - grabX); selected.y = (float)(configY - grabY); }
        else if (operation == Operation.RESIZE) { selected.width = Math.max(30, oldW + (float)(configX - downX)); selected.height = Math.max(20, oldH + (float)(configY - downY)); }
        else { double cx = selected.x + selected.width / 2, cy = selected.y + selected.height / 2; selected.rotation = oldRotation + (float)Math.toDegrees(Math.atan2(configY - cy, configX - cx) - Math.atan2(downY - cy, downX - cx)); }
        dirty = true;
        syncWidget(selected);
        return true;
    }

    @Override public boolean mouseReleased(double x, double y, int button) { if (operation != Operation.NONE) save(); operation = Operation.NONE; return true; }
    @Override
    public void removed() {
        if (dirty) save();
        super.removed();
    }

    private Popup hit(double x, double y) { if (!inContent(x, y)) return null; double configX = toConfigX(x), configY = toConfigY(y); for (int i = popups.size() - 1; i >= 0; i--) { Popup p = popups.get(i); if (configX >= p.x && configX <= p.x + p.width && configY >= p.y && configY <= p.y + p.height) return p; } return null; }
    private void select(Popup popup) { selected = popup; popups.remove(popup); popups.add(popup); }
    private void syncWidget(Popup popup) { ButtonWidget w = popupButtons.get(popup); if (w != null) { w.setX(screenX(popup.x)); w.setY(screenY(popup.y)); w.setWidth(Math.max(20,(int)Math.round(popup.width * scaleX()))); w.setHeight(Math.max(20,(int)Math.round(popup.height * scaleY()))); w.setMessage(Text.literal(popup.name)); } }
    private void startExecution(Popup popup) {
        commandQueue.clear();
        for (CommandLine line : popup.commands) {
            if (line.command == null || line.command.isBlank()) continue;
            commandQueue.addLast(new QueuedCommand(line.command, Math.max(0, line.delay)));
        }
        commandWaitTicks = 0;
    }
    void updateSelectedCommand(String command) { if (selected != null) { selected.commands.clear(); selected.commands.add(new CommandLine(command)); dirty = true; save(); } }
    void savePanel() { dirty = true; save(); }
    private void save() {
        if (!dirty) return;
        if (externalConfigChanged || fileChangedSinceLoad()) {
            externalConfigChanged = true;
            notifyPlayer("配置文件已在外部修改，请先点击亮起的“刷新”按钮，避免覆盖新配置。");
            return;
        }
        Path file = configFile();
        Path temporary = null;
        try {
            Config config = new Config();
            config.revision = revision + 1;
            config.activePanelId = activePanel == null ? null : activePanel.id;
            config.popups = popups;
            config.panels = panels;
            config.preview.width = previewWidth;
            config.preview.height = previewHeight;
            Files.createDirectories(file.getParent());
            temporary = Files.createTempFile(file.getParent(), "monvhua_command_panel", ".tmp");
            Files.writeString(temporary, GSON.toJson(config));
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            revision = config.revision;
            dirty = false;
            externalConfigChanged = false;
            captureFileStamp();
        } catch (Exception exception) {
            notifyPlayer("命令面板配置保存失败：" + exception.getMessage());
        } finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (Exception ignored) {}
        }
    }

    private boolean fileChangedSinceLoad() {
        if (loadedFileModified == Long.MIN_VALUE) return false;
        try {
            Path file = configFile();
            BasicFileAttributes attributes = Files.exists(file) ? Files.readAttributes(file, BasicFileAttributes.class) : null;
            long modified = attributes == null ? -1 : attributes.lastModifiedTime().toMillis();
            long size = attributes == null ? -1 : attributes.size();
            Object fileKey = attributes == null ? null : attributes.fileKey();
            return modified != loadedFileModified || size != loadedFileSize || !Objects.equals(fileKey, loadedFileKey);
        } catch (Exception ignored) {
            return true;
        }
    }


    private enum Operation { NONE, MOVE, RESIZE, ROTATE }
    private record QueuedCommand(String command, int delay) {}
    static final class Config {
        int version = CONFIG_VERSION;
        long revision;
        String activePanelId;
        List<Popup> popups = new ArrayList<>();
        List<Panel> panels = new ArrayList<>();
        Preview preview = new Preview();
    }
    static final class Preview { int width = 854; int height = 480; }
    static final class Panel {
        String id = UUID.randomUUID().toString();
        String name = "面板";
        List<Popup> popups = new ArrayList<>();
        Panel() {}
        Panel(String name) { this.name = name; }
        void normalize() {
            if (id == null || id.isBlank()) id = UUID.randomUUID().toString();
            if (name == null || name.isBlank()) name = "面板";
            if (popups == null) popups = new ArrayList<>();
            normalizePopups(popups);
        }
    }
    static final class CommandLine { String id = UUID.randomUUID().toString(); String command = ""; int delay = 2; CommandLine() {} CommandLine(String value) { command = value; } }
    static final class Popup { String id = UUID.randomUUID().toString(); String name, command; List<CommandLine> commands = new ArrayList<>(); float x, y, width, height, rotation; Popup() {} Popup(String n, String c, float px, float py, float w, float h) { name=n; command=c; commands.add(new CommandLine(c)); x=px; y=py; width=w; height=h; } void normalize() { if (id == null || id.isBlank()) id = UUID.randomUUID().toString(); if (name == null || name.isBlank()) name = "Popup"; if (commands == null) commands = new ArrayList<>(); commands.removeIf(Objects::isNull); if (commands.isEmpty() && command != null && !command.isBlank()) commands.add(new CommandLine(command)); if (commands.isEmpty()) commands.add(new CommandLine("")); for (CommandLine line : commands) { if (line.id == null || line.id.isBlank()) line.id = UUID.randomUUID().toString(); if (line.command == null) line.command = ""; if (line.delay < 0) line.delay = 2; } command = null; } }
}
