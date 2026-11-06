package com.kuilunfuzhe.monvhua.features.forensics.blood;

import com.kuilunfuzhe.monvhua.features.paint.PaintSurface;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.*;

/** Public geometry only. Source identities are held separately in the server store. */
public record BloodPatch(String id, BlockPos pos, Direction face, float u, float v,
                         float radius, int kind, long seed, int revision) {
    public BloodPatch { pos = pos.toImmutable(); }
    public NbtCompound write() {
        NbtCompound n = new NbtCompound(); n.putString("id", id); n.putLong("pos", pos.asLong());
        n.putInt("face", face.getIndex()); n.putFloat("u", u); n.putFloat("v", v);
        n.putFloat("radius", radius); n.putInt("kind", kind); n.putLong("seed", seed); n.putInt("revision", revision); return n;
    }
    public static BloodPatch read(NbtCompound n) {
        return new BloodPatch(n.getString("id", ""), BlockPos.fromLong(n.getLong("pos", 0)), Direction.byIndex(n.getInt("face", 1)),
                n.getFloat("u", .5f), n.getFloat("v", .5f), n.getFloat("radius", .1f), n.getInt("kind", 0), n.getLong("seed", 0), n.getInt("revision", 1));
    }
    public Vec3d center() { return PaintSurface.point(pos, face, 0, 0, 1, 0).add(right().multiply(u)).add(down().multiply(v)); }
    public Vec3d right() { return PaintSurface.point(pos, face, 1, 0, 1, 0).subtract(PaintSurface.point(pos, face, 0, 0, 1, 0)); }
    public Vec3d down() { return PaintSurface.point(pos, face, 0, 1, 1, 0).subtract(PaintSurface.point(pos, face, 0, 0, 1, 0)); }
    public boolean covers(double x, double y) {
        double dx = x - u, dy = y - v;
        double squared = dx * dx + dy * dy, outer = radius * radius;
        if (squared > outer) return false;
        if (squared < outer * .76 * .76) return true;
        double angle = Math.atan2(dy, dx);
        double irregular = .88 + .07 * Math.sin(angle * 5 + (seed & 255)) + .05 * Math.cos(angle * 9 + ((seed >>> 8) & 255));
        return squared <= outer * irregular * irregular;
    }
    public boolean covers(Vec3d point) {
        Vec3d origin = PaintSurface.point(pos, face, 0, 0, 1, 0), delta = point.subtract(origin);
        return covers(delta.dotProduct(right()), delta.dotProduct(down()));
    }
}
