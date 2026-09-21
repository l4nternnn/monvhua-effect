package com.kuilunfuzhe.monvhua.client.commandpanel;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.player.PlayerEntity;
import com.kuilunfuzhe.monvhua.item.commandpanel.CommandPanelItems;
import com.kuilunfuzhe.monvhua.item.commandpanel.CommandPanelItem;
import com.kuilunfuzhe.monvhua.network.commandpanel.CommandPanelPackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import java.util.*;
import com.google.gson.*;

/** Per-client state for the UI plane attached to the switch model. */
public final class CommandPanelItemUiState {
    private static boolean visible;
    private static int selected;
    private static long session;
    private static int request;
    private static long rosterRevision = -1;
    private static final List<Entry> entries = new ArrayList<>();
    private record Entry(String role, String uuid, String name) {}

    private CommandPanelItemUiState() {}

    public static boolean isVisible() {
        return visible;
    }

    public static int selectedIndex() {
        return selected;
    }
    public static long sessionId() { return session; }
    public static int requestSeq() { return request; }

    public static void toggle() {
        visible = !visible;
        if (visible) {
            session = System.nanoTime();
            if (session == 0) session = 1;
            request = 0;
            rosterRevision = -1;
            selected = 0;
            entries.clear();
            send((byte)0, request, new Entry("", "", ""));
        } else {
            send((byte)2, ++request, new Entry("", "", ""));
            session = 0;
            request = 0;
            rosterRevision = -1;
            selected = 0;
            entries.clear();
        }
    }

    public static boolean selectPrevious() {
        if (entries.size() < 2) return false;
        int old = selected;
        selected = (selected - 1 + entries.size()) % entries.size();
        sendSelection();
        return selected != old;
    }

    public static boolean selectNext() {
        if (entries.size() < 2) return false;
        int old = selected;
        selected = (selected + 1) % entries.size();
        sendSelection();
        return selected != old;
    }

    public static void trigger(PlayerEntity player, String animation) {
        ItemStack stack = player.getMainHandStack();
        if (!stack.isOf(CommandPanelItems.COMMAND_PANEL)) return;
        CommandPanelItem.requestAnimation(animation);
    }

    public static void tick(MinecraftClient client) {
        if (client.player == null) {
            resetConnection();
        }
    }

    public static void resetConnection() {
        visible = false;
        selected = 0;
        session = 0;
        request = 0;
        rosterRevision = -1;
        entries.clear();
    }
    public static void receiveRoster(long rosterSession, long revision, String json) {
        if (!visible || rosterSession != session) return;
        if (revision <= rosterRevision) return;
        Entry previous = entries.isEmpty() ? null : entries.get(Math.max(0, Math.min(selected, entries.size() - 1)));
        int previousIndex = selected;
        List<Entry> updated = new ArrayList<>();
        try {
            for (JsonElement element : JsonParser.parseString(json).getAsJsonArray()) {
                JsonObject e = element.getAsJsonObject();
                String role = e.has("role") ? e.get("role").getAsString() : "";
                String uuid = e.has("uuid") ? e.get("uuid").getAsString() : "";
                String name = e.has("name") ? e.get("name").getAsString() : "";
                if (!role.isBlank()) updated.add(new Entry(role, uuid, name));
            }
        } catch (RuntimeException ignored) {
            updated.clear();
        }
        entries.clear();
        entries.addAll(updated);
        rosterRevision = revision;
        if (entries.isEmpty()) {
            selected = 0;
        } else {
            int preserved = previous == null ? -1 : indexOfIdentity(previous);
            selected = preserved >= 0 ? preserved : Math.max(0, Math.min(previousIndex, entries.size() - 1));
            sendSelection();
        }
    }
    public static void receiveViewResult(long resultSession, int resultRequest, boolean accepted, String role, String uuid) {
        if (!visible || resultSession != session || resultRequest != request || !accepted) return;
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (entry.role().equals(role) && entry.uuid().equals(uuid)) {
                selected = i;
                return;
            }
        }
    }
    private static int indexOfIdentity(Entry entry) {
        for (int i = 0; i < entries.size(); i++) {
            Entry candidate = entries.get(i);
            if (candidate.role().equals(entry.role()) && candidate.uuid().equals(entry.uuid())) return i;
        }
        return -1;
    }
    private static void sendSelection() { Entry e=entries.get(selected); send((byte)1, ++request, e); }
    private static void send(byte operation, int requestId) { send(operation, requestId, new Entry("", "", "")); }
    private static void send(byte operation, int requestId, Entry entry) {
        if (ClientPlayNetworking.canSend(CommandPanelPackets.PanelViewC2S.ID)) ClientPlayNetworking.send(new CommandPanelPackets.PanelViewC2S(operation, session, requestId, entry.role(), entry.uuid()));
    }

    public static boolean shouldRender(ItemStack stack, boolean firstPerson) {
        return visible && firstPerson && stack.isOf(CommandPanelItems.COMMAND_PANEL);
    }
}
