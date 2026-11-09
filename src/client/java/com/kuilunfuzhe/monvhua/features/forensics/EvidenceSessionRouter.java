package com.kuilunfuzhe.monvhua.features.forensics;

import com.kuilunfuzhe.monvhua.network.forensics.ForensicPackets;
import java.util.HashMap;
import java.util.Map;

/** Client-thread routing: only the newest outstanding request can update each live panel. */
public final class EvidenceSessionRouter {
    private static final Map<Integer,EvidencePanel> pending = new HashMap<>();
    public static void expect(EvidencePanel panel,int id){remove(panel);pending.put(id,panel);}
    public static void remove(EvidencePanel panel){pending.values().removeIf(value->value==panel);}
    public static void receive(ForensicPackets.SummaryResult result){
        var panel=pending.remove(result.requestId());if(panel!=null)panel.accept(result.snapshot());
    }
    public static void clear(){pending.clear();}
}
