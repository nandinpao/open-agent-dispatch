package com.opensocket.aievent.core.capability;

import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantContextHolder;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantExecutionContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Phase 4 WHO SHOULD authority.
 *
 * <p>Only persisted Phase 3 PASS decisions may enter ranking. Authorization is never a score.
 * Runtime eligibility is a hard gate before weighted ranking. This service selects a Capability
 * Binding/Provider as routing evidence only; it never chooses Netty, A2A, MCP, HTTP, endpoints,
 * credentials or Agent Pools. Phase 4 preview decisions are not wired into Dispatch/A2A runtime.</p>
 */
@Service
public class ProviderRoutingService {
    private static final TypeReference<Map<String, Integer>> INT_MAP = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {};
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final TypeReference<List<ProviderRoutingCandidateScore>> CANDIDATE_LIST = new TypeReference<>() {};
    private static final Set<String> PROFILE_TYPES = Set.of("FAST", "LOW_COST", "BALANCED", "HIGH_ACCURACY", "CRITICAL", "CUSTOM");
    private static final Set<String> PROFILE_STATUSES = Set.of("DRAFT", "ACTIVE", "SUSPENDED", "RETIRED");
    private static final Set<String> WEIGHT_KEYS = Set.of("QUALITY", "RELIABILITY", "LATENCY", "COST", "LOAD", "LOCALITY");
    private static final Set<String> OBSERVATION_SOURCES = Set.of("RUNTIME_OBSERVED", "HISTORICAL_METRICS", "ADMIN_VERIFIED", "SYNTHETIC_PREVIEW");

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;

    public ProviderRoutingService(NamedParameterJdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public List<RoutingProfile> listProfiles(String tenantId, String status, String search, String afterProfileId, int limit) {
        String tenant = requireTenant(tenantId);
        bindDatabaseTenantContext(tenant);
        MapSqlParameterSource params = new MapSqlParameterSource("tenantId", tenant).addValue("limit", normalizeLimit(limit));
        StringBuilder where = new StringBuilder(" where tenant_id=:tenantId");
        if (!blank(status)) { where.append(" and status=:status"); params.addValue("status", normalizeProfileStatus(status)); }
        else where.append(" and status<>'RETIRED'");
        if (!blank(search)) {
            where.append(" and (lower(profile_id) like :search or lower(display_name) like :search or lower(coalesce(description,'')) like :search)");
            params.addValue("search", "%" + search.trim().toLowerCase(Locale.ROOT) + "%");
        }
        if (!blank(afterProfileId)) { where.append(" and profile_id>:afterProfileId"); params.addValue("afterProfileId", afterProfileId.trim()); }
        return List.copyOf(jdbc.query("""
                select tenant_id,profile_id,display_name,description,profile_type,status,weights_json,
                       min_quality_score,min_reliability_score,max_p95_latency_ms,max_estimated_cost,max_load_percent,
                       max_observation_age_seconds,version,created_at,updated_at
                from routing_profiles
                """ + where + " order by profile_id asc limit :limit", params, new RoutingProfileRowMapper()));
    }

    @Transactional(readOnly = true)
    public Optional<RoutingProfile> findProfile(String tenantId, String profileId) {
        String tenant = requireTenant(tenantId);
        bindDatabaseTenantContext(tenant);
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    select tenant_id,profile_id,display_name,description,profile_type,status,weights_json,
                           min_quality_score,min_reliability_score,max_p95_latency_ms,max_estimated_cost,max_load_percent,
                           max_observation_age_seconds,version,created_at,updated_at
                    from routing_profiles where tenant_id=:tenantId and profile_id=:profileId
                    """, new MapSqlParameterSource("tenantId", tenant).addValue("profileId", requireNonBlank(profileId, "profileId")), new RoutingProfileRowMapper()));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Transactional
    public RoutingProfile upsertProfile(String tenantId, String pathProfileId, RoutingProfile request, String reason) {
        String tenant = requireTenant(tenantId);
        bindDatabaseTenantContext(tenant);
        if (request == null) throw new IllegalArgumentException("Routing Profile request body is required");
        String profileId = requireNonBlank(firstNonBlank(pathProfileId, request.profileId()), "profileId");
        if (!blank(request.profileId()) && !profileId.equals(request.profileId().trim())) throw new IllegalArgumentException("profileId in the request body must match the path");
        RoutingProfile existing = findProfile(tenant, profileId).orElse(null);
        String changeReason = existing == null ? firstNonBlank(reason, "Initial Routing Profile creation") : requireNonBlank(reason, "X-Change-Reason");
        String displayName = requireNonBlank(request.displayName(), "displayName");
        String profileType = normalizeProfileType(request.profileType());
        String status = normalizeProfileStatus(request.status());
        Map<String, Integer> weights = normalizeWeights(request.weights(), profileType);
        validateScore(request.minQualityScore(), "minQualityScore");
        validateScore(request.minReliabilityScore(), "minReliabilityScore");
        validateNonNegative(request.maxP95LatencyMs(), "maxP95LatencyMs");
        validateNonNegative(request.maxEstimatedCost(), "maxEstimatedCost");
        validateScore(request.maxLoadPercent(), "maxLoadPercent");
        int maxAge = request.maxObservationAgeSeconds() == null ? 300 : request.maxObservationAgeSeconds();
        if (maxAge < 1 || maxAge > 86400) throw new IllegalArgumentException("maxObservationAgeSeconds must be between 1 and 86400");
        int version = existing == null ? 1 : existing.version() + 1;
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("""
                insert into routing_profiles(tenant_id,profile_id,display_name,description,profile_type,status,weights_json,
                  min_quality_score,min_reliability_score,max_p95_latency_ms,max_estimated_cost,max_load_percent,max_observation_age_seconds,
                  version,created_at,updated_at)
                values(:tenantId,:profileId,:displayName,:description,:profileType,:status,cast(:weights as jsonb),
                  :minQuality,:minReliability,:maxLatency,:maxCost,:maxLoad,:maxAge,:version,:createdAt,:updatedAt)
                on conflict(tenant_id,profile_id) do update set
                  display_name=excluded.display_name,description=excluded.description,profile_type=excluded.profile_type,status=excluded.status,
                  weights_json=excluded.weights_json,min_quality_score=excluded.min_quality_score,min_reliability_score=excluded.min_reliability_score,
                  max_p95_latency_ms=excluded.max_p95_latency_ms,max_estimated_cost=excluded.max_estimated_cost,max_load_percent=excluded.max_load_percent,
                  max_observation_age_seconds=excluded.max_observation_age_seconds,version=excluded.version,updated_at=excluded.updated_at
                """, new MapSqlParameterSource("tenantId", tenant).addValue("profileId", profileId).addValue("displayName", displayName)
                .addValue("description", trimToNull(request.description())).addValue("profileType", profileType).addValue("status", status)
                .addValue("weights", writeJson(weights)).addValue("minQuality", request.minQualityScore()).addValue("minReliability", request.minReliabilityScore())
                .addValue("maxLatency", request.maxP95LatencyMs()).addValue("maxCost", request.maxEstimatedCost()).addValue("maxLoad", request.maxLoadPercent())
                .addValue("maxAge", maxAge).addValue("version", version).addValue("createdAt", existing == null ? now : existing.createdAt()).addValue("updatedAt", now));
        appendProfileVersion(tenant, profileId, version, changeReason, now);
        return findProfile(tenant, profileId).orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<RoutingProfileVersion> profileVersions(String tenantId, String profileId) {
        String tenant = requireTenant(tenantId);
        bindDatabaseTenantContext(tenant);
        return List.copyOf(jdbc.query("""
                select tenant_id,profile_id,version,snapshot_json,change_reason,actor_ref,created_at
                from routing_profile_versions where tenant_id=:tenantId and profile_id=:profileId order by version asc
                """, new MapSqlParameterSource("tenantId", tenant).addValue("profileId", requireNonBlank(profileId, "profileId")),
                (rs, rowNum) -> new RoutingProfileVersion(rs.getString("tenant_id"), rs.getString("profile_id"), rs.getInt("version"),
                        readMap(rs.getString("snapshot_json")), rs.getString("change_reason"), rs.getString("actor_ref"), rs.getObject("created_at", OffsetDateTime.class))));
    }

    @Transactional
    public ProviderEligibilityObservation recordObservation(String tenantId, ProviderEligibilityObservation request) {
        String tenant = requireTenant(tenantId);
        bindDatabaseTenantContext(tenant);
        if (request == null) throw new IllegalArgumentException("Provider Eligibility Observation request body is required");
        String bindingId = requireNonBlank(request.bindingId(), "bindingId");
        String providerId = requireNonBlank(request.providerId(), "providerId");
        BindingIdentity identity = loadBindingIdentity(tenant, bindingId);
        if (!providerId.equals(identity.providerId())) throw new IllegalArgumentException("providerId does not match the Capability Binding");
        String source = normalizeObservationSource(request.observationSource());
        validateScore(request.qualityScore(), "qualityScore");
        validateScore(request.reliabilityScore(), "reliabilityScore");
        validateNonNegative(request.p95LatencyMs(), "p95LatencyMs");
        validateNonNegative(request.estimatedCost(), "estimatedCost");
        validateScore(request.loadPercent(), "loadPercent");
        validateScore(request.localityScore(), "localityScore");
        OffsetDateTime observedAt = request.observedAt() == null ? OffsetDateTime.now() : request.observedAt();
        OffsetDateTime expiresAt = request.expiresAt();
        if (expiresAt != null && !expiresAt.isAfter(observedAt)) throw new IllegalArgumentException("expiresAt must be after observedAt");
        String observationId = blank(request.observationId()) ? "provider-eligibility-" + UUID.randomUUID() : request.observationId().trim();
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("""
                insert into provider_eligibility_observations(
                  tenant_id,observation_id,binding_id,provider_id,observation_source,available,healthy,capacity_available,
                  quality_score,reliability_score,p95_latency_ms,estimated_cost,load_percent,locality_score,observed_at,expires_at,created_at)
                values(:tenantId,:observationId,:bindingId,:providerId,:source,:available,:healthy,:capacity,
                  :quality,:reliability,:latency,:cost,:load,:locality,:observedAt,:expiresAt,:createdAt)
                """, new MapSqlParameterSource("tenantId", tenant).addValue("observationId", observationId).addValue("bindingId", bindingId)
                .addValue("providerId", providerId).addValue("source", source).addValue("available", request.available()).addValue("healthy", request.healthy())
                .addValue("capacity", request.capacityAvailable()).addValue("quality", request.qualityScore()).addValue("reliability", request.reliabilityScore())
                .addValue("latency", request.p95LatencyMs()).addValue("cost", request.estimatedCost()).addValue("load", request.loadPercent())
                .addValue("locality", request.localityScore()).addValue("observedAt", observedAt).addValue("expiresAt", expiresAt).addValue("createdAt", now));
        return findObservation(tenant, observationId).orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<ProviderEligibilityObservation> listObservations(String tenantId, String bindingId, int limit) {
        String tenant = requireTenant(tenantId);
        bindDatabaseTenantContext(tenant);
        MapSqlParameterSource params = new MapSqlParameterSource("tenantId", tenant).addValue("limit", normalizeLimit(limit));
        String where = blank(bindingId) ? "" : " and binding_id=:bindingId";
        if (!blank(bindingId)) params.addValue("bindingId", bindingId.trim());
        return List.copyOf(jdbc.query("""
                select tenant_id,observation_id,binding_id,provider_id,observation_source,available,healthy,capacity_available,
                       quality_score,reliability_score,p95_latency_ms,estimated_cost,load_percent,locality_score,observed_at,expires_at,created_at
                from provider_eligibility_observations where tenant_id=:tenantId
                """ + where + " order by observed_at desc,observation_id desc limit :limit", params, new ObservationRowMapper()));
    }

    @Transactional(readOnly = true)
    public List<ProviderRoutingDecision> listDecisions(String tenantId, String capabilityCode, int limit) {
        String tenant = requireTenant(tenantId);
        bindDatabaseTenantContext(tenant);
        MapSqlParameterSource params = new MapSqlParameterSource("tenantId", tenant).addValue("limit", normalizeLimit(limit));
        String where = blank(capabilityCode) ? "" : " and capability_code=:capabilityCode";
        if (!blank(capabilityCode)) params.addValue("capabilityCode", capabilityCode.trim().toLowerCase(Locale.ROOT));
        return List.copyOf(jdbc.query("""
                select tenant_id,decision_id,decision_mode,result,capability_code,operation,routing_profile_id,routing_profile_version,
                       selected_binding_id,selected_provider_id,reason_codes_json,candidates_json,evaluated_at
                from provider_routing_decisions where tenant_id=:tenantId
                """ + where + " order by evaluated_at desc,decision_id desc limit :limit", params, new DecisionRowMapper()));
    }

    /** Admin-only WHO SHOULD preview; selected binding is evidence and does not execute. */
    @Transactional
    public ProviderRoutingDecision evaluatePreview(String tenantId, ProviderRoutingPreviewRequest request) {
        return evaluate(tenantId, request, "PREVIEW");
    }

    /** Phase 12 server-side WHO SHOULD runtime evaluation. Only RUNTIME WHO MAY evidence is accepted. */
    @Transactional
    public ProviderRoutingDecision evaluateRuntime(String tenantId, ProviderRoutingPreviewRequest request) {
        return evaluate(tenantId, request, "RUNTIME");
    }

    private ProviderRoutingDecision evaluate(String tenantId, ProviderRoutingPreviewRequest request, String decisionMode) {
        String tenant = requireTenant(tenantId);
        bindDatabaseTenantContext(tenant);
        if (request == null) throw new IllegalArgumentException("Provider Routing preview request body is required");
        String capabilityCode = requireNonBlank(request.capabilityCode(), "capabilityCode").toLowerCase(Locale.ROOT);
        String operation = requireNonBlank(request.operation(), "operation").toUpperCase(Locale.ROOT).replace(' ', '_');
        RoutingProfile profile = findProfile(tenant, requireNonBlank(request.routingProfileId(), "routingProfileId"))
                .orElseThrow(() -> new IllegalArgumentException("Routing Profile does not exist: " + request.routingProfileId()));
        if (!"ACTIVE".equals(profile.status())) throw new IllegalArgumentException("Routing Profile must be ACTIVE for WHO SHOULD evaluation");
        List<String> ids = new ArrayList<>(new LinkedHashSet<>(request.authorizationDecisionIds()));
        if (ids.isEmpty()) throw new IllegalArgumentException("At least one persisted Phase 3 PASS authorizationDecisionId is required");
        int decisionLimit = "RUNTIME".equals(decisionMode) ? 5000 : 200;
        if (ids.size() > decisionLimit) throw new IllegalArgumentException("At most " + decisionLimit + " authorizationDecisionIds may be ranked in one " + decisionMode + " evaluation");

        List<ProviderRoutingCandidateScore> candidates = new ArrayList<>();
        for (String decisionId : ids) candidates.add(evaluateCandidateEligibility(tenant, capabilityCode, operation, profile, decisionMode, loadAuthorizationEvidence(tenant, requireNonBlank(decisionId, "authorizationDecisionId"))));
        List<ProviderRoutingCandidateScore> prelimEligible = candidates.stream().filter(candidate -> "ELIGIBLE".equals(candidate.eligibilityResult())).toList();
        if (prelimEligible.isEmpty()) return persistDecision(tenant, capabilityCode, operation, profile, decisionMode, "NO_ELIGIBLE_PROVIDER", null, null,
                List.of("NO_ELIGIBLE_PROVIDER"), candidates);
        ScoringBounds bounds = scoringBounds(tenant, prelimEligible);
        candidates = candidates.stream().map(candidate -> scoreEligibleCandidate(tenant, profile, bounds, candidate)).toList();
        List<ProviderRoutingCandidateScore> eligible = candidates.stream().filter(candidate -> "ELIGIBLE".equals(candidate.eligibilityResult()) && candidate.totalScore() != null).toList();
        if (eligible.isEmpty()) return persistDecision(tenant, capabilityCode, operation, profile, decisionMode, "NO_ELIGIBLE_PROVIDER", null, null,
                List.of("NO_ELIGIBLE_PROVIDER"), candidates);
        BigDecimal top = eligible.stream().map(ProviderRoutingCandidateScore::totalScore).max(Comparator.naturalOrder()).orElseThrow();
        List<ProviderRoutingCandidateScore> winners = eligible.stream().filter(candidate -> candidate.totalScore().compareTo(top) == 0).toList();
        if (winners.size() != 1) return persistDecision(tenant, capabilityCode, operation, profile, decisionMode, "ROUTING_AMBIGUOUS", null, null,
                List.of("ROUTING_SCORE_TIE", "FAIL_CLOSED_NO_LEXICAL_TIE_BREAK"), candidates);
        ProviderRoutingCandidateScore winner = winners.get(0);
        return persistDecision(tenant, capabilityCode, operation, profile, decisionMode, "SELECTED", winner.bindingId(), winner.providerId(),
                List.of("WHO_SHOULD_SELECTED", "AUTHORIZATION_NOT_REEVALUATED_AS_SCORE"), candidates);
    }

    private ProviderRoutingCandidateScore evaluateCandidateEligibility(String tenant, String capabilityCode, String operation, RoutingProfile profile, String decisionMode, AuthorizationEvidence auth) {
        List<String> failures = new ArrayList<>();
        if (!"PASS".equals(auth.result())) failures.add("WHO_MAY_NOT_PASS");
        if (!decisionMode.equals(auth.decisionMode())) failures.add("PHASE3_DECISION_MODE_MISMATCH");
        if (!capabilityCode.equals(auth.capabilityCode())) failures.add("AUTHORIZATION_CAPABILITY_MISMATCH");
        if (!operation.equals(auth.operation())) failures.add("AUTHORIZATION_OPERATION_MISMATCH");
        BindingIdentity identity = loadBindingIdentity(tenant, auth.bindingId());
        if (!auth.providerId().equals(identity.providerId())) failures.add("AUTHORIZATION_PROVIDER_MISMATCH");
        if (!"REGISTERED".equals(identity.providerStatus())) failures.add("PROVIDER_NOT_REGISTERED");
        if (!"APPROVED".equals(identity.trustStatus())) failures.add("BINDING_CATALOG_TRUST_NOT_APPROVED");
        if (!capabilityCode.equals(identity.capabilityCode())) failures.add("BINDING_CAPABILITY_MISMATCH");
        if (!identity.supportedOperations().isEmpty() && !identity.supportedOperations().contains(operation)) failures.add("OPERATION_NOT_SUPPORTED_BY_BINDING");
        if (auth.selectedPolicyId() == null || auth.selectedPolicyVersion() == null || !authorizationPolicyStillCurrent(tenant, auth)) failures.add("AUTHORIZATION_DECISION_STALE");

        ProviderEligibilityObservation observation = latestObservation(tenant, auth.bindingId()).orElse(null);
        if (observation == null) failures.add("NO_ELIGIBILITY_OBSERVATION");
        else {
            failures.addAll(eligibilityFailures(profile, observation));
            failures.addAll(missingScoreMetrics(profile, observation));
        }
        if (!failures.isEmpty()) return new ProviderRoutingCandidateScore(auth.bindingId(), auth.providerId(), auth.providerType(), auth.decisionId(),
                observation == null ? null : observation.observationId(), "EXCLUDED", List.copyOf(failures), Map.of(), null);
        return new ProviderRoutingCandidateScore(auth.bindingId(), auth.providerId(), auth.providerType(), auth.decisionId(), observation.observationId(),
                "ELIGIBLE", List.of(), Map.of(), null);
    }

    private List<String> missingScoreMetrics(RoutingProfile profile, ProviderEligibilityObservation observation) {
        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, Integer> weight : profile.weights().entrySet()) {
            if (weight.getValue() <= 0) continue;
            boolean absent = switch (weight.getKey()) {
                case "QUALITY" -> observation.qualityScore() == null;
                case "RELIABILITY" -> observation.reliabilityScore() == null;
                case "LATENCY" -> observation.p95LatencyMs() == null;
                case "COST" -> observation.estimatedCost() == null;
                case "LOAD" -> observation.loadPercent() == null;
                case "LOCALITY" -> observation.localityScore() == null;
                default -> true;
            };
            if (absent) missing.add("MISSING_SCORE_METRIC_" + weight.getKey());
        }
        return missing;
    }

    private ProviderRoutingCandidateScore scoreEligibleCandidate(String tenant, RoutingProfile profile, ScoringBounds bounds, ProviderRoutingCandidateScore candidate) {
        if (!"ELIGIBLE".equals(candidate.eligibilityResult()) || candidate.eligibilityObservationId() == null) return candidate;
        ProviderEligibilityObservation observation = findObservation(tenant, candidate.eligibilityObservationId()).orElseThrow();
        Map<String, BigDecimal> components = scoreComponents(observation, bounds);
        BigDecimal total = weightedTotal(profile.weights(), components);
        return new ProviderRoutingCandidateScore(candidate.bindingId(), candidate.providerId(), candidate.providerType(), candidate.authorizationDecisionId(),
                candidate.eligibilityObservationId(), "ELIGIBLE", List.of(), components, total);
    }

    private ScoringBounds scoringBounds(String tenant, List<ProviderRoutingCandidateScore> candidates) {
        List<ProviderEligibilityObservation> observations = candidates.stream()
                .map(candidate -> findObservation(tenant, candidate.eligibilityObservationId()).orElseThrow())
                .toList();
        Long minLatency = observations.stream().map(ProviderEligibilityObservation::p95LatencyMs).filter(java.util.Objects::nonNull).min(Long::compareTo).orElse(null);
        Long maxLatency = observations.stream().map(ProviderEligibilityObservation::p95LatencyMs).filter(java.util.Objects::nonNull).max(Long::compareTo).orElse(null);
        BigDecimal minCost = observations.stream().map(ProviderEligibilityObservation::estimatedCost).filter(java.util.Objects::nonNull).min(BigDecimal::compareTo).orElse(null);
        BigDecimal maxCost = observations.stream().map(ProviderEligibilityObservation::estimatedCost).filter(java.util.Objects::nonNull).max(BigDecimal::compareTo).orElse(null);
        return new ScoringBounds(minLatency, maxLatency, minCost, maxCost);
    }

    private List<String> eligibilityFailures(RoutingProfile profile, ProviderEligibilityObservation observation) {
        List<String> failures = new ArrayList<>();
        OffsetDateTime now = OffsetDateTime.now();
        if (!observation.available()) failures.add("PROVIDER_UNAVAILABLE");
        if (!observation.healthy()) failures.add("PROVIDER_UNHEALTHY");
        if (!observation.capacityAvailable()) failures.add("PROVIDER_CAPACITY_UNAVAILABLE");
        if (observation.expiresAt() != null && !observation.expiresAt().isAfter(now)) failures.add("ELIGIBILITY_OBSERVATION_EXPIRED");
        long ageSeconds = Math.max(0, Duration.between(observation.observedAt(), now).getSeconds());
        if (ageSeconds > profile.maxObservationAgeSeconds()) failures.add("ELIGIBILITY_OBSERVATION_STALE");
        if (profile.minQualityScore() != null && (observation.qualityScore() == null || observation.qualityScore().compareTo(profile.minQualityScore()) < 0)) failures.add("MIN_QUALITY_NOT_MET");
        if (profile.minReliabilityScore() != null && (observation.reliabilityScore() == null || observation.reliabilityScore().compareTo(profile.minReliabilityScore()) < 0)) failures.add("MIN_RELIABILITY_NOT_MET");
        if (profile.maxP95LatencyMs() != null && (observation.p95LatencyMs() == null || observation.p95LatencyMs() > profile.maxP95LatencyMs())) failures.add("MAX_LATENCY_EXCEEDED");
        if (profile.maxEstimatedCost() != null && (observation.estimatedCost() == null || observation.estimatedCost().compareTo(profile.maxEstimatedCost()) > 0)) failures.add("MAX_COST_EXCEEDED");
        if (profile.maxLoadPercent() != null && (observation.loadPercent() == null || observation.loadPercent().compareTo(profile.maxLoadPercent()) > 0)) failures.add("MAX_LOAD_EXCEEDED");
        return failures;
    }

    private Map<String, BigDecimal> scoreComponents(ProviderEligibilityObservation observation, ScoringBounds bounds) {
        Map<String, BigDecimal> scores = new LinkedHashMap<>();
        if (observation.qualityScore() != null) scores.put("QUALITY", clamp(observation.qualityScore()));
        if (observation.reliabilityScore() != null) scores.put("RELIABILITY", clamp(observation.reliabilityScore()));
        if (observation.localityScore() != null) scores.put("LOCALITY", clamp(observation.localityScore()));
        if (observation.loadPercent() != null) scores.put("LOAD", clamp(BigDecimal.valueOf(100).subtract(observation.loadPercent())));
        if (observation.p95LatencyMs() != null) {
            scores.put("LATENCY", normalizeLowerBetter(BigDecimal.valueOf(observation.p95LatencyMs()),
                    bounds.minLatencyMs() == null ? null : BigDecimal.valueOf(bounds.minLatencyMs()),
                    bounds.maxLatencyMs() == null ? null : BigDecimal.valueOf(bounds.maxLatencyMs())));
        }
        if (observation.estimatedCost() != null) {
            scores.put("COST", normalizeLowerBetter(observation.estimatedCost(), bounds.minCost(), bounds.maxCost()));
        }
        return Map.copyOf(scores);
    }

    private BigDecimal normalizeLowerBetter(BigDecimal value, BigDecimal min, BigDecimal max) {
        if (value == null || min == null || max == null) return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        if (max.compareTo(min) == 0) return BigDecimal.valueOf(100).setScale(4, RoundingMode.HALF_UP);
        return clamp(max.subtract(value).divide(max.subtract(min), 8, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)));
    }

    private BigDecimal weightedTotal(Map<String, Integer> weights, Map<String, BigDecimal> scores) {
        int denominator = weights.values().stream().mapToInt(Integer::intValue).sum();
        if (denominator <= 0) throw new IllegalArgumentException("Routing Profile weights must sum to more than zero");
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<String, Integer> weight : weights.entrySet()) {
            if (weight.getValue() <= 0) continue;
            total = total.add(scores.get(weight.getKey()).multiply(BigDecimal.valueOf(weight.getValue())));
        }
        return total.divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
    }

    private boolean authorizationPolicyStillCurrent(String tenant, AuthorizationEvidence auth) {
        Integer count = jdbc.queryForObject("""
                select count(*) from delegation_policies
                where tenant_id=:tenantId and policy_id=:policyId and version=:version and status='ACTIVE' and effect='ALLOW'
                """, new MapSqlParameterSource("tenantId", tenant).addValue("policyId", auth.selectedPolicyId()).addValue("version", auth.selectedPolicyVersion()), Integer.class);
        return count != null && count == 1;
    }

    private AuthorizationEvidence loadAuthorizationEvidence(String tenant, String decisionId) {
        try {
            return jdbc.queryForObject("""
                    select decision_id,decision_mode,result,capability_code,operation,binding_id,provider_id,provider_type,
                           selected_policy_id,selected_policy_version,evaluated_at
                    from delegation_authorization_decisions where tenant_id=:tenantId and decision_id=:decisionId
                    """, new MapSqlParameterSource("tenantId", tenant).addValue("decisionId", decisionId),
                    (rs, rowNum) -> new AuthorizationEvidence(rs.getString("decision_id"), rs.getString("decision_mode"), rs.getString("result"),
                            rs.getString("capability_code"), rs.getString("operation"), rs.getString("binding_id"), rs.getString("provider_id"), rs.getString("provider_type"),
                            rs.getString("selected_policy_id"), (Integer) rs.getObject("selected_policy_version"), rs.getObject("evaluated_at", OffsetDateTime.class)));
        } catch (EmptyResultDataAccessException ex) {
            throw new IllegalArgumentException("Phase 3 authorization decision does not exist: " + decisionId);
        }
    }

    private BindingIdentity loadBindingIdentity(String tenant, String bindingId) {
        try {
            return jdbc.queryForObject("""
                    select b.binding_id,b.capability_code,b.provider_id,b.supported_operations_json,b.trust_status,
                           p.provider_type,p.catalog_status
                    from capability_bindings b join capability_providers p on p.tenant_id=b.tenant_id and p.provider_id=b.provider_id
                    where b.tenant_id=:tenantId and b.binding_id=:bindingId
                    """, new MapSqlParameterSource("tenantId", tenant).addValue("bindingId", bindingId),
                    (rs, rowNum) -> new BindingIdentity(rs.getString("binding_id"), rs.getString("capability_code"), rs.getString("provider_id"),
                            rs.getString("provider_type"), rs.getString("catalog_status"), readStringList(rs.getString("supported_operations_json")), rs.getString("trust_status")));
        } catch (EmptyResultDataAccessException ex) {
            throw new IllegalArgumentException("Capability Binding does not exist: " + bindingId);
        }
    }

    private Optional<ProviderEligibilityObservation> latestObservation(String tenant, String bindingId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    select tenant_id,observation_id,binding_id,provider_id,observation_source,available,healthy,capacity_available,
                           quality_score,reliability_score,p95_latency_ms,estimated_cost,load_percent,locality_score,observed_at,expires_at,created_at
                    from provider_eligibility_observations where tenant_id=:tenantId and binding_id=:bindingId
                    order by observed_at desc,observation_id desc limit 1
                    """, new MapSqlParameterSource("tenantId", tenant).addValue("bindingId", bindingId), new ObservationRowMapper()));
        } catch (EmptyResultDataAccessException ex) { return Optional.empty(); }
    }

    private Optional<ProviderEligibilityObservation> findObservation(String tenant, String observationId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    select tenant_id,observation_id,binding_id,provider_id,observation_source,available,healthy,capacity_available,
                           quality_score,reliability_score,p95_latency_ms,estimated_cost,load_percent,locality_score,observed_at,expires_at,created_at
                    from provider_eligibility_observations where tenant_id=:tenantId and observation_id=:observationId
                    """, new MapSqlParameterSource("tenantId", tenant).addValue("observationId", observationId), new ObservationRowMapper()));
        } catch (EmptyResultDataAccessException ex) { return Optional.empty(); }
    }

    private ProviderRoutingDecision persistDecision(String tenant, String capabilityCode, String operation, RoutingProfile profile, String decisionMode, String result,
                                                      String selectedBindingId, String selectedProviderId, List<String> reasons,
                                                      List<ProviderRoutingCandidateScore> candidates) {
        String decisionId = "provider-routing-" + UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("""
                insert into provider_routing_decisions(
                  tenant_id,decision_id,decision_mode,result,capability_code,operation,routing_profile_id,routing_profile_version,
                  selected_binding_id,selected_provider_id,reason_codes_json,candidates_json,evaluated_at)
                values(:tenantId,:decisionId,:decisionMode,:result,:capabilityCode,:operation,:profileId,:profileVersion,
                  :selectedBindingId,:selectedProviderId,cast(:reasons as jsonb),cast(:candidates as jsonb),:evaluatedAt)
                """, new MapSqlParameterSource("tenantId", tenant).addValue("decisionId", decisionId).addValue("decisionMode", decisionMode).addValue("result", result)
                .addValue("capabilityCode", capabilityCode).addValue("operation", operation).addValue("profileId", profile.profileId())
                .addValue("profileVersion", profile.version()).addValue("selectedBindingId", selectedBindingId).addValue("selectedProviderId", selectedProviderId)
                .addValue("reasons", writeJson(reasons)).addValue("candidates", writeJson(candidates)).addValue("evaluatedAt", now));
        return new ProviderRoutingDecision(decisionId, tenant, decisionMode, result, capabilityCode, operation, profile.profileId(), profile.version(),
                selectedBindingId, selectedProviderId, reasons, candidates, now);
    }

    private void appendProfileVersion(String tenant, String profileId, int version, String reason, OffsetDateTime now) {
        jdbc.update("""
                insert into routing_profile_versions(tenant_id,profile_id,version,snapshot_json,change_reason,actor_ref,created_at)
                select tenant_id,profile_id,:version,to_jsonb(routing_profiles),:reason,:actorRef,:createdAt
                from routing_profiles where tenant_id=:tenantId and profile_id=:profileId
                """, new MapSqlParameterSource("tenantId", tenant).addValue("profileId", profileId).addValue("version", version)
                .addValue("reason", requireNonBlank(reason, "reason")).addValue("actorRef", effectiveActorRef()).addValue("createdAt", now));
    }

    private Map<String, Integer> normalizeWeights(Map<String, Integer> requested, String profileType) {
        Map<String, Integer> source = requested == null || requested.isEmpty() ? defaultWeights(profileType) : requested;
        LinkedHashMap<String, Integer> result = new LinkedHashMap<>();
        for (String key : WEIGHT_KEYS) result.put(key, 0);
        for (Map.Entry<String, Integer> entry : source.entrySet()) {
            String key = requireNonBlank(entry.getKey(), "weight key").toUpperCase(Locale.ROOT);
            if (!WEIGHT_KEYS.contains(key)) throw new IllegalArgumentException("Unsupported routing weight: " + key);
            int value = entry.getValue() == null ? 0 : entry.getValue();
            if (value < 0 || value > 1000) throw new IllegalArgumentException("Routing weight must be between 0 and 1000: " + key);
            result.put(key, value);
        }
        if (result.values().stream().mapToInt(Integer::intValue).sum() <= 0) throw new IllegalArgumentException("Routing Profile weights must sum to more than zero");
        return Map.copyOf(result);
    }

    private Map<String, Integer> defaultWeights(String type) {
        return switch (type) {
            case "FAST" -> Map.of("QUALITY", 15, "RELIABILITY", 20, "LATENCY", 45, "COST", 5, "LOAD", 15, "LOCALITY", 0);
            case "LOW_COST" -> Map.of("QUALITY", 15, "RELIABILITY", 20, "LATENCY", 10, "COST", 45, "LOAD", 10, "LOCALITY", 0);
            case "HIGH_ACCURACY" -> Map.of("QUALITY", 50, "RELIABILITY", 30, "LATENCY", 10, "COST", 0, "LOAD", 5, "LOCALITY", 5);
            case "CRITICAL" -> Map.of("QUALITY", 45, "RELIABILITY", 40, "LATENCY", 5, "COST", 0, "LOAD", 5, "LOCALITY", 5);
            default -> Map.of("QUALITY", 35, "RELIABILITY", 25, "LATENCY", 20, "COST", 10, "LOAD", 10, "LOCALITY", 0);
        };
    }

    private String normalizeProfileType(String value) {
        String normalized = blank(value) ? "BALANCED" : value.trim().toUpperCase(Locale.ROOT);
        if (!PROFILE_TYPES.contains(normalized)) throw new IllegalArgumentException("Unsupported profileType: " + normalized);
        return normalized;
    }
    private String normalizeProfileStatus(String value) {
        String normalized = blank(value) ? "DRAFT" : value.trim().toUpperCase(Locale.ROOT);
        if (!PROFILE_STATUSES.contains(normalized)) throw new IllegalArgumentException("Unsupported status: " + normalized);
        return normalized;
    }
    private String normalizeObservationSource(String value) {
        String normalized = blank(value) ? "ADMIN_VERIFIED" : value.trim().toUpperCase(Locale.ROOT);
        if (!OBSERVATION_SOURCES.contains(normalized)) throw new IllegalArgumentException("Unsupported observationSource: " + normalized);
        return normalized;
    }

    private BigDecimal clamp(BigDecimal value) { return value.max(BigDecimal.ZERO).min(BigDecimal.valueOf(100)).setScale(4, RoundingMode.HALF_UP); }
    private void validateScore(BigDecimal value, String field) { if (value != null && (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.valueOf(100)) > 0)) throw new IllegalArgumentException(field + " must be between 0 and 100"); }
    private void validateNonNegative(BigDecimal value, String field) { if (value != null && value.signum() < 0) throw new IllegalArgumentException(field + " must be >= 0"); }
    private void validateNonNegative(Long value, String field) { if (value != null && value < 0) throw new IllegalArgumentException(field + " must be >= 0"); }
    private int normalizeLimit(int value) { return Math.max(1, Math.min(value <= 0 ? 100 : value, 500)); }
    private String requireTenant(String value) { return requireNonBlank(value, "tenantId"); }
    private String requireNonBlank(String value, String field) { if (blank(value)) throw new IllegalArgumentException(field + " is required"); return value.trim(); }
    private String firstNonBlank(String first, String second) { return !blank(first) ? first : second; }
    private String trimToNull(String value) { return blank(value) ? null : value.trim(); }
    private boolean blank(String value) { return value == null || value.isBlank(); }

    private void bindDatabaseTenantContext(String tenantId) {
        String tenant = requireTenant(tenantId);
        IamTenantExecutionContext requestContext = IamTenantContextHolder.current().orElse(null);
        if (requestContext != null && !"INSTANCE".equalsIgnoreCase(requestContext.tenantId()) && !tenant.equals(requestContext.tenantId())) {
            throw new IllegalArgumentException("Tenant context mismatch for Provider Routing persistence");
        }
        String actor = requestContext == null || blank(requestContext.actorId()) ? "provider-routing" : requestContext.actorId();
        jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_tenant_id', ?, true)", String.class, tenant);
        jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_actor_id', ?, true)", String.class, actor);
    }
    private String effectiveActorRef() {
        IamTenantExecutionContext context = IamTenantContextHolder.current().orElse(null);
        return context == null || blank(context.actorId()) ? "provider-routing" : context.actorId();
    }

    private String writeJson(Object value) {
        try { return json.writeValueAsString(value == null ? Map.of() : value); }
        catch (Exception ex) { throw new IllegalArgumentException("Provider Routing JSON cannot be serialized", ex); }
    }
    private Map<String, Integer> readIntMap(String value) {
        try { return blank(value) ? Map.of() : json.readValue(value, INT_MAP); }
        catch (Exception ex) { throw new IllegalStateException("Routing Profile weights cannot be read", ex); }
    }
    private Map<String, Object> readMap(String value) {
        try { return blank(value) ? Map.of() : json.readValue(value, OBJECT_MAP); }
        catch (Exception ex) { throw new IllegalStateException("Provider Routing object cannot be read", ex); }
    }
    private List<String> readStringList(String value) {
        try { return blank(value) ? List.of() : json.readValue(value, STRING_LIST); }
        catch (Exception ex) { throw new IllegalStateException("Provider Routing list cannot be read", ex); }
    }
    private List<ProviderRoutingCandidateScore> readCandidates(String value) {
        try { return blank(value) ? List.of() : json.readValue(value, CANDIDATE_LIST); }
        catch (Exception ex) { throw new IllegalStateException("Provider Routing candidates cannot be read", ex); }
    }

    private final class RoutingProfileRowMapper implements RowMapper<RoutingProfile> {
        @Override public RoutingProfile mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new RoutingProfile(rs.getString("tenant_id"), rs.getString("profile_id"), rs.getString("display_name"), rs.getString("description"),
                    rs.getString("profile_type"), rs.getString("status"), readIntMap(rs.getString("weights_json")), rs.getBigDecimal("min_quality_score"),
                    rs.getBigDecimal("min_reliability_score"), (Long) rs.getObject("max_p95_latency_ms"), rs.getBigDecimal("max_estimated_cost"),
                    rs.getBigDecimal("max_load_percent"), rs.getInt("max_observation_age_seconds"), rs.getInt("version"),
                    rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class));
        }
    }
    private final class ObservationRowMapper implements RowMapper<ProviderEligibilityObservation> {
        @Override public ProviderEligibilityObservation mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new ProviderEligibilityObservation(rs.getString("tenant_id"), rs.getString("observation_id"), rs.getString("binding_id"), rs.getString("provider_id"),
                    rs.getString("observation_source"), rs.getBoolean("available"), rs.getBoolean("healthy"), rs.getBoolean("capacity_available"),
                    rs.getBigDecimal("quality_score"), rs.getBigDecimal("reliability_score"), (Long) rs.getObject("p95_latency_ms"), rs.getBigDecimal("estimated_cost"),
                    rs.getBigDecimal("load_percent"), rs.getBigDecimal("locality_score"), rs.getObject("observed_at", OffsetDateTime.class),
                    rs.getObject("expires_at", OffsetDateTime.class), rs.getObject("created_at", OffsetDateTime.class));
        }
    }
    private final class DecisionRowMapper implements RowMapper<ProviderRoutingDecision> {
        @Override public ProviderRoutingDecision mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new ProviderRoutingDecision(rs.getString("decision_id"), rs.getString("tenant_id"), rs.getString("decision_mode"), rs.getString("result"),
                    rs.getString("capability_code"), rs.getString("operation"), rs.getString("routing_profile_id"), rs.getInt("routing_profile_version"),
                    rs.getString("selected_binding_id"), rs.getString("selected_provider_id"), readStringList(rs.getString("reason_codes_json")),
                    readCandidates(rs.getString("candidates_json")), rs.getObject("evaluated_at", OffsetDateTime.class));
        }
    }

    private record AuthorizationEvidence(String decisionId, String decisionMode, String result, String capabilityCode, String operation,
                                         String bindingId, String providerId, String providerType, String selectedPolicyId,
                                         Integer selectedPolicyVersion, OffsetDateTime evaluatedAt) {}
    private record BindingIdentity(String bindingId, String capabilityCode, String providerId, String providerType, String providerStatus,
                                   List<String> supportedOperations, String trustStatus) {}
    private record ScoringBounds(Long minLatencyMs, Long maxLatencyMs, BigDecimal minCost, BigDecimal maxCost) {}
}
