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
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;

public final class CommandPanelScreen extends Screen {
    private static final Gson GSON = new Gson();
    private static final Type POPUP_TYPE = new TypeToken<List<Popup>>() {}.getType();
    private static final Map<UUID, List<Popup>> DATA = new HashMap<>();
    public static void receiveData(String json) { MinecraftClient c=MinecraftClient.getInstance(); if(c.currentScreen instanceof CommandPanelScreen s) try { List<Popup> loaded=GSON.fromJson(json,POPUP_TYPE); if(loaded!=null){s.popups.clear();s.popups.addAll(loaded);s.selected=null;s.clearAndInit();} } catch(Exception ignored) {} }
    public static void receivePermission(boolean value) { if (MinecraftClient.getInstance().currentScreen instanceof CommandPanelScreen screen) { screen.editable = value; screen.permissionReceived = true; screen.clearAndInit(); } }
    private final List<Popup> popups;
    private Popup selected;
    private Operation operation = Operation.NONE;
    private double grabX, grabY, downX, downY;
    private float oldW, oldH, oldRotation;
    private boolean editable;
    private boolean permissionReceived;

    public CommandPanelScreen() {
        super(Text.translatable("item.monvhua.command_panel"));
        UUID id = MinecraftClient.getInstance().player == null ? new UUID(0, 0) : MinecraftClient.getInstance().player.getUuid();
        popups = DATA.computeIfAbsent(id, key -> new ArrayList<>());
        if (popups.isEmpty()) popups.add(new Popup("示例按钮", "/say hello", 120, 90, 160, 52));
        if (MinecraftClient.getInstance().player != null) ClientPlayNetworking.send(new CommandPanelPackets.RequestC2S());
    }

    @Override protected void init() {
        if (!permissionReceived) return;
        if (editable) addDrawableChild(ButtonWidget.builder(Text.literal("New"), button -> {
            Popup popup = new Popup("New popup", "/say hello", 30 + popups.size() * 12, 50 + popups.size() * 12, 140, 48);
            popups.add(popup); select(popup); save();
        }).dimensions(8, 8, 60, 20).build());
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xCC101014);
        for (Popup popup : popups) {
            int x = (int) popup.x, y = (int) popup.y, w = (int) popup.width, h = (int) popup.height;
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(x + w / 2.0f, y + h / 2.0f);
            context.getMatrices().rotate((float) Math.toRadians(popup.rotation));
            context.fill(-w / 2, -h / 2, w / 2, h / 2, 0xE02A2A32);
            context.drawBorder(-w / 2, -h / 2, w, h, popup == selected ? 0xFFFFFFFF : 0xFF777777);
            context.drawTextWithShadow(textRenderer, Text.literal(popup.name), -w / 2 + 6, -h / 2 + 6, 0xFFFFFFFF);
            context.drawTextWithShadow(textRenderer, Text.literal(popup.command), -w / 2 + 6, -h / 2 + 22, 0xFFB8B8C8);
            context.getMatrices().popMatrix();
            if (popup == selected && editable) {
                context.fill(x + w - 4, y + h - 4, x + w + 4, y + h + 4, 0xFFFFFFFF);
                context.fill(x + w / 2 - 4, y - 24, x + w / 2 + 4, y - 16, 0xFFFFFFFF);
            }
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override public boolean shouldPause() { return false; }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (super.mouseClicked(x, y, button)) return true;
        if (!editable) { Popup p = hit(x, y); if (button == 0 && p != null) { ClientPlayNetworking.send(new CommandPanelPackets.ExecuteC2S(p.command)); return true; } return false; }
        if (button == 1) { Popup p = hit(x, y); if (p != null) { select(p); client.setScreen(new CommandPopupEditScreen(this, p)); return true; } return false; }
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
        return true;
    }

    @Override public boolean mouseReleased(double x, double y, int button) { if (operation != Operation.NONE) save(); operation = Operation.NONE; return true; }
    @Override public void removed() { super.removed(); }
    private Popup hit(double x, double y) { for (int i = popups.size() - 1; i >= 0; i--) { Popup p = popups.get(i); if (x >= p.x && x <= p.x + p.width && y >= p.y && y <= p.y + p.height) return p; } return null; }
    private void select(Popup popup) { selected = popup; popups.remove(popup); popups.add(popup); }
    void updateSelectedCommand(String command) { if (selected != null) { selected.command = command; save(); } }
    void savePanel() { save(); }
    private void save() { if (editable) ClientPlayNetworking.send(new CommandPanelPackets.SaveC2S(GSON.toJson(popups, POPUP_TYPE))); }
    private enum Operation { NONE, MOVE, RESIZE, ROTATE }
    static final class Popup { String name, command; float x, y, width, height, rotation; Popup(String n, String c, float px, float py, float w, float h) { name=n; command=c; x=px; y=py; width=w; height=h; } }
}
