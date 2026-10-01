package com.opensocket.aievent.core.fingerprint;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;

import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationAuthorityRegistry;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSetKeys;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSnapshotValues;

/** V41-C3R2M typed runtime view for event fingerprint policy and masking. */
@Component
public final class FingerprintRuntimeConfigurationView {
    public static final String SET_KEY=RuntimeConfigurationSetKeys.CORE_SYSTEM;
    public static final String ENABLED="core.fingerprint.enabled";
    public static final String POLICY_VERSION="core.fingerprint.policy-version";
    public static final String DEFAULT_FIELDS="core.fingerprint.default-fields";
    public static final String MASKING_ENABLED="core.fingerprint.masking.enabled";
    public static final String MASKING_RULES="core.fingerprint.masking.rules";
    public static final String POLICIES="core.fingerprint.policies";
    public static final Set<String> ALL=Set.of(ENABLED,POLICY_VERSION,DEFAULT_FIELDS,MASKING_ENABLED,MASKING_RULES,POLICIES);

    private final RuntimeConfigurationSnapshotValues values; private final RuntimeConfigurationAuthorityRegistry authority; private final FingerprintPolicyProperties startup;
    @Autowired public FingerprintRuntimeConfigurationView(RuntimeConfigurationSnapshotValues values,RuntimeConfigurationAuthorityRegistry authority,FingerprintPolicyProperties startup){this.values=values;this.authority=authority;this.startup=startup;}
    public FingerprintRuntimeConfigurationView(FingerprintPolicyProperties startup){this(null,null,startup==null?new FingerprintPolicyProperties():startup);}
    public boolean runtimeBacked(){return values!=null&&values.hasSnapshot(SET_KEY);} public String snapshotVersionToken(){if(values==null)return "STARTUP";return values.revisionId(SET_KEY).orElse("NONE")+":"+values.payloadHash(SET_KEY).orElse("NONE");}
    public boolean enabled(){return booleanValue(ENABLED,startup.isEnabled());}
    public String policyVersion(){String v=textValue(POLICY_VERSION,startup.getPolicyVersion());v=v==null?"":v.trim();if(!v.matches("[A-Za-z0-9._:-]{1,64}"))throw invalid(POLICY_VERSION);return v;}
    public boolean maskingEnabled(){return booleanValue(MASKING_ENABLED,startup.getMasking()!=null&&startup.getMasking().isEnabled());}
    public String replacementToken(){FingerprintPolicyProperties.MessageMasking m=startup.getMasking();return m==null||m.getReplacementToken()==null?"<var>":m.getReplacementToken();}
    public List<String> defaultFields(){if(!useRuntime(DEFAULT_FIELDS))return validateFields(startup.getDefaultFields(),DEFAULT_FIELDS);return parseStringArray(requiredJson(DEFAULT_FIELDS),DEFAULT_FIELDS,1,64);}
    public List<FingerprintPolicyProperties.MaskRule> maskingRules(){if(!useRuntime(MASKING_RULES)){FingerprintPolicyProperties.MessageMasking m=startup.getMasking();return validateRules(m==null?List.of():m.getRules());}JsonNode n=requiredJson(MASKING_RULES);if(!n.isArray())throw invalid(MASKING_RULES);List<FingerprintPolicyProperties.MaskRule> out=new ArrayList<>();for(JsonNode x:n){if(!x.isObject())throw invalid(MASKING_RULES);String name=text(x,"name");String regex=text(x,"regex");String repl=text(x,"replacement");if(name.isBlank()||regex.isBlank())throw invalid(MASKING_RULES);try{Pattern.compile(regex);}catch(PatternSyntaxException ex){throw invalid(MASKING_RULES);}out.add(new FingerprintPolicyProperties.MaskRule(name,regex,repl));if(out.size()>128)throw invalid(MASKING_RULES);}return List.copyOf(out);}
    public List<FingerprintPolicyProperties.Policy> policies(){if(!useRuntime(POLICIES))return validatePolicies(startup.getPolicies());JsonNode n=requiredJson(POLICIES);if(!n.isArray())throw invalid(POLICIES);List<FingerprintPolicyProperties.Policy> out=new ArrayList<>();for(JsonNode x:n){if(!x.isObject())throw invalid(POLICIES);FingerprintPolicyProperties.Policy p=new FingerprintPolicyProperties.Policy();p.setName(text(x,"name"));p.setSourceSystems(array(x,"sourceSystems"));p.setEventTypes(array(x,"eventTypes"));p.setObjectTypes(array(x,"objectTypes"));p.setErrorCodes(array(x,"errorCodes"));p.setFields(array(x,"fields"));if(p.getName()==null||p.getName().isBlank())throw invalid(POLICIES);validateFields(p.getFields(),POLICIES);out.add(p);if(out.size()>128)throw invalid(POLICIES);}return List.copyOf(out);}
    public List<Map<String,Object>> maskingRulesProjection(){List<Map<String,Object>> out=new ArrayList<>();for(var r:maskingRules()){Map<String,Object> m=new LinkedHashMap<>();m.put("name",r.getName());m.put("regex",r.getRegex());m.put("replacement",r.getReplacement());out.add(m);}return List.copyOf(out);}
    public List<Map<String,Object>> policiesProjection(){List<Map<String,Object>> out=new ArrayList<>();for(var p:policies()){Map<String,Object> m=new LinkedHashMap<>();m.put("name",p.getName());m.put("sourceSystems",p.getSourceSystems());m.put("eventTypes",p.getEventTypes());m.put("objectTypes",p.getObjectTypes());m.put("errorCodes",p.getErrorCodes());m.put("fields",p.getFields());out.add(m);}return List.copyOf(out);}
    private boolean useRuntime(String key){if(values==null)return false;if(required(key)){requireAll(key);return true;}return values.jsonValue(SET_KEY,key).isPresent();}
    private JsonNode requiredJson(String key){if(values==null)throw incomplete(key);if(required(key))requireAll(key);return values.jsonValue(SET_KEY,key).orElseThrow(()->incomplete(key));}
    private List<String> parseStringArray(JsonNode n,String key,int min,int max){if(!n.isArray())throw invalid(key);List<String> out=new ArrayList<>();for(JsonNode x:n){if(!x.isTextual())throw invalid(key);String v=x.textValue().trim();if(v.isBlank())throw invalid(key);out.add(v);if(out.size()>max)throw invalid(key);}if(out.size()<min)throw invalid(key);return validateFields(out,key);}
    private List<String> validateFields(List<String> in,String key){if(in==null||in.isEmpty())throw invalid(key);List<String> out=new ArrayList<>();for(String v:in){String n=v==null?"":v.trim();if(!n.matches("[A-Za-z][A-Za-z0-9._:-]{0,63}"))throw invalid(key);out.add(n);}return List.copyOf(out);}
    private List<FingerprintPolicyProperties.MaskRule> validateRules(List<FingerprintPolicyProperties.MaskRule> in){if(in==null)return List.of();for(var r:in){if(r==null||r.getRegex()==null||r.getRegex().isBlank())throw invalid(MASKING_RULES);try{Pattern.compile(r.getRegex());}catch(PatternSyntaxException ex){throw invalid(MASKING_RULES);}}return List.copyOf(in);}
    private List<FingerprintPolicyProperties.Policy> validatePolicies(List<FingerprintPolicyProperties.Policy> in){if(in==null)return List.of();for(var p:in){if(p==null||p.getName()==null||p.getName().isBlank())throw invalid(POLICIES);if(p.getFields()!=null&&!p.getFields().isEmpty())validateFields(p.getFields(),POLICIES);}return List.copyOf(in);}
    private String text(JsonNode n,String field){JsonNode v=n.get(field);return v==null||v.isNull()?"":v.asText("");}
    private List<String> array(JsonNode n,String field){JsonNode v=n.get(field);if(v==null||v.isNull())return List.of();if(!v.isArray())throw invalid(POLICIES);List<String> out=new ArrayList<>();for(JsonNode x:v){if(!x.isTextual())throw invalid(POLICIES);out.add(x.textValue());}return List.copyOf(out);}
    private boolean required(String key){return authority!=null&&authority.isRuntimeAuthoritative(key);}private void requireAll(String key){if(values==null||!values.hasSnapshot(SET_KEY))throw incomplete(key);values.requireKeys(SET_KEY,ALL);}private boolean booleanValue(String key,boolean fb){if(values==null)return fb;if(required(key)){requireAll(key);return values.booleanValue(SET_KEY,key).orElseThrow(()->incomplete(key));}return values.booleanValue(SET_KEY,key).orElse(fb);}private String textValue(String key,String fb){if(values==null)return fb;if(required(key)){requireAll(key);return values.textValue(SET_KEY,key).orElseThrow(()->incomplete(key));}return values.textValue(SET_KEY,key).orElse(fb);}private static IllegalStateException invalid(String k){return new IllegalStateException("RUNTIME_CONFIG_VALIDATION_FAILED key="+k);}private static IllegalStateException incomplete(String k){return new IllegalStateException("CONFIGURATION_INCOMPLETE setKey="+SET_KEY+" key="+k);}
}
