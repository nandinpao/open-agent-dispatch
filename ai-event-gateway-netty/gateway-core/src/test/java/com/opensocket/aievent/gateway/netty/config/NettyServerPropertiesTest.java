package com.opensocket.aievent.gateway.netty.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NettyServerPropertiesTest {

    @Test
    void staticPeerParserShouldSupportIpv4AndDefaults() {
        var peer = NettyServerProperties.StaticPeer.parse("node-2@192.168.1.12:18080:19090");

        assertThat(peer).isNotNull();
        assertThat(peer.nodeId()).isEqualTo("node-2");
        assertThat(peer.host()).isEqualTo("192.168.1.12");
        assertThat(peer.adminPort()).isEqualTo(18080);
        assertThat(peer.tcpPort()).isEqualTo(19090);
        assertThat(peer.websocketPort()).isEqualTo(19091);
        assertThat(peer.clusterUdpPort()).isEqualTo(19100);
    }

    @Test
    void staticPeerParserShouldSupportBracketedIpv6() {
        var peer = NettyServerProperties.StaticPeer.parse("node-v6@[fd00::12]:18080:19090:19091:19100");

        assertThat(peer).isNotNull();
        assertThat(peer.nodeId()).isEqualTo("node-v6");
        assertThat(peer.host()).isEqualTo("fd00::12");
        assertThat(peer.adminPort()).isEqualTo(18080);
        assertThat(peer.tcpPort()).isEqualTo(19090);
        assertThat(peer.websocketPort()).isEqualTo(19091);
        assertThat(peer.clusterUdpPort()).isEqualTo(19100);
    }

    @Test
    void tcpAndWebSocketPropertiesShouldExposeSafeRuntimeDefaults() {
        var tcp = new NettyServerProperties.Tcp(true, "", -1, 0, 0, 0, true, 0, -1, 0);
        var websocket = new NettyServerProperties.Websocket(true, "", -1, 0, 0, 0, true, 0, true, -1, 0);

        assertThat(tcp.safeHost()).isEqualTo("0.0.0.0");
        assertThat(tcp.safePort()).isEqualTo(19090);
        assertThat(tcp.safeBossThreads()).isEqualTo(1);
        assertThat(tcp.safeSoBacklog()).isEqualTo(128);
        assertThat(tcp.safeMaxFrameLengthBytes()).isEqualTo(1024 * 1024);

        assertThat(websocket.safeHost()).isEqualTo("0.0.0.0");
        assertThat(websocket.safePort()).isEqualTo(19091);
        assertThat(websocket.safeBossThreads()).isEqualTo(1);
        assertThat(websocket.safeSoBacklog()).isEqualTo(128);
        assertThat(websocket.safeMaxContentLengthBytes()).isEqualTo(1024 * 1024);
    }
    @Test
    void topLevelPropertiesShouldCreateSafeNestedDefaultsWhenBinderLeavesSectionsNull() {
        var properties = new NettyServerProperties(null, null, null);

        assertThat(properties.tcp()).isNotNull();
        assertThat(properties.tcp().safePort()).isEqualTo(19090);
        assertThat(properties.websocket()).isNotNull();
        assertThat(properties.websocket().safePort()).isEqualTo(19091);
        assertThat(properties.cluster()).isNotNull();
        assertThat(properties.cluster().enabled()).isFalse();
        assertThat(properties.cluster().safeAnnounceHost()).isEqualTo("127.0.0.1");
        assertThat(properties.cluster().safeUdpPort()).isEqualTo(19100);
    }

}
