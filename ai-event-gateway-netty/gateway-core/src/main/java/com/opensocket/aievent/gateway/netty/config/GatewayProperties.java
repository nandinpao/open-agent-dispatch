package com.opensocket.aievent.gateway.netty.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Locale;

/**
 * Configuration properties holder for Gateway Properties. Values are bound from application.yml
 * and environment variables so local, Docker, cluster, and production deployments can use the
 * same code path.
 *
 * <p>This class intentionally uses JavaBean-style binding instead of a record. Spring Boot 4 / Spring
 * Framework 7 can otherwise try to instantiate a multi-constructor record as a regular bean and fail
 * with "No default constructor found" when the property class has compatibility constructors.</p>
 */
@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {

    private String nodeId = "gateway-node-001";
    private String environment = "local";
    private String version = "dev";
    private String description = "AI Event Gateway Netty";
    private String siteId = "LOCAL";
    private String siteName = "Local Site";
    private String region = "local";
    private String zone = "local-zone";

    /**
     * Required by Spring Boot ConfigurationProperties JavaBean binding.
     */
    public GatewayProperties() {
    }

    /**
     * Backward-compatible constructor for existing unit tests and single-site deployments.
     */
    public GatewayProperties(
            String nodeId,
            String environment,
            String version,
            String description
    ) {
        this(nodeId, environment, version, description, "LOCAL", "Local Site", "local", "local-zone");
    }

    public GatewayProperties(
            String nodeId,
            String environment,
            String version,
            String description,
            String siteId,
            String siteName,
            String region,
            String zone
    ) {
        setNodeId(nodeId);
        setEnvironment(environment);
        setVersion(version);
        setDescription(description);
        setSiteId(siteId);
        setSiteName(siteName);
        setRegion(region);
        setZone(zone);
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = blank(nodeId) ? "gateway-node-001" : nodeId.trim();
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = blank(environment) ? "local" : environment.trim();
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = blank(version) ? "dev" : version.trim();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = blank(description) ? "AI Event Gateway Netty" : description.trim();
    }

    public String getSiteId() {
        return siteId;
    }

    public void setSiteId(String siteId) {
        this.siteId = blank(siteId) ? "LOCAL" : siteId.trim().toUpperCase(Locale.ROOT);
        if (blank(this.siteName)) {
            this.siteName = this.siteId;
        }
        if (blank(this.zone)) {
            this.zone = this.siteId.toLowerCase(Locale.ROOT) + "-zone";
        }
    }

    public String getSiteName() {
        return siteName;
    }

    public void setSiteName(String siteName) {
        this.siteName = blank(siteName) ? siteId : siteName.trim();
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = blank(region) ? "local" : region.trim();
    }

    public String getZone() {
        return zone;
    }

    public void setZone(String zone) {
        this.zone = blank(zone) ? siteId.toLowerCase(Locale.ROOT) + "-zone" : zone.trim();
    }

    /** Record-compatible accessor retained to avoid changing existing call sites. */
    public String nodeId() {
        return nodeId;
    }

    /** Record-compatible accessor retained to avoid changing existing call sites. */
    public String environment() {
        return environment;
    }

    /** Record-compatible accessor retained to avoid changing existing call sites. */
    public String version() {
        return version;
    }

    /** Record-compatible accessor retained to avoid changing existing call sites. */
    public String description() {
        return description;
    }

    /** Record-compatible accessor retained to avoid changing existing call sites. */
    public String siteId() {
        return siteId;
    }

    /** Record-compatible accessor retained to avoid changing existing call sites. */
    public String siteName() {
        return siteName;
    }

    /** Record-compatible accessor retained to avoid changing existing call sites. */
    public String region() {
        return region;
    }

    /** Record-compatible accessor retained to avoid changing existing call sites. */
    public String zone() {
        return zone;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
