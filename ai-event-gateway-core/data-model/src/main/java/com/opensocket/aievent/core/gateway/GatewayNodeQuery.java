package com.opensocket.aievent.core.gateway;

public class GatewayNodeQuery {
    private GatewayNodeStatus status;
    private String siteId;
    private String region;
    private String zone;
    private int limit = 100;

    public GatewayNodeStatus getStatus() { return status; }
    public void setStatus(GatewayNodeStatus status) { this.status = status; }
    public String getSiteId() { return siteId; }
    public void setSiteId(String siteId) { this.siteId = siteId; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getZone() { return zone; }
    public void setZone(String zone) { this.zone = zone; }
    public int getLimit() { return limit; }
    public void setLimit(int limit) { this.limit = Math.max(1, Math.min(limit, 1000)); }
}
