package com.kuilunfuzhe.monvhua.features.carryentity;

import com.kuilunfuzhe.monvhua.features.forensics.CorpseCarryService;
import com.kuilunfuzhe.monvhua.features.forensics.physics.CorpseTransportController;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import java.util.OptionalDouble;

/** Server-thread integration API. This class reads stamina; the external system owns consumption. */
public final class CarryEffort {
    public enum Mode { CARRY, DRAG }
    @FunctionalInterface public interface StaminaProvider {
        OptionalDouble remaining(ServerPlayerEntity carrier, Mode mode);
    }
    private static final Identifier SPEED = Identifier.of("monvhua", "carry_effort_speed");
    private static volatile StaminaProvider provider;
    private static volatile double carryMultiplier = .45, dragMultiplier = .30;
    private static long lastError;
    private CarryEffort() {}

    /** Register null to disconnect the external integration. Register on the server thread. */
    public static void setStaminaProvider(StaminaProvider value) { provider = value; }
    /** Runtime tuning: fractions of movement speed, both in (0, 1]. */
    public static void setSpeedMultipliers(double carry, double drag) {
        if (!validMultiplier(carry) || !validMultiplier(drag)) throw new IllegalArgumentException("Speed multipliers must be finite and in (0, 1]");
        carryMultiplier = carry; dragMultiplier = drag;
    }
    public static boolean validMultiplier(double value) { return Double.isFinite(value) && value > 0 && value <= 1; }
    public static double speedMultiplier(Mode mode) { return mode == Mode.DRAG ? dragMultiplier : carryMultiplier; }
    public static boolean hasRemaining(OptionalDouble value) {
        return value != null && value.isPresent() && Double.isFinite(value.getAsDouble()) && value.getAsDouble() > 0;
    }
    public static boolean canMoveCorpse(ServerPlayerEntity player, Mode mode) {
        StaminaProvider current = provider;
        if (current == null) return true;
        try { return hasRemaining(current.remaining(player, mode)); }
        catch (RuntimeException error) {
            long now = System.nanoTime();
            if (now - lastError > 5_000_000_000L) {
                lastError = now;
                org.slf4j.LoggerFactory.getLogger("monvhua-carry").error("External stamina unavailable; releasing corpse", error);
            }
            return false;
        }
    }
    public static Mode activeMode(ServerPlayerEntity player) {
        String corpseMode = CorpseTransportController.mode(player.getUuid());
        if (!corpseMode.isEmpty()) return corpseMode.equals("drag") ? Mode.DRAG : Mode.CARRY;
        if (CorpseCarryService.isCarrying(player.getUuid())) return Mode.CARRY;
        if (CarryManager.CARRIED_ENTITIES.containsKey(player))
            return CarryManager.getCarryPoseMode(player) == CarryManager.CarryPoseMode.DRAG ? Mode.DRAG : Mode.CARRY;
        return null;
    }
    public static void refreshSpeed(ServerPlayerEntity player) {
        var attribute = player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
        if (attribute == null) return;
        Mode mode = activeMode(player);
        var old = attribute.getModifier(SPEED);
        if (mode == null || !player.isAlive() || player.isSpectator()) { attribute.removeModifier(SPEED); return; }
        double amount = speedMultiplier(mode) - 1;
        if (old != null && old.value() == amount) return;
        attribute.removeModifier(SPEED);
        attribute.addTemporaryModifier(new EntityAttributeModifier(SPEED, amount, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }
    public static void initialize() {
        // Read stamina before movement controllers, then reconcile speed after all end-tick releases.
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            for (var player : server.getPlayerManager().getPlayerList()) {
                if (CorpseCarryService.isCarrying(player.getUuid())) {
                    Mode mode = activeMode(player);
                    if (!canMoveCorpse(player, mode)) {
                        CorpseCarryService.handle(player, -1, true);
                        player.sendMessage(net.minecraft.text.Text.literal("体力不足或不可用，已松开遗体"), true);
                    }
                }
                refreshSpeed(player);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> server.getPlayerManager().getPlayerList().forEach(CarryEffort::refreshSpeed));
        ServerPlayConnectionEvents.DISCONNECT.register((h, s) -> clearSpeed(h.player));
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> s.getPlayerManager().getPlayerList().forEach(CarryEffort::clearSpeed));
    }
    private static void clearSpeed(ServerPlayerEntity player) {
        var attribute = player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
        if (attribute != null) attribute.removeModifier(SPEED);
    }
}
