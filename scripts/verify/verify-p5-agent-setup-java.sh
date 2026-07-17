#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
SRC=$TMP/src; OUT=$TMP/out; mkdir -p "$SRC" "$OUT"
w(){ mkdir -p "$(dirname "$SRC/$1")"; cat > "$SRC/$1"; }
w org/springframework/stereotype/Service.java <<'EOF'
package org.springframework.stereotype; public @interface Service {}
EOF
w org/springframework/beans/factory/annotation/Autowired.java <<'EOF'
package org.springframework.beans.factory.annotation; public @interface Autowired {}
EOF
w org/springframework/transaction/annotation/Transactional.java <<'EOF'
package org.springframework.transaction.annotation; public @interface Transactional {}
EOF
w com/opensocket/aievent/core/agent/AgentStatus.java <<'EOF'
package com.opensocket.aievent.core.agent; public enum AgentStatus { ONLINE, OFFLINE, EXPIRED, ERROR, DRAINING }
EOF
w com/opensocket/aievent/core/agent/AgentRuntimeCapabilityItem.java <<'EOF'
package com.opensocket.aievent.core.agent; public record AgentRuntimeCapabilityItem(String capabilityValue) {}
EOF
w com/opensocket/aievent/core/agent/AgentSnapshot.java <<'EOF'
package com.opensocket.aievent.core.agent; import java.util.*; public class AgentSnapshot { public AgentStatus getStatus(){return null;} public Object getLastHeartbeatAt(){return null;} public Object getConnectedAt(){return null;} public int getAvailableSlots(){return 0;} public boolean isAssignable(){return false;} public String getAgentId(){return null;} public List<String> getCapabilities(){return List.of();} }
EOF
w com/opensocket/aievent/core/agent/AgentDirectoryService.java <<'EOF'
package com.opensocket.aievent.core.agent; import java.util.*; public class AgentDirectoryService { public Optional<AgentSnapshot> findAgent(String a){return Optional.empty();} public List<AgentRuntimeCapabilityItem> findRuntimeCapabilityItems(String a){return List.of();} }
EOF
w com/opensocket/aievent/core/agent/assignment/AgentCapabilityAssignmentStatus.java <<'EOF'
package com.opensocket.aievent.core.agent.assignment; public enum AgentCapabilityAssignmentStatus { APPROVED, PENDING }
EOF
w com/opensocket/aievent/core/agent/assignment/AgentCapabilityAssignment.java <<'EOF'
package com.opensocket.aievent.core.agent.assignment; public class AgentCapabilityAssignment { public AgentCapabilityAssignmentStatus getStatus(){return null;} public String getCapabilityCode(){return null;} }
EOF
w com/opensocket/aievent/core/agent/assignment/AgentRuntimeBinding.java <<'EOF'
package com.opensocket.aievent.core.agent.assignment; import java.time.*; import java.util.*; public class AgentRuntimeBinding { public String getBindingId(){return null;} public String getBindingStatus(){return null;} public String getRuntimeCode(){return null;} public String getRuntimeId(){return null;} public void setTenantId(String v){} public void setAgentId(String v){} public void setRuntimeId(String v){} public void setRuntimeCode(String v){} public void setBindingStatus(String v){} public void setVerifiedBy(String v){} public void setVerifiedAt(OffsetDateTime v){} public void setApprovedBy(String v){} public void setApprovedAt(OffsetDateTime v){} public void setCapacityLimit(int v){} public void setDataScope(String v){} public void setRiskLimit(String v){} public void setMetadata(Map<String,Object> v){} }
EOF
w com/opensocket/aievent/core/agent/assignment/RuntimeResource.java <<'EOF'
package com.opensocket.aievent.core.agent.assignment; import java.util.*; public class RuntimeResource { public String getRuntimeId(){return null;} public String getRuntimeCode(){return null;} public String getRuntimeType(){return null;} public String getExecutionHost(){return null;} public Map<String,Object> getMetadata(){return Map.of();} public void setTenantId(String v){} public void setRuntimeId(String v){} public void setRuntimeCode(String v){} public void setRuntimeName(String v){} public void setRuntimeType(String v){} public void setConnectorType(String v){} public void setExecutionHost(String v){} public void setEnvironment(String v){} public void setTrustStatus(String v){} public void setStatus(String v){} public void setCapacityLimit(int v){} public void setMetadata(Map<String,Object> v){} }
EOF
w com/opensocket/aievent/core/agent/assignment/SupplyProfile.java <<'EOF'
package com.opensocket.aievent.core.agent.assignment; import java.util.*; public class SupplyProfile { public String getStatus(){return null;} public void setTenantId(String v){} public void setProfileCode(String v){} public void setProfileName(String v){} public void setAgentId(String v){} public void setRuntimeBindingId(String v){} public void setRuntimeId(String v){} public void setServiceRole(String v){} public void setServiceLevel(String v){} public void setQualityGrade(String v){} public void setRiskLimit(String v){} public void setDataScope(String v){} public void setCapacityPolicy(String v){} public void setStatus(String v){} public void setCapabilitySnapshot(List<String> v){} public void setMetadata(Map<String,Object> v){} }
EOF
w com/opensocket/aievent/core/agent/assignment/DispatchPolicy.java <<'EOF'
package com.opensocket.aievent.core.agent.assignment; public class DispatchPolicy { public String getStatus(){return null;} }
EOF
w com/opensocket/aievent/core/agent/assignment/AgentAssignmentService.java <<'EOF'
package com.opensocket.aievent.core.agent.assignment; import java.util.*; public class AgentAssignmentService { public List<AgentCapabilityAssignment> findAgentCapabilities(String a){return List.of();} public List<AgentRuntimeBinding> findRuntimeBindingsByAgent(String a,String s){return List.of();} public List<SupplyProfile> findSupplyProfilesByAgent(String t,String a,String s){return List.of();} public List<DispatchPolicy> searchDispatchPolicies(String t,String s,int l){return List.of();} public RuntimeResource upsertRuntimeResource(RuntimeResource r){return r;} public AgentRuntimeBinding upsertRuntimeBinding(String a,AgentRuntimeBinding b){return b;} public SupplyProfile upsertSupplyProfile(SupplyProfile p){return p;} public RuntimeResource getRuntimeResource(String t,String r){return null;} public List<RuntimeResource> searchRuntimeResources(String t,String a,String b,int l){return List.of();} }
EOF
w com/opensocket/aievent/core/agent/governance/AgentAuthorizationScope.java <<'EOF'
package com.opensocket.aievent.core.agent.governance; public class AgentAuthorizationScope { public void setTenantId(String v){} public void setSystemCode(String v){} public void setTaskType(String v){} public void setEnabled(boolean v){} }
EOF
w com/opensocket/aievent/core/agent/governance/AgentEnrollmentRequest.java <<'EOF'
package com.opensocket.aievent.core.agent.governance; import java.time.*; import java.util.*; public class AgentEnrollmentRequest { public String getEnrollmentId(){return "e";} public void setClaimedAgentId(String v){} public void setTenantId(String v){} public void setAgentName(String v){} public void setAgentType(String v){} public void setSubmittedAt(OffsetDateTime v){} public void setSubmittedMetadata(Map<String,Object> v){} public void setEvidence(Map<String,Object> v){} }
EOF
w com/opensocket/aievent/core/agent/governance/AgentEnrollmentApprovalCommand.java <<'EOF'
package com.opensocket.aievent.core.agent.governance; import java.util.*; public class AgentEnrollmentApprovalCommand { public void setAgentId(String v){} public void setApprovedBy(String v){} public void setTenantId(String v){} public void setAgentName(String v){} public void setAgentType(String v){} public void setOwnerTeam(String v){} public void setDescription(String v){} public void setComment(String v){} public void setCapabilities(List<String> v){} public void setScopes(List<AgentAuthorizationScope> v){} public void setCredentialToken(String v){} }
EOF
w com/opensocket/aievent/core/agent/governance/AgentProfileCapability.java <<'EOF'
package com.opensocket.aievent.core.agent.governance; public class AgentProfileCapability { public boolean isEnabled(){return false;} public String getCapabilityCode(){return null;} }
EOF
w com/opensocket/aievent/core/agent/governance/AgentCredential.java <<'EOF'
package com.opensocket.aievent.core.agent.governance; public class AgentCredential { public String getCredentialStatus(){return null;} }
EOF
w com/opensocket/aievent/core/agent/governance/AgentProfile.java <<'EOF'
package com.opensocket.aievent.core.agent.governance; import java.util.*; public class AgentProfile { public String getTenantId(){return null;} public String getAgentId(){return null;} public String getAgentType(){return null;} public boolean allowsConnection(){return false;} public AgentCredential getCredential(){return null;} public List<AgentProfileCapability> getCapabilities(){return List.of();} }
EOF
w com/opensocket/aievent/core/agent/governance/AgentGovernanceService.java <<'EOF'
package com.opensocket.aievent.core.agent.governance; public class AgentGovernanceService { public AgentProfile getProfile(String a){return null;} public AgentEnrollmentRequest submitEnrollment(AgentEnrollmentRequest e){return e;} public AgentProfile approveEnrollment(String e, AgentEnrollmentApprovalCommand c){return new AgentProfile();} }
EOF
# Setup stubs
w com/opensocket/aievent/core/agent/setup/AgentSetupRequest.java <<'EOF'
package com.opensocket.aievent.core.agent.setup; import java.util.*; public class AgentSetupRequest { public String getTenantId(){return null;} public String getAgentId(){return null;} public String getAgentName(){return "a";} public String getOwnerTeam(){return null;} public String getDescription(){return null;} public String getPurpose(){return "GENERAL";} public String getRuntimeType(){return null;} public String getGatewayUrl(){return null;} public String getCredentialToken(){return null;} public boolean isAutoApprove(){return false;} public boolean isCreateDefaultCapabilities(){return false;} public boolean isCreateRuntimeBinding(){return false;} public boolean isCreateSupplyProfile(){return false;} public boolean isCreateDefaultDispatchRule(){return false;} public int getCapacityLimit(){return 1;} public String getOperatorId(){return null;} public List<String> getDefaultCapabilities(){return List.of();} public List<String> getDefaultTaskTypes(){return List.of();} public Map<String,Object> getMetadata(){return Map.of();} }
EOF
w com/opensocket/aievent/core/agent/setup/AgentSetupReadinessCheck.java <<'EOF'
package com.opensocket.aievent.core.agent.setup; import java.util.*; public class AgentSetupReadinessCheck { public static AgentSetupReadinessCheck ready(String a,String b,String c){return new AgentSetupReadinessCheck();} public static AgentSetupReadinessCheck pending(String a,String b,String c,String d){return new AgentSetupReadinessCheck();} public boolean isReady(){return false;} public String getCode(){return null;} public void setMetadata(Map<String,Object> v){} }
EOF
w com/opensocket/aievent/core/agent/setup/AgentSetupTroubleshootingStep.java <<'EOF'
package com.opensocket.aievent.core.agent.setup; public class AgentSetupTroubleshootingStep { public static AgentSetupTroubleshootingStep warn(String a,String b,String c,String d){return new AgentSetupTroubleshootingStep();} public static AgentSetupTroubleshootingStep error(String a,String b,String c,String d){return new AgentSetupTroubleshootingStep();} public static AgentSetupTroubleshootingStep command(String a,String b,String c,String d){return new AgentSetupTroubleshootingStep();} public static AgentSetupTroubleshootingStep info(String a,String b,String c,String d){return new AgentSetupTroubleshootingStep();} }
EOF
w com/opensocket/aievent/core/agent/setup/AgentSetupStartCommand.java <<'EOF'
package com.opensocket.aievent.core.agent.setup; import java.util.*; public class AgentSetupStartCommand { public void setRuntimeType(String v){} public void setGatewayUrl(String v){} public void setDockerCommand(String v){} public void setLocalCommand(String v){} public void setRemoteCommand(String v){} public void setCommand(String v){} public void setHealthCheckCommand(String v){} public void setLogsCommand(String v){} public void setExpectedCapabilities(List<String> v){} public void setCapabilityEnvironmentVariable(String v){} public void setVerifyConnectionCommand(String v){} public void setStartupSteps(List<String> v){} public void setTroubleshooting(List<AgentSetupTroubleshootingStep> v){} public List<AgentSetupTroubleshootingStep> getTroubleshooting(){return List.of();} public void setEnvironment(Map<String,Object> v){} public void setDiagnostics(Map<String,Object> v){} }
EOF
w com/opensocket/aievent/core/agent/setup/AgentSetupReadinessResponse.java <<'EOF'
package com.opensocket.aievent.core.agent.setup; import java.time.*; import java.util.*; public class AgentSetupReadinessResponse { public void setTenantId(String v){} public void setAgentId(String v){} public void setReady(boolean v){} public void setStatus(String v){} public void setSummary(String v){} public void setBlockingReasons(List<String> v){} public void setChecks(List<AgentSetupReadinessCheck> v){} public void setMetadata(Map<String,Object> v){} public void setProfileCapabilities(List<String> v){} public void setRuntimeReportedCapabilities(List<String> v){} public void setMissingRuntimeCapabilities(List<String> v){} public void setExtraRuntimeCapabilities(List<String> v){} public void setStartCommand(AgentSetupStartCommand v){} public void setTroubleshooting(List<AgentSetupTroubleshootingStep> v){} public void setGeneratedAt(OffsetDateTime v){} }
EOF
w com/opensocket/aievent/core/agent/setup/AgentSetupResponse.java <<'EOF'
package com.opensocket.aievent.core.agent.setup; import java.time.*; import java.util.*; import com.opensocket.aievent.core.agent.assignment.*; import com.opensocket.aievent.core.agent.governance.*; public class AgentSetupResponse { private List<AgentSetupReadinessCheck> checks=List.of(); public void setTenantId(String v){} public void setAgentId(String v){} public void setTaskScope(List<String> v){} public void setCreatedAt(OffsetDateTime v){} public void setEnrollment(AgentEnrollmentRequest v){} public void setSetupStatus(String v){} public void setReadinessChecks(List<AgentSetupReadinessCheck> v){checks=v;} public List<AgentSetupReadinessCheck> getReadinessChecks(){return checks;} public void setStartCommand(AgentSetupStartCommand v){} public void setMetadata(Map<String,Object> v){} public void setAgentProfile(AgentProfile v){} public void setCapabilityCatalog(List<?> v){} public void setCapabilityAssignments(List<AgentCapabilityAssignment> v){} public void setRuntimeResource(RuntimeResource v){} public void setRuntimeBinding(AgentRuntimeBinding v){} public void setSupplyProfile(SupplyProfile v){} public void setDispatchPolicy(DispatchPolicy v){} }
EOF
STUBS=()
while IFS= read -r stub; do
  STUBS+=("${stub}")
done < <(find "$SRC" -name '*.java')
javac -d "$OUT" "${STUBS[@]}" "$ROOT/ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/setup/AgentSetupService.java"
echo 'AgentSetupService compile harness passed'
