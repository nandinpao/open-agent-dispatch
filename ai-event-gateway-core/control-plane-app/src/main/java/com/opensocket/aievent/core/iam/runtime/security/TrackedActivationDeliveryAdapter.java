package com.opensocket.aievent.core.iam.runtime.security;

import com.opensocket.aievent.core.iam.api.application.port.IamActivationDeliveryPort;
import com.opensocket.aievent.core.iam.api.application.port.IamOneTimeSecretDeliveryPort;
import com.opensocket.aievent.core.iam.persistence.dao.IamApiRuntimeDao;
import com.opensocket.aievent.core.iam.runtime.config.IamRuntimeProperties;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantContextHolder;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantExecutionContext;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * P2.3A activation delivery coordinator.
 *
 * <p>It records only non-secret delivery evidence. The one-time token and generated setup URL are
 * transient values and are never written to the delivery table or application logs.</p>
 */
public final class TrackedActivationDeliveryAdapter implements IamActivationDeliveryPort {
    private static final Logger log = LoggerFactory.getLogger(TrackedActivationDeliveryAdapter.class);
    private final IamApiRuntimeDao dao;
    private final IamOneTimeSecretDeliveryPort developmentFileDelivery;
    private final JavaMailSender mailSender;
    private final IamRuntimeProperties properties;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public TrackedActivationDeliveryAdapter(
            IamApiRuntimeDao dao,
            IamOneTimeSecretDeliveryPort developmentFileDelivery,
            JavaMailSender mailSender,
            IamRuntimeProperties properties,
            Clock clock,
            TransactionTemplate transactions) {
        this.dao = dao;
        this.developmentFileDelivery = developmentFileDelivery;
        this.mailSender = mailSender;
        this.properties = properties;
        this.clock = clock;
        this.transactions = transactions;
    }

    @Override
    public DeliveryReceipt deliver(DeliveryCommand command) {
        String method = normalizeMethod(command.deliveryMethod());
        String deliveryId = UUID.randomUUID().toString();
        Instant issuedAt = clock.instant();
        String recipient = command.recipientReference() == null ? "" : command.recipientReference().trim();
        insertReceipt(command, deliveryId, method, recipient, issuedAt, "EMAIL".equals(method) ? "QUEUED" : "ISSUED");
        String actionUrl = setupActionUrl(command.purpose(), command.oneTimeSecret());

        if ("MANUAL".equals(method)) {
            mark(deliveryId, command.tenantId(), "DELIVERED", issuedAt, null, "");
            return new DeliveryReceipt(deliveryId, method, "DELIVERED", manualReference(command.actorId()),
                    issuedAt, command.expiresAt(), "", actionUrl);
        }
        if ("EMAIL".equals(method)) {
            if (!properties.isActivationEmailEnabled()) {
                return failed(command, deliveryId, method, recipient, issuedAt, "SMTP_NOT_ENABLED");
            }
            if (mailSender == null) {
                return failed(command, deliveryId, method, recipient, issuedAt, "SMTP_NOT_CONFIGURED");
            }
            if (!looksLikeEmail(recipient)) {
                return failed(command, deliveryId, method, recipient, issuedAt, "ACTIVATION_EMAIL_INVALID");
            }
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                return failed(command, deliveryId, method, recipient, issuedAt, "ACTIVATION_DELIVERY_TRANSACTION_REQUIRED");
            }
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendEmailAfterCommit(command, deliveryId, recipient, actionUrl);
                }
            });
            return new DeliveryReceipt(deliveryId, method, "QUEUED", recipient,
                    issuedAt, command.expiresAt(), "", "");
        }

        try {
            developmentFileDelivery.deliver(
                    command.purpose(), recipient, command.oneTimeSecret(), command.expiresAt(), command.correlationId());
            mark(deliveryId, command.tenantId(), "DELIVERED", issuedAt, null, "");
            return new DeliveryReceipt(deliveryId, method, "DELIVERED", recipient,
                    issuedAt, command.expiresAt(), "", "");
        } catch (RuntimeException ex) {
            return failed(command, deliveryId, method, recipient, issuedAt, "DEVELOPMENT_FILE_DELIVERY_FAILED");
        }
    }

    private DeliveryReceipt failed(
            DeliveryCommand command,
            String deliveryId,
            String method,
            String recipient,
            Instant issuedAt,
            String failureCode) {
        mark(deliveryId, command.tenantId(), "FAILED", null, clock.instant(), failureCode);
        return new DeliveryReceipt(deliveryId, method, "FAILED", recipient,
                issuedAt, command.expiresAt(), failureCode, "");
    }

    private void sendEmailAfterCommit(
            DeliveryCommand command,
            String deliveryId,
            String recipient,
            String actionUrl) {
        try {
            IamTenantContextHolder.withContext(
                    new IamTenantExecutionContext(command.tenantId(), command.actorId()),
                    () -> {
                        transactions.executeWithoutResult(status -> {
                            try {
                                SimpleMailMessage message = new SimpleMailMessage();
                                if (properties.getActivationMailFrom() != null && !properties.getActivationMailFrom().isBlank()) {
                                    message.setFrom(properties.getActivationMailFrom().trim());
                                }
                                message.setTo(recipient);
                                message.setSubject(subject(command.purpose()));
                                message.setText(body(command.purpose(), actionUrl, command.expiresAt()));
                                mailSender.send(message);
                                mark(deliveryId, command.tenantId(), "DELIVERED", clock.instant(), null, "");
                            } catch (RuntimeException ex) {
                                mark(deliveryId, command.tenantId(), "FAILED", null, clock.instant(), "SMTP_DELIVERY_FAILED");
                            }
                        });
                        return null;
                    });
        } catch (RuntimeException ex) {
            // The onboarding transaction is already committed. Never turn a post-commit delivery-receipt
            // failure into an apparent onboarding rollback, and never log the one-time secret or URL.
            log.warn("Activation email post-commit processing failed tenant={} delivery={} code=ACTIVATION_POST_COMMIT_FAILED",
                    command.tenantId(), deliveryId);
        }
    }

    private void insertReceipt(
            DeliveryCommand command,
            String deliveryId,
            String method,
            String recipient,
            Instant issuedAt,
            String initialStatus) {
        Map<String,Object> row = new HashMap<>();
        row.put("tenantId", command.tenantId());
        row.put("deliveryId", deliveryId);
        row.put("userId", command.userId());
        row.put("tokenId", command.tokenId());
        row.put("purpose", command.purpose());
        row.put("deliveryMethod", method);
        row.put("deliveryStatus", initialStatus);
        row.put("recipientReference", "MANUAL".equals(method) ? manualReference(command.actorId()) : recipient);
        row.put("issuedAt", issuedAt);
        row.put("expiresAt", command.expiresAt());
        row.put("failureCode", "");
        row.put("correlationId", command.correlationId() == null ? "" : command.correlationId());
        row.put("createdBy", command.actorId());
        dao.insertActivationDelivery(row);
    }

    private void mark(
            String deliveryId,
            String tenantId,
            String status,
            Instant deliveredAt,
            Instant failedAt,
            String failureCode) {
        Map<String,Object> row = new HashMap<>();
        row.put("tenantId", tenantId);
        row.put("deliveryId", deliveryId);
        row.put("deliveryStatus", status);
        row.put("deliveredAt", deliveredAt);
        row.put("failedAt", failedAt);
        row.put("failureCode", failureCode == null ? "" : failureCode);
        dao.updateActivationDelivery(row);
    }

    private String normalizeMethod(String requested) {
        String method = requested == null || requested.isBlank()
                ? properties.getActivationDeliveryDefaultMethod()
                : requested;
        method = method.trim().toUpperCase(Locale.ROOT);
        if (!java.util.Set.of("EMAIL", "MANUAL", "DEVELOPMENT_FILE").contains(method)) {
            throw new IllegalArgumentException("IDENTITY_ACTIVATION_DELIVERY_METHOD_UNSUPPORTED");
        }
        return method;
    }

    private String setupActionUrl(String purpose, String secret) {
        String encoded = URLEncoder.encode(secret, StandardCharsets.UTF_8);
        String path = "USER_INVITATION".equals(purpose)
                ? "/activate-account?token=" + encoded
                : "/reset-password?token=" + encoded;
        return properties.getActivationBaseUrl() + path;
    }

    private static String subject(String purpose) {
        return "USER_INVITATION".equals(purpose)
                ? "Activate your OpenDispatch account"
                : "Set up your OpenDispatch password";
    }

    private static String body(String purpose, String actionUrl, Instant expiresAt) {
        String action = "USER_INVITATION".equals(purpose) ? "activate your account" : "set up or reset your password";
        return "Use this one-time OpenDispatch link to " + action + ":\n\n"
                + actionUrl + "\n\nThis link expires at " + expiresAt
                + ". If you did not expect this message, contact your OpenDispatch administrator.";
    }

    private static boolean looksLikeEmail(String value) {
        int at = value == null ? -1 : value.indexOf('@');
        return at > 0 && value.indexOf('.', at) > at + 1;
    }

    private static String manualReference(String actorId) {
        return "Manual handoff by " + (actorId == null || actorId.isBlank() ? "administrator" : actorId);
    }
}
