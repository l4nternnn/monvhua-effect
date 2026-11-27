package com.kuilunfuzhe.monvhua.features.area_tip;

import java.lang.reflect.Method;

/** Resolves once without linking the mod to one particular Axiom render descriptor. */
public final class AxiomSelectionRenderBridge {
    private final Method render;
    private final Object flags;

    public AxiomSelectionRenderBridge(Class<?> selectionType, Class<?> contextType) throws NoSuchMethodException {
        Method method;
        Object argument;
        try {
            method = selectionType.getMethod("render", contextType, boolean.class);
            // In 6.1.3 false maps to region render flags 7; true maps to flags 4.
            argument = false;
        } catch (NoSuchMethodException legacy) {
            method = selectionType.getMethod("render", contextType, int.class);
            argument = 7;
        }
        render = method;
        flags = argument;
    }

    public void render(Object context) throws ReflectiveOperationException {
        render.invoke(null, context, flags);
    }
}
