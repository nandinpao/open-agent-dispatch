package com.opensocket.aievent.core.iam.persistence.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("aeg.iam.rbac")
public class IamRbacProperties {
    private boolean enabled=false; private long cacheMaximumSize=10000; private Duration cacheTtl=Duration.ofSeconds(30);
    private String invalidationChannel="opendispatch:iam:cache-invalidation"; private double shadowMatchSamplingRate=0.01;
    private int shadowSampleQueueCapacity=4096; private int shadowMaxMetadataEntries=16;
    public boolean isEnabled(){return enabled;} public void setEnabled(boolean enabled){this.enabled=enabled;}
    public long getCacheMaximumSize(){return cacheMaximumSize;} public void setCacheMaximumSize(long v){cacheMaximumSize=v;}
    public Duration getCacheTtl(){return cacheTtl;} public void setCacheTtl(Duration v){cacheTtl=v;}
    public String getInvalidationChannel(){return invalidationChannel;} public void setInvalidationChannel(String v){invalidationChannel=v;}
    public double getShadowMatchSamplingRate(){return shadowMatchSamplingRate;} public void setShadowMatchSamplingRate(double v){shadowMatchSamplingRate=v;}
    public int getShadowSampleQueueCapacity(){return shadowSampleQueueCapacity;} public void setShadowSampleQueueCapacity(int v){shadowSampleQueueCapacity=v;}
    public int getShadowMaxMetadataEntries(){return shadowMaxMetadataEntries;} public void setShadowMaxMetadataEntries(int v){shadowMaxMetadataEntries=v;}
}
