package com.opensocket.aievent.core.action.executor.issue.scoped;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.OffsetDateTime;
import java.util.Map;

import com.opensocket.aievent.core.integration.identity.IntegrationAuthType;
import com.opensocket.aievent.core.integration.identity.IntegrationConnection;
import com.opensocket.aievent.core.integration.identity.IntegrationConnectionStatus;
import com.opensocket.aievent.core.integration.identity.IntegrationCredentialMetadata;
import com.opensocket.aievent.core.integration.identity.IntegrationCredentialStatus;
import com.opensocket.aievent.core.integration.identity.IntegrationPermissionCapability;
import com.opensocket.aievent.core.integration.identity.IntegrationPrincipal;
import com.opensocket.aievent.core.integration.identity.IntegrationPrincipalStatus;
import com.opensocket.aievent.core.integration.identity.IntegrationPrincipalType;
import com.opensocket.aievent.core.integration.identity.IntegrationProjectMapping;
import com.opensocket.aievent.core.integration.identity.IntegrationProviderType;
import com.opensocket.aievent.core.integration.identity.IntegrationRiskLevel;
import com.opensocket.aievent.core.integration.identity.PermissionProbeResultStatus;
import com.opensocket.aievent.core.integration.identity.ProjectMappingStatus;
import com.opensocket.aievent.core.integration.identity.ResolvedIntegrationSecret;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ProviderPermissionProbeGatewayTest {

    @Test
    void jiraProbeUsesScopedProjectPermissionsAndDetectsAdministrator() throws Exception {
        try (ProbeServer server = new ProbeServer()) {
            server.json("/rest/api/3/myself", 200, "{\"accountId\":\"erp-bot\"}");
            server.json("/rest/api/3/project/ERP", 200, "{\"key\":\"ERP\"}");
            server.json("/rest/api/3/mypermissions", 200,
                    "{\"permissions\":{" +
                    "\"BROWSE_PROJECTS\":{\"havePermission\":true}," +
                    "\"CREATE_ISSUES\":{\"havePermission\":true}," +
                    "\"ADD_COMMENTS\":{\"havePermission\":true}," +
                    "\"EDIT_ISSUES\":{\"havePermission\":true}," +
                    "\"LINK_ISSUES\":{\"havePermission\":false}," +
                    "\"ADMINISTER\":{\"havePermission\":true}}}");

            var observation = gateway().probe(connection(IntegrationProviderType.JIRA, server.baseUrl()),
                    principal("erp-bot"), credential(), mapping("ERP"));

            assertThat(observation.capabilities().get(IntegrationPermissionCapability.PROJECT_VISIBLE))
                    .isEqualTo(PermissionProbeResultStatus.GRANTED);
            assertThat(observation.capabilities().get(IntegrationPermissionCapability.CREATE_ISSUE))
                    .isEqualTo(PermissionProbeResultStatus.GRANTED);
            assertThat(observation.capabilities().get(IntegrationPermissionCapability.CREATE_RELATION))
                    .isEqualTo(PermissionProbeResultStatus.DENIED);
            assertThat(observation.elevatedPermissions()).contains("PROVIDER_ADMINISTRATOR");
        }
    }

    @Test
    void redmineProbeRejectsAProjectOutsideThePrincipalScope() throws Exception {
        try (ProbeServer server = new ProbeServer()) {
            server.json("/users/current.json", 200, "{\"user\":{\"id\":7,\"admin\":false}}");
            server.json("/projects/MES.json", 403, "{\"error\":\"forbidden\"}");

            var observation = gateway().probe(connection(IntegrationProviderType.REDMINE, server.baseUrl()),
                    principal("erp-bot"), credential(), mapping("MES"));

            assertThat(observation.capabilities().get(IntegrationPermissionCapability.AUTHENTICATE))
                    .isEqualTo(PermissionProbeResultStatus.GRANTED);
            assertThat(observation.capabilities().get(IntegrationPermissionCapability.PROJECT_VISIBLE))
                    .isEqualTo(PermissionProbeResultStatus.DENIED);
            assertThat(observation.capabilities().get(IntegrationPermissionCapability.READ_ISSUE))
                    .isEqualTo(PermissionProbeResultStatus.DENIED);
        }
    }

    private ProviderPermissionProbeGateway gateway() {
        return new ProviderPermissionProbeGateway(new com.opensocket.aievent.core.integration.identity.IntegrationSecretResolver() {
            public ResolvedIntegrationSecret resolve(IntegrationCredentialMetadata metadata) { return new ResolvedIntegrationSecret("scoped-token".toCharArray()); }
            public String mode() { return "TEST"; }
        }, JsonMapper.builder().build());
    }

    private IntegrationConnection connection(IntegrationProviderType provider, String baseUrl) {
        return new IntegrationConnection("tenant-a", "connection-a", provider, provider.name(), baseUrl,
                "SELF_HOSTED", null, IntegrationConnectionStatus.ACTIVE, 3000, null, null, null,
                true, 1, null, null);
    }

    private IntegrationPrincipal principal(String id) {
        return new IntegrationPrincipal("tenant-a", "connection-a", id, id, IntegrationPrincipalType.SERVICE_ACCOUNT,
                "ERP", null, "manufacturing", id, IntegrationPrincipalStatus.ACTIVE, IntegrationRiskLevel.LOW,
                Map.of(), java.util.List.of(), OffsetDateTime.now(), 1, null, null);
    }

    private IntegrationCredentialMetadata credential() {
        return new IntegrationCredentialMetadata("tenant-a", "credential-a", "erp-bot", IntegrationAuthType.API_TOKEN,
                "vault://tenant-a/jira/erp", "v1", "1234", OffsetDateTime.now(), null, null, null,
                IntegrationCredentialStatus.ACTIVE, 1, null, null);
    }

    private IntegrationProjectMapping mapping(String project) {
        return new IntegrationProjectMapping("tenant-a", "mapping-" + project, "connection-a", "ERP", null,
                "ERP", null, "REPAIR", project, project, "Task", null,
                "erp-bot", "erp-bot", "erp-bot", "erp-bot", "erp-bot", null,
                null, null, null, ProjectMappingStatus.VALID, 100, false, true, 1, null, null);
    }

    private static final class ProbeServer implements AutoCloseable {
        private final HttpServer server;

        private ProbeServer() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.start();
        }

        private void json(String path, int status, String body) {
            server.createContext(path, exchange -> respond(exchange, status, body));
        }

        private String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        private static void respond(HttpExchange exchange, int status, String body) throws IOException {
            byte[] payload = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, payload.length);
            exchange.getResponseBody().write(payload);
            exchange.close();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
