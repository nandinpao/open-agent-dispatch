package com.opensocket.aievent.core.a2a.core;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class A2ACoordinationGuardTest {
    private final A2ACoordinationGuard guard = new A2ACoordinationGuard();
    @Test void rejectsSameDirectionalDomain() { assertThrows(IllegalArgumentException.class, () -> guard.requireDirectionalDomains("finance", "finance")); }
    @Test void calculatesNextHop() { assertEquals(3, guard.requireHopWithinLimit(2, 4)); }
    @Test void rejectsHopLimit() { assertThrows(IllegalStateException.class, () -> guard.requireHopWithinLimit(4, 4)); }
    @Test void rejectsDomainCycle() { assertThrows(IllegalStateException.class, () -> guard.requireNoDomainCycle(List.of("finance", "it"), "finance")); }
}
