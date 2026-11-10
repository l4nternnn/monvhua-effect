package com.kuilunfuzhe.monvhua.features.carryentity;

import org.junit.jupiter.api.Test;
import java.util.OptionalDouble;
import static org.junit.jupiter.api.Assertions.*;

public class CarryEffortTest {
    @Test public void carryAndDragHaveDifferentDefaults() {
        assertEquals(.45, CarryEffort.speedMultiplier(CarryEffort.Mode.CARRY), 1e-9);
        assertEquals(.30, CarryEffort.speedMultiplier(CarryEffort.Mode.DRAG), 1e-9);
        assertTrue(CarryEffort.speedMultiplier(CarryEffort.Mode.DRAG) < CarryEffort.speedMultiplier(CarryEffort.Mode.CARRY));
    }
    @Test public void providerValuesMustBeFiniteAndPositive() {
        assertTrue(CarryEffort.hasRemaining(OptionalDouble.of(1)));
        assertFalse(CarryEffort.hasRemaining(OptionalDouble.empty()));
        assertFalse(CarryEffort.hasRemaining(OptionalDouble.of(0)));
        assertFalse(CarryEffort.hasRemaining(OptionalDouble.of(-1)));
        assertFalse(CarryEffort.hasRemaining(OptionalDouble.of(Double.NaN)));
        assertFalse(CarryEffort.hasRemaining(OptionalDouble.of(Double.POSITIVE_INFINITY)));
    }
    @Test public void speedMultiplierValidationRejectsUnsafeValues() {
        assertTrue(CarryEffort.validMultiplier(1));
        assertTrue(CarryEffort.validMultiplier(.01));
        assertFalse(CarryEffort.validMultiplier(0));
        assertFalse(CarryEffort.validMultiplier(-.1));
        assertFalse(CarryEffort.validMultiplier(1.01));
        assertFalse(CarryEffort.validMultiplier(Double.NaN));
    }
}
