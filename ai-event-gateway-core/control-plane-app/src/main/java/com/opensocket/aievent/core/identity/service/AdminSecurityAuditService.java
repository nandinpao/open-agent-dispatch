package com.opensocket.aievent.core.identity.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import com.opensocket.aievent.core.identity.AdminPrincipal;
import com.opensocket.aievent.core.identity.AdminRole;
import com.opensocket.aievent.core.identity.dto.AdminSecurityAuditEvent;
import com.opensocket.aievent.core.identity.dto.AdminSecurityAuditResponse;

@Service
public class AdminSecurityAuditService {
    private static final Logger log = LoggerFactory.getLogger(AdminSecurityAuditService.class);
    private static final int MAX_EVENTS = 500;
    private final Deque<AdminSecurityAuditEvent> events = new ConcurrentLinkedDeque<>();

    public void record(String type, String outcome, String username, AdminPrincipal principal,
                       HttpServletRequest request, String reason) {
        HttpSession session = request == null ? null : request.getSession(false);
        String sessionReference = session == null ? "" : sessionReference(session.getId());
        AdminSecurityAuditEvent event = new AdminSecurityAuditEvent(
                UUID.randomUUID().toString(), OffsetDateTime.now(), safe(type), safe(outcome), safe(username),
                principal == null ? "" : principal.userId(), principal == null ? "" : principal.selectedTenantId(),
                sessionReference, clientAddress(request), request == null ? "" : safe(request.getHeader("User-Agent")), safe(reason));
        events.addFirst(event);
        while (events.size() > MAX_EVENTS) events.pollLast();
        log.info("admin_security_audit eventType={} outcome={} username={} userId={} tenantId={} sessionRef={} sourceAddress={} reason={}",
                event.eventType(), event.outcome(), event.username(), event.userId(), event.tenantId(), event.sessionReference(), event.sourceAddress(), event.reason());
    }

    public AdminSecurityAuditResponse recent(Authentication authentication, int limit) {
        requireAdmin(authentication);
        int safeLimit = Math.max(1, Math.min(limit, 500));
        ArrayList<AdminSecurityAuditEvent> result = new ArrayList<>(safeLimit);
        for (AdminSecurityAuditEvent event : events) { if (result.size() >= safeLimit) break; result.add(event); }
        return new AdminSecurityAuditResponse(result);
    }

    private AdminPrincipal requireAdmin(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AdminPrincipal principal)
                || !principal.roles().contains(AdminRole.ADMIN)) {
            throw new AdminSessionAccessDeniedException("ADMIN role is required to inspect authentication audit events.");
        }
        return principal;
    }

    static String sessionReference(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return "";
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(sessionId.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (Exception ex) { return "unavailable"; }
    }
    private String clientAddress(HttpServletRequest request) {
        if (request == null) return "";
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank() ? safe(request.getRemoteAddr()) : forwarded.split(",",2)[0].trim();
    }
    private static String safe(String value) { return value == null ? "" : value.replaceAll("[\r\n]", " ").trim(); }
}
