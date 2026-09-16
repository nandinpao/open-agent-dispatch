package com.opensocket.aievent.core.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class LegacyAuthorityRetirementFilterTest {

    private final LegacyAuthorityRetirementFilter filter = new LegacyAuthorityRetirementFilter();

    @Test
    void shouldFailClosedForLegacyAgentSkillMutation() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/agents/agent-1/skills/evaluate");
        request.setRequestURI("/admin/agents/agent-1/skills/evaluate");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chained = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> chained.set(true));

        assertThat(response.getStatus()).isEqualTo(410);
        assertThat(response.getContentAsString()).contains("LEGACY_AGENT_SKILL_AUTHORITY_RETIRED");
        assertThat(chained).isFalse();
    }

    @Test
    void shouldPreserveHistoricalSkillReads() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/agents/agent-1/skills/approved");
        request.setRequestURI("/admin/agents/agent-1/skills/approved");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chained = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> chained.set(true));

        assertThat(chained).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
