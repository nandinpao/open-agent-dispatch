package com.opensocket.aievent.core.iam.api.response;

import com.opensocket.aievent.core.iam.identity.domain.HumanUser;
import java.time.Instant;

public record UserResponse(
        String userId,String username,String email,String displayName,String status,String creationMode,
        Instant createdAt,Instant updatedAt,long version,
        String authenticationMethod,String signInState,String passwordState,String mfaState,String deliveryStatus,
        String accessReadiness,String responsibilitySummary) {
    public static UserResponse from(HumanUser u){
        String signIn = switch (u.status()) {
            case ACTIVE -> "READY";
            case MFA_ENROLLMENT_REQUIRED -> "MFA_REQUIRED";
            case LOCKED -> "LOCKED";
            case SUSPENDED -> "SUSPENDED";
            case DISABLED, DELETED -> "DISABLED";
            default -> "SETUP_REQUIRED";
        };
        return new UserResponse(u.userId().value(),u.username().value(),u.email().map(e->e.value()).orElse(""),u.displayName(),
                u.status().name(),u.creationMode().name(),u.createdAt(),u.updatedAt(),u.version(),
                "LOCAL",signIn,"","","NOT_ISSUED","NO_RESPONSIBILITY","");
    }
}
