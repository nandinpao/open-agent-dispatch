package com.opensocket.aievent.core.iam.runtime.security;

import com.opensocket.aievent.core.iam.api.application.port.IamSessionCookiePort;
import com.opensocket.aievent.core.iam.api.response.SessionResponse;
import com.opensocket.aievent.core.iam.runtime.config.IamRuntimeProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

public final class ServletIamSessionCookieAdapter implements IamSessionCookiePort {
    private final IamRuntimeProperties properties; private final IamSessionCookieCodec codec; private final java.time.Clock clock;
    public ServletIamSessionCookieAdapter(IamRuntimeProperties properties,IamSessionCookieCodec codec,java.time.Clock clock){this.properties=properties;this.codec=codec;this.clock=clock;}
    @Override public void write(SessionResponse session,HttpServletRequest request,HttpServletResponse response){
        String locator=codec.encode(session.tenantId(),session.sessionId(),session.absoluteExpiresAt());
        Duration maxAge=Duration.between(clock.instant(),session.absoluteExpiresAt());
        ResponseCookie cookie=ResponseCookie.from(properties.getSessionCookieName(),locator).httpOnly(true).secure(properties.isSessionCookieSecure()).sameSite(properties.getSessionCookieSameSite()).path(properties.getSessionCookiePath()).maxAge(maxAge.isNegative()?Duration.ZERO:maxAge).build();
        response.addHeader(HttpHeaders.SET_COOKIE,cookie.toString());
    }
    @Override public void clear(HttpServletRequest request,HttpServletResponse response){
        ResponseCookie cookie=ResponseCookie.from(properties.getSessionCookieName(),"").httpOnly(true).secure(properties.isSessionCookieSecure()).sameSite(properties.getSessionCookieSameSite()).path(properties.getSessionCookiePath()).maxAge(Duration.ZERO).build();
        response.addHeader(HttpHeaders.SET_COOKIE,cookie.toString());
    }
    public String read(HttpServletRequest request){Cookie[] cookies=request.getCookies();if(cookies==null)return "";for(Cookie c:cookies)if(properties.getSessionCookieName().equals(c.getName()))return c.getValue();return "";}
}
