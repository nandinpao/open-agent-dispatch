package com.opensocket.aievent.core.iam.persistence.config;

import com.opensocket.aievent.core.iam.authentication.application.port.in.*;
import com.opensocket.aievent.core.iam.authentication.application.port.out.*;
import com.opensocket.aievent.core.iam.authentication.application.service.*;
import com.opensocket.aievent.core.iam.identity.application.port.in.IdentityCommandPort;
import com.opensocket.aievent.core.iam.identity.application.port.in.IdentityQueryPort;
import com.opensocket.aievent.core.iam.persistence.crypto.*;
import java.time.Clock;
import java.util.Base64;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.interceptor.*;

@AutoConfiguration(after={IamPersistenceAutoConfiguration.class,IamApplicationServiceConfiguration.class})
@EnableConfigurationProperties(IamAuthenticationProperties.class)
@ConditionalOnProperty(prefix="aeg.iam.authentication",name="enabled",havingValue="true")
public class IamAuthenticationApplicationServiceConfiguration {
    @Bean @ConditionalOnMissingBean PasswordHashingPort iamPasswordHashingPort(){return new SpringSecurityArgon2PasswordHashingAdapter();}
    @Bean @ConditionalOnMissingBean PasswordBreachCheckPort iamPasswordBreachCheckPort(){return new DisabledPasswordBreachCheckAdapter();}
    @Bean @ConditionalOnMissingBean OneTimeSecretPort iamOneTimeSecretPort(IamAuthenticationProperties p){return new HmacOneTimeSecretAdapter(decode(p.getSecretPepperBase64(),32,"secretPepperBase64"));}
    @Bean @ConditionalOnMissingBean MfaSecretProtectorPort iamMfaSecretProtectorPort(IamAuthenticationProperties p){return new AesGcmMfaSecretProtector(p.getMfaKeyId(),decode(p.getMfaMasterKeyBase64(),32,"mfaMasterKeyBase64"));}
    @Bean @ConditionalOnMissingBean TotpVerificationPort iamTotpVerificationPort(IamAuthenticationProperties p){return new Rfc6238TotpVerificationAdapter(p.getTotpWindow());}

    @Bean @ConditionalOnMissingBean PasswordAuthenticationCommandPort passwordAuthenticationCommandPort(
      PasswordCredentialRepository c,PasswordHistoryRepository h,PasswordPolicyRepository p,PasswordHashingPort hash,
      PasswordBreachCheckPort breach,SubjectSecurityStateRepository states,LoginAttemptRepository attempts,
      AuthenticationSubjectPort subjects,BrowserSessionRepository sessions,SecurityEpochPort epochs,
      AuthenticationEventPublisher events,Clock clock,PlatformTransactionManager tx){
        return proxy(new PasswordAuthenticationService(c,h,p,hash,breach,states,attempts,subjects,sessions,epochs,events,clock,com.opensocket.aievent.core.iam.authentication.domain.LoginRateLimitPolicy.secureDefault()),PasswordAuthenticationCommandPort.class,tx);
    }
    @Bean @ConditionalOnMissingBean MfaAuthenticationCommandPort mfaAuthenticationCommandPort(
      MfaMethodRepository methods,RecoveryCodeRepository recovery,MfaSecretProtectorPort protector,
      TotpVerificationPort totp,OneTimeSecretPort secrets,BrowserSessionRepository sessions,
      SecurityEpochPort epochs,AuthenticationEventPublisher events,Clock clock,PlatformTransactionManager tx){
        return proxy(new MfaAuthenticationService(methods,recovery,protector,totp,secrets,sessions,epochs,events,clock),MfaAuthenticationCommandPort.class,tx);
    }
    @Bean @ConditionalOnMissingBean SessionCommandPort sessionCommandPort(
      BrowserSessionRepository sessions, OneTimeSecretPort secrets, SecurityEpochPort epochs,
      AuthenticationEventPublisher events, Clock clock, PlatformTransactionManager tx) {
        return proxy(new SessionApplicationService(sessions, secrets, epochs, events, clock),
                SessionCommandPort.class, tx);
    }
    @Bean @ConditionalOnMissingBean RootAuthenticationCommandPort rootAuthenticationCommandPort(
      RootBootstrapStateRepository bootstrap,RootRecoveryGrantRepository recovery,BrowserSessionRepository sessions,
      IdentityCommandPort identityCommands,IdentityQueryPort identityQueries,SecurityEpochPort epochs,
      OneTimeSecretPort secrets,AuthenticationEventPublisher events,Clock clock,PlatformTransactionManager tx){
        return proxy(new RootAuthenticationService(bootstrap,recovery,sessions,identityCommands,identityQueries,epochs,secrets,events,clock),RootAuthenticationCommandPort.class,tx);
    }
    @Bean @ConditionalOnMissingBean ReauthenticationCommandPort reauthenticationCommandPort(
      BrowserSessionRepository sessions,PasswordCredentialRepository credentials,PasswordHashingPort hashing,
      MfaMethodRepository mfa,MfaSecretProtectorPort protector,TotpVerificationPort totp,
      ReauthenticationGrantRepository grants,OneTimeSecretPort secrets,Clock clock,PlatformTransactionManager tx){
        return proxy(new ReauthenticationService(sessions,credentials,hashing,mfa,protector,totp,grants,secrets,clock),ReauthenticationCommandPort.class,tx);
    }
    private static byte[] decode(String value,int size,String field){if(value==null||value.isBlank())throw new IllegalStateException("aeg.iam.authentication."+field+" is required when authentication is enabled");byte[] decoded=Base64.getDecoder().decode(value);if(decoded.length<size)throw new IllegalStateException(field+" must contain at least "+size+" decoded bytes");return decoded.length==size?decoded:java.util.Arrays.copyOf(decoded,size);}
    private static <T>T proxy(T target,Class<T> api,PlatformTransactionManager manager){DefaultTransactionAttribute a=new DefaultTransactionAttribute();a.setName("iam-auth:"+api.getSimpleName());MatchAlwaysTransactionAttributeSource source=new MatchAlwaysTransactionAttributeSource();source.setTransactionAttribute(a);TransactionInterceptor advice=new TransactionInterceptor(manager,source);ProxyFactory factory=new ProxyFactory();factory.setTarget(target);factory.setInterfaces(api);factory.addAdvice(advice);return api.cast(factory.getProxy(api.getClassLoader()));}
}
