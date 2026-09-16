package com.opensocket.aievent.core.iam.api.controller;

import com.opensocket.aievent.core.iam.api.application.port.IamFederationAuthenticationApiPort;
import com.opensocket.aievent.core.iam.api.application.port.IamSessionCookiePort;
import com.opensocket.aievent.core.iam.api.context.IamApiRequestContextFactory;
import com.opensocket.aievent.core.iam.api.error.IamApiException;
import com.opensocket.aievent.core.iam.api.request.OidcStartRequest;
import com.opensocket.aievent.core.iam.api.response.FederationProviderPublicResponse;
import com.opensocket.aievent.core.iam.api.response.OidcStartResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public enterprise sign-in surface. It never accepts roles or permissions from an IdP. */
@RestController
@RequestMapping("/api/session/federation")
@ConditionalOnBean(IamFederationAuthenticationApiPort.class)
@ConditionalOnProperty(prefix = "aeg.iam.api", name = "enabled", havingValue = "true")
public class IamFederationSessionController {
    private final IamFederationAuthenticationApiPort federation;
    private final IamApiRequestContextFactory contexts;
    private final IamSessionCookiePort cookies;

    public IamFederationSessionController(
            IamFederationAuthenticationApiPort federation,
            IamApiRequestContextFactory contexts,
            IamSessionCookiePort cookies) {
        this.federation = federation;
        this.contexts = contexts;
        this.cookies = cookies;
    }

    @GetMapping("/providers")
    public List<FederationProviderPublicResponse> providers(
            @RequestParam("tenant") String tenant,
            HttpServletRequest request) {
        return federation.publicProviders(tenant, contexts.from(request));
    }

    @PostMapping("/oidc/start")
    public OidcStartResponse start(
            @Valid @RequestBody OidcStartRequest body,
            HttpServletRequest request) {
        return federation.startOidc(body, contexts.from(request));
    }

    @GetMapping("/oidc/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "error_description", required = false) String errorDescription,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (error != null && !error.isBlank()) {
            return loginError("AUTH_FEDERATION_PROVIDER_REJECTED");
        }
        if (code == null || code.isBlank() || state == null || state.isBlank()) {
            return loginError("AUTH_FEDERATION_CALLBACK_INVALID");
        }
        try {
            var result = federation.completeOidc(code, state, contexts.from(request));
            cookies.write(result.session(), request, response);
            String target = result.returnTo();
            String separator = target.contains("?") ? "&" : "?";
            return ResponseEntity.status(302)
                    .header(HttpHeaders.LOCATION, URI.create(target + separator + "federatedSignIn=1").toASCIIString())
                    .build();
        } catch (IamApiException exception) {
            return loginError(exception.errorCode());
        } catch (RuntimeException exception) {
            return loginError("AUTH_FEDERATION_SIGN_IN_FAILED");
        }
    }

    private static ResponseEntity<Void> loginError(String code) {
        String target = "/login?federationError=" + java.net.URLEncoder.encode(code, java.nio.charset.StandardCharsets.UTF_8);
        return ResponseEntity.status(302).header(HttpHeaders.LOCATION, target).build();
    }

}