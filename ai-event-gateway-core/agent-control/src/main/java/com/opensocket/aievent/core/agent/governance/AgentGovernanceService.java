package com.opensocket.aievent.core.agent.governance;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opensocket.aievent.core.agent.setup.AgentSetupTroubleshootingStep;

@Service
public class AgentGovernanceService {
    private static final Logger log = LoggerFactory.getLogger(AgentGovernanceService.class);

    private final AgentGovernanceRepository repository;

    public AgentGovernanceService(AgentGovernanceRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public AgentEnrollmentRequest submitEnrollment(AgentEnrollmentRequest request) {
        AgentEnrollmentRequest input = request == null ? new AgentEnrollmentRequest() : request;
        String claimedAgentId = firstNonBlank(input.getClaimedAgentId(), input.getAgentName());
        String tenantId = firstNonBlank(input.getTenantId(), "default");
        String stage = "VALIDATE_REQUEST";
        try {
            log.info("agent_enrollment_submit_started tenantId={} claimedAgentId={} agentName={} agentType={} fingerprintPresent={} metadataKeys={}",
                    safe(tenantId), safe(claimedAgentId), safe(input.getAgentName()), safe(input.getAgentType()),
                    !blank(input.getFingerprint()), input.getSubmittedMetadata() == null ? 0 : input.getSubmittedMetadata().size());
            if (blank(claimedAgentId)) {
                throw new IllegalArgumentException("claimedAgentId is required to submit agent enrollment");
            }
            OffsetDateTime now = now();

            stage = "FIND_LATEST_ENROLLMENT";
            var existing = repository.findLatestEnrollmentByAgent(claimedAgentId);
            log.info("agent_enrollment_latest_lookup_completed tenantId={} claimedAgentId={} existing={}",
                    safe(tenantId), safe(claimedAgentId), existing.isPresent());

            stage = existing.isPresent() ? "MERGE_EXISTING_ENROLLMENT" : "INITIALIZE_ENROLLMENT";
            AgentEnrollmentRequest enrollment = existing
                    .map(value -> mergeEnrollmentObservation(value, input, now))
                    .orElseGet(() -> initializeEnrollment(input, claimedAgentId, now));

            stage = "SAVE_ENROLLMENT";
            log.info("agent_enrollment_save_started tenantId={} claimedAgentId={} enrollmentId={} status={}",
                    safe(enrollment.getTenantId()), safe(claimedAgentId), safe(enrollment.getEnrollmentId()),
                    enrollment.getStatus() == null ? "" : enrollment.getStatus().name());
            AgentEnrollmentRequest saved = repository.saveEnrollment(enrollment);

            stage = "APPEND_AUDIT";
            if (saved.getCreatedAt() != null && saved.getCreatedAt().equals(saved.getUpdatedAt())) {
                appendAudit(null, saved.getEnrollmentId(), "ENROLLMENT_SUBMITTED", null, saved.getStatus().name(), "system", "Agent enrollment submitted");
            }
            log.info("agent_enrollment_submit_completed tenantId={} claimedAgentId={} enrollmentId={} status={}",
                    safe(saved.getTenantId()), safe(claimedAgentId), safe(saved.getEnrollmentId()),
                    saved.getStatus() == null ? "" : saved.getStatus().name());
            return saved;
        } catch (RuntimeException ex) {
            log.error("agent_enrollment_submit_failed stage={} tenantId={} claimedAgentId={} agentName={} agentType={} exception={} message={}",
                    stage, safe(tenantId), safe(claimedAgentId), safe(input.getAgentName()), safe(input.getAgentType()),
                    ex.getClass().getName(), safe(ex.getMessage()), ex);
            throw ex;
        }
    }

    @Transactional
    public AgentProfile approveEnrollment(String enrollmentId, AgentEnrollmentApprovalCommand command) {
        AgentEnrollmentRequest enrollment = repository.findEnrollment(enrollmentId)
                .orElseThrow(() -> new IllegalArgumentException("Enrollment not found: " + enrollmentId));
        AgentEnrollmentApprovalCommand request = command == null ? new AgentEnrollmentApprovalCommand() : command;
        OffsetDateTime now = now();
        String agentId = firstNonBlank(request.getAgentId(), enrollment.getClaimedAgentId());
        if (blank(agentId)) {
            throw new IllegalArgumentException("agentId or claimedAgentId is required to approve enrollment");
        }
        String tokenHash = credentialHash(request.getCredentialToken(), request.getCredentialHash());
        if (blank(tokenHash) && blank(request.getPublicKeyFingerprint())) {
            throw new IllegalArgumentException("credentialToken, credentialHash, or publicKeyFingerprint is required to approve an agent enrollment");
        }
        AgentProfile profile = repository.findProfile(agentId).orElseGet(AgentProfile::new);
        if (isSecurityBlocked(profile)) {
            throw new IllegalStateException(
                    "Enrollment approval cannot restore a blocked Agent profile. "
                            + "Use /admin/agents/{agentId}/approve with credential rotation.");
        }
        String oldStatus = profile.getApprovalStatus() == null ? null : profile.getApprovalStatus().name();
        AgentEnrollmentStatus oldEnrollmentStatus = enrollment.getStatus();
        profile.setAgentId(agentId);
        profile.setTenantId(firstNonBlank(request.getTenantId(), enrollment.getTenantId(), profile.getTenantId(), "default"));
        profile.setAgentName(firstNonBlank(request.getAgentName(), enrollment.getAgentName(), profile.getAgentName(), agentId));
        profile.setAgentType(firstNonBlank(request.getAgentType(), enrollment.getAgentType(), profile.getAgentType(), "UNKNOWN"));
        profile.setOwnerTeam(firstNonBlank(request.getOwnerTeam(), profile.getOwnerTeam()));
        profile.setDescription(firstNonBlank(request.getDescription(), profile.getDescription()));
        profile.setApprovalStatus(AgentApprovalStatus.APPROVED);
        profile.setEnabled(true);
        profile.setRiskStatus(AgentRiskStatus.NORMAL);
        profile.setPolicyVersion(profile.getPolicyVersion() + 1);
        profile.setCreatedAt(profile.getCreatedAt() == null ? now : profile.getCreatedAt());
        profile.setUpdatedAt(now);
        AgentProfile saved = repository.saveProfile(profile);

        enrollment.setStatus(AgentEnrollmentStatus.APPROVED);
        enrollment.setReviewedBy(firstNonBlank(request.getApprovedBy(), "system"));
        enrollment.setReviewedAt(now);
        enrollment.setReviewComment(request.getComment());
        enrollment.setUpdatedAt(now);
        repository.saveEnrollment(enrollment);

        repository.replaceCapabilities(agentId, buildCapabilities(agentId, request.getCapabilities(), enrollment.getReviewedBy(), now));
        repository.replaceScopes(agentId, buildScopes(agentId, profile.getTenantId(), request.getScopes(), now));

        saveCredential(agentId, request.getCredentialType(), tokenHash, request.getPublicKeyFingerprint(), Math.max(1, saved.getPolicyVersion()), request.getCredentialExpiresAt(), now);

        saved.setCapabilities(repository.findEnabledCapabilities(agentId));
        saved.setAuthorizationScopes(repository.findEnabledScopes(agentId));
        String auditAction = oldEnrollmentStatus == AgentEnrollmentStatus.REJECTED ? "ENROLLMENT_REAPPROVED" : "ENROLLMENT_APPROVED";
        appendAudit(agentId, enrollmentId, auditAction, oldStatus, AgentApprovalStatus.APPROVED.name(), enrollment.getReviewedBy(), request.getComment());
        return enrichProfile(saved);
    }

    @Transactional
    public AgentEnrollmentRequest rejectEnrollment(String enrollmentId, AgentEnrollmentRejectCommand command) {
        AgentEnrollmentRequest enrollment = repository.findEnrollment(enrollmentId)
                .orElseThrow(() -> new IllegalArgumentException("Enrollment not found: " + enrollmentId));
        OffsetDateTime now = now();
        String operatorId = command == null || blank(command.rejectedBy()) ? "system" : command.rejectedBy();
        String reason = command == null ? null : command.reason();
        enrollment.setStatus(AgentEnrollmentStatus.REJECTED);
        enrollment.setReviewedBy(operatorId);
        enrollment.setReviewedAt(now);
        enrollment.setReviewComment(reason);
        enrollment.setUpdatedAt(now);
        AgentEnrollmentRequest saved = repository.saveEnrollment(enrollment);

        String agentId = firstNonBlank(enrollment.getClaimedAgentId(), enrollment.getAgentName());
        if (!blank(agentId)) repository.findProfile(agentId).ifPresent(profile -> {
            String oldStatus = profile.getApprovalStatus() == null ? null : profile.getApprovalStatus().name();
            profile.setApprovalStatus(AgentApprovalStatus.REJECTED);
            profile.setEnabled(false);
            profile.setPolicyVersion(profile.getPolicyVersion() + 1);
            profile.setUpdatedAt(now);
            repository.revokeCredentials(agentId, firstNonBlank(reason, "enrollment rejected by admin"), now);
            AgentProfile rejectedProfile = repository.saveProfile(profile);
            appendAudit(agentId, enrollmentId, "AGENT_REJECTED", oldStatus, rejectedProfile.getApprovalStatus().name(), operatorId, firstNonBlank(reason, "Rejected enrollment also disabled the Core Agent profile"));
        });

        appendAudit(agentId, enrollmentId, "ENROLLMENT_REJECTED", null, AgentEnrollmentStatus.REJECTED.name(), operatorId, reason);
        return saved;
    }

    public AgentConnectionAuthorizationResult authorizeConnection(AgentConnectionAuthorizationRequest request) {
        AgentConnectionAuthorizationRequest body = request == null ? new AgentConnectionAuthorizationRequest() : request;
        String agentId = body.effectiveAgentId();
        if (blank(agentId)) {
            AgentConnectionAuthorizationResult result = AgentConnectionAuthorizationResult.deny((String) null, AgentAuthorizationDenyReason.AGENT_ID_REQUIRED, "Agent id is required");
            saveDeniedSecurityEvent(body, result);
            return result;
        }
        AgentProfile profile = repository.findProfile(agentId).orElse(null);
        if (profile == null) {
            ensureEnrollmentFromConnection(body, agentId);
            AgentConnectionAuthorizationResult result = AgentConnectionAuthorizationResult.deny(agentId, AgentAuthorizationDenyReason.AGENT_NOT_APPROVED, "Agent is pending Core enrollment review");
            saveDeniedSecurityEvent(body, result);
            return result;
        }
        AgentConnectionAuthorizationResult denial = governanceDenial(profile);
        if (denial != null) {
            saveDeniedSecurityEvent(body, denial);
            return denial;
        }
        OffsetDateTime now = now();
        String tokenHash = credentialHash(body.getCredentialToken(), body.getCredentialHash());
        AgentCredential credential = null;
        if (!blank(tokenHash)) {
            credential = repository.findActiveCredentialByTokenHash(agentId, tokenHash, now).orElse(null);
        }
        if (credential == null && !blank(body.getPublicKeyFingerprint())) {
            credential = repository.findActiveCredentialByFingerprint(agentId, body.getPublicKeyFingerprint(), now).orElse(null);
        }
        if (credential == null) {
            AgentConnectionAuthorizationResult result = AgentConnectionAuthorizationResult.deny(agentId, AgentAuthorizationDenyReason.CREDENTIAL_INVALID, "Agent credential is missing, invalid, expired, or revoked");
            saveDeniedSecurityEvent(body, result);
            return result;
        }
        List<String> capabilities = repository.findEnabledCapabilities(agentId).stream()
                .map(AgentCapability::getCapabilityCode)
                .filter(value -> !blank(value))
                .distinct()
                .toList();
        List<AgentAuthorizationScope> scopes = repository.findEnabledScopes(agentId);
        List<String> allowedTaskTypes = scopes.stream()
                .map(AgentAuthorizationScope::getTaskType)
                .filter(value -> !blank(value))
                .distinct()
                .toList();
        List<String> allowedSystemCodes = scopes.stream()
                .map(AgentAuthorizationScope::getSystemCode)
                .filter(value -> !blank(value))
                .distinct()
                .toList();
        AgentConnectionAuthorizationResult result = AgentConnectionAuthorizationResult.allow(profile, capabilities, allowedTaskTypes, allowedSystemCodes, credential.getCredentialVersion());
        saveSecurityEvent(body, result, AgentSecurityEventType.CONNECTION_AUTHORIZED);
        return result;
    }


    @Transactional
    public AgentProfile issueCredential(String agentId, AgentCredentialIssueCommand command) {
        AgentProfile profile = requireProfile(agentId);
        AgentCredentialIssueCommand request = command == null ? new AgentCredentialIssueCommand() : command;
        assertCredentialIssueAllowed(profile);
        OffsetDateTime now = now();
        String tokenHash = credentialHash(request.getCredentialToken(), request.getCredentialHash());
        if (blank(tokenHash) && blank(request.getPublicKeyFingerprint())) {
            throw new IllegalArgumentException("credentialToken, credentialHash, or publicKeyFingerprint is required to issue an agent credential");
        }
        if (request.isRevokeExisting()) {
            repository.revokeCredentials(agentId, firstNonBlank(request.getReason(), "credential rotated by admin"), now);
        }
        profile.setPolicyVersion(profile.getPolicyVersion() + 1);
        profile.setUpdatedAt(now);
        AgentProfile saved = repository.saveProfile(profile);

        saveCredential(agentId, request.getCredentialType(), tokenHash, request.getPublicKeyFingerprint(), Math.max(1, saved.getPolicyVersion()), request.getCredentialExpiresAt(), now);

        appendAudit(
                agentId,
                null,
                request.isRevokeExisting() ? "AGENT_CREDENTIAL_ROTATED" : "AGENT_CREDENTIAL_ISSUED",
                saved.getApprovalStatus() == null ? null : saved.getApprovalStatus().name(),
                saved.getApprovalStatus() == null ? null : saved.getApprovalStatus().name(),
                firstNonBlank(request.getOperatorId(), "system"),
                firstNonBlank(request.getReason(), "Credential material issued from Admin UI")
        );
        return enrichProfile(saved);
    }

    @Transactional
    public AgentProfile approveAgent(String agentId, AgentProfileApprovalCommand command) {
        AgentProfile profile = requireProfile(agentId);
        AgentProfileApprovalCommand request = command == null ? new AgentProfileApprovalCommand() : command;
        OffsetDateTime now = now();
        String oldStatus = profile.getApprovalStatus() == null ? null : profile.getApprovalStatus().name();
        String tokenHash = credentialHash(request.getCredentialToken(), request.getCredentialHash());
        boolean hasNewCredentialMaterial = !blank(tokenHash) || !blank(request.getPublicKeyFingerprint());
        boolean hasActiveCredential = repository.findLatestCredential(agentId)
                .map(credential -> credential.activeAt(now))
                .orElse(false);

        if (isSecurityBlocked(profile) && !hasNewCredentialMaterial) {
            throw new IllegalArgumentException("credentialToken, credentialHash, or publicKeyFingerprint is required to restore a blocked agent profile");
        }
        if (!hasNewCredentialMaterial && !hasActiveCredential) {
            throw new IllegalArgumentException("credentialToken, credentialHash, or publicKeyFingerprint is required to approve an agent without an active credential");
        }

        profile.setApprovalStatus(AgentApprovalStatus.APPROVED);
        profile.setRiskStatus(request.getRiskStatus() == null ? AgentRiskStatus.NORMAL : request.getRiskStatus());
        profile.setEnabled(request.getEnabled() == null || request.getEnabled());
        profile.setPolicyVersion(profile.getPolicyVersion() + 1);
        profile.setUpdatedAt(now);
        AgentProfile saved = repository.saveProfile(profile);

        if (hasNewCredentialMaterial) {
            if (request.isRevokeExisting()) {
                repository.revokeCredentials(agentId, firstNonBlank(request.getReason(), "agent approved/restored with new credential"), now);
            }
            saveCredential(agentId, request.getCredentialType(), tokenHash, request.getPublicKeyFingerprint(), Math.max(1, saved.getPolicyVersion()), request.getCredentialExpiresAt(), now);
        }

        appendAudit(
                agentId,
                null,
                "AGENT_APPROVED",
                oldStatus,
                saved.getApprovalStatus().name(),
                firstNonBlank(request.getOperatorId(), "system"),
                firstNonBlank(request.getReason(), "Agent restored to APPROVED from Admin UI")
        );
        return enrichProfile(saved);
    }

    @Transactional
    public AgentProfile disableAgent(String agentId, String operatorId, String reason) {
        AgentProfile profile = requireProfile(agentId);
        String oldStatus = profile.getApprovalStatus().name();
        profile.setEnabled(false);
        profile.setPolicyVersion(profile.getPolicyVersion() + 1);
        profile.setUpdatedAt(now());
        AgentProfile saved = repository.saveProfile(profile);
        appendAudit(agentId, null, "AGENT_DISABLED", oldStatus, saved.getApprovalStatus().name(), firstNonBlank(operatorId, "system"), reason);
        return saved;
    }

    @Transactional
    public AgentProfile enableAgent(String agentId, String operatorId, String reason) {
        AgentProfile profile = requireProfile(agentId);
        if (profile.getApprovalStatus() != AgentApprovalStatus.APPROVED || isSecurityBlocked(profile)) {
            throw new IllegalStateException("Blocked or non-approved Agent profiles cannot be enabled directly. Use /admin/agents/{agentId}/approve with credential rotation.");
        }
        profile.setEnabled(true);
        profile.setPolicyVersion(profile.getPolicyVersion() + 1);
        profile.setUpdatedAt(now());
        AgentProfile saved = repository.saveProfile(profile);
        appendAudit(agentId, null, "AGENT_ENABLED", null, saved.getApprovalStatus().name(), firstNonBlank(operatorId, "system"), reason);
        return saved;
    }

    @Transactional
    public AgentProfile suspendAgent(String agentId, String operatorId, String reason) {
        AgentProfile profile = requireProfile(agentId);
        String oldStatus = profile.getApprovalStatus().name();
        profile.setApprovalStatus(AgentApprovalStatus.SUSPENDED);
        profile.setRiskStatus(AgentRiskStatus.SUSPENDED);
        profile.setEnabled(false);
        profile.setPolicyVersion(profile.getPolicyVersion() + 1);
        profile.setUpdatedAt(now());
        AgentProfile saved = repository.saveProfile(profile);
        appendAudit(agentId, null, "AGENT_SUSPENDED", oldStatus, saved.getApprovalStatus().name(), firstNonBlank(operatorId, "system"), reason);
        return saved;
    }

    @Transactional
    public AgentProfile revokeAgent(String agentId, String operatorId, String reason) {
        AgentProfile profile = requireProfile(agentId);
        String oldStatus = profile.getApprovalStatus().name();
        OffsetDateTime now = now();
        profile.setApprovalStatus(AgentApprovalStatus.REVOKED);
        profile.setRiskStatus(AgentRiskStatus.REVOKED);
        profile.setEnabled(false);
        profile.setPolicyVersion(profile.getPolicyVersion() + 1);
        profile.setUpdatedAt(now);
        repository.revokeCredentials(agentId, reason, now);
        AgentProfile saved = repository.saveProfile(profile);
        appendAudit(agentId, null, "AGENT_REVOKED", oldStatus, saved.getApprovalStatus().name(), firstNonBlank(operatorId, "system"), reason);
        return saved;
    }

    public List<AgentEnrollmentRequest> searchEnrollments(AgentEnrollmentStatus status, int limit) {
        return repository.searchEnrollments(status, safeLimit(limit));
    }

    @Transactional
    public AgentProfile enforceDuplicateRuntimeSecurity(String agentId, AgentDuplicateRuntimeSecurityCommand command) {
        AgentProfile profile = requireProfile(agentId);
        AgentDuplicateRuntimeSecurityCommand request = command == null ? new AgentDuplicateRuntimeSecurityCommand() : command;
        OffsetDateTime now = now();
        String oldState = profile.getApprovalStatus().name() + "/" + profile.getRiskStatus().name() + "/enabled=" + profile.isEnabled();
        String reason = firstNonBlank(request.getReason(), "Duplicate runtime sessions detected. Agent quarantined and credential rotation required.");

        profile.setRiskStatus(AgentRiskStatus.QUARANTINED);
        profile.setEnabled(false);
        profile.setPolicyVersion(profile.getPolicyVersion() + 1);
        profile.setUpdatedAt(now);
        AgentProfile saved = repository.saveProfile(profile);

        boolean credentialsRevoked = false;
        if (request.isRevokeCredentials()) {
            credentialsRevoked = repository.revokeCredentials(agentId, firstNonBlank(reason, "duplicate runtime security enforcement"), now) > 0;
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("gatewayNodeIds", request.getGatewayNodeIds() == null ? List.of() : request.getGatewayNodeIds());
        metadata.put("connectedCount", request.getConnectedCount());
        metadata.put("disconnectAll", request.isDisconnectAll());
        metadata.put("requireCredentialRotation", request.isRequireCredentialRotation());
        metadata.put("revokeCredentials", request.isRevokeCredentials());
        metadata.put("credentialsRevoked", credentialsRevoked);
        saveSecurityEvent(agentId, AgentSecurityEventType.DUPLICATE_RUNTIME_QUARANTINED, reason, metadata);
        if (request.isRequireCredentialRotation()) {
            saveSecurityEvent(agentId, AgentSecurityEventType.CREDENTIAL_ROTATION_REQUIRED,
                    "Credential rotation required before clearing duplicate runtime quarantine.", metadata);
        }
        appendAudit(agentId, null, "DUPLICATE_RUNTIME_SECURITY_ENFORCED", oldState,
                saved.getApprovalStatus().name() + "/" + saved.getRiskStatus().name() + "/enabled=" + saved.isEnabled(),
                firstNonBlank(request.getOperatorId(), "system"), reason);
        return enrichProfile(saved);
    }

    @Transactional
    public AgentProfile resolveDuplicateRuntimeSecurity(String agentId, AgentDuplicateRuntimeResolveCommand command) {
        AgentProfile profile = requireProfile(agentId);
        AgentDuplicateRuntimeResolveCommand request = command == null ? new AgentDuplicateRuntimeResolveCommand() : command;
        OffsetDateTime now = now();
        String oldState = profile.getApprovalStatus().name() + "/" + profile.getRiskStatus().name() + "/enabled=" + profile.isEnabled();
        String reason = firstNonBlank(request.getReason(), "Duplicate runtime security case resolved after credential rotation.");

        AgentCredential latestCredential = repository.findLatestCredential(agentId).orElse(null);
        boolean hasActiveCredential = latestCredential != null && latestCredential.activeAt(now);
        boolean credentialIssuedAfterQuarantine = latestCredential != null
                && latestCredential.getIssuedAt() != null
                && profile.getUpdatedAt() != null
                && latestCredential.getIssuedAt().isAfter(profile.getUpdatedAt());
        if (Boolean.TRUE.equals(request.getEnableAfterRotation()) && (!hasActiveCredential || !credentialIssuedAfterQuarantine)) {
            throw new IllegalStateException("Cannot resolve duplicate runtime security enforcement without an active credential issued after the quarantine timestamp.");
        }
        profile.setRiskStatus(AgentRiskStatus.NORMAL);
        if (Boolean.TRUE.equals(request.getEnableAfterRotation()) && profile.getApprovalStatus() == AgentApprovalStatus.APPROVED) {
            profile.setEnabled(true);
        }
        profile.setPolicyVersion(profile.getPolicyVersion() + 1);
        profile.setUpdatedAt(now);
        AgentProfile saved = repository.saveProfile(profile);

        AgentProfile enriched = enrichProfile(saved);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("enableAfterRotation", Boolean.TRUE.equals(request.getEnableAfterRotation()));
        metadata.put("credentialStatus", enriched.getCredential() == null ? "MISSING" : enriched.getCredential().getCredentialStatus().name());
        saveSecurityEvent(agentId, AgentSecurityEventType.CREDENTIAL_ROTATION_COMPLETED, reason, metadata);
        saveSecurityEvent(agentId, AgentSecurityEventType.DUPLICATE_RUNTIME_REMEDIATED, "Duplicate runtime quarantine cleared.", metadata);
        appendAudit(agentId, null, "DUPLICATE_RUNTIME_SECURITY_RESOLVED", oldState,
                saved.getApprovalStatus().name() + "/" + saved.getRiskStatus().name() + "/enabled=" + saved.isEnabled(),
                firstNonBlank(request.getOperatorId(), "system"), reason);
        return enriched;
    }

    public List<AgentProfile> searchProfiles(AgentApprovalStatus status, int limit) {
        return repository.searchProfiles(status, safeLimit(limit)).stream()
                .map(this::enrichProfile)
                .toList();
    }

    public AgentProfile getProfile(String agentId) {
        return enrichProfile(requireProfile(agentId));
    }

    @Transactional
    public AgentProfile updateProfile(String agentId, AgentProfileUpdateCommand command) {
        AgentProfile profile = requireProfile(agentId);
        AgentProfileUpdateCommand request = command == null ? new AgentProfileUpdateCommand() : command;
        OffsetDateTime now = now();
        String oldStatus = profile.getApprovalStatus() == null ? null : profile.getApprovalStatus().name();
        if (!blank(request.getTenantId())) profile.setTenantId(request.getTenantId());
        if (!blank(request.getAgentName())) profile.setAgentName(request.getAgentName());
        if (!blank(request.getAgentType())) profile.setAgentType(request.getAgentType());
        if (request.getOwnerTeam() != null) profile.setOwnerTeam(request.getOwnerTeam());
        if (request.getDescription() != null) profile.setDescription(request.getDescription());
        AgentApprovalStatus targetApprovalStatus = request.getApprovalStatus() == null ? profile.getApprovalStatus() : request.getApprovalStatus();
        AgentRiskStatus targetRiskStatus = request.getRiskStatus() == null ? profile.getRiskStatus() : request.getRiskStatus();
        if (Boolean.TRUE.equals(request.getEnabled())
                && (targetApprovalStatus != AgentApprovalStatus.APPROVED || isSecurityBlocked(targetApprovalStatus, targetRiskStatus))) {
            throw new IllegalStateException("Blocked or non-approved Agent profiles cannot be enabled through profile update. Use /admin/agents/{agentId}/approve with credential rotation.");
        }
        if (request.getApprovalStatus() != null) profile.setApprovalStatus(request.getApprovalStatus());
        if (request.getEnabled() != null) profile.setEnabled(request.getEnabled());
        if (request.getRiskStatus() != null) profile.setRiskStatus(request.getRiskStatus());
        if (profile.getApprovalStatus() != AgentApprovalStatus.APPROVED || isSecurityBlocked(profile)) {
            profile.setEnabled(false);
            if (profile.getApprovalStatus() == AgentApprovalStatus.REVOKED || profile.getRiskStatus() == AgentRiskStatus.REVOKED) {
                repository.revokeCredentials(agentId, firstNonBlank(request.getReason(), "profile updated to revoked by admin"), now);
                profile.setApprovalStatus(AgentApprovalStatus.REVOKED);
                profile.setRiskStatus(AgentRiskStatus.REVOKED);
            } else if (profile.getApprovalStatus() == AgentApprovalStatus.SUSPENDED || profile.getRiskStatus() == AgentRiskStatus.SUSPENDED) {
                profile.setApprovalStatus(AgentApprovalStatus.SUSPENDED);
                profile.setRiskStatus(AgentRiskStatus.SUSPENDED);
            }
        }
        profile.setPolicyVersion(profile.getPolicyVersion() + 1);
        profile.setUpdatedAt(now);
        AgentProfile saved = repository.saveProfile(profile);

        if (request.getCapabilities() != null) {
            repository.replaceCapabilities(agentId, buildCapabilities(agentId, request.getCapabilities(), firstNonBlank(request.getOperatorId(), "system"), now));
        }
        if (request.getScopes() != null) {
            repository.replaceScopes(agentId, buildScopes(agentId, saved.getTenantId(), request.getScopes(), now));
        }
        AgentProfile enriched = enrichProfile(saved);
        appendAudit(agentId, null, "AGENT_PROFILE_UPDATED", oldStatus, enriched.getApprovalStatus().name(), firstNonBlank(request.getOperatorId(), "system"), request.getReason());
        return enriched;
    }

    public List<AgentSecurityEvent> searchSecurityEvents(String agentId, int limit) {
        return repository.searchSecurityEvents(agentId, safeLimit(limit));
    }


    public List<AgentSecurityEnforcementPolicy> searchSecurityEnforcementPolicies(int limit) {
        return repository.searchSecurityEnforcementPolicies(safeLimit(limit));
    }

    public AgentSecurityEnforcementPolicy getSecurityEnforcementPolicy(String agentId) {
        String key = blank(agentId) ? "*" : agentId;
        return repository.findSecurityEnforcementPolicy(key)
                .or(() -> repository.findSecurityEnforcementPolicy("*"))
                .orElseGet(() -> defaultSecurityEnforcementPolicy(key));
    }

    @Transactional
    public AgentSecurityEnforcementPolicy updateSecurityEnforcementPolicy(String agentId, AgentSecurityEnforcementPolicyUpdateCommand command) {
        String key = blank(agentId) ? "*" : agentId;
        AgentSecurityEnforcementPolicyUpdateCommand request = command == null ? new AgentSecurityEnforcementPolicyUpdateCommand() : command;
        OffsetDateTime now = now();
        AgentSecurityEnforcementPolicy policy = repository.findSecurityEnforcementPolicy(key).orElseGet(() -> defaultSecurityEnforcementPolicy(key));
        if (request.getEnabled() != null) policy.setEnabled(request.getEnabled());
        if (request.getDuplicateRuntimeMode() != null) policy.setDuplicateRuntimeMode(request.getDuplicateRuntimeMode());
        if (request.getRequireCredentialRotation() != null) policy.setRequireCredentialRotation(request.getRequireCredentialRotation());
        if (request.getNotifyEmail() != null) policy.setNotifyEmail(request.getNotifyEmail());
        if (request.getNotifySlack() != null) policy.setNotifySlack(request.getNotifySlack());
        if (request.getNotifySiem() != null) policy.setNotifySiem(request.getNotifySiem());
        if (request.getEmailRecipients() != null) policy.setEmailRecipients(request.getEmailRecipients());
        if (request.getSlackChannels() != null) policy.setSlackChannels(request.getSlackChannels());
        if (request.getSiemTopics() != null) policy.setSiemTopics(request.getSiemTopics());
        if (request.getMetadata() != null) policy.setMetadata(request.getMetadata());
        policy.setUpdatedBy(firstNonBlank(request.getOperatorId(), "system"));
        policy.setCreatedAt(policy.getCreatedAt() == null ? now : policy.getCreatedAt());
        policy.setUpdatedAt(now);
        AgentSecurityEnforcementPolicy saved = repository.saveSecurityEnforcementPolicy(policy);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("policyId", saved.getPolicyId());
        metadata.put("duplicateRuntimeMode", saved.getDuplicateRuntimeMode().name());
        metadata.put("notifyEmail", saved.isNotifyEmail());
        metadata.put("notifySlack", saved.isNotifySlack());
        metadata.put("notifySiem", saved.isNotifySiem());
        saveSecurityEvent(key, AgentSecurityEventType.DUPLICATE_RUNTIME_POLICY_EVALUATED, "Security enforcement policy updated.", metadata);
        appendAudit("*".equals(key) ? null : key, null, "SECURITY_ENFORCEMENT_POLICY_UPDATED", null, saved.getDuplicateRuntimeMode().name(), saved.getUpdatedBy(), "Per-agent security enforcement policy updated");
        return saved;
    }

    public void queueSecurityNotifications(String agentId, AgentSecurityEvent sourceEvent, AgentSecurityEnforcementPolicy policy) {
        if (policy == null || !policy.isEnabled()) return;
        Map<String, Object> base = new LinkedHashMap<>();
        base.put("sourceSecurityEventId", sourceEvent == null ? null : sourceEvent.getSecurityEventId());
        base.put("policyId", policy.getPolicyId());
        base.put("duplicateRuntimeMode", policy.getDuplicateRuntimeMode().name());
        base.put("emailRecipients", policy.getEmailRecipients());
        base.put("slackChannels", policy.getSlackChannels());
        base.put("siemTopics", policy.getSiemTopics());
        if (policy.isNotifyEmail()) {
            saveSecurityEvent(agentId, AgentSecurityEventType.SECURITY_NOTIFICATION_QUEUED, "Email notification queued for duplicate runtime security event.", withChannel(base, "EMAIL"));
        }
        if (policy.isNotifySlack()) {
            saveSecurityEvent(agentId, AgentSecurityEventType.SECURITY_NOTIFICATION_QUEUED, "Slack notification queued for duplicate runtime security event.", withChannel(base, "SLACK"));
        }
        if (policy.isNotifySiem()) {
            saveSecurityEvent(agentId, AgentSecurityEventType.SECURITY_NOTIFICATION_QUEUED, "SIEM notification queued for duplicate runtime security event.", withChannel(base, "SIEM"));
        }
    }

    private AgentSecurityEnforcementPolicy defaultSecurityEnforcementPolicy(String agentId) {
        AgentSecurityEnforcementPolicy policy = new AgentSecurityEnforcementPolicy();
        policy.setAgentId(firstNonBlank(agentId, "*"));
        policy.setEnabled(true);
        policy.setDuplicateRuntimeMode(AgentSecurityEnforcementMode.ALERT_ONLY);
        policy.setRequireCredentialRotation(true);
        policy.setCreatedAt(now());
        policy.setUpdatedAt(policy.getCreatedAt());
        policy.setUpdatedBy("system-default");
        return policy;
    }

    private Map<String, Object> withChannel(Map<String, Object> source, String channel) {
        Map<String, Object> copy = new LinkedHashMap<>(source == null ? Map.of() : source);
        copy.put("channel", channel);
        copy.put("deliveryMode", "OUTBOX_PLACEHOLDER");
        return copy;
    }

    public AgentLatestAuthFailureResponse latestAuthFailure(String agentId) {
        String requestedAgentId = firstNonBlank(agentId);
        if (blank(requestedAgentId)) {
            throw new IllegalArgumentException("agentId is required");
        }
        AgentSecurityEvent latestFailure = repository.searchSecurityEvents(requestedAgentId, 200).stream()
                .filter(this::isRuntimeAuthFailureEvent)
                .findFirst()
                .orElse(null);

        AgentLatestAuthFailureResponse response = new AgentLatestAuthFailureResponse();
        response.setAgentId(requestedAgentId);
        response.setGeneratedAt(now());
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("sourceOfTruth", "CORE_AGENT_SECURITY_EVENTS");
        metadata.put("eventWindowLimit", 200);
        response.setMetadata(metadata);

        if (latestFailure == null) {
            response.setHasFailure(false);
            response.setSummary("No runtime authorization failure has been recorded for this Agent.");
            response.setTroubleshooting(List.of(
                    AgentSetupTroubleshootingStep.info(
                            "NO_AUTH_FAILURE_RECORDED",
                            "No auth failure recorded",
                            "Core has not stored a denied runtime authorization event for this Agent yet.",
                            "If the runtime still cannot connect, retry startup and refresh this panel."
                    )
            ));
            return response;
        }

        Object denyReasonValue = latestFailure.getMetadata() == null ? null : latestFailure.getMetadata().get("denyReason");
        String denyReason = firstNonBlank(
                denyReasonValue == null ? null : String.valueOf(denyReasonValue),
                latestFailure.getReason(),
                latestFailure.getEventType() == null ? null : latestFailure.getEventType().name()
        );
        response.setHasFailure(true);
        response.setSecurityEventId(latestFailure.getSecurityEventId());
        response.setEventType(latestFailure.getEventType() == null ? null : latestFailure.getEventType().name());
        response.setDenyReason(denyReason);
        response.setReason(latestFailure.getReason());
        response.setGatewayNodeId(latestFailure.getGatewayNodeId());
        response.setClaimedAgentId(latestFailure.getClaimedAgentId());
        response.setRemoteAddress(latestFailure.getRemoteAddress());
        response.setOccurredAt(latestFailure.getOccurredAt());
        response.setSecurityEventLink("/security-events?agentId=" + requestedAgentId + "&eventId=" + firstNonBlank(latestFailure.getSecurityEventId(), "latest"));
        response.setSummary(authFailureSummary(denyReason, latestFailure));
        response.setTroubleshooting(authFailureTroubleshooting(denyReason));
        response.setRepairActions(buildRepairActions(requestedAgentId, denyReason, repository.findProfile(requestedAgentId).orElse(null), latestFailure));
        Map<String, Object> eventMetadata = new LinkedHashMap<>(metadata);
        if (latestFailure.getMetadata() != null) eventMetadata.putAll(latestFailure.getMetadata());
        eventMetadata.put("latestSecurityEventId", latestFailure.getSecurityEventId());
        response.setMetadata(eventMetadata);
        return response;
    }


    public AgentConnectionRepairActionsResponse connectionRepairActions(String agentId) {
        AgentLatestAuthFailureResponse failure = latestAuthFailure(agentId);
        AgentConnectionRepairActionsResponse response = new AgentConnectionRepairActionsResponse();
        response.setAgentId(failure.getAgentId());
        response.setHasFailure(failure.isHasFailure());
        response.setDenyReason(failure.getDenyReason());
        response.setSecurityEventId(failure.getSecurityEventId());
        response.setSummary(failure.isHasFailure()
                ? "Repair actions are derived from the latest Core runtime authorization failure."
                : "No repair action is required because Core has not recorded a runtime authorization failure for this Agent.");
        response.setActions(failure.getRepairActions());
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("sourceOfTruth", "CORE_AGENT_SECURITY_EVENTS");
        metadata.put("latestAuthFailureEndpoint", "/admin/agents/" + failure.getAgentId() + "/latest-auth-failure");
        response.setMetadata(metadata);
        response.setGeneratedAt(now());
        return response;
    }

    @Transactional
    public AgentConnectionRepairActionResult executeConnectionRepairAction(String agentId, String actionCode, AgentConnectionRepairActionCommand command) {
        String requestedAgentId = firstNonBlank(agentId);
        if (blank(requestedAgentId)) {
            throw new IllegalArgumentException("agentId is required");
        }
        String normalizedActionCode = normalizeActionCode(actionCode);
        AgentConnectionRepairActionCommand request = command == null ? new AgentConnectionRepairActionCommand() : command;
        AgentLatestAuthFailureResponse before = latestAuthFailure(requestedAgentId);
        AgentProfile profile = null;
        String message;

        switch (normalizedActionCode) {
            case "ROTATE_CREDENTIAL" -> {
                assertCredentialMaterial(request, "ROTATE_CREDENTIAL requires credentialToken, credentialHash, or publicKeyFingerprint");
                profile = issueCredential(requestedAgentId, toCredentialIssueCommand(request, "Connection repair action ROTATE_CREDENTIAL"));
                message = "Credential rotated. Update runtime secrets with the new credential and restart the Agent.";
            }
            case "ENABLE_AGENT" -> {
                profile = enableAgent(requestedAgentId, request.getOperatorId(), firstNonBlank(request.getReason(), "Connection repair action ENABLE_AGENT"));
                message = "Agent enabled. Restart or refresh the runtime connection.";
            }
            case "APPROVE_AGENT" -> {
                profile = approveAgent(requestedAgentId, toProfileApprovalCommand(request, "Connection repair action APPROVE_AGENT"));
                message = "Agent approved. Restart or refresh the runtime connection.";
            }
            case "RESTORE_AGENT_WITH_CREDENTIAL" -> {
                assertCredentialMaterial(request, "RESTORE_AGENT_WITH_CREDENTIAL requires credentialToken, credentialHash, or publicKeyFingerprint");
                profile = approveAgent(requestedAgentId, toProfileApprovalCommand(request, "Connection repair action RESTORE_AGENT_WITH_CREDENTIAL"));
                message = "Agent restored with a new credential. Update runtime secrets and restart the Agent.";
            }
            default -> throw new IllegalArgumentException("Unsupported connection repair action: " + actionCode);
        }

        AgentLatestAuthFailureResponse after = latestAuthFailure(requestedAgentId);
        AgentConnectionRepairActionResult result = new AgentConnectionRepairActionResult();
        result.setAgentId(requestedAgentId);
        result.setActionCode(normalizedActionCode);
        result.setStatus("COMPLETED");
        result.setMessage(message);
        result.setProfile(profile);
        result.setLatestAuthFailure(after);
        result.setNextActions(after.getRepairActions());
        result.setTroubleshooting(after.getTroubleshooting());
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("sourceOfTruth", "CORE_AGENT_GOVERNANCE_SERVICE");
        metadata.put("latestAuthFailureBefore", before.getSecurityEventId());
        metadata.put("latestAuthFailureAfter", after.getSecurityEventId());
        metadata.put("credentialMaterialReturned", false);
        result.setMetadata(metadata);
        result.setOccurredAt(now());
        saveSecurityEvent(requestedAgentId, AgentSecurityEventType.SECURITY_NOTIFICATION_QUEUED,
                "Connection repair action completed: " + normalizedActionCode, metadata);
        return result;
    }

    public AgentSecurityEvent saveSecurityEvent(AgentSecurityEvent event) {
        AgentSecurityEvent securityEvent = event == null ? new AgentSecurityEvent() : event;
        if (blank(securityEvent.getSecurityEventId())) securityEvent.setSecurityEventId("asec-" + UUID.randomUUID());
        if (securityEvent.getOccurredAt() == null) securityEvent.setOccurredAt(now());
        securityEvent.setCreatedAt(securityEvent.getCreatedAt() == null ? now() : securityEvent.getCreatedAt());
        return repository.saveSecurityEvent(securityEvent);
    }

    public List<AgentGovernanceTableDiagnostic> tableDiagnostics() {
        return repository.tableDiagnostics();
    }

    public String mode() {
        return repository.mode();
    }

    public String hashCredentialToken(String token) {
        return sha256(token);
    }

    private AgentEnrollmentRequest initializeEnrollment(AgentEnrollmentRequest input, String claimedAgentId, OffsetDateTime now) {
        AgentEnrollmentRequest enrollment = input;
        enrollment.setClaimedAgentId(claimedAgentId);
        if (blank(enrollment.getEnrollmentId())) enrollment.setEnrollmentId("enroll-" + UUID.randomUUID());
        if (blank(enrollment.getTenantId())) enrollment.setTenantId("default");
        if (blank(enrollment.getAgentName())) enrollment.setAgentName(claimedAgentId);
        if (blank(enrollment.getAgentType())) enrollment.setAgentType("UNKNOWN");
        enrollment.setStatus(AgentEnrollmentStatus.PENDING_REVIEW);
        enrollment.setSubmittedAt(enrollment.getSubmittedAt() == null ? now : enrollment.getSubmittedAt());
        enrollment.setCreatedAt(enrollment.getCreatedAt() == null ? now : enrollment.getCreatedAt());
        enrollment.setUpdatedAt(now);
        return enrollment;
    }

    private AgentEnrollmentRequest mergeEnrollmentObservation(AgentEnrollmentRequest existing, AgentEnrollmentRequest input, OffsetDateTime now) {
        if (existing.getStatus() == AgentEnrollmentStatus.APPROVED || existing.getStatus() == AgentEnrollmentStatus.REJECTED) {
            return existing;
        }
        if (!blank(input.getTenantId())) existing.setTenantId(input.getTenantId());
        if (!blank(input.getAgentName())) existing.setAgentName(input.getAgentName());
        if (!blank(input.getAgentType())) existing.setAgentType(input.getAgentType());
        if (input.getSubmittedMetadata() != null && !input.getSubmittedMetadata().isEmpty()) existing.setSubmittedMetadata(input.getSubmittedMetadata());
        if (input.getEvidence() != null && !input.getEvidence().isEmpty()) existing.setEvidence(input.getEvidence());
        if (!blank(input.getFingerprint())) existing.setFingerprint(input.getFingerprint());
        if (!blank(input.getRemoteAddress())) existing.setRemoteAddress(input.getRemoteAddress());
        existing.setUpdatedAt(now);
        return existing;
    }

    private void ensureEnrollmentFromConnection(AgentConnectionAuthorizationRequest request, String agentId) {
        repository.findLatestEnrollmentByAgent(agentId).orElseGet(() -> {
            AgentEnrollmentRequest enrollment = new AgentEnrollmentRequest();
            enrollment.setClaimedAgentId(agentId);
            enrollment.setTenantId(firstNonBlank(metadataString(request, "tenantId"), "default"));
            enrollment.setAgentName(firstNonBlank(metadataString(request, "agentName"), agentId));
            enrollment.setAgentType(firstNonBlank(metadataString(request, "agentType"), "UNKNOWN"));
            enrollment.setSubmittedMetadata(request.getMetadata());
            enrollment.setFingerprint(request.getFingerprint());
            enrollment.setRemoteAddress(request.getRemoteAddress());
            enrollment.setSubmittedAt(now());
            return submitEnrollment(enrollment);
        });
    }

    private String safe(String value) {
        if (value == null) return "";
        String normalized = value.replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.length() <= 256 ? normalized : normalized.substring(0, 256) + "...";
    }

    private boolean isSecurityBlocked(AgentProfile profile) {
        if (profile == null) {
            return false;
        }
        return isSecurityBlocked(profile.getApprovalStatus(), profile.getRiskStatus());
    }

    private boolean isSecurityBlocked(AgentApprovalStatus approvalStatus, AgentRiskStatus riskStatus) {
        if (approvalStatus == AgentApprovalStatus.REVOKED
                || approvalStatus == AgentApprovalStatus.SUSPENDED) {
            return true;
        }
        return riskStatus == AgentRiskStatus.REVOKED
                || riskStatus == AgentRiskStatus.SUSPENDED
                || riskStatus == AgentRiskStatus.QUARANTINED
                || riskStatus == AgentRiskStatus.COMPROMISED;
    }

    private void assertCredentialIssueAllowed(AgentProfile profile) {
        if (profile == null || profile.getApprovalStatus() != AgentApprovalStatus.APPROVED || isSecurityBlocked(profile)) {
            throw new IllegalStateException(
                    "Credential issuance is allowed only for APPROVED Agent profiles with NORMAL risk. "
                            + "Use /admin/agents/{agentId}/approve with credential rotation to restore blocked or non-approved agents.");
        }
        if (profile.getRiskStatus() != null && profile.getRiskStatus() != AgentRiskStatus.NORMAL) {
            throw new IllegalStateException(
                    "Credential issuance is allowed only for APPROVED Agent profiles with NORMAL risk. "
                            + "Use /admin/agents/{agentId}/approve with credential rotation to restore blocked or non-approved agents.");
        }
    }

    private AgentConnectionAuthorizationResult governanceDenial(AgentProfile profile) {
        if (profile.getApprovalStatus() != AgentApprovalStatus.APPROVED) {
            AgentAuthorizationDenyReason reason = switch (profile.getApprovalStatus()) {
                case REJECTED -> AgentAuthorizationDenyReason.AGENT_REJECTED;
                case SUSPENDED -> AgentAuthorizationDenyReason.AGENT_SUSPENDED;
                case REVOKED -> AgentAuthorizationDenyReason.AGENT_REVOKED;
                default -> AgentAuthorizationDenyReason.AGENT_NOT_APPROVED;
            };
            return AgentConnectionAuthorizationResult.deny(profile, reason, "Agent approval status does not allow connection: " + profile.getApprovalStatus());
        }
        if (!profile.isEnabled()) {
            return AgentConnectionAuthorizationResult.deny(profile, AgentAuthorizationDenyReason.AGENT_DISABLED, "Agent is disabled by Core");
        }
        if (profile.getRiskStatus() == AgentRiskStatus.QUARANTINED) {
            return AgentConnectionAuthorizationResult.deny(profile, AgentAuthorizationDenyReason.AGENT_QUARANTINED, "Agent is quarantined by Core");
        }
        if (profile.getRiskStatus() == AgentRiskStatus.SUSPENDED) {
            return AgentConnectionAuthorizationResult.deny(profile, AgentAuthorizationDenyReason.AGENT_SUSPENDED, "Agent is suspended by Core");
        }
        if (profile.getRiskStatus() == AgentRiskStatus.REVOKED || profile.getRiskStatus() == AgentRiskStatus.COMPROMISED) {
            return AgentConnectionAuthorizationResult.deny(profile, AgentAuthorizationDenyReason.AGENT_REVOKED, "Agent risk status does not allow connection: " + profile.getRiskStatus());
        }
        return null;
    }

    private void saveDeniedSecurityEvent(AgentConnectionAuthorizationRequest request, AgentConnectionAuthorizationResult result) {
        saveSecurityEvent(request, result, mapDeniedEventType(result.getReason()));
    }

    private void saveSecurityEvent(AgentConnectionAuthorizationRequest request, AgentConnectionAuthorizationResult result, AgentSecurityEventType type) {
        AgentSecurityEvent event = new AgentSecurityEvent();
        event.setSecurityEventId("asec-" + UUID.randomUUID());
        event.setGatewayNodeId(request.getGatewayNodeId());
        event.setClaimedAgentId(request.getClaimedAgentId());
        event.setAgentId(result.getAgentId());
        event.setEventType(type);
        event.setReason(result.getReason() == null ? result.getMessage() : result.getReason().name());
        event.setFingerprint(request.getFingerprint());
        event.setRemoteAddress(request.getRemoteAddress());
        Map<String, Object> metadata = new LinkedHashMap<>(request.getMetadata());
        metadata.put("authorizationDecision", result.getDecision() == null ? null : result.getDecision().name());
        metadata.put("authorizationAllowed", result.getDecision() == AgentAuthorizationDecision.ALLOW);
        if (result.getReason() != null && result.getReason() != AgentAuthorizationDenyReason.NONE) {
            metadata.put("denyReason", result.getReason().name());
            metadata.put("userFacingReason", result.getMessage());
        }
        event.setMetadata(metadata);
        event.setOccurredAt(now());
        event.setCreatedAt(now());
        repository.saveSecurityEvent(event);
    }

    private AgentSecurityEventType mapDeniedEventType(AgentAuthorizationDenyReason reason) {
        return switch (reason) {
            case AGENT_NOT_FOUND -> AgentSecurityEventType.UNKNOWN_AGENT_ATTEMPT;
            case AGENT_DISABLED -> AgentSecurityEventType.DISABLED_AGENT_ATTEMPT;
            case AGENT_SUSPENDED, AGENT_QUARANTINED -> AgentSecurityEventType.SUSPENDED_AGENT_ATTEMPT;
            case AGENT_REVOKED, CREDENTIAL_REVOKED -> AgentSecurityEventType.REVOKED_CREDENTIAL_ATTEMPT;
            case CREDENTIAL_INVALID, CREDENTIAL_REQUIRED -> AgentSecurityEventType.INVALID_CREDENTIAL;
            default -> AgentSecurityEventType.CONNECTION_DENIED;
        };
    }

    private boolean isRuntimeAuthFailureEvent(AgentSecurityEvent event) {
        if (event == null || event.getEventType() == null) return false;
        return switch (event.getEventType()) {
            case CONNECTION_DENIED, INVALID_CREDENTIAL, REVOKED_CREDENTIAL_ATTEMPT, UNKNOWN_AGENT_ATTEMPT,
                    DISABLED_AGENT_ATTEMPT, SUSPENDED_AGENT_ATTEMPT -> true;
            default -> false;
        };
    }

    private String authFailureSummary(String denyReason, AgentSecurityEvent event) {
        String reason = firstNonBlank(denyReason, "CONNECTION_DENIED");
        return switch (reason) {
            case "CREDENTIAL_INVALID", "INVALID_CREDENTIAL" -> "The latest runtime authorization failed because the credential token, hash, or fingerprint did not match an active Core credential.";
            case "CREDENTIAL_REVOKED", "REVOKED_CREDENTIAL_ATTEMPT" -> "The latest runtime authorization used a revoked credential. Rotate and redeploy the Agent credential.";
            case "AGENT_NOT_APPROVED", "AGENT_NOT_FOUND", "UNKNOWN_AGENT_ATTEMPT" -> "The latest runtime authorization failed because Core does not have an approved Agent profile for the claimed Agent ID.";
            case "AGENT_DISABLED", "DISABLED_AGENT_ATTEMPT" -> "The latest runtime authorization failed because this Agent is disabled in Core.";
            case "AGENT_SUSPENDED", "AGENT_QUARANTINED", "SUSPENDED_AGENT_ATTEMPT" -> "The latest runtime authorization failed because this Agent is suspended or quarantined by Core governance.";
            case "AGENT_REVOKED" -> "The latest runtime authorization failed because this Agent has been revoked.";
            case "AGENT_ID_REQUIRED" -> "The latest runtime authorization did not include an Agent ID.";
            default -> firstNonBlank(event == null ? null : event.getReason(), "The latest runtime authorization was denied by Core governance.");
        };
    }

    private List<AgentSetupTroubleshootingStep> authFailureTroubleshooting(String denyReason) {
        String reason = firstNonBlank(denyReason, "CONNECTION_DENIED");
        return switch (reason) {
            case "CREDENTIAL_INVALID", "INVALID_CREDENTIAL" -> List.of(
                    AgentSetupTroubleshootingStep.error("CREDENTIAL_INVALID", "Credential mismatch", "The runtime token, token hash, or public key fingerprint does not match an active Core credential.", "Copy a fresh credential from Core, redeploy the runtime, then restart the Agent."),
                    AgentSetupTroubleshootingStep.command("VERIFY_AUTHORIZATION", "Verify authorization manually", "Run the generated verify authorization curl command from the Agent setup panel with the expected Agent ID and token.", "curl -fsS -X POST <core-url>/internal/agents/authorize-connection -H 'Content-Type: application/json' -d '{\"agentId\":\"<agent-id>\",\"credentialToken\":\"<token>\"}'")
            );
            case "CREDENTIAL_REVOKED", "REVOKED_CREDENTIAL_ATTEMPT" -> List.of(
                    AgentSetupTroubleshootingStep.error("CREDENTIAL_REVOKED", "Credential revoked", "The runtime used a credential that Core has revoked.", "Rotate the credential, update runtime secrets, and restart the Agent.")
            );
            case "AGENT_NOT_APPROVED", "AGENT_NOT_FOUND", "UNKNOWN_AGENT_ATTEMPT" -> List.of(
                    AgentSetupTroubleshootingStep.error("AGENT_NOT_APPROVED", "Agent is not approved", "Core does not have an approved profile for this Agent ID.", "Approve the Agent enrollment or confirm the runtime uses the exact Agent ID shown in Admin UI."),
                    AgentSetupTroubleshootingStep.warn("AGENT_ID_MISMATCH", "Check Agent ID", "A common cause is running the runtime with a different OPENSOCKET_AGENT_ID than the Admin UI Agent ID.", "Update OPENSOCKET_AGENT_ID and restart the runtime.")
            );
            case "AGENT_DISABLED", "DISABLED_AGENT_ATTEMPT" -> List.of(
                    AgentSetupTroubleshootingStep.error("AGENT_DISABLED", "Agent disabled", "Core profile is disabled, so runtime authorization is denied.", "Enable the Agent profile in Admin UI, then restart the runtime.")
            );
            case "AGENT_SUSPENDED", "AGENT_QUARANTINED", "SUSPENDED_AGENT_ATTEMPT" -> List.of(
                    AgentSetupTroubleshootingStep.error("AGENT_BLOCKED_BY_GOVERNANCE", "Agent blocked by governance", "Core governance has suspended or quarantined this Agent.", "Review Security Guardrails and resolve duplicate runtime or credential rotation requirements before reconnecting.")
            );
            default -> List.of(
                    AgentSetupTroubleshootingStep.warn("AUTH_DENIED", "Runtime authorization denied", "Core denied the runtime authorization request.", "Review the linked security event and verify Agent ID, credential, approval status, and governance policy.")
            );
        };
    }


    private List<AgentConnectionRepairAction> buildRepairActions(String agentId, String denyReason, AgentProfile profile, AgentSecurityEvent latestFailure) {
        if (blank(agentId)) {
            return List.of();
        }
        String normalizedReason = normalizeDenyReason(denyReason);
        List<AgentConnectionRepairAction> actions = new java.util.ArrayList<>();
        String repairEndpointBase = "/admin/agents/" + agentId + "/connection-repair-actions/";
        if (!blank(latestFailure == null ? null : latestFailure.getSecurityEventId())) {
            actions.add(AgentConnectionRepairAction.navigate(
                    "VIEW_SECURITY_EVENT",
                    "Open security event",
                    "Open the Core security event that recorded the latest denied runtime authorization.",
                    "/security-events?agentId=" + agentId + "&eventId=" + latestFailure.getSecurityEventId()
            ));
        }

        switch (normalizedReason) {
            case "CREDENTIAL_INVALID", "CREDENTIAL_REVOKED", "REVOKED_CREDENTIAL_ATTEMPT", "INVALID_CREDENTIAL" -> {
                AgentConnectionRepairAction action = AgentConnectionRepairAction.execute(
                        "ROTATE_CREDENTIAL",
                        "Rotate credential",
                        "Issue a replacement credential for this Agent. Update runtime secrets with the new token before reconnecting.",
                        repairEndpointBase + "ROTATE_CREDENTIAL");
                action.setRequiresCredentialToken(true);
                action.setHighRisk(true);
                action.setNextStep("Paste a new credential token, run the action, update the runtime environment, then restart the Agent.");
                actions.add(action);
            }
            case "AGENT_DISABLED", "DISABLED_AGENT_ATTEMPT" -> {
                AgentConnectionRepairAction action = AgentConnectionRepairAction.execute(
                        "ENABLE_AGENT",
                        "Enable agent",
                        "Enable the approved Core Agent profile so runtime authorization can succeed.",
                        repairEndpointBase + "ENABLE_AGENT");
                action.setEnabled(profile != null && profile.getApprovalStatus() == AgentApprovalStatus.APPROVED && !isSecurityBlocked(profile));
                if (!action.isEnabled()) {
                    action.setDisabledReason("This Agent is not approved or is blocked by governance. Use Restore agent with credential instead.");
                }
                action.setNextStep("After enabling, restart or refresh the runtime connection.");
                actions.add(action);
            }
            case "AGENT_NOT_APPROVED", "AGENT_NOT_FOUND", "UNKNOWN_AGENT_ATTEMPT" -> {
                AgentConnectionRepairAction approve = AgentConnectionRepairAction.execute(
                        "APPROVE_AGENT",
                        "Approve agent",
                        "Approve an existing Core Agent profile so runtime authorization can proceed.",
                        repairEndpointBase + "APPROVE_AGENT");
                approve.setRequiresCredentialToken(profile == null || profile.getCredential() == null || profile.getCredential().getCredentialStatus() != AgentCredentialStatus.ACTIVE);
                approve.setEnabled(profile != null);
                if (!approve.isEnabled()) {
                    approve.setDisabledReason("No Core Agent profile exists yet. Review or create the enrollment before approving.");
                }
                approve.setNextStep("Confirm the runtime OPENSOCKET_AGENT_ID exactly matches this Agent ID.");
                actions.add(approve);
                actions.add(AgentConnectionRepairAction.navigate(
                        "REVIEW_ENROLLMENT",
                        "Review enrollment",
                        "Open enrollment review if the runtime attempted to connect before the Agent profile existed.",
                        "/agents/enrollments?agentId=" + agentId
                ));
            }
            case "AGENT_SUSPENDED", "AGENT_QUARANTINED", "SUSPENDED_AGENT_ATTEMPT" -> {
                AgentConnectionRepairAction action = AgentConnectionRepairAction.execute(
                        "RESTORE_AGENT_WITH_CREDENTIAL",
                        "Restore agent with credential",
                        "Clear suspended or quarantined state and issue a replacement credential in one controlled repair action.",
                        repairEndpointBase + "RESTORE_AGENT_WITH_CREDENTIAL");
                action.setRequiresCredentialToken(true);
                action.setHighRisk(true);
                action.setNextStep("Use this only after confirming the governance block is resolved. Update runtime secrets and restart the Agent.");
                actions.add(action);
            }
            default -> {
                AgentConnectionRepairAction action = AgentConnectionRepairAction.navigate(
                        "FOLLOW_TROUBLESHOOTING",
                        "Follow troubleshooting steps",
                        "No safe automatic repair action is available for this denial reason. Follow the troubleshooting instructions first.",
                        "/agents/" + agentId + "?tab=connection"
                );
                actions.add(action);
            }
        }
        return actions;
    }

    private AgentCredentialIssueCommand toCredentialIssueCommand(AgentConnectionRepairActionCommand request, String defaultReason) {
        AgentCredentialIssueCommand command = new AgentCredentialIssueCommand();
        command.setOperatorId(firstNonBlank(request.getOperatorId(), "system"));
        command.setReason(firstNonBlank(request.getReason(), defaultReason));
        command.setCredentialToken(request.getCredentialToken());
        command.setCredentialHash(request.getCredentialHash());
        command.setPublicKeyFingerprint(request.getPublicKeyFingerprint());
        command.setCredentialExpiresAt(request.getCredentialExpiresAt());
        command.setRevokeExisting(request.getRevokeExisting() == null || request.getRevokeExisting());
        return command;
    }

    private AgentProfileApprovalCommand toProfileApprovalCommand(AgentConnectionRepairActionCommand request, String defaultReason) {
        AgentProfileApprovalCommand command = new AgentProfileApprovalCommand();
        command.setOperatorId(firstNonBlank(request.getOperatorId(), "system"));
        command.setReason(firstNonBlank(request.getReason(), defaultReason));
        command.setEnabled(request.getEnableAfterRepair() == null || request.getEnableAfterRepair());
        command.setRiskStatus(AgentRiskStatus.NORMAL);
        command.setCredentialToken(request.getCredentialToken());
        command.setCredentialHash(request.getCredentialHash());
        command.setPublicKeyFingerprint(request.getPublicKeyFingerprint());
        command.setCredentialExpiresAt(request.getCredentialExpiresAt());
        command.setRevokeExisting(request.getRevokeExisting() == null || request.getRevokeExisting());
        return command;
    }

    private void assertCredentialMaterial(AgentConnectionRepairActionCommand request, String message) {
        if (request == null || (blank(request.getCredentialToken()) && blank(request.getCredentialHash()) && blank(request.getPublicKeyFingerprint()))) {
            throw new IllegalArgumentException(message);
        }
    }

    private String normalizeActionCode(String value) {
        return firstNonBlank(value, "").trim().toUpperCase().replace('-', '_').replace(' ', '_');
    }

    private String normalizeDenyReason(String value) {
        return normalizeActionCode(firstNonBlank(value, "AUTH_DENIED"));
    }

    private List<AgentCapability> buildCapabilities(String agentId, List<String> requestedCapabilities, String approvedBy, OffsetDateTime approvedAt) {
        List<String> capabilityCodes = requestedCapabilities == null ? List.of() : requestedCapabilities.stream()
                .filter(value -> !blank(value))
                .distinct()
                .toList();
        if (capabilityCodes.isEmpty()) {
            capabilityCodes = List.of("GENERAL_AGENT");
        }
        return capabilityCodes.stream()
                .map(capability -> {
                    AgentCapability item = new AgentCapability(agentId, capability);
                    item.setCapabilityVersion("v1");
                    item.setEnabled(true);
                    item.setApprovedBy(firstNonBlank(approvedBy, "system"));
                    item.setApprovedAt(approvedAt);
                    return item;
                })
                .toList();
    }

    private List<AgentAuthorizationScope> buildScopes(String agentId, String tenantId, List<AgentAuthorizationScope> requestedScopes, OffsetDateTime updatedAt) {
        List<AgentAuthorizationScope> scopes = requestedScopes == null ? List.of() : requestedScopes.stream()
                .filter(Objects::nonNull)
                .toList();
        if (scopes.isEmpty()) {
            AgentAuthorizationScope defaultScope = new AgentAuthorizationScope();
            defaultScope.setTenantId(firstNonBlank(tenantId, "default"));
            defaultScope.setSystemCode("*");
            defaultScope.setTaskType("*");
            scopes = List.of(defaultScope);
        }
        return scopes.stream()
                .peek(scope -> {
                    if (blank(scope.getScopeId())) scope.setScopeId("scope-" + UUID.randomUUID());
                    scope.setAgentId(agentId);
                    if (blank(scope.getTenantId())) scope.setTenantId(firstNonBlank(tenantId, "default"));
                    if (blank(scope.getSystemCode())) scope.setSystemCode("*");
                    if (blank(scope.getTaskType())) scope.setTaskType("*");
                    scope.setEnabled(true);
                    scope.setCreatedAt(scope.getCreatedAt() == null ? updatedAt : scope.getCreatedAt());
                    scope.setUpdatedAt(updatedAt);
                })
                .toList();
    }

    private AgentCredential saveCredential(String agentId, AgentCredentialType credentialType, String tokenHash, String publicKeyFingerprint, int credentialVersion, OffsetDateTime expiresAt, OffsetDateTime now) {
        AgentCredential credential = new AgentCredential();
        credential.setCredentialId("cred-" + UUID.randomUUID());
        credential.setAgentId(agentId);
        credential.setCredentialType(credentialType);
        credential.setTokenHash(tokenHash);
        credential.setPublicKeyFingerprint(publicKeyFingerprint);
        credential.setCredentialVersion(credentialVersion);
        credential.setIssuedAt(now);
        credential.setExpiresAt(expiresAt);
        credential.setCreatedAt(now);
        credential.setUpdatedAt(now);
        return repository.saveCredential(credential);
    }

    private AgentProfile enrichProfile(AgentProfile profile) {
        if (profile == null || blank(profile.getAgentId())) return profile;
        profile.setCredential(repository.findLatestCredential(profile.getAgentId()).map(this::credentialSummary).orElse(null));
        profile.setCapabilities(repository.findEnabledCapabilities(profile.getAgentId()));
        profile.setAuthorizationScopes(repository.findEnabledScopes(profile.getAgentId()));
        return profile;
    }

    private AgentCredentialSummary credentialSummary(AgentCredential credential) {
        if (credential == null) return null;
        AgentCredentialSummary summary = new AgentCredentialSummary();
        summary.setCredentialId(credential.getCredentialId());
        summary.setCredentialType(credential.getCredentialType());
        summary.setCredentialVersion(credential.getCredentialVersion());
        summary.setPublicKeyFingerprint(credential.getPublicKeyFingerprint());
        summary.setIssuedAt(credential.getIssuedAt());
        summary.setExpiresAt(credential.getExpiresAt());
        summary.setRevokedAt(credential.getRevokedAt());
        OffsetDateTime now = now();
        if (credential.getRevokedAt() != null) {
            summary.setCredentialStatus(AgentCredentialStatus.REVOKED);
        } else if (credential.getExpiresAt() != null && credential.getExpiresAt().isBefore(now)) {
            summary.setCredentialStatus(AgentCredentialStatus.EXPIRED);
        } else {
            summary.setCredentialStatus(AgentCredentialStatus.ACTIVE);
        }
        return summary;
    }

    private AgentProfile requireProfile(String agentId) {
        if (blank(agentId)) throw new IllegalArgumentException("agentId is required");
        return repository.findProfile(agentId).orElseThrow(() -> new IllegalArgumentException("Agent profile not found: " + agentId));
    }

    private void appendAudit(String agentId, String enrollmentId, String action, String oldStatus, String newStatus, String operatorId, String reason) {
        AgentApprovalAuditEntry audit = new AgentApprovalAuditEntry();
        audit.setAuditId("audit-" + UUID.randomUUID());
        audit.setAgentId(agentId);
        audit.setEnrollmentId(enrollmentId);
        audit.setAction(action);
        audit.setOldStatus(oldStatus);
        audit.setNewStatus(newStatus);
        audit.setOperatorId(firstNonBlank(operatorId, "system"));
        audit.setReason(reason);
        audit.setCreatedAt(now());
        repository.appendAudit(audit);
    }

    private AgentSecurityEvent saveSecurityEvent(String agentId, AgentSecurityEventType eventType, String reason, Map<String, Object> metadata) {
        AgentSecurityEvent event = new AgentSecurityEvent();
        event.setSecurityEventId("asec-" + UUID.randomUUID());
        event.setAgentId(agentId);
        event.setClaimedAgentId(agentId);
        event.setEventType(eventType);
        event.setReason(reason);
        event.setMetadata(metadata);
        event.setOccurredAt(now());
        event.setCreatedAt(now());
        return repository.saveSecurityEvent(event);
    }

    private String credentialHash(String token, String providedHash) {
        if (!blank(providedHash)) return providedHash.trim();
        if (!blank(token)) return sha256(token.trim());
        return null;
    }

    private String sha256(String value) {
        if (blank(value)) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not hash agent credential", ex);
        }
    }

    private int safeLimit(int limit) {
        return Math.max(1, Math.min(500, limit <= 0 ? 100 : limit));
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private String metadataString(AgentConnectionAuthorizationRequest request, String key) {
        if (request == null || request.getMetadata() == null) return null;
        Object value = request.getMetadata().get(key);
        return value instanceof String text && !text.isBlank() ? text.trim() : null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (!blank(value)) return value;
        }
        return null;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
