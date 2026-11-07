package com.kuilunfuzhe.monvhua.features.forensics;

import net.minecraft.server.network.ServerPlayerEntity;
import java.util.List;
import java.util.UUID;

/** Read-only boundary between collected evidence permissions and summary screens. */
public interface EvidenceSummarySource {
    EvidenceSummarySnapshot snapshot(ServerPlayerEntity viewer, EvidenceFilter filter);

    record EvidenceFilter(UUID corpseId, String part, boolean collectedOnly) {
        public EvidenceFilter {
            part = part == null ? "torso" : part;
        }
    }
    record EvidenceSummarySnapshot(List<CorpseEvidenceView> corpses, long revision, boolean complete) {
        public EvidenceSummarySnapshot {
            corpses = List.copyOf(corpses == null ? List.of() : corpses);
        }
    }
    record CorpseEvidenceView(UUID corpseId, String displayName, String part, List<String> records) {
        public CorpseEvidenceView {
            displayName = displayName == null ? "未知身份" : displayName;
            part = part == null ? "torso" : part;
            records = List.copyOf(records == null ? List.of() : records);
        }
    }
}
