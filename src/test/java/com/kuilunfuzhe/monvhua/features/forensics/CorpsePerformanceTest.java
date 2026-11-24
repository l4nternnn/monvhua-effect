package com.kuilunfuzhe.monvhua.features.forensics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CorpsePerformanceTest {
    @Test void disabledSamplerRetainsNothing() {
        CorpsePerformance.reset();
        assertEquals(0,CorpsePerformance.begin());
        CorpsePerformance.count(CorpsePerformance.Counter.GEOMETRY_CACHED);
        CorpsePerformance.end(CorpsePerformance.Metric.GEOMETRY,1);
        assertFalse(CorpsePerformance.report().contains("GEOMETRY"));
    }
}
