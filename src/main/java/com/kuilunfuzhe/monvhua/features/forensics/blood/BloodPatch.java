package com.kuilunfuzhe.monvhua.features.forensics.blood;

import com.kuilunfuzhe.monvhua.features.paint.PaintSurface;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.*;

/** Public geometry only. Source identities are held separately in the server store. */
public record BloodPatch(String id, BlockPos pos, Direction face, float u, float v,
                         float radius, int kind, long seed, int revision, String treatment) {
    public BloodPatch { pos = pos.toImmutable(); if (treatment == null || (!treatment.isEmpty() && treatment.length() != 256)) throw new IllegalArgumentException("Invalid treatment grid"); if(treatment.chars().anyMatch(c->c>255))throw new IllegalArgumentException("Invalid treatment intensity"); }
    public BloodPatch(String id, BlockPos pos, Direction face, float u, float v, float radius, int kind, long seed, int revision) {
        this(id,pos,face,u,v,radius,kind,seed,revision,"");
    }
    public int density(double x, double y) {
        if (treatment.isEmpty()) return 255;
        return treatment.charAt(MathHelper.clamp((int)(y*16),0,15)*16+MathHelper.clamp((int)(x*16),0,15));
    }
    public BloodPatch treated(String mask, int revision) { return new BloodPatch(id,pos,face,u,v,radius,kind,seed,revision,mask); }
    public double mass() {
        double sum=0;
        for(int y=Math.max(0,(int)Math.floor((v-radius)*64));y<Math.min(64,(int)Math.ceil((v+radius)*64));y++)for(int x=Math.max(0,(int)Math.floor((u-radius)*64));x<Math.min(64,(int)Math.ceil((u+radius)*64));x++)if(covers((x+.5)/64,(y+.5)/64))sum+=density((x+.5)/64,(y+.5)/64)/255.0;
        return sum;
    }
    public NbtCompound write() {
        NbtCompound n = new NbtCompound(); n.putString("id", id); n.putLong("pos", pos.asLong());
        n.putInt("face", face.getIndex()); n.putFloat("u", u); n.putFloat("v", v);
        n.putFloat("radius", radius); n.putInt("kind", kind); n.putLong("seed", seed); n.putInt("revision", revision); n.putString("treatment", treatment); return n;
    }
    public static BloodPatch read(NbtCompound n) {
        return new BloodPatch(n.getString("id", ""), BlockPos.fromLong(n.getLong("pos", 0)), Direction.byIndex(n.getInt("face", 1)),
                n.getFloat("u", .5f), n.getFloat("v", .5f), n.getFloat("radius", .1f), n.getInt("kind", 0), n.getLong("seed", 0), n.getInt("revision", 1), n.getString("treatment", ""));
    }
    public Vec3d center() { return PaintSurface.point(pos, face, 0, 0, 1, 0).add(right().multiply(u)).add(down().multiply(v)); }
    public Vec3d right() { return PaintSurface.point(pos, face, 1, 0, 1, 0).subtract(PaintSurface.point(pos, face, 0, 0, 1, 0)); }
    public Vec3d down() { return PaintSurface.point(pos, face, 0, 1, 1, 0).subtract(PaintSurface.point(pos, face, 0, 0, 1, 0)); }
    public boolean covers(double x, double y) {
        double dx = x - u, dy = y - v;
        if (kind == 4) {
            double angle=(seed & 65535L)/65535.0*Math.PI*2-Math.PI;
            double along=dx*Math.cos(angle)+dy*Math.sin(angle), across=-dx*Math.sin(angle)+dy*Math.cos(angle);
            return Math.pow(Math.max(0,Math.abs(along)-radius*.75),2)+across*across <= Math.pow(radius*.25,2);
        }
        if (kind == 3) {
            double angle = (seed & 65535L) / 65535.0 * Math.PI * 2 - Math.PI;
            double along = dx * Math.cos(angle) + dy * Math.sin(angle);
            double across = -dx * Math.sin(angle) + dy * Math.cos(angle);
            double width = radius * .42;
            boolean outline = along * along / (radius * radius) + across * across / (width * width) <= 1;
            return outline && (along < -radius * .2 || along > radius * .05);
        }
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
