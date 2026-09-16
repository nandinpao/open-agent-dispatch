package com.opensocket.aievent.core.security;

import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * Canonical CSRF contract for OpenDispatch browser/API administration surfaces.
 *
 * <p>The Admin UI is an API/SPA client and obtains the token from
 * {@code GET /api/session/csrf}. Keep one deterministic cookie/header contract
 * instead of relying on the default XOR request token representation that is
 * intended primarily for server-rendered forms.</p>
 */
public final class OpenDispatchCsrfSupport {
    public static final String COOKIE_NAME = "XSRF-TOKEN";
    public static final String HEADER_NAME = "X-XSRF-TOKEN";

    private OpenDispatchCsrfSupport() {}

    public static CookieCsrfTokenRepository cookieRepository(String path) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieName(COOKIE_NAME);
        repository.setHeaderName(HEADER_NAME);
        repository.setCookiePath(path == null || path.isBlank() ? "/" : path);
        return repository;
    }

    public static CsrfTokenRequestAttributeHandler requestHandler() {
        CsrfTokenRequestAttributeHandler handler = new CsrfTokenRequestAttributeHandler();
        handler.setCsrfRequestAttributeName("_csrf");
        return handler;
    }
}
