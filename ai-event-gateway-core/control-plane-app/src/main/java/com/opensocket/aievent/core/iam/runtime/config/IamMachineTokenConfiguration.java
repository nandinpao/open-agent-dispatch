package com.opensocket.aievent.core.iam.runtime.config;

import com.opensocket.aievent.core.iam.persistence.crypto.AesGcmMachineSigningKeyProtector;
import com.opensocket.aievent.core.iam.rbac.application.port.out.TenantRbacExecutionPort;
import com.opensocket.aievent.core.iam.runtime.machine.*;
import com.opensocket.aievent.core.iam.runtime.eventintake.*;
import com.opensocket.aievent.core.iam.token.application.port.in.ServiceAccountCredentialCommandPort;
import com.opensocket.aievent.core.iam.token.application.port.out.*;
import com.opensocket.aievent.core.iam.token.application.service.MachineJwtApplicationService;
import com.opensocket.aievent.core.security.incident.RuntimeIncidentControlPolicy;
import com.opensocket.aievent.core.security.audit.SecurityAuditFailureReporter;
import java.time.Clock;
import java.util.Arrays;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(prefix="aeg.iam.machine-token",name="enabled",havingValue="true")
public class IamMachineTokenConfiguration {
    @Bean SmartInitializingSingleton iamMachineTokenActivationValidator(IamMachineTokenProperties p,Environment env){return ()->{p.validateEnabled();if(!Boolean.parseBoolean(env.getProperty("aeg.iam.token.enabled","false")))throw new IllegalStateException("aeg.iam.machine-token.enabled requires aeg.iam.token.enabled=true");};}

    /**
     * HF05: machine OAuth must not defer its first signing-key write to an unaffiliated HTTP/JWK
     * request. Bootstrap one publishable key during application activation inside a real Spring
     * transaction so /oauth2/jwks and /oauth2/token share the same persistence semantics.
     */
    @Bean SmartInitializingSingleton iamMachineSigningKeyReadinessInitializer(
            MachineJwtApplicationService jwt,MachineSigningKeyRepository keys,Clock clock,PlatformTransactionManager transactionManager){
        return ()->{
            TransactionTemplate transactions=new TransactionTemplate(transactionManager);
            transactions.setName("iam-machine:signing-key-bootstrap");
            Boolean ready=transactions.execute(status->{
                jwt.ensureSigningKey();
                return !keys.publishable(clock.instant()).isEmpty();
            });
            if(!Boolean.TRUE.equals(ready)) throw new IllegalStateException("MACHINE_SIGNING_KEY_BOOTSTRAP_NOT_READY");
        };
    }

    @Bean MachineSigningKeyProtectorPort machineSigningKeyProtector(IamMachineTokenProperties p){byte[] k=p.requireProtectionKey();try{return new AesGcmMachineSigningKeyProtector(p.getSigningKeyProtectionKeyId(),k);}finally{Arrays.fill(k,(byte)0);}}
    @Bean MachineJwtCodecPort machineJwtCodec(ObjectMapper mapper){return new SpringSecurityMachineJwtCodec(mapper);}
    @Bean MachineJwtApplicationService machineJwtApplicationService(MachineSigningKeyRepository keys,MachineSigningKeyProtectorPort protector,MachineJwtCodecPort codec,Clock clock,IamMachineTokenProperties p){
        return new MachineJwtApplicationService(keys,protector,codec,clock,new MachineJwtApplicationService.Policy(
                p.getIssuer(),p.getAccessTokenTtl(),p.getMinimumAccessTokenTtl(),p.getClockSkew(),p.getSigningKeyRotation(),p.getSigningKeyVerificationGrace(),p.getRsaKeySize()));
    }
    @Bean IamMachineTokenRuntimeOrchestrator iamMachineTokenRuntimeOrchestrator(MachineCredentialDirectoryPort directory,MachineOAuthRateLimitPort rates,
            ServiceAccountCredentialSecretPort secrets,TenantRbacExecutionPort tenants,ServiceAccountCredentialCommandPort credentials,
            ServiceAccountRepository accounts,TokenPermissionAuthorityPort authority,TokenSecurityEpochPort epochs,
            MachineJwtApplicationService jwt,MachineTokenAuditPort audit,Clock clock,IamMachineTokenProperties p,RuntimeIncidentControlPolicy incidentControls,
            SecurityAuditFailureReporter auditFailures){
        return new IamMachineTokenRuntimeOrchestrator(directory,rates,secrets,tenants,credentials,accounts,authority,epochs,jwt,audit,clock,p,incidentControls,auditFailures);
    }
    @Bean EventIntakeMachineResourceAuthorizer eventIntakeMachineResourceAuthorizer(MachineJwtApplicationService jwt,TenantRbacExecutionPort tenants,
            TokenPermissionAuthorityPort authority,TokenSecurityEpochPort epochs,ServiceAccountRepository accounts,TokenRateLimitPort rates,
            EventIntakeSecurityProperties eventIntake,Clock clock,RuntimeIncidentControlPolicy incidentControls){
        return new EventIntakeMachineResourceAuthorizer(jwt,tenants,authority,epochs,accounts,rates,eventIntake,clock,incidentControls);
    }
    @Bean EventIntakeMachineAuthenticationFilter eventIntakeMachineAuthenticationFilter(EventIntakeMachineResourceAuthorizer authorizer,
            EventIntakeSecurityProperties eventIntake,MachineResourceAccessAuditPort audit,Clock clock,TrustedClientIpResolver clientIp,
            SecurityAuditFailureReporter auditFailures){
        return new EventIntakeMachineAuthenticationFilter(authorizer,eventIntake,audit,clock,clientIp,auditFailures);
    }
    @Bean EventIntakeAuthorityEnforcer eventIntakeAuthorityEnforcer(EventIntakeSecurityProperties eventIntake,MachineResourceAccessAuditPort audit,Clock clock,TrustedClientIpResolver clientIp,RuntimeIncidentControlPolicy incidentControls,SecurityAuditFailureReporter auditFailures){
        return new EventIntakeAuthorityEnforcer(eventIntake,audit,clock,clientIp,incidentControls,auditFailures);
    }
    @Bean FilterRegistrationBean<EventIntakeMachineAuthenticationFilter> disableEventIntakeMachineFilterServletRegistration(EventIntakeMachineAuthenticationFilter filter){
        FilterRegistrationBean<EventIntakeMachineAuthenticationFilter> registration=new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

}
