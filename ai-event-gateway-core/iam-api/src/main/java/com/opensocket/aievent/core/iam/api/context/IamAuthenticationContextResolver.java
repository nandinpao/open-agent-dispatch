package com.opensocket.aievent.core.iam.api.context;
import com.opensocket.aievent.core.iam.security.contract.AuthenticationContext;
import java.util.Optional;
public interface IamAuthenticationContextResolver { Optional<AuthenticationContext> resolve(); }
