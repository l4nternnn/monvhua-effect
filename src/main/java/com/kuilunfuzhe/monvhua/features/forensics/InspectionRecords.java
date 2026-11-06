package com.kuilunfuzhe.monvhua.features.forensics;

import net.minecraft.nbt.*;
import java.util.*;

public final class InspectionRecords {
    private InspectionRecords() {}
    /** Filter and cap on the server; equal-damage hits retain chronological order. */
    public static List<NbtCompound> strongest(NbtList hits, String part, int limit) {
        List<NbtCompound> matching = new ArrayList<>();
        for (NbtElement element : hits) if (element instanceof NbtCompound hit
                && hit.getString("part", "unknown").equals(part)
                && Float.isFinite(hit.getFloat("amount", 0)) && hit.getFloat("amount", 0) > 0) matching.add(hit);
        matching.sort(Comparator.comparingDouble((NbtCompound h) -> h.getFloat("amount", 0)).reversed());
        return matching.stream().limit(Math.max(0, limit)).map(NbtCompound::copy).toList();
    }
}
