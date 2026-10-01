package com.opensocket.aievent.core.kernel.configuration.cutover;

import java.time.OffsetDateTime;

/** Durable evidence that an ordered cutover wave completed runtime convergence. */
public record RuntimeConfigurationCutoverWaveCertification(
        String certificationId,String waveId,String status,int authorityContractVersion,
        int configSetCount,int runtimeKeyCount,int requiredNodeCount,int convergedNodeCount,
        String evidenceJson,String certifiedBy,String reason,OffsetDateTime certifiedAt) {
    public RuntimeConfigurationCutoverWaveCertification {
        certificationId=required(certificationId,"certificationId"); waveId=required(waveId,"waveId");
        status=required(status,"status").toUpperCase(); certifiedBy=required(certifiedBy,"certifiedBy");
        reason=required(reason,"reason"); evidenceJson=evidenceJson==null?"{}":evidenceJson;
        if(authorityContractVersion<2) throw new IllegalArgumentException("cutover wave certification requires authority contract v2 or later");
        if(configSetCount<1||runtimeKeyCount<1||requiredNodeCount<1||convergedNodeCount!=requiredNodeCount)
            throw new IllegalArgumentException("cutover wave certification requires complete non-zero runtime convergence");
        if(!"PASS".equals(status)) throw new IllegalArgumentException("only PASS cutover certifications are durable");
        if(certifiedAt==null) throw new IllegalArgumentException("certifiedAt is required");
    }
    private static String required(String value,String field){if(value==null||value.isBlank())throw new IllegalArgumentException(field+" is required");return value.trim();}
}
