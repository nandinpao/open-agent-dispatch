package com.opensocket.aievent.core.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class V41C3R3GHF3CutoverAuthorizationContractTest {
    private final R3HumanApiPermissionRegistry registry = new R3HumanApiPermissionRegistry();

    @Test
    void waveReadRoutesUseDedicatedViewPermission() {
        assertPermission("GET", "/api/platform/runtime-configuration/cutover-waves", "configuration.cutover.view", false);
        assertPermission("GET", "/api/platform/runtime-configuration/cutover-waves/C3R3-W6", "configuration.cutover.view", false);
        assertPermission("GET", "/api/platform/runtime-configuration/cutover-waves/C3R3-W6/safety-attestation", "configuration.cutover.view", false);
        assertPermission("GET", "/api/platform/runtime-configuration/cutover-waves/C3R3-W6/certification", "configuration.cutover.view", false);
    }

    @Test
    void waveMutationRoutesUseSeparatedAtomicPermissions() {
        assertPermission("POST", "/api/platform/runtime-configuration/cutover-waves/C3R3-W6/safety-attestation", "configuration.cutover.assess", false);
        assertPermission("POST", "/api/platform/runtime-configuration/cutover-waves/C3R3-W6/prepare", "configuration.cutover.prepare", true);
        assertPermission("POST", "/api/platform/runtime-configuration/cutover-waves/C3R3-W6/finalize", "configuration.cutover.finalize", true);
        assertPermission("POST", "/api/platform/runtime-configuration/cutover-waves/C3R3-W6/cancel", "configuration.cutover.cancel", true);
        assertPermission("POST", "/api/platform/runtime-configuration/cutover-waves/C3R3-W6/certify", "configuration.cutover.certify", true);
    }

    private void assertPermission(String method, String path, String permission, boolean highRisk) {
        var resolved = registry.resolve(method, path).orElseThrow();
        assertThat(resolved.rule().permission()).isEqualTo(permission);
        assertThat(resolved.rule().scopeType()).isEqualTo(com.opensocket.aievent.core.iam.rbac.domain.ScopeType.INSTANCE);
        assertThat(resolved.rule().highRisk()).isEqualTo(highRisk);
    }
}
