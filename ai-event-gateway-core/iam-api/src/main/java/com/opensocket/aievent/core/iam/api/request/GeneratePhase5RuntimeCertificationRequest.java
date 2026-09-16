package com.opensocket.aievent.core.iam.api.request;
import jakarta.validation.constraints.NotBlank;import jakarta.validation.constraints.Pattern;import jakarta.validation.constraints.Size;
public record GeneratePhase5RuntimeCertificationRequest(
 @NotBlank @Size(max=64) String sourceVersion,
 @Pattern(regexp="PASS|FAIL|NOT_EXECUTED") String postgresqlCleanStatus,
 @Pattern(regexp="PASS|FAIL|NOT_EXECUTED") String postgresqlUpgradeStatus,
 @Pattern(regexp="PASS|FAIL|NOT_EXECUTED") String applicationContextStatus,
 @Pattern(regexp="PASS|FAIL|NOT_EXECUTED") String adminUiBuildStatus,
 @Pattern(regexp="PASS|FAIL|NOT_EXECUTED") String playwrightStatus,
 @Pattern(regexp="PASS|FAIL|NOT_EXECUTED") String loadTestStatus,
 @Pattern(regexp="HEALTHY|UNHEALTHY|NOT_EXECUTED") String pipelineStatus,
 @NotBlank @Size(max=20000) String evidenceJson){}
