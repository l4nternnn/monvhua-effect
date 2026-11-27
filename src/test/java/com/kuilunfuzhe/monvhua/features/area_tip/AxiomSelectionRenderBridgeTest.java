package com.kuilunfuzhe.monvhua.features.area_tip;

import org.junit.jupiter.api.Test;
import java.lang.reflect.InvocationTargetException;
import static org.junit.jupiter.api.Assertions.*;

class AxiomSelectionRenderBridgeTest {
    public static class Modern {
        static Object context;
        static boolean flags;
        public static void render(Object value, boolean flag) { context = value; flags = flag; }
        public static void render(Object value, int flag) { fail("Must prefer modern signature"); }
    }
    public static class Legacy {
        static Object context;
        static int flags;
        public static void render(Object value, int flag) { context = value; flags = flag; }
    }
    public static class Broken {
        public static void render(Object value, boolean flag) { throw new IllegalStateException("render failure"); }
    }
    @Test void modernAndLegacyPreserveFullSelectionFlags() throws Exception {
        Object context = new Object();
        var modern = new AxiomSelectionRenderBridge(Modern.class, Object.class);
        modern.render(context);
        assertSame(context, Modern.context);
        assertFalse(Modern.flags);
        var legacy = new AxiomSelectionRenderBridge(Legacy.class, Object.class);
        legacy.render(context);
        assertSame(context, Legacy.context);
        assertEquals(7, Legacy.flags);
        modern.render(context); // cached binding remains usable
    }
    @Test void unsupportedApiAndRenderFailureRemainDiagnosable() throws Exception {
        assertThrows(NoSuchMethodException.class, () -> new AxiomSelectionRenderBridge(Object.class, Object.class));
        var bridge = new AxiomSelectionRenderBridge(Broken.class, Object.class);
        var error = assertThrows(InvocationTargetException.class, () -> bridge.render(new Object()));
        assertInstanceOf(IllegalStateException.class, error.getCause());
    }
}
