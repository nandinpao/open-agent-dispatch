package com.opensocket.aievent.core.incident;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class IncidentManagerWiringTest {

    @Test
    void shouldDeclareExactlyOneSpringInjectionConstructor() {
        List<Constructor<?>> injectionConstructors = Arrays.stream(IncidentManager.class.getDeclaredConstructors())
                .filter(constructor -> constructor.isAnnotationPresent(Autowired.class))
                .toList();

        assertEquals(1, injectionConstructors.size(),
                "IncidentManager has compatibility constructors, so its Spring injection constructor must be explicit");
        assertArrayEquals(
                new Class<?>[] {IncidentRepository.class, IncidentModuleProperties.class},
                injectionConstructors.getFirst().getParameterTypes());
    }
}
