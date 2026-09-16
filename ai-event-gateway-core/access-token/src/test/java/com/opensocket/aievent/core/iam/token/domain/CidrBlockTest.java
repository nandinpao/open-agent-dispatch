package com.opensocket.aievent.core.iam.token.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CidrBlockTest {
    @Test
    void acceptsIpLiteralsAndNeverResolvesHostnames() {
        CidrBlock block = CidrBlock.parse("10.0.0.0/8");

        assertTrue(block.contains("10.20.30.40"));
        assertFalse(block.contains("192.168.1.1"));
        assertFalse(block.contains("example.com"));
        assertThrows(IllegalArgumentException.class, () -> CidrBlock.parse("example.com/24"));
    }
}
