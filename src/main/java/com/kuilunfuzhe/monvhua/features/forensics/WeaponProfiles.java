package com.kuilunfuzhe.monvhua.features.forensics;

import com.google.gson.*;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.slf4j.LoggerFactory;
import java.util.*;

/** Item capabilities are not attacks: only the selected mode supplies components. */
public final class WeaponProfiles implements SimpleSynchronousResourceReloadListener {
    public static final String KEY = "monvhua:forensics";
    public record Component(String kind, double weight) {}
    public record Mode(String delivery, List<Component> components) {}
    public record Profile(String id, int priority, Set<String> items, String tag, String defaultMode,
                          Map<String, Mode> modes) {}
    public record Selection(String profile, String mode, Mode definition) {}
    private static Map<String, Profile> profiles = Map.of();
    private static final Set<String> REPORTED_CONFLICTS = new HashSet<>();
    private static final Set<String> KINDS = Set.of("cut", "puncture", "blunt", "burn", "other", "unknown");

    @Override public Identifier getFabricId() { return Identifier.of("monvhua", "forensic_profiles"); }
    @Override public void reload(ResourceManager manager) {
        Map<String, Profile> next = new TreeMap<>();
        try {
            for (var entry : manager.findResources("forensic_weapon_profiles", p -> p.getPath().endsWith(".json")).entrySet()) {
                String path = entry.getKey().getPath();
                String id = entry.getKey().getNamespace() + ":" + path.substring("forensic_weapon_profiles/".length(), path.length() - 5);
                try (var reader = entry.getValue().getReader()) {
                    next.put(id, parse(id, JsonParser.parseReader(reader).getAsJsonObject()));
                }
            }
            profiles = Map.copyOf(next);
            REPORTED_CONFLICTS.clear();
        } catch (Exception exception) {
            LoggerFactory.getLogger("monvhua-forensics").error("Invalid forensic weapon profiles; keeping previous configuration", exception);
        }
    }

    public static Profile parse(String id, JsonObject json) {
        if (json.get("schema_version").getAsInt() != 1) throw new IllegalArgumentException("Unsupported profile version: " + id);
        JsonObject match = json.getAsJsonObject("match");
        if (match.has("items") == match.has("tag")) throw new IllegalArgumentException("Use items OR tag: " + id);
        Set<String> items = new HashSet<>();
        if (match.has("items")) for (var item : match.getAsJsonArray("items")) items.add(Identifier.of(item.getAsString()).toString());
        String tag = match.has("tag") ? Identifier.of(match.get("tag").getAsString()).toString() : "";
        Map<String, Mode> modes = new LinkedHashMap<>();
        for (var entry : json.getAsJsonObject("modes").entrySet()) {
            if (!entry.getKey().matches("[a-z0-9_]{1,32}")) throw new IllegalArgumentException("Invalid mode: " + id);
            JsonObject value = entry.getValue().getAsJsonObject();
            String delivery = value.get("delivery").getAsString();
            if (!Set.of("melee", "projectile").contains(delivery)) throw new IllegalArgumentException("Invalid delivery: " + id);
            List<Component> components = new ArrayList<>();
            double total = 0;
            for (var element : value.getAsJsonArray("components")) {
                JsonObject c = element.getAsJsonObject();
                String kind = c.get("kind").getAsString();
                double weight = c.get("weight").getAsDouble();
                if (!KINDS.contains(kind) || !Double.isFinite(weight) || weight <= 0) throw new IllegalArgumentException("Invalid component: " + id);
                total += weight;
                components.add(new Component(kind, weight));
            }
            if (components.size() > 8 || Math.abs(total - 1) > 0.00001) throw new IllegalArgumentException("Component weights must sum to 1: " + id);
            modes.put(entry.getKey(), new Mode(delivery, List.copyOf(components)));
        }
        String defaultMode = json.get("default_mode").getAsString();
        if (!modes.containsKey(defaultMode) || modes.size() > 16) throw new IllegalArgumentException("Invalid default/modes: " + id);
        return new Profile(id, json.has("priority") ? json.get("priority").getAsInt() : 0,
                Set.copyOf(items), tag, defaultMode, Collections.unmodifiableMap(modes));
    }

    public static NbtCompound metadata(ItemStack stack) {
        return stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt().getCompoundOrEmpty(KEY);
    }

    public static Profile profile(ItemStack stack) {
        String explicit = metadata(stack).getString("profile", "");
        if (!explicit.isEmpty()) return profiles.get(explicit);
        String item = Registries.ITEM.getId(stack.getItem()).toString();
        Profile best = null;
        boolean ambiguous = false;
        for (Profile p : profiles.values()) {
            if (!p.items.contains(item) && (p.tag.isEmpty() || !stack.isIn(TagKey.of(RegistryKeys.ITEM, Identifier.of(p.tag))))) continue;
            if (best == null || p.priority > best.priority) { best = p; ambiguous = false; }
            else if (p.priority == best.priority) ambiguous = true;
        }
        if (ambiguous && REPORTED_CONFLICTS.add(item)) LoggerFactory.getLogger("monvhua-forensics").warn("Ambiguous equal-priority weapon profiles for {}; classification will be unknown", item);
        return ambiguous ? null : best;
    }

    public static Selection select(ItemStack stack, String delivery) {
        Profile p = profile(stack);
        if (p != null) {
            String selected = metadata(stack).getString("selected_mode", p.defaultMode);
            Mode mode = p.modes.get(selected);
            if (mode == null) { selected = p.defaultMode; mode = p.modes.get(selected); }
            return mode.delivery.equals(delivery) ? new Selection(p.id, selected, mode) : null;
        }
        // Explicit but invalid profiles must not silently become another weapon.
        if (!metadata(stack).getString("profile", "").isEmpty()) return null;
        // An ambiguous profile match must not escape into the capability-tag fallback.
        String item = Registries.ITEM.getId(stack.getItem()).toString();
        if (profiles.values().stream().anyMatch(candidate -> candidate.items.contains(item)
                || (!candidate.tag.isEmpty() && stack.isIn(TagKey.of(RegistryKeys.ITEM, Identifier.of(candidate.tag)))))) return null;
        List<String> kinds = new ArrayList<>();
        for (String kind : List.of("cut", "puncture", "blunt"))
            if (stack.isIn(TagKey.of(RegistryKeys.ITEM, Identifier.of("monvhua", "forensics/can_" + kind)))) kinds.add(kind);
        if (kinds.size() != 1 || !delivery.equals("melee")) return null;
        return new Selection("tag:" + kinds.getFirst(), "default", new Mode(delivery, List.of(new Component(kinds.getFirst(), 1))));
    }

    public static String cycle(ItemStack stack) {
        Profile p = profile(stack);
        if (p == null) return "此物品没有有效的攻击模式配置";
        List<String> modes = p.modes.entrySet().stream().filter(e -> e.getValue().delivery.equals("melee")).map(Map.Entry::getKey).toList();
        if (modes.isEmpty()) return "此物品没有近战模式";
        NbtCompound root = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
        NbtCompound meta = metadata(stack);
        String next = modes.get((modes.indexOf(meta.getString("selected_mode", p.defaultMode)) + 1) % modes.size());
        meta.putString("selected_mode", next);
        root.put(KEY, meta);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(root));
        return "攻击模式：" + switch (next) { case "slash" -> "挥砍"; case "thrust" -> "刺击"; case "pommel" -> "柄击"; default -> next; };
    }
}
