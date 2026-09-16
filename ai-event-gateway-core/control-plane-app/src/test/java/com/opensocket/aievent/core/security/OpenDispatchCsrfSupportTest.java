package com.opensocket.aievent.core.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class OpenDispatchCsrfSupportTest {
    @Test
    void apiContractUsesOneRawCookieAndHeaderToken() {
        var repository = OpenDispatchCsrfSupport.cookieRepository("/");
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        var token = repository.generateToken(request);
        repository.saveToken(token, request, response);

        assertThat(token.getHeaderName()).isEqualTo(OpenDispatchCsrfSupport.HEADER_NAME);
        assertThat(token.getParameterName()).isEqualTo("_csrf");
        assertThat(response.getCookie(OpenDispatchCsrfSupport.COOKIE_NAME)).isNotNull();
        assertThat(response.getCookie(OpenDispatchCsrfSupport.COOKIE_NAME).getValue()).isEqualTo(token.getToken());
        assertThat(OpenDispatchCsrfSupport.requestHandler())
                .isInstanceOf(org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler.class);
    }
}
