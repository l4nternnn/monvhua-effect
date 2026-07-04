package com.kuilunfuzhe.monvhua.features.textarea;

import com.kuilunfuzhe.monvhua.features.area_tip.AreaTipClient;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class TextAreaVolumeIndex {
    private static final int SECTION_SIZE = 16;
    private static final int MAX_INDEXED_SECTIONS_PER_AREA = 4096;

    private static final TextAreaVolumeIndex EMPTY = new TextAreaVolumeIndex(Map.of(), List.of());

    private final Map<SectionKey, List<Candidate>> candidatesBySection;
    private final List<Candidate> globalCandidates;

    private TextAreaVolumeIndex(Map<SectionKey, List<Candidate>> candidatesBySection, List<Candidate> globalCandidates) {
        this.candidatesBySection = candidatesBySection;
        this.globalCandidates = globalCandidates;
    }

    static TextAreaVolumeIndex empty() {
        return EMPTY;
    }

    static TextAreaVolumeIndex build(List<AreaTipClient.AreaView> areas) {
        if (areas == null || areas.isEmpty()) {
            return empty();
        }
        Map<SectionKey, List<Candidate>> candidatesBySection = new HashMap<>();
        List<Candidate> globalCandidates = new ArrayList<>();
        for (int order = 0; order < areas.size(); order++) {
            AreaTipClient.AreaView area = areas.get(order);
            if (area == null) {
                continue;
            }
            Candidate candidate = Candidate.of(order, area);
            if (area.hasExplicitBlocks()) {
                registerExplicitBlocks(candidatesBySection, candidate, area.blocks());
                continue;
            }
            registerVolume(candidatesBySection, globalCandidates, candidate, area.minBlock(), area.maxBlock());
        }
        return new TextAreaVolumeIndex(copyIndex(candidatesBySection), List.copyOf(globalCandidates));
    }

    Optional<AreaTipClient.AreaView> areaAt(BlockPos pos) {
        if (pos == null) {
            return Optional.empty();
        }
        Candidate best = null;
        List<Candidate> localCandidates = candidatesBySection.get(SectionKey.of(pos));
        best = bestMatching(best, localCandidates, pos);
        best = bestMatching(best, globalCandidates, pos);
        return best == null ? Optional.empty() : Optional.of(best.area());
    }

    private static Candidate bestMatching(Candidate best, List<Candidate> candidates, BlockPos pos) {
        if (candidates == null || candidates.isEmpty()) {
            return best;
        }
        for (Candidate candidate : candidates) {
            if (best != null && candidate.order() >= best.order()) {
                continue;
            }
            if (candidate.contains(pos)) {
                best = candidate;
            }
        }
        return best;
    }

    private static void registerExplicitBlocks(Map<SectionKey, List<Candidate>> candidatesBySection,
                                               Candidate candidate,
                                               List<BlockPos> blocks) {
        Set<SectionKey> sections = new HashSet<>();
        for (BlockPos block : blocks) {
            if (block != null) {
                sections.add(SectionKey.of(block));
            }
        }
        for (SectionKey section : sections) {
            candidatesBySection.computeIfAbsent(section, ignored -> new ArrayList<>()).add(candidate);
        }
    }

    private static void registerVolume(Map<SectionKey, List<Candidate>> candidatesBySection,
                                       List<Candidate> globalCandidates,
                                       Candidate candidate,
                                       BlockPos min,
                                       BlockPos max) {
        int minSectionX = sectionCoord(min.getX());
        int minSectionY = sectionCoord(min.getY());
        int minSectionZ = sectionCoord(min.getZ());
        int maxSectionX = sectionCoord(max.getX());
        int maxSectionY = sectionCoord(max.getY());
        int maxSectionZ = sectionCoord(max.getZ());
        long sectionCount = (long) (maxSectionX - minSectionX + 1)
                * (maxSectionY - minSectionY + 1)
                * (maxSectionZ - minSectionZ + 1);
        if (sectionCount > MAX_INDEXED_SECTIONS_PER_AREA) {
            globalCandidates.add(candidate);
            return;
        }
        for (int x = minSectionX; x <= maxSectionX; x++) {
            for (int y = minSectionY; y <= maxSectionY; y++) {
                for (int z = minSectionZ; z <= maxSectionZ; z++) {
                    candidatesBySection.computeIfAbsent(new SectionKey(x, y, z), ignored -> new ArrayList<>()).add(candidate);
                }
            }
        }
    }

    private static Map<SectionKey, List<Candidate>> copyIndex(Map<SectionKey, List<Candidate>> index) {
        Map<SectionKey, List<Candidate>> copy = new HashMap<>();
        for (Map.Entry<SectionKey, List<Candidate>> entry : index.entrySet()) {
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return copy;
    }

    private static int sectionCoord(int blockCoord) {
        return Math.floorDiv(blockCoord, SECTION_SIZE);
    }

    private record Candidate(int order, AreaTipClient.AreaView area, Set<Long> explicitBlocks) {
        static Candidate of(int order, AreaTipClient.AreaView area) {
            Set<Long> explicitBlocks = Set.of();
            if (area.hasExplicitBlocks()) {
                Set<Long> keys = new HashSet<>();
                for (BlockPos block : area.blocks()) {
                    if (block != null) {
                        keys.add(block.asLong());
                    }
                }
                explicitBlocks = Set.copyOf(keys);
            }
            return new Candidate(order, area, explicitBlocks);
        }

        boolean contains(BlockPos pos) {
            if (!explicitBlocks.isEmpty()) {
                return explicitBlocks.contains(pos.asLong());
            }
            return area.containsBlock(pos);
        }
    }

    private record SectionKey(int x, int y, int z) {
        static SectionKey of(BlockPos pos) {
            return new SectionKey(sectionCoord(pos.getX()), sectionCoord(pos.getY()), sectionCoord(pos.getZ()));
        }
    }
}
