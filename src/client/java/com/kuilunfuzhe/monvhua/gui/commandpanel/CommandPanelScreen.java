package com.kuilunfuzhe.monvhua.gui.commandpanel;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
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

public final class CommandPanelScreen extends Screen {
    private static final Gson GSON = new Gson();
    private static final Type POPUP_TYPE = new TypeToken<List<Popup>>() {}.getType();
    private static final int CONFIG_VERSION = 2;
    private final Map<Popup, ButtonWidget> popupButtons = new HashMap<>();
    private final Deque<QueuedCommand> commandQueue = new ArrayDeque<>();
    private int commandWaitTicks;

    public static void receiveData(long revision, String json) { MinecraftClient c=MinecraftClient.getInstance(); if(c.currentScreen instanceof CommandPanelScreen s) try { List<Popup> loaded=GSON.fromJson(json,POPUP_TYPE); if(loaded!=null){s.revision = revision;s.selected=null;s.clearAndInit();} } catch(Exception ignored) {} }
    public static void receivePermission(boolean value) { if (MinecraftClient.getInstance().currentScreen instanceof CommandPanelScreen screen) { screen.editable = value; screen.permissionReceived = true; screen.clearAndInit(); } }
    private final List<Popup> popups;
    private Popup selected;
    private Operation operation = Operation.NONE;
    private double grabX, grabY, downX, downY;
    private float oldW, oldH, oldRotation;
    private boolean editable;
    private boolean permissionReceived;
    private long revision;

    public CommandPanelScreen() {
        super(Text.translatable("item.monvhua.command_panel"));
        popups = new ArrayList<>();

        loadLocal();
        if (popups.isEmpty()) { popups.add(new Popup("示例按钮", "/say hello", 120, 90, 160, 52)); save(); }
        if (MinecraftClient.getInstance().player != null) ClientPlayNetworking.send(new CommandPanelPackets.RequestC2S());
    }
    private void loadLocal() {
        try {
            Path f = MinecraftClient.getInstance().runDirectory.toPath().resolve("config/monvhua_command_panel.json");
            if (!Files.exists(f)) return;
            String json = Files.readString(f);
            JsonElement root = JsonParser.parseString(json);
            Config config;
            if (root.isJsonArray()) {
                config = new Config();
                List<Popup> legacy = GSON.fromJson(root, POPUP_TYPE);
                config.popups = legacy == null ? new ArrayList<>() : legacy;
            } else {
                config = GSON.fromJson(root, Config.class);
            }
            if (config != null && config.popups != null) {
                revision = config.revision;
                for (Popup popup : config.popups) { popup.normalize(); popups.add(popup); }
            }
        } catch (Exception ignored) {}
    }


    @Override protected void init() {
        if (!permissionReceived) return;
        popupButtons.clear();
        for (Popup popup : popups) {
            ButtonWidget widget = ButtonWidget.builder(Text.literal(popup.name), b -> {
                if (!editable) startExecution(popup);
            }).dimensions((int) popup.x, (int) popup.y, Math.max(20, (int) popup.width), Math.max(20, (int) popup.height)).build();
            widget.active = true;
            popupButtons.put(popup, widget);
            addDrawableChild(widget);
        }
        if (editable) addDrawableChild(ButtonWidget.builder(Text.literal("New"), button -> {
            Popup popup = new Popup("New popup", "/say hello", 30 + popups.size() * 12, 50 + popups.size() * 12, 140, 48);
            popups.add(popup); select(popup); save(); clearAndInit();
        }).dimensions(8, 8, 60, 20).build());
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xCC101014);
        super.render(context, mouseX, mouseY, delta);
        for (Popup popup : popups) {
            int x = (int) popup.x, y = (int) popup.y, w = (int) popup.width, h = (int) popup.height;
            if (popup == selected && editable) {
                context.fill(x + w - 4, y + h - 4, x + w + 4, y + h + 4, 0xFFFFFFFF);
                context.fill(x + w / 2 - 4, y - 24, x + w / 2 + 4, y - 16, 0xFFFFFFFF);
            }
        }
    }

    @Override public boolean shouldPause() { return false; }

    @Override public void tick() {
        super.tick();
        if (commandWaitTicks > 0 && --commandWaitTicks > 0) return;
        QueuedCommand queued = commandQueue.pollFirst();
        if (queued != null) {
            ClientPlayNetworking.send(new CommandPanelPackets.ExecuteC2S(queued.command));
            commandWaitTicks = Math.max(0, queued.delay);
        }
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == 1) { Popup p = hit(x, y); if (p != null) { select(p); client.setScreen(new CommandPopupEditScreen(this, p)); return true; } }
        if (editable && button == 0 && x >= 8 && x <= 68 && y >= 8 && y <= 28 && super.mouseClicked(x, y, button)) return true;
        if (!editable && super.mouseClicked(x, y, button)) return true;
        if (!editable) return false;
        if (button != 0) return super.mouseClicked(x, y, button);
        if (selected != null && Math.abs(x - (selected.x + selected.width / 2)) < 10 && Math.abs(y - (selected.y - 20)) < 10) {
            operation = Operation.ROTATE; downX = x; downY = y; oldRotation = selected.rotation; return true;
        }
        if (selected != null && Math.abs(x - (selected.x + selected.width)) < 12 && Math.abs(y - (selected.y + selected.height)) < 12) {
            operation = Operation.RESIZE; downX = x; downY = y; oldW = selected.width; oldH = selected.height; return true;
        }
        Popup popup = hit(x, y);
        if (popup == null) { selected = null; return true; }
        select(popup); operation = Operation.MOVE; grabX = x - popup.x; grabY = y - popup.y; return true;
    }

    @Override public boolean mouseDragged(double x, double y, int button, double deltaX, double deltaY) {
        if (!editable || selected == null || operation == Operation.NONE) return super.mouseDragged(x, y, button, deltaX, deltaY);
        if (operation == Operation.MOVE) { selected.x = (float)(x - grabX); selected.y = (float)(y - grabY); }
        else if (operation == Operation.RESIZE) { selected.width = Math.max(30, oldW + (float)(x - downX)); selected.height = Math.max(20, oldH + (float)(y - downY)); }
        else { double cx = selected.x + selected.width / 2, cy = selected.y + selected.height / 2; selected.rotation = oldRotation + (float)Math.toDegrees(Math.atan2(y - cy, x - cx) - Math.atan2(downY - cy, downX - cx)); }
        syncWidget(selected);
        return true;
    }

    @Override public boolean mouseReleased(double x, double y, int button) { if (operation != Operation.NONE) save(); operation = Operation.NONE; return true; }
    @Override
    public void removed() {
        save();
        super.removed();
    }

    private Popup hit(double x, double y) { for (int i = popups.size() - 1; i >= 0; i--) { Popup p = popups.get(i); if (x >= p.x && x <= p.x + p.width && y >= p.y && y <= p.y + p.height) return p; } return null; }
    private void select(Popup popup) { selected = popup; popups.remove(popup); popups.add(popup); }
    private void syncWidget(Popup popup) { ButtonWidget w = popupButtons.get(popup); if (w != null) { w.setX((int)popup.x); w.setY((int)popup.y); w.setWidth(Math.max(20,(int)popup.width)); w.setHeight(Math.max(20,(int)popup.height)); w.setMessage(Text.literal(popup.name)); } }
    private void startExecution(Popup popup) {
        commandQueue.clear();
        for (CommandLine line : popup.commands) {
            if (line.command == null || line.command.isBlank()) continue;
            commandQueue.addLast(new QueuedCommand(line.command, Math.max(0, line.delay)));
        }
        commandWaitTicks = 0;
    }
    void updateSelectedCommand(String command) { if (selected != null) { selected.commands.clear(); selected.commands.add(new CommandLine(command)); save(); } }
    void savePanel() { save(); }
    private void save() { try { Path file = MinecraftClient.getInstance().runDirectory.toPath().resolve("config/monvhua_command_panel.json"); Files.createDirectories(file.getParent()); Config config = new Config(); config.revision = revision; config.popups = popups; Files.writeString(file, GSON.toJson(config)); } catch (Exception ignored) {} }
    private enum Operation { NONE, MOVE, RESIZE, ROTATE }
    private record QueuedCommand(String command, int delay) {}
    static final class Config { int version = CONFIG_VERSION; long revision; List<Popup> popups = new ArrayList<>(); }
    static final class CommandLine { String id = UUID.randomUUID().toString(); String command = ""; int delay = 2; CommandLine() {} CommandLine(String value) { command = value; } }
    static final class Popup { String id = UUID.randomUUID().toString(); String name, command; List<CommandLine> commands = new ArrayList<>(); float x, y, width, height, rotation; Popup() {} Popup(String n, String c, float px, float py, float w, float h) { name=n; command=c; commands.add(new CommandLine(c)); x=px; y=py; width=w; height=h; } void normalize() { if (id == null || id.isBlank()) id = UUID.randomUUID().toString(); if (name == null || name.isBlank()) name = "Popup"; if (commands == null) commands = new ArrayList<>(); commands.removeIf(Objects::isNull); if (commands.isEmpty() && command != null && !command.isBlank()) commands.add(new CommandLine(command)); if (commands.isEmpty()) commands.add(new CommandLine("")); for (CommandLine line : commands) { if (line.id == null || line.id.isBlank()) line.id = UUID.randomUUID().toString(); if (line.command == null) line.command = ""; if (line.delay < 0) line.delay = 2; } command = null; } }
}
