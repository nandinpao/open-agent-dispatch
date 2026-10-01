package com.opensocket.aievent.core.action;

import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationAuthorityRegistry;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSetKeys;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSnapshotValues;

/** Active Adapter Action policy values that have canonical execution consumers. */
@Component
public final class AdapterActionPolicyRuntimeConfigurationView {
    public static final String SET_KEY=RuntimeConfigurationSetKeys.ADAPTER_ACTION_SYSTEM;
    public static final String CREATE_SUPPRESSED_RECORDS="adapter-actions.create-suppressed-records";
    public static final String ISSUE_ADAPTER_NAME="adapter-actions.issue.adapter-name";
    public static final Set<String> ALL=Set.of(CREATE_SUPPRESSED_RECORDS,ISSUE_ADAPTER_NAME);
    private final AdapterActionProperties startup;private final RuntimeConfigurationSnapshotValues values;private final RuntimeConfigurationAuthorityRegistry authority;
    @Autowired public AdapterActionPolicyRuntimeConfigurationView(AdapterActionProperties startup,RuntimeConfigurationSnapshotValues values,RuntimeConfigurationAuthorityRegistry authority){this.startup=startup;this.values=values;this.authority=authority;}
    AdapterActionPolicyRuntimeConfigurationView(AdapterActionProperties startup,RuntimeConfigurationSnapshotValues values){this(startup,values,new RuntimeConfigurationAuthorityRegistry());}
    public boolean createSuppressedRecords(){return booleanValue(CREATE_SUPPRESSED_RECORDS,startup.isCreateSuppressedRecords());}
    public String issueAdapterName(){String value=textValue(ISSUE_ADAPTER_NAME,startup.getIssue().getAdapterName()).trim();if(value.isBlank()||value.length()>128)throw invalid(ISSUE_ADAPTER_NAME);return value;}
    private boolean booleanValue(String key,boolean fallback){if(runtimeRequired(key)){requireKey(key);return values.booleanValue(SET_KEY,key).orElseThrow(()->incomplete(key));}return values==null?fallback:values.booleanValue(SET_KEY,key).orElse(fallback);}
    private String textValue(String key,String fallback){if(runtimeRequired(key)){requireKey(key);return values.textValue(SET_KEY,key).orElseThrow(()->incomplete(key));}return values==null?fallback:values.textValue(SET_KEY,key).orElse(fallback);}
    private boolean runtimeRequired(String key){return authority!=null&&authority.isRuntimeAuthoritative(key);}
    private void requireKey(String key){if(values==null||!values.hasSnapshot(SET_KEY))throw incomplete(key+": snapshot missing");if(!values.keys(SET_KEY).contains(key))throw incomplete(key+": key missing");}
    private static IllegalStateException invalid(String key){return new IllegalStateException("RUNTIME_CONFIG_VALIDATION_FAILED "+key);}
    private static IllegalStateException incomplete(String detail){return new IllegalStateException("CONFIGURATION_INCOMPLETE setKey="+SET_KEY+" detail="+detail);}
}
