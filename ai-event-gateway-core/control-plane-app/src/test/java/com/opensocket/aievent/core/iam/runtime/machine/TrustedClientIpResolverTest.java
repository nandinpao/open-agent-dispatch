package com.opensocket.aievent.core.iam.runtime.machine;

import static org.assertj.core.api.Assertions.assertThat;

import com.opensocket.aievent.core.iam.runtime.config.IamMachineTokenProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class TrustedClientIpResolverTest {
    private TrustedClientIpResolver resolver() {
        IamMachineTokenProperties properties = new IamMachineTokenProperties();
        properties.setTrustedProxyCidrs(List.of("10.0.0.0/8", "2001:db8:ffff::/48"));
        return new TrustedClientIpResolver(properties);
    }

    @Test
    void untrustedPeerCannotSpoofForwardingHeaders() {
        MockHttpServletRequest request = request("203.0.113.10");
        request.addHeader("X-Forwarded-For", "192.0.2.99");
        assertThat(resolver().resolve(request)).isEqualTo("203.0.113.10");
    }

    @Test
    void trustedProxyUsesRightMostUntrustedHopInsteadOfCallerSuppliedLeftMostValue() {
        MockHttpServletRequest request = request("10.0.0.20");
        request.addHeader("X-Forwarded-For", "192.0.2.99, 203.0.113.10");
        assertThat(resolver().resolve(request)).isEqualTo("203.0.113.10");
    }

    @Test
    void trustedProxyChainIsWalkedFromRightToLeft() {
        MockHttpServletRequest request = request("10.0.0.20");
        request.addHeader("X-Forwarded-For", "203.0.113.10, 10.2.0.5");
        assertThat(resolver().resolve(request)).isEqualTo("203.0.113.10");
    }

    @Test
    void standardizedForwardedHeaderUsesSameTrustedChainRule() {
        MockHttpServletRequest request = request("10.0.0.20");
        request.addHeader("Forwarded", "for=192.0.2.99;proto=https, for=203.0.113.10;proto=https");
        assertThat(resolver().resolve(request)).isEqualTo("203.0.113.10");
    }

    @Test
    void bracketedIpv6ForwardedAddressIsAccepted() {
        MockHttpServletRequest request = request("2001:db8:ffff::10");
        request.addHeader("Forwarded", "for=\"[2001:db8:1234::20]:443\"");
        assertThat(resolver().resolve(request)).isEqualTo("2001:db8:1234::20");
    }

    private static MockHttpServletRequest request(String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/oauth2/token");
        request.setRemoteAddr(remoteAddress);
        return request;
    }
}
