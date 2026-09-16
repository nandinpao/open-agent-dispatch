package com.opensocket.aievent.database.persistence.dispatch.flow;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.opensocket.aievent.core.dispatch.flow.FlowMatchDecision;
import com.opensocket.aievent.core.dispatch.flow.FlowRuleEvaluation;
import com.opensocket.aievent.core.dispatch.flow.FlowRuleEvaluationCandidate;
import com.opensocket.aievent.core.dispatch.flow.FlowRuleRoutingRepository;
import com.opensocket.aievent.core.dispatch.flow.FlowRuleRuntimeMatch;
import com.opensocket.aievent.core.dispatch.flow.FlowRuleRuntimeQuery;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;

import tools.jackson.databind.ObjectMapper;

/**
 * A0-R3 deterministic Flow Match persistence adapter.
 *
 * <p>Physical Rule authority remains dispatch_policies. The adapter does not use requestedSkill,
 * semantic similarity, updated_at, or rule_id as authoritative selection inputs. All exact/wildcard
 * matches are evaluated first; only the minimum-priority matched bucket may win.</p>
 */
@DatabaseRepositoryAdapter
public class JdbcFlowRuleRoutingRepository implements FlowRuleRoutingRepository {
    private static final Logger log = LoggerFactory.getLogger(JdbcFlowRuleRoutingRepository.class);
    private static final String EVALUATOR_VERSION = "A0R3_DETERMINISTIC_FLOW_MATCH_V1";
    private static final String DECISION_SOURCE = "A0_R3_FLOW_MATCH_AUTHORITY";
    private static final String DECISION_AUTHORITY_VERSION = "A0R3_FLOW_MATCH_AUTHORITY_V1";

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    public JdbcFlowRuleRoutingRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<FlowRuleRuntimeMatch> findBestMatch(FlowRuleRuntimeQuery query) {
        return evaluate(query).selectedMatch();
    }

    @Override
    public FlowRuleEvaluation evaluate(FlowRuleRuntimeQuery query) {
        return evaluateInternal(query, false);
    }

    @Override
    public FlowRuleEvaluation evaluateForSimulation(FlowRuleRuntimeQuery query) {
        if (query == null || blank(query.getFlowId())) {
            return noMatch(List.of());
        }
        return evaluateInternal(query, true);
    }

    private FlowRuleEvaluation evaluateInternal(FlowRuleRuntimeQuery query, boolean allowDraftFlow) {
        if (query == null || blank(normalize(query.getSourceSystem())) && blank(normalize(query.getOriginSourceSystem()))) {
            return noMatch(List.of());
        }
        String rawTenantId = requireNonBlank(query.getTenantId(), "tenantId").trim();
        String sourceSystem = firstNonBlank(normalize(query.getSourceSystem()), normalize(query.getOriginSourceSystem()));
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantIds", tenantAliases(rawTenantId))
                .addValue("sourceSystem", sourceSystem)
                .addValue("hasFlowId", !blank(query.getFlowId()))
                .addValue("flowId", firstNonBlank(query.getFlowId(), "__ANY_FLOW_ID__"))
                .addValue("allowDraftFlow", allowDraftFlow);

        List<FlowRuleRuntimeMatch> rules = jdbc.query(CANDIDATE_RULE_SQL, params, MATCH_ROW_MAPPER);
        Map<String,List<RegisteredCriterion>> registeredCriteria = loadRegisteredCriteria(rawTenantId, rules);
        List<FlowRuleEvaluationCandidate> evaluated = rules.stream()
                .map(rule -> evaluateRule(rule, query, registeredCriteria.getOrDefault(rule.getRuleId(), List.of())))
                .sorted(Comparator.comparingInt(FlowRuleEvaluationCandidate::priority)
                        .thenComparing(c -> safe(c.rule() == null ? null : c.rule().getRuleId())))
                .toList();

        List<FlowRuleEvaluationCandidate> matches = evaluated.stream().filter(FlowRuleEvaluationCandidate::matched).toList();
        if (matches.isEmpty()) {
            List<FlowRuleEvaluationCandidate> closest = evaluated.stream()
                    .sorted(Comparator.comparingDouble(FlowRuleEvaluationCandidate::matchRatio).reversed()
                            .thenComparingInt(FlowRuleEvaluationCandidate::priority)
                            .thenComparing(c -> safe(c.rule() == null ? null : c.rule().getRuleId())))
                    .limit(3)
                    .toList();
            log.warn("a0r3_flow_match_no_match tenantId={} sourceSystem={} eventStage={} eventType={} objectType={} errorCode={} evaluatedRuleCount={} closest={}",
                    rawTenantId, sourceSystem, normalize(query.getEventStage()), normalize(query.getEventType()), normalize(query.getObjectType()), normalize(query.getErrorCode()),
                    evaluated.size(), closest.stream().map(c -> c.rule().getRuleId() + ":" + c.failedCriterion()).toList());
            return new FlowRuleEvaluation(FlowMatchDecision.MatchResult.NO_MATCH, evaluated, List.of(), closest, null, EVALUATOR_VERSION);
        }

        int minimumPriority = matches.stream().mapToInt(FlowRuleEvaluationCandidate::priority).min().orElse(100);
        List<FlowRuleEvaluationCandidate> minimumBucket = matches.stream()
                .filter(candidate -> candidate.priority() == minimumPriority)
                .sorted(Comparator.comparing(c -> safe(c.rule().getRuleId())))
                .toList();
        if (minimumBucket.size() > 1) {
            log.error("a0r3_flow_match_ambiguous tenantId={} sourceSystem={} minimumPriority={} ruleIds={} authorityAction=FAIL_CLOSED",
                    rawTenantId, sourceSystem, minimumPriority, minimumBucket.stream().map(c -> c.rule().getRuleId()).toList());
            return new FlowRuleEvaluation(FlowMatchDecision.MatchResult.AMBIGUOUS, evaluated, minimumBucket, List.of(), minimumPriority, EVALUATOR_VERSION);
        }

        FlowRuleRuntimeMatch winner = minimumBucket.get(0).rule();
        winner.setMatchReason("A0-R3 deterministic Flow Rule matched: minimumPriority=" + minimumPriority
                + ", flowId=" + winner.getFlowId() + ", ruleId=" + winner.getRuleId());
        log.info("a0r3_flow_match_matched tenantId={} sourceSystem={} minimumPriority={} flowId={} ruleId={} evaluatedRuleCount={}",
                rawTenantId, sourceSystem, minimumPriority, winner.getFlowId(), winner.getRuleId(), evaluated.size());
        return new FlowRuleEvaluation(FlowMatchDecision.MatchResult.MATCHED, evaluated, minimumBucket, List.of(), minimumPriority, EVALUATOR_VERSION);
    }

    @Override
    public void recordAuthoritativeDecision(String taskId, long taskVersion, FlowRuleRuntimeQuery query, FlowRuleEvaluation evaluation) {
        if (blank(taskId) || query == null || evaluation == null || blank(query.getTenantId())) return;
        String tenantId = query.getTenantId().trim();
        String stable = tenantId + ":" + taskId;
        String evaluationSetId = "fes-a0r3-" + sha256(stable).substring(0, 40);
        String decisionId = "fmd-a0r3-" + sha256("decision:" + stable).substring(0, 40);
        FlowRuleEvaluationCandidate selected = evaluation.matchResult() == FlowMatchDecision.MatchResult.MATCHED
                && evaluation.minimumPriorityMatches().size() == 1 ? evaluation.minimumPriorityMatches().get(0) : null;
        FlowRuleEvaluationCandidate closest = evaluation.closestRules().isEmpty() ? null : evaluation.closestRules().get(0);
        FlowRuleRuntimeMatch selectedRule = selected == null ? null : selected.rule();
        FlowRuleRuntimeMatch contextRule = selectedRule != null ? selectedRule
                : (!evaluation.minimumPriorityMatches().isEmpty() ? evaluation.minimumPriorityMatches().get(0).rule()
                : (closest == null ? null : closest.rule()));

        List<Map<String,Object>> fullEvaluation = evaluation.evaluatedRules().stream().map(this::candidateEvidence).toList();
        List<Map<String,Object>> closestEvidence = evaluation.closestRules().stream().map(this::candidateEvidence).toList();
        Map<String,Object> input = inputSnapshot(query);
        String fullJson = write(fullEvaluation);
        String closestJson = write(closestEvidence);
        String inputJson = write(input);
        String digest = sha256(inputJson + "\n" + fullJson + "\n" + evaluation.matchResult() + "\n" + evaluation.evaluatorVersion());

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenant", tenantId).addValue("task", taskId).addValue("taskVersion", Math.max(1L, taskVersion))
                .addValue("evaluationSet", evaluationSetId).addValue("decision", decisionId)
                .addValue("flow", contextRule == null ? null : contextRule.getFlowId())
                .addValue("flowVersion", contextRule == null ? null : contextRule.getFlowVersion())
                .addValue("input", inputJson).addValue("evaluated", fullJson).addValue("closest", closestJson)
                .addValue("count", evaluation.evaluatedRules().size()).addValue("result", evaluation.matchResult().name())
                .addValue("minimumPriority", evaluation.minimumMatchedPriority()).addValue("digest", digest)
                .addValue("evaluator", evaluation.evaluatorVersion())
                .addValue("matchedRule", selectedRule == null ? null : selectedRule.getRuleId())
                .addValue("matchedPriority", selected == null ? null : selected.priority())
                .addValue("summaryRules", write(summaryEvidence(evaluation)))
                .addValue("serviceCode", selectedRule == null ? null : selectedRule.getServiceCode())
                .addValue("capabilities", write(selectedRule == null ? List.of() : selectedRule.getRequiredSkills()))
                .addValue("closestRule", closest == null ? null : closest.rule().getRuleId())
                .addValue("closestPriority", closest == null ? null : closest.priority())
                .addValue("closestFailed", closest == null ? null : closest.failedCriterion())
                .addValue("closestRatio", closest == null ? null : closest.matchRatio())
                .addValue("authorityVersion", DECISION_AUTHORITY_VERSION)
                .addValue("now", OffsetDateTime.now());

        // The Flow resolver runs before initial Task INSERT in some legacy creation paths. The
        // INSERT...SELECT WHERE EXISTS contract makes that first attempt a safe no-op; callers
        // invoke this method again immediately after materialization.
        jdbc.update("""
          insert into flow_evaluation_sets(tenant_id,evaluation_set_id,task_id,flow_id,flow_version,input_snapshot_json,evaluated_rules_json,closest_rules_json,evaluated_rule_count,match_result,minimum_matched_priority,evaluation_digest,evaluator_version,created_at)
          select :tenant,:evaluationSet,:task,:flow,:flowVersion,cast(:input as jsonb),cast(:evaluated as jsonb),cast(:closest as jsonb),:count,:result,:minimumPriority,:digest,:evaluator,:now
           where exists(select 1 from tasks where tenant_id=:tenant and task_id=:task)
          on conflict(tenant_id,evaluation_set_id) do nothing
          """, params);
        jdbc.update("""
          insert into flow_match_decisions(tenant_id,decision_id,task_id,task_version,decision_source,flow_id,flow_version,evaluated_rule_count,matched_rule_id,matched_rule_priority,evaluated_rules_json,output_service_code,output_capability_requirements_json,match_result,flow_evaluation_set_ref,flow_evaluation_set_digest,closest_rule_id,closest_rule_priority,closest_rule_failed_criterion,closest_rule_match_ratio,decision_authority_version,decided_at,evaluator_version)
          select :tenant,:decision,:task,:taskVersion,:decisionSource,:flow,:flowVersion,:count,:matchedRule,:matchedPriority,cast(:summaryRules as jsonb),:serviceCode,cast(:capabilities as jsonb),:result,:evaluationSet,:digest,:closestRule,:closestPriority,:closestFailed,:closestRatio,:authorityVersion,:now,:evaluator
           where exists(select 1 from tasks where tenant_id=:tenant and task_id=:task)
          on conflict do nothing
          """, params.addValue("decisionSource", DECISION_SOURCE));
    }

    private FlowRuleEvaluationCandidate evaluateRule(FlowRuleRuntimeMatch rule, FlowRuleRuntimeQuery query, List<RegisteredCriterion> registeredCriteria) {
        List<String> failed = new ArrayList<>();
        int matched = 0;
        int total = 0;
        MatchCount count;

        count = criterion("SOURCE_SYSTEM", rule.getSourceSystem(), firstNonBlank(normalize(query.getSourceSystem()), normalize(query.getOriginSourceSystem())), failed); matched += count.matched(); total += count.total();
        count = criterion("ORIGIN_SOURCE_SYSTEM", rule.getOriginSourceSystem(), normalize(query.getOriginSourceSystem()), failed); matched += count.matched(); total += count.total();
        count = criterion("TARGET_SYSTEM", rule.getTargetSystem(), normalize(query.getTargetSystem()), failed); matched += count.matched(); total += count.total();
        count = criterion("EVENT_STAGE", rule.getEventStage(), firstNonBlank(normalize(query.getEventStage()), "EXTERNAL"), failed); matched += count.matched(); total += count.total();
        count = criterion("EVENT_TYPE", rule.getEventType(), normalize(query.getEventType()), failed); matched += count.matched(); total += count.total();
        count = criterion("OBJECT_TYPE", rule.getObjectType(), normalize(query.getObjectType()), failed); matched += count.matched(); total += count.total();
        count = criterion("ERROR_CODE", rule.getErrorCode(), normalize(query.getErrorCode()), failed); matched += count.matched(); total += count.total();

        // Registered typed criteria are authoritative only when the registry explicitly marks them
        // ACTIVE + authoritative + indexable and permits the configured operator. Raw condition_json
        // is never evaluated. Invalid configured criteria fail the Rule closed rather than disappearing.
        for (RegisteredCriterion criterion : registeredCriteria) {
            if (!criterion.required()) continue; // non-required rows are metadata/advisory only in A0-R3
            total++;
            if (!criterion.valid()) {
                failed.add("REGISTERED_ATTRIBUTE_INVALID:" + criterion.criterionId() + ":" + criterion.invalidReason());
                continue;
            }
            Object actual = registeredAttributeValue(query, criterion.attributeCode(), criterion.sourceField());
            if (matchesRegisteredCriterion(criterion, actual)) {
                matched++;
            } else {
                failed.add("ATTRIBUTE:" + criterion.attributeCode());
            }
        }

        double ratio = total == 0 ? 1d : ((double) matched / (double) total);
        return new FlowRuleEvaluationCandidate(rule, rule.getPriority() == null ? 100 : rule.getPriority(), failed.isEmpty(),
                failed.isEmpty() ? null : failed.get(0), failed, matched, total, ratio);
    }

    private MatchCount criterion(String name, String ruleValue, String inputValue, List<String> failed) {
        String expected = normalize(ruleValue);
        if (blank(expected) || "*".equals(expected)) return new MatchCount(0, 0);
        if (!blank(inputValue) && expected.equals(normalize(inputValue))) return new MatchCount(1, 1);
        failed.add(name);
        return new MatchCount(0, 1);
    }

    private Map<String,List<RegisteredCriterion>> loadRegisteredCriteria(String tenantId, List<FlowRuleRuntimeMatch> rules) {
        if (rules == null || rules.isEmpty()) return Map.of();
        List<String> ruleIds = rules.stream().map(FlowRuleRuntimeMatch::getRuleId).filter(id -> !blank(id)).distinct().toList();
        if (ruleIds.isEmpty()) return Map.of();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantIds", tenantAliases(tenantId))
                .addValue("ruleIds", ruleIds);
        List<RegisteredCriterion> rows = jdbc.query(REGISTERED_CRITERIA_SQL, params, (rs, rowNum) -> {
            String allowedOps = rs.getString("allowed_operators_json");
            String operator = normalizeOperator(rs.getString("operator"));
            boolean registryPresent = rs.getString("registry_attribute_code") != null;
            boolean valid = registryPresent
                    && rs.getBoolean("registry_authoritative")
                    && "ACTIVE".equalsIgnoreCase(rs.getString("registry_status"))
                    && !"NONE".equalsIgnoreCase(rs.getString("index_strategy"))
                    && jsonArrayContains(allowedOps, operator);
            String invalidReason = valid ? null : !registryPresent ? "REGISTRY_MISSING"
                    : !rs.getBoolean("registry_authoritative") ? "REGISTRY_NOT_AUTHORITATIVE"
                    : !"ACTIVE".equalsIgnoreCase(rs.getString("registry_status")) ? "REGISTRY_NOT_ACTIVE"
                    : "NONE".equalsIgnoreCase(rs.getString("index_strategy")) ? "ATTRIBUTE_NOT_INDEXABLE"
                    : "OPERATOR_NOT_ALLOWED";
            return new RegisteredCriterion(rs.getString("rule_id"), rs.getString("criterion_id"), rs.getString("attribute_code"),
                    rs.getString("source_field"), operator, rs.getString("data_type"), parseJsonValue(rs.getString("typed_value_json")),
                    rs.getBoolean("required"), valid, invalidReason);
        });
        Map<String,List<RegisteredCriterion>> grouped = new HashMap<>();
        for (RegisteredCriterion row : rows) grouped.computeIfAbsent(row.ruleId(), ignored -> new ArrayList<>()).add(row);
        grouped.replaceAll((key,value) -> value.stream().sorted(Comparator.comparing(RegisteredCriterion::criterionId)).toList());
        return Map.copyOf(grouped);
    }

    private Object registeredAttributeValue(FlowRuleRuntimeQuery query, String attributeCode, String sourceField) {
        String code = attributeCode == null ? "" : attributeCode.trim().toUpperCase(Locale.ROOT);
        Object core = switch (code) {
            case "SOURCE_SYSTEM" -> firstNonBlank(normalize(query.getSourceSystem()), normalize(query.getOriginSourceSystem()));
            case "ORIGIN_SOURCE_SYSTEM" -> normalize(query.getOriginSourceSystem());
            case "TARGET_SYSTEM" -> normalize(query.getTargetSystem());
            case "EVENT_STAGE" -> firstNonBlank(normalize(query.getEventStage()), "EXTERNAL");
            case "EVENT_TYPE" -> normalize(query.getEventType());
            case "OBJECT_TYPE" -> normalize(query.getObjectType());
            case "ERROR_CODE" -> normalize(query.getErrorCode());
            case "SEVERITY" -> normalize(query.getSeverity());
            default -> null;
        };
        if (core != null) return core;
        Map<String,Object> attrs = query.getMatchAttributes();
        if (attrs == null || attrs.isEmpty()) return null;
        for (Map.Entry<String,Object> entry : attrs.entrySet()) {
            if (entry.getKey() == null) continue;
            if (entry.getKey().equalsIgnoreCase(attributeCode) || (!blank(sourceField) && entry.getKey().equalsIgnoreCase(sourceField))) {
                return entry.getValue();
            }
        }
        return null;
    }

    private boolean matchesRegisteredCriterion(RegisteredCriterion criterion, Object actual) {
        String operator = criterion.operator();
        if ("EXISTS".equals(operator)) return actual != null && (!(actual instanceof String s) || !s.isBlank());
        if (actual == null) return false;
        Object expected = criterion.expectedValue();
        if ("IN".equals(operator)) {
            if (!(expected instanceof List<?> values)) return false;
            return values.stream().anyMatch(value -> compareTyped(actual, value, criterion.dataType()) == 0);
        }
        Integer comparison = compareTyped(actual, expected, criterion.dataType());
        if (comparison == null) return false;
        return switch (operator) {
            case "EQ" -> comparison == 0;
            case "GT" -> comparison > 0;
            case "GTE" -> comparison >= 0;
            case "LT" -> comparison < 0;
            case "LTE" -> comparison <= 0;
            default -> false;
        };
    }

    private Integer compareTyped(Object actual, Object expected, String dataType) {
        try {
            return switch (dataType == null ? "STRING" : dataType.toUpperCase(Locale.ROOT)) {
                case "INTEGER", "DECIMAL" -> decimal(actual).compareTo(decimal(expected));
                case "BOOLEAN" -> Boolean.compare(booleanValue(actual), booleanValue(expected));
                case "TIMESTAMP" -> instant(actual).compareTo(instant(expected));
                default -> canonicalString(actual).compareTo(canonicalString(expected));
            };
        } catch (RuntimeException ex) {
            return null; // malformed typed input deterministically does not match
        }
    }

    private BigDecimal decimal(Object value) {
        if (value instanceof Number number) return new BigDecimal(number.toString());
        return new BigDecimal(String.valueOf(value).trim());
    }
    private boolean booleanValue(Object value) {
        if (value instanceof Boolean b) return b;
        String normalized = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        if ("true".equals(normalized)) return true;
        if ("false".equals(normalized)) return false;
        throw new IllegalArgumentException("not boolean");
    }
    private Instant instant(Object value) {
        if (value instanceof Instant i) return i;
        if (value instanceof OffsetDateTime o) return o.toInstant();
        return OffsetDateTime.parse(String.valueOf(value).trim()).toInstant();
    }
    private String canonicalString(Object value) { return value == null ? "" : String.valueOf(value).trim().toUpperCase(Locale.ROOT); }
    private String normalizeOperator(String value) { return value == null ? "" : value.trim().toUpperCase(Locale.ROOT); }
    private boolean jsonArrayContains(String raw, String value) {
        try {
            if (raw == null || value == null) return false;
            Object parsed = json.readValue(raw, Object.class);
            return parsed instanceof List<?> list && list.stream().anyMatch(item -> value.equalsIgnoreCase(String.valueOf(item)));
        } catch (Exception ex) { return false; }
    }
    private Object parseJsonValue(String raw) {
        try { return raw == null ? null : json.readValue(raw, Object.class); }
        catch (Exception ex) { return null; }
    }

    private FlowRuleEvaluation noMatch(List<FlowRuleEvaluationCandidate> candidates) {
        return new FlowRuleEvaluation(FlowMatchDecision.MatchResult.NO_MATCH, candidates, List.of(), List.of(), null, EVALUATOR_VERSION);
    }

    private Map<String,Object> candidateEvidence(FlowRuleEvaluationCandidate candidate) {
        FlowRuleRuntimeMatch rule = candidate.rule();
        Map<String,Object> value = new LinkedHashMap<>();
        value.put("flowId", rule == null ? null : rule.getFlowId());
        value.put("flowVersion", rule == null ? null : rule.getFlowVersion());
        value.put("ruleId", rule == null ? null : rule.getRuleId());
        value.put("ruleCode", rule == null ? null : rule.getRuleCode());
        value.put("priority", candidate.priority());
        value.put("matched", candidate.matched());
        value.put("failedCriterion", candidate.failedCriterion());
        value.put("failedCriteria", candidate.failedCriteria());
        value.put("matchedCriteriaCount", candidate.matchedCriteriaCount());
        value.put("totalCriteriaCount", candidate.totalCriteriaCount());
        value.put("matchRatio", candidate.matchRatio());
        return value;
    }

    private List<Map<String,Object>> summaryEvidence(FlowRuleEvaluation evaluation) {
        List<FlowRuleEvaluationCandidate> source = switch (evaluation.matchResult()) {
            case MATCHED, AMBIGUOUS -> evaluation.minimumPriorityMatches();
            case NO_MATCH -> evaluation.closestRules();
        };
        return source.stream().limit(20).map(this::candidateEvidence).toList();
    }

    private Map<String,Object> inputSnapshot(FlowRuleRuntimeQuery query) {
        Map<String,Object> input = new LinkedHashMap<>();
        input.put("sourceSystem", normalize(query.getSourceSystem()));
        input.put("originSourceSystem", normalize(query.getOriginSourceSystem()));
        input.put("targetSystem", normalize(query.getTargetSystem()));
        input.put("eventStage", firstNonBlank(normalize(query.getEventStage()), "EXTERNAL"));
        input.put("eventType", normalize(query.getEventType()));
        input.put("objectType", normalize(query.getObjectType()));
        input.put("errorCode", normalize(query.getErrorCode()));
        input.put("severity", normalize(query.getSeverity()));
        input.put("registeredAttributes", query.getMatchAttributes());
        return input;
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception ex) { throw new IllegalStateException("FLOW_MATCH_EVIDENCE_SERIALIZATION_FAILED", ex); }
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) { throw new IllegalStateException("SHA256_UNAVAILABLE", ex); }
    }

    private static final String REGISTERED_CRITERIA_SQL = """
      select c.rule_id,c.criterion_id,c.attribute_code,c.operator,c.typed_value_json::text,c.required,
             r.attribute_code as registry_attribute_code,r.data_type,r.allowed_operators_json::text,
             r.source_field,r.index_strategy,r.authoritative as registry_authoritative,r.status as registry_status
        from flow_rule_attribute_criteria c
        left join flow_match_attribute_registry r on r.attribute_code=c.attribute_code
       where upper(c.tenant_id) in (:tenantIds)
         and c.rule_id in (:ruleIds)
         and upper(coalesce(c.status,'ACTIVE'))='ACTIVE'
       order by c.rule_id,c.criterion_id
      """;

    private static final String CANDIDATE_RULE_SQL = """
      with source_flows as (
        select f.tenant_id,f.flow_id,f.flow_code,coalesce(f.version,1) as flow_version,
               f.source_system,f.default_pool_id,dp.pool_code as default_pool_code,
               coalesce(dp.selection_strategy,'LOWEST_LOAD') as default_selection_strategy,
               f.default_routing_strategy,f.issue_sync_policy,
               exists (select 1 from dispatch_issue_policy_write_audits a
                        where a.tenant_id=f.tenant_id and a.flow_id=f.flow_id and a.resource_type='FLOW'
                          and a.operation in ('CREATE','UPDATE')) as issue_policy_explicitly_managed
          from dispatch_flows f
          left join agent_pools dp on dp.tenant_id=f.tenant_id and dp.pool_id=f.default_pool_id
         where upper(f.tenant_id) in (:tenantIds)
           and (:allowDraftFlow=true or upper(coalesce(f.status,'DRAFT')) in ('ACTIVE','ENABLED'))
           and upper(coalesce(f.flow_type,'SOURCE_FLOW'))='SOURCE_FLOW'
           and (:hasFlowId=false or f.flow_id=:flowId)
           and upper(f.source_system) in (:sourceSystem,'*')
      )
      select p.tenant_id,p.flow_id,f.flow_code,f.flow_version::text as flow_version,
             p.policy_id as rule_id,p.policy_code as rule_code,coalesce(p.priority,100) as priority,
             p.service_code,
             coalesce(nullif(upper(p.rule_scope),''),case upper(coalesce(nullif(p.event_stage,''),'EXTERNAL')) when 'A2A' then 'A2A_DISPATCH' when 'RESULT' then 'RESULT_CALLBACK' when 'ISSUE' then 'ISSUE_TRACKING' else 'EXTERNAL_INTAKE' end) as rule_scope,
             upper(coalesce(nullif(p.event_stage,''),'EXTERNAL')) as event_stage,
             upper(coalesce(nullif(p.source_system,''),nullif(p.origin_source_system,''),f.source_system)) as source_system,
             upper(p.origin_source_system) as origin_source_system,upper(p.target_system) as target_system,
             upper(p.event_type) as event_type,upper(p.object_type) as object_type,upper(p.error_code) as error_code,
             upper(p.requested_skill) as policy_requested_skill,upper(p.handoff_mode) as handoff_mode,
             upper(coalesce(p.capability_requirement_mode,'NONE')) as capability_requirement_mode,
             upper(p.required_operation) as required_operation,upper(coalesce(p.side_effect_level,'NONE')) as side_effect_level,
             upper(coalesce(p.candidate_pool_mode,'EXPLICIT_FLOW_AGENTS')) as candidate_pool_mode,
             upper(coalesce(p.routing_strategy,f.default_routing_strategy,'WEIGHTED_SCORE')) as routing_strategy,
             coalesce(p.explicit_action_authorization_required,false) as explicit_action_authorization_required,
             coalesce(p.requirement_model_version,1) as requirement_model_version,
             upper(nullif(p.issue_sync_policy,'')) as rule_issue_sync_policy,
             upper(nullif(f.issue_sync_policy,'')) as flow_issue_sync_policy,
             case
               when nullif(p.issue_sync_policy,'') is not null then 'RULE_OVERRIDE'
               when nullif(f.issue_sync_policy,'') is not null then 'FLOW_DEFAULT'
               else 'SYSTEM_FALLBACK'
             end as issue_sync_policy_source,
             upper(coalesce(nullif(p.issue_sync_policy,''),nullif(f.issue_sync_policy,''),'OPTIONAL')) as issue_sync_policy,
             f.issue_policy_explicitly_managed as flow_issue_policy_explicitly_managed,
             coalesce(string_agg(distinct upper(frc.skill_code),',') filter(where frc.skill_code is not null and btrim(frc.skill_code)<>''),'') as required_skills,
             coalesce(p.target_pool_id,f.default_pool_id) as target_pool_id,
             coalesce(p.target_pool_code,tp.pool_code,f.default_pool_code) as target_pool_code,
             f.default_pool_id,coalesce(tp.selection_strategy,f.default_selection_strategy,'LOWEST_LOAD') as selection_strategy,
             false as source_default_pool
        from dispatch_policies p
        join source_flows f on f.tenant_id=p.tenant_id and f.flow_id=p.flow_id
        left join agent_pools tp on tp.tenant_id=p.tenant_id and tp.pool_id=coalesce(p.target_pool_id,f.default_pool_id)
        left join flow_required_capabilities frc on frc.tenant_id=p.tenant_id and frc.flow_id=p.flow_id
             and (frc.rule_id=p.policy_id or frc.rule_id is null) and coalesce(frc.required,true)=true
       where upper(p.tenant_id) in (:tenantIds)
         and p.flow_id is not null
         and upper(coalesce(p.status,'DRAFT')) in ('ACTIVE','ENABLED')
       group by p.tenant_id,p.flow_id,f.flow_code,f.flow_version,p.policy_id,p.policy_code,p.priority,p.service_code,p.rule_scope,p.event_stage,
                p.source_system,p.origin_source_system,f.source_system,p.target_system,p.event_type,p.object_type,p.error_code,p.requested_skill,
                p.handoff_mode,p.capability_requirement_mode,p.required_operation,p.side_effect_level,p.candidate_pool_mode,p.routing_strategy,
                f.default_routing_strategy,p.explicit_action_authorization_required,p.requirement_model_version,p.issue_sync_policy,f.issue_sync_policy,f.issue_policy_explicitly_managed,p.target_pool_id,p.target_pool_code,
                tp.pool_code,tp.selection_strategy,f.default_pool_id,f.default_pool_code,f.default_selection_strategy
       order by p.priority asc,p.policy_id asc
      """;

    private static final RowMapper<FlowRuleRuntimeMatch> MATCH_ROW_MAPPER = new RowMapper<>() {
        @Override public FlowRuleRuntimeMatch mapRow(ResultSet rs, int rowNum) throws SQLException {
            FlowRuleRuntimeMatch match = new FlowRuleRuntimeMatch();
            match.setTenantId(rs.getString("tenant_id")); match.setFlowId(rs.getString("flow_id")); match.setFlowCode(rs.getString("flow_code"));
            match.setFlowVersion(rs.getString("flow_version")); match.setRuleId(rs.getString("rule_id")); match.setRuleCode(rs.getString("rule_code"));
            match.setPriority(rs.getInt("priority")); match.setServiceCode(rs.getString("service_code")); match.setRuleScope(rs.getString("rule_scope"));
            match.setEventStage(rs.getString("event_stage")); match.setSourceSystem(rs.getString("source_system")); match.setOriginSourceSystem(rs.getString("origin_source_system"));
            match.setTargetSystem(rs.getString("target_system")); match.setEventType(rs.getString("event_type")); match.setObjectType(rs.getString("object_type")); match.setErrorCode(rs.getString("error_code"));
            List<String> skills = splitSkills(rs.getString("required_skills")); match.setRequiredSkills(skills);
            match.setRequestedSkill(firstNonBlankStatic(rs.getString("policy_requested_skill"), skills.isEmpty() ? null : skills.get(0)));
            match.setHandoffMode(rs.getString("handoff_mode")); match.setCapabilityRequirementMode(rs.getString("capability_requirement_mode")); match.setRequiredOperation(rs.getString("required_operation"));
            match.setSideEffectLevel(rs.getString("side_effect_level")); match.setCandidatePoolMode(rs.getString("candidate_pool_mode")); match.setRoutingStrategy(rs.getString("routing_strategy"));
            match.setTargetPoolId(rs.getString("target_pool_id")); match.setTargetPoolCode(rs.getString("target_pool_code")); match.setDefaultPoolId(rs.getString("default_pool_id"));
            match.setSelectionStrategy(rs.getString("selection_strategy")); match.setSourceDefaultPool(false); match.setExplicitActionAuthorizationRequired(rs.getBoolean("explicit_action_authorization_required"));
            match.setRequirementModelVersion(rs.getInt("requirement_model_version"));
            match.setRuleIssueSyncPolicy(rs.getString("rule_issue_sync_policy"));
            match.setFlowIssueSyncPolicy(rs.getString("flow_issue_sync_policy"));
            match.setIssueSyncPolicySource(rs.getString("issue_sync_policy_source"));
            match.setIssueSyncPolicy(rs.getString("issue_sync_policy"));
            match.setFlowIssuePolicyExplicitlyManaged(rs.getBoolean("flow_issue_policy_explicitly_managed"));
            return match;
        }
    };

    private static List<String> tenantAliases(String rawTenantId) {
        if (blank(rawTenantId)) return List.of();
        return Arrays.stream(new String[]{rawTenantId,rawTenantId.toUpperCase(Locale.ROOT),rawTenantId.toLowerCase(Locale.ROOT)})
                .map(value -> value == null ? null : value.trim().toUpperCase(Locale.ROOT)).filter(value -> !blank(value)).distinct().toList();
    }
    private static List<String> splitSkills(String raw) {
        if (blank(raw)) return List.of();
        return Arrays.stream(raw.split(",")).map(JdbcFlowRuleRoutingRepository::normalize).filter(value -> !blank(value)).distinct().toList();
    }
    private static String firstNonBlankStatic(String... values) { if (values != null) for (String value:values) if (!blank(value)) return value; return null; }
    private static String firstNonBlank(String... values) { return firstNonBlankStatic(values); }
    private static String requireNonBlank(String value,String field) { if (blank(value)) throw new IllegalArgumentException(field+" is required"); return value; }
    private static String normalize(String value) {
        if (blank(value)) return null;
        String normalized=value.trim().replace('-','_').replace('.','_').replace(' ','_').toUpperCase(Locale.ROOT);
        return normalized.startsWith("NO_") ? null : normalized;
    }
    private static String safe(String value) { return value == null ? "" : value; }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private record MatchCount(int matched,int total) {}
    private record RegisteredCriterion(String ruleId,String criterionId,String attributeCode,String sourceField,String operator,String dataType,Object expectedValue,boolean required,boolean valid,String invalidReason) {}
}
