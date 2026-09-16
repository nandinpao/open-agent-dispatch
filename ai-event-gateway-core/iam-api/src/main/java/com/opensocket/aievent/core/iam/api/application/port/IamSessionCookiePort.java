package com.opensocket.aievent.core.iam.api.application.port;

import com.opensocket.aievent.core.iam.api.response.SessionResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Browser-session cookie boundary owned by the runtime composition layer.
 *
 * <p>The API layer never serializes an unsigned session identifier itself. The
 * implementation must emit an opaque, integrity-protected locator and clear it
 * on logout. A tenant hint carried by the locator is only a repository locator;
 * the loaded server-side session remains the authority.</p>
 */
public interface IamSessionCookiePort {
    void write(SessionResponse session, HttpServletRequest request, HttpServletResponse response);
    void clear(HttpServletRequest request, HttpServletResponse response);
}
