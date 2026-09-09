package com.kuilunfuzhe.monvhua.features.glitch;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

/** A normalized, axis-aligned visual plane. Bounds are inclusive block bounds. */
public record GlitchPlane(UUID id, String name, Axis normal, BlockPos min, BlockPos max,
                          boolean enabled, long seed, float density, int refreshTicks) {
    public static final Codec<GlitchPlane> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").xmap(UUID::fromString, UUID::toString).forGetter(GlitchPlane::id),
            Codec.STRING.fieldOf("name").forGetter(GlitchPlane::name),
            Codec.STRING.fieldOf("normal").xmap(Axis::valueOf, Axis::name).forGetter(GlitchPlane::normal),
            BlockPos.CODEC.fieldOf("min").forGetter(GlitchPlane::min),
            BlockPos.CODEC.fieldOf("max").forGetter(GlitchPlane::max),
            Codec.BOOL.fieldOf("enabled").forGetter(GlitchPlane::enabled),
            Codec.LONG.fieldOf("seed").forGetter(GlitchPlane::seed),
            Codec.FLOAT.fieldOf("density").forGetter(GlitchPlane::density),
            Codec.INT.fieldOf("refreshTicks").forGetter(GlitchPlane::refreshTicks)
    ).apply(instance, GlitchPlane::new));

    public GlitchPlane {
        id = id == null ? UUID.randomUUID() : id;
        name = name == null ? "" : name;
        normal = normal == null ? Axis.Z : normal;
        min = min == null ? BlockPos.ORIGIN : min.toImmutable();
        max = max == null ? min : max.toImmutable();
        density = Math.clamp(density, 0.0F, 1.0F);
        refreshTicks = Math.clamp(refreshTicks, 1, 20);
    }

    public GlitchPlane withEnabled(boolean value) { return new GlitchPlane(id, name, normal, min, max, value, seed, density, refreshTicks); }
    public GlitchPlane withDensity(float value) { return new GlitchPlane(id, name, normal, min, max, enabled, seed, value, refreshTicks); }
    public GlitchPlane withRefreshTicks(int value) { return new GlitchPlane(id, name, normal, min, max, enabled, seed, density, value); }
    public GlitchPlane withSeed(long value) { return new GlitchPlane(id, name, normal, min, max, enabled, value, density, refreshTicks); }

    public enum Axis { X, Y, Z }
}
