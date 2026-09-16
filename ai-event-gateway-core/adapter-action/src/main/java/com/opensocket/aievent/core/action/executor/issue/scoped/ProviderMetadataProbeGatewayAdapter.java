package com.opensocket.aievent.core.action.executor.issue.scoped;

import com.opensocket.aievent.core.integration.identity.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
@Primary
public class ProviderMetadataProbeGatewayAdapter implements ProviderMetadataProbeGateway {
    private static final TypeReference<List<Map<String,Object>>> LIST = new TypeReference<>() {};
    private final IntegrationSecretResolver secrets;
    private final ScopedProviderHttpSupport http;
    private final ObjectMapper json;

    public ProviderMetadataProbeGatewayAdapter(IntegrationSecretResolver secrets, ObjectMapper json) {
        this.secrets = secrets;
        this.http = new ScopedProviderHttpSupport(json);
        this.json = json;
    }

    @Override
    public ProviderMetadataSnapshot probe(IntegrationConnection connection,
                                          IntegrationPrincipal principal,
                                          IntegrationCredentialMetadata credential,
                                          IntegrationProjectMapping mapping,
                                          ProviderMetadataProbeRequest request) {
        try (var resolved = secrets.resolve(credential)) {
            return switch (connection.providerType()) {
                case JIRA -> jira(connection, principal, credential, mapping, request, resolved.reveal());
                case REDMINE -> redmine(connection, principal, credential, mapping, request, resolved.reveal());
                case GITLAB_ISSUES -> unsupported(connection, mapping, request);
            };
        } catch (Exception failure) {
            var now = OffsetDateTime.now();
            String detail = failure.getMessage() == null || failure.getMessage().isBlank()
                    ? failure.getClass().getSimpleName()
                    : failure.getMessage();
            return new ProviderMetadataSnapshot(request.tenantId(), "metadata-" + UUID.randomUUID(),
                    connection.connectionId(), mapping.mappingId(), mapping.externalProjectId(), mapping.externalProjectKey(),
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                    Map.of("METADATA", PermissionProbeResultStatus.ERROR), null, null,
                    ProjectMappingGovernanceService.schemaHash(mapping, List.of()), 1, ProviderMetadataCacheStatus.FAILED,
                    now, now.plusMinutes(5), "Provider Metadata Probe failed: " + detail, request.correlationId());
        }
    }

    private ProviderMetadataSnapshot jira(IntegrationConnection c, IntegrationPrincipal p, IntegrationCredentialMetadata credential,
                                          IntegrationProjectMapping m, ProviderMetadataProbeRequest request, String secret) throws Exception {
        var headers = http.authHeaders(p, credential, secret);
        var projectsResponse = http.get(ScopedProviderHttpSupport.join(c.baseUrl(), "/rest/api/3/project/search?maxResults=100"), headers, c.timeoutMs());
        var projectsBody = http.parse(projectsResponse.body());
        var projects = maps(projectsBody.get("values")).stream().map(v -> new ProviderProjectMetadata(s(v,"id"), s(v,"key"), s(v,"name"), true)).toList();
        String projectId = first(m.externalProjectId(), projects.stream().filter(v -> Objects.equals(v.projectKey(), m.externalProjectKey())).map(ProviderProjectMetadata::projectId).findFirst().orElse(null));
        var createMetaResponse = http.get(ScopedProviderHttpSupport.join(c.baseUrl(), "/rest/api/3/issue/createmeta?projectIds=" + enc(projectId) + "&expand=projects.issuetypes.fields"), headers, c.timeoutMs());
        var createMeta = http.parse(createMetaResponse.body());
        var issueTypes = new ArrayList<ProviderIssueTypeMetadata>();
        var fields = new LinkedHashMap<String,ProviderFieldMetadata>();
        for (var project : maps(createMeta.get("projects"))) {
            for (var issue : maps(project.get("issuetypes"))) {
                var required = new ArrayList<String>();
                Object rawFields = issue.get("fields");
                if (rawFields instanceof Map<?,?> fm) {
                    for (var e : fm.entrySet()) {
                        String id = String.valueOf(e.getKey());
                        Map<String,Object> fv = map(e.getValue());
                        boolean req = bool(fv.get("required"));
                        if (req) required.add(id);
                        fields.put(id, new ProviderFieldMetadata(id, id, s(fv,"name"), schemaType(fv), req, allowed(fv.get("allowedValues"))));
                    }
                }
                issueTypes.add(new ProviderIssueTypeMetadata(s(issue,"id"), s(issue,"id"), s(issue,"name"), required));
            }
        }
        var linkTypes = new ArrayList<ProviderLinkTypeMetadata>();
        var linksResponse = http.get(ScopedProviderHttpSupport.join(c.baseUrl(), "/rest/api/3/issueLinkType"), headers, c.timeoutMs());
        for (var v : maps(http.parse(linksResponse.body()).get("issueLinkTypes"))) linkTypes.add(new ProviderLinkTypeMetadata(s(v,"id"), s(v,"name"), s(v,"outward"), s(v,"inward")));
        var permissions = new LinkedHashMap<String,PermissionProbeResultStatus>();
        permissions.put("PROJECT_VISIBLE", ScopedProviderHttpSupport.ok(projectsResponse.statusCode()) ? PermissionProbeResultStatus.GRANTED : PermissionProbeResultStatus.DENIED);
        permissions.put("CREATE_METADATA", ScopedProviderHttpSupport.ok(createMetaResponse.statusCode()) ? PermissionProbeResultStatus.GRANTED : PermissionProbeResultStatus.DENIED);
        var now=OffsetDateTime.now(); var fieldList=List.copyOf(fields.values());
        return new ProviderMetadataSnapshot(request.tenantId(),"metadata-"+UUID.randomUUID(),c.connectionId(),m.mappingId(),projectId,m.externalProjectKey(),projects,issueTypes,fieldList,List.of(),linkTypes,List.of(),List.of(),permissions,
                projectsResponse.headers().firstValue("ETag").orElse(null),projectsResponse.headers().firstValue("Last-Modified").orElse(null),ProjectMappingGovernanceService.schemaHash(m,issueTypes,fieldList,List.of(),linkTypes),1,ProviderMetadataCacheStatus.FRESH,now,now.plusHours(1),"Jira project, create metadata and link types probed with scoped Principal.",request.correlationId());
    }

    private ProviderMetadataSnapshot redmine(IntegrationConnection c, IntegrationPrincipal p, IntegrationCredentialMetadata credential,
                                             IntegrationProjectMapping m, ProviderMetadataProbeRequest request, String secret) throws Exception {
        var headers = http.redmineAuthHeaders(p, credential, secret);

        var currentUserResponse = http.get(ScopedProviderHttpSupport.join(c.baseUrl(), "/users/current.json"), headers, c.timeoutMs());
        var currentUserBody = http.requireJsonObject(currentUserResponse, "Redmine", "current-user authentication", "user");
        var currentUser = map(currentUserBody.get("user"));
        String currentUserLabel = first(s(currentUser, "login"), s(currentUser, "name"), s(currentUser, "id"), "unknown");

        var projectsResponse = http.get(ScopedProviderHttpSupport.join(c.baseUrl(), "/projects.json?limit=100"), headers, c.timeoutMs());
        var projectsBody = http.requireJsonCollection(projectsResponse, "Redmine", "project discovery", "projects");
        var projects = maps(projectsBody.get("projects")).stream()
                .map(v -> new ProviderProjectMetadata(s(v, "id"), s(v, "identifier"), s(v, "name"), true))
                .toList();

        var trackersResponse = http.get(ScopedProviderHttpSupport.join(c.baseUrl(), "/trackers.json"), headers, c.timeoutMs());
        var trackersBody = http.requireJsonCollection(trackersResponse, "Redmine", "tracker discovery", "trackers");
        var trackerRows = maps(trackersBody.get("trackers"));

        var fields = new ArrayList<ProviderFieldMetadata>();
        fields.add(new ProviderFieldMetadata("subject", "subject", "Subject", "STRING", true, List.of()));
        fields.add(new ProviderFieldMetadata("description", "description", "Description", "TEXT", false, List.of()));

        // Redmine treats Priority as provider metadata, not as an OpenDispatch enum.  Keep provider ids
        // ordered with the provider default first so Route B can materialize a valid priority_id.
        var prioritiesResponse = http.get(ScopedProviderHttpSupport.join(c.baseUrl(), "/enumerations/issue_priorities.json"), headers, c.timeoutMs());
        var prioritiesBody = http.requireJsonCollection(prioritiesResponse, "Redmine", "issue-priority discovery", "issue_priorities");
        var priorityRows = new ArrayList<>(maps(prioritiesBody.get("issue_priorities")));
        priorityRows.sort(Comparator.comparing((Map<String,Object> v) -> !bool(v.get("is_default"))));
        var priorityIds = priorityRows.stream().map(v -> s(v, "id")).filter(Objects::nonNull).toList();
        fields.add(new ProviderFieldMetadata("priority_id", "priority_id", "Priority", "ENUM", true, priorityIds));

        // Redmine custom-field discovery can require elevated metadata permission on some deployments.
        // When available, govern the fields; when unavailable, do not make metadata discovery itself fail.
        var requiredCustomByTracker = new LinkedHashMap<String, List<String>>();
        try {
            var customResponse = http.get(ScopedProviderHttpSupport.join(c.baseUrl(), "/custom_fields.json"), headers, c.timeoutMs());
            if (ScopedProviderHttpSupport.ok(customResponse.statusCode())) {
                var customBody = http.parse(customResponse.body());
                for (var v : maps(customBody.get("custom_fields"))) {
                    String customizedType = first(s(v, "customized_type"), "issue");
                    if (!"issue".equalsIgnoreCase(customizedType)) continue;
                    String id = s(v, "id");
                    if (id == null) continue;
                    String fieldId = "custom_field:" + id;
                    boolean required = bool(v.get("is_required"));
                    List<String> allowed = possibleValues(v.get("possible_values"));
                    fields.add(new ProviderFieldMetadata(fieldId, fieldId, first(s(v, "name"), fieldId), first(s(v, "field_format"), "STRING"), required, allowed));
                    if (required) {
                        List<Map<String,Object>> scopedTrackers = maps(v.get("trackers"));
                        if (scopedTrackers.isEmpty()) {
                            requiredCustomByTracker.computeIfAbsent("*", k -> new ArrayList<>()).add(fieldId);
                        } else {
                            for (var tracker : scopedTrackers) {
                                String trackerId = s(tracker, "id");
                                if (trackerId != null) requiredCustomByTracker.computeIfAbsent(trackerId, k -> new ArrayList<>()).add(fieldId);
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // Optional metadata enrichment. Required custom fields can still be explicitly governed by Mapping requiredFields.
        }

        var issueTypes = trackerRows.stream().map(v -> {
            String id = s(v, "id");
            var required = new ArrayList<String>();
            required.add("subject");
            required.add("priority_id");
            required.addAll(requiredCustomByTracker.getOrDefault("*", List.of()));
            required.addAll(requiredCustomByTracker.getOrDefault(id, List.of()));
            return new ProviderIssueTypeMetadata(id, id, s(v, "name"), required);
        }).toList();

        var statusResponse = http.get(ScopedProviderHttpSupport.join(c.baseUrl(), "/issue_statuses.json"), headers, c.timeoutMs());
        var statusBody = http.requireJsonCollection(statusResponse, "Redmine", "issue-status discovery", "issue_statuses");
        var transitions = maps(statusBody.get("issue_statuses")).stream()
                .map(v -> new ProviderTransitionMetadata(s(v, "id"), s(v, "id"), s(v, "name"), null, s(v, "name")))
                .toList();

        var permissions = Map.of(
                "AUTHENTICATE", PermissionProbeResultStatus.GRANTED,
                "PROJECT_VISIBLE", PermissionProbeResultStatus.GRANTED,
                "TRACKERS_VISIBLE", PermissionProbeResultStatus.GRANTED,
                "PRIORITIES_VISIBLE", PermissionProbeResultStatus.GRANTED);
        var now = OffsetDateTime.now();
        String summary = projects.isEmpty()
                ? "Redmine API account '" + currentUserLabel + "' returned 0 visible projects. The projects visible in your browser may belong to a different Redmine user; verify that this API-key account is a member of the expected projects."
                : "Redmine API account '" + currentUserLabel + "' loaded " + projects.size() + " projects, " + issueTypes.size() + " trackers, " + priorityIds.size() + " priorities, " + transitions.size() + " statuses.";
        return new ProviderMetadataSnapshot(request.tenantId(), "metadata-" + UUID.randomUUID(), c.connectionId(),
                m.mappingId(), m.externalProjectId(), m.externalProjectKey(), projects, issueTypes, fields, transitions,
                List.of(), List.of(), List.of(), permissions, projectsResponse.headers().firstValue("ETag").orElse(null),
                projectsResponse.headers().firstValue("Last-Modified").orElse(null),
                ProjectMappingGovernanceService.schemaHash(m, issueTypes, fields, transitions, List.of()), 1,
                ProviderMetadataCacheStatus.FRESH, now, now.plusHours(1), summary, request.correlationId());
    }

    private List<String> possibleValues(Object value) {
        if (!(value instanceof List<?> values)) return List.of();
        var out = new ArrayList<String>();
        for (Object item : values) {
            if (item instanceof Map<?,?>) {
                Map<String,Object> row = map(item);
                String v = first(s(row, "value"), s(row, "id"), s(row, "name"));
                if (v != null) out.add(v);
            } else if (item != null) out.add(String.valueOf(item));
        }
        return List.copyOf(out);
    }

    private ProviderMetadataSnapshot unsupported(IntegrationConnection c, IntegrationProjectMapping m, ProviderMetadataProbeRequest request) {
        var now=OffsetDateTime.now();
        return new ProviderMetadataSnapshot(request.tenantId(),"metadata-"+UUID.randomUUID(),c.connectionId(),m.mappingId(),m.externalProjectId(),m.externalProjectKey(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),Map.of("METADATA",PermissionProbeResultStatus.UNSUPPORTED),null,null,ProjectMappingGovernanceService.schemaHash(m,List.of()),1,ProviderMetadataCacheStatus.FAILED,now,now.plusMinutes(5),"Provider Metadata Probe is not implemented for this Provider.",request.correlationId());
    }

    @SuppressWarnings("unchecked") private List<Map<String,Object>> maps(Object value){if(value instanceof List<?> l)return l.stream().map(this::map).toList();return List.of();}
    @SuppressWarnings("unchecked") private Map<String,Object> map(Object value){return value instanceof Map<?,?> m?(Map<String,Object>)m:Map.of();}
    private String s(Map<String,Object> v,String key){Object x=v.get(key);return x==null?null:String.valueOf(x);}
    private boolean bool(Object v){return v instanceof Boolean b&&b;}
    private String schemaType(Map<String,Object> v){Object schema=v.get("schema");return schema instanceof Map<?,?> m&&m.get("type")!=null?String.valueOf(m.get("type")):"STRING";}
    private List<String> allowed(Object v){return maps(v).stream().map(x->first(s(x,"value"),s(x,"name"),s(x,"id"))).filter(Objects::nonNull).toList();}
    private String enc(String v){return java.net.URLEncoder.encode(v==null?"":v,java.nio.charset.StandardCharsets.UTF_8);}
    private String first(String... values){for(String v:values)if(v!=null&&!v.isBlank())return v;return null;}
}
