package com.opensocket.aievent.core.identity.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

import com.opensocket.aievent.core.identity.AdminAccount;
import com.opensocket.aievent.core.identity.AdminIdentityRepository;
import com.opensocket.aievent.core.identity.AdminPrincipal;
import com.opensocket.aievent.core.identity.AdminRole;
import com.opensocket.aievent.core.identity.dto.AdminSessionDescriptor;
import com.opensocket.aievent.core.identity.dto.AdminSessionRevocationResponse;
import com.opensocket.aievent.core.identity.dto.AdminSessionsResponse;

/** Lists and revokes Redis-backed Admin sessions without exposing raw session identifiers to browser JavaScript. */
@Service
public class AdminSessionManagementService {
    private static final int PUBLIC_REFERENCE_BYTES = 16;

    private final FindByIndexNameSessionRepository<? extends Session> sessions;
    private final AdminIdentityRepository identities;
    private final AdminSecurityAuditService audit;

    public AdminSessionManagementService(FindByIndexNameSessionRepository<? extends Session> sessions,
                                         AdminIdentityRepository identities,
                                         AdminSecurityAuditService audit) {
        this.sessions = sessions;
        this.identities = identities;
        this.audit = audit;
    }

    public AdminSessionsResponse list(Authentication authentication, HttpServletRequest request) {
        AdminPrincipal actor = requirePrincipal(authentication);
        String currentSessionId = currentSessionId(request);
        List<Session> visible = visibleSessions(actor);
        List<AdminSessionDescriptor> descriptors = visible.stream()
                .map(session -> descriptor(session, principal(session), currentSessionId))
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(AdminSessionDescriptor::lastAccessedAt).reversed())
                .toList();
        return new AdminSessionsResponse(reference(currentSessionId), descriptors);
    }

    public AdminSessionRevocationResponse revoke(String sessionReference,
                                                  Authentication authentication,
                                                  HttpServletRequest request) {
        AdminPrincipal actor = requirePrincipal(authentication);
        Session target = visibleSessions(actor).stream()
                .filter(session -> constantTimeEquals(reference(session.getId()), sessionReference))
                .findFirst()
                .orElseThrow(() -> new AdminSessionNotFoundException(sessionReference));
        AdminPrincipal owner = principal(target);
        boolean admin = actor.roles().contains(AdminRole.ADMIN);
        if (!admin && (owner == null || !actor.getUsername().equalsIgnoreCase(owner.getUsername()))) {
            audit.record("SESSION_REVOKE", "DENIED", actor.getUsername(), actor, request,
                    "Session is owned by another account.");
            throw new AdminSessionAccessDeniedException("Only ADMIN can revoke another user's session.");
        }

        boolean current = target.getId().equals(currentSessionId(request));
        sessions.deleteById(target.getId());
        if (current) {
            HttpSession currentSession = request.getSession(false);
            if (currentSession != null) currentSession.invalidate();
        }
        audit.record("SESSION_REVOKE", "SUCCESS", actor.getUsername(), actor, request,
                owner == null ? "owner=unknown" : "owner=" + owner.getUsername());
        return new AdminSessionRevocationResponse("REVOKED", reference(target.getId()), current, OffsetDateTime.now());
    }

    private List<Session> visibleSessions(AdminPrincipal actor) {
        Map<String, Session> found = new LinkedHashMap<>();
        if (actor.roles().contains(AdminRole.ADMIN)) {
            for (AdminAccount account : identities.findAll()) {
                sessions.findByPrincipalName(account.username()).forEach((id, session) -> found.putIfAbsent(id, session));
            }
        }
        else {
            sessions.findByPrincipalName(actor.getUsername()).forEach((id, session) -> found.putIfAbsent(id, session));
        }
        return new ArrayList<>(found.values());
    }

    private AdminSessionDescriptor descriptor(Session session, AdminPrincipal owner, String currentSessionId) {
        if (owner == null) return null;
        return new AdminSessionDescriptor(
                reference(session.getId()),
                owner.getUsername(),
                owner.userId(),
                owner.selectedTenantId(),
                session.getCreationTime().atOffset(ZoneOffset.UTC),
                session.getLastAccessedTime().atOffset(ZoneOffset.UTC),
                session.getLastAccessedTime().plus(session.getMaxInactiveInterval()).atOffset(ZoneOffset.UTC),
                session.getId().equals(currentSessionId),
                session.isExpired());
    }

    private AdminPrincipal principal(Session session) {
        Object value = session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        if (value instanceof SecurityContext context
                && context.getAuthentication() != null
                && context.getAuthentication().getPrincipal() instanceof AdminPrincipal principal) {
            return principal;
        }
        return null;
    }

    private String currentSessionId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? "" : session.getId();
    }

    static String reference(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return "";
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(sessionId.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, PUBLIC_REFERENCE_BYTES);
        }
        catch (Exception exception) {
            throw new IllegalStateException("Unable to create Admin session reference.", exception);
        }
    }

    private boolean constantTimeEquals(String expected, String provided) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                (provided == null ? "" : provided.trim()).getBytes(StandardCharsets.UTF_8));
    }

    private AdminPrincipal requirePrincipal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AdminPrincipal principal)) {
            throw new AdminSessionAccessDeniedException("Authenticated Core Admin principal is required.");
        }
        return principal;
    }
}
