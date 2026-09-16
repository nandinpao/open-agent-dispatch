package com.opensocket.aievent.core.api;

import com.opensocket.aievent.core.analytics.EnterpriseAnalyticsProjectionService;
import com.opensocket.aievent.core.analytics.EnterpriseAnalyticsProjectionService.*;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceAction;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceType;
import com.opensocket.aievent.core.resourceaccess.contract.VisibilityLevel;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceListScopeQueryPlan;
import com.opensocket.aievent.core.resourceaccess.runtime.ScopedBusinessResourceAccessCoordinator;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

/** Read-only Phase 12.6 projection API. It never makes an authorization or operational decision from analytics data. */
@RestController
@RequestMapping("/api/admin/analytics")
public class EnterpriseAnalyticsProjectionController {
    private static final Logger LOGGER = LoggerFactory.getLogger(EnterpriseAnalyticsProjectionController.class);
    private final EnterpriseAnalyticsProjectionService service;
    private final ObjectProvider<ScopedBusinessResourceAccessCoordinator> scopedAccess;
    public EnterpriseAnalyticsProjectionController(EnterpriseAnalyticsProjectionService service,ObjectProvider<ScopedBusinessResourceAccessCoordinator> scopedAccess){this.service=service;this.scopedAccess=scopedAccess;}


    /** Phase 7 access profile: scope is projected from current Resource Access, never selected from an untrusted Tenant header. */
    @GetMapping("/scope-profile")
    public AnalyticsAccessProfile scopeProfile(){
        ResourceListScopeQueryPlan plan=analyticsPlan();
        return service.accessProfile(guard().activeTenantId(),plan);
    }

    /** Searchable/paged organization browser for large Tenants. */
    @GetMapping("/organization")
    public AnalyticsOrganizationPage organization(
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue="DEPARTMENT") String kind,@RequestParam(required=false) String departmentId,
            @RequestParam(defaultValue="") String text,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size){
        ResourceListScopeQueryPlan plan=analyticsPlan();
        if(departmentId!=null&&!departmentId.isBlank()) department(departmentId);
        return service.organizationPage(guard().activeTenantId(),from,to,kind,departmentId,text,page,size,plan);
    }

    /**
     * Phase 7 aggregate read model. Sections fail independently, so one projection outage does not blank the entire Dashboard.
     * It is an application aggregate, not Backend-to-Backend HTTP fan-out.
     */
    @GetMapping("/overview-view")
    public EnterpriseAnalyticsOverviewView overviewView(
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required=false) String departmentId,@RequestParam(required=false) String groupId,
            @RequestParam(defaultValue="DAY") String bucket,@RequestParam(defaultValue="") String organizationText,
            @RequestParam(defaultValue="0") int organizationPage,@RequestParam(defaultValue="25") int organizationSize){
        ResourceListScopeQueryPlan plan=analyticsPlan();
        AnalyticsAccessProfile access=service.accessProfile(guard().activeTenantId(),plan);
        String effectiveDepartment=text(departmentId), effectiveGroup=text(groupId);
        if(effectiveGroup!=null) group(effectiveGroup);
        if(effectiveDepartment!=null) department(effectiveDepartment);
        final String dept=effectiveDepartment, grp=effectiveGroup, tenant=guard().activeTenantId();
        final boolean planWide=!access.tenantWide() && dept==null && grp==null;
        Map<String,AnalyticsSection<?>> sections=new LinkedHashMap<>();
        sections.put("summary",safe("summary",tenant,() -> planWide
                ? service.scopedWorkloadSummary(tenant,from,to,plan)
                : service.workloadSummary(tenant,from,to,dept,grp)));
        sections.put("trend",safe("trend",tenant,() -> planWide
                ? service.scopedWorkloadTrend(tenant,from,to,bucket,plan)
                : service.workloadTrend(tenant,from,to,dept,grp,bucket)));
        sections.put("origin",safe("origin",tenant,() -> planWide
                ? service.scopedByOrigin(tenant,from,to,100,plan)
                : service.byOrigin(tenant,from,to,dept,grp,100)));
        sections.put("failureDomain",safe("failureDomain",tenant,() -> planWide
                ? service.scopedByFailureDomain(tenant,from,to,100,plan)
                : service.byFailureDomain(tenant,from,to,dept,grp,100)));
        sections.put("agents",safe("agents",tenant,() -> planWide
                ? service.scopedByAgent(tenant,from,to,25,plan)
                : service.byAgent(tenant,from,to,dept,grp,25)));
        sections.put("credentials",safe("credentials",tenant,() -> planWide
                ? service.scopedByCredential(tenant,from,to,25,plan)
                : service.byCredential(tenant,from,to,dept,grp,25)));
        sections.put("sourceSystems",safe("sourceSystems",tenant,() -> planWide
                ? service.scopedBySourceSystem(tenant,from,to,25,plan)
                : service.bySourceSystem(tenant,from,to,dept,grp,25)));
        sections.put("recent",safe("recent",tenant,() -> planWide
                ? service.scopedRecentWorkloads(tenant,from,to,50,null,plan)
                : service.recentWorkloads(tenant,from,to,dept,grp,50,null)));
        String organizationKind=dept==null?"DEPARTMENT":"GROUP";
        sections.put("organization",safe("organization",tenant,() -> service.organizationPage(tenant,from,to,organizationKind,dept,organizationText,organizationPage,organizationSize,plan)));
        sections.put("security",planWide?AnalyticsSection.omitted("PLAN_WIDE_SECURITY_ANALYTICS_NOT_AVAILABLE")
                : grp==null?safe("security",tenant,() -> service.securitySummary(tenant,from,to,dept)):AnalyticsSection.omitted("GROUP_SECURITY_ANALYTICS_NOT_AVAILABLE"));
        sections.put("securityDepartments",access.tenantWide()?safe("securityDepartments",tenant,() -> service.securityByDepartment(tenant,from,to,100)):AnalyticsSection.omitted("TENANT_WIDE_AUTHORITY_REQUIRED"));
        sections.put("projection",access.tenantWide()?safe("projection",tenant,() -> service.projectionStatus(tenant)):AnalyticsSection.omitted("TENANT_WIDE_AUTHORITY_REQUIRED"));
        sections.put("performance",access.tenantWide()?safe("performance",tenant,() -> service.dashboardPerformanceStatus(tenant)):AnalyticsSection.omitted("TENANT_WIDE_AUTHORITY_REQUIRED"));
        return new EnterpriseAnalyticsOverviewView(access,dept==null?"":dept,grp==null?"":grp,Map.copyOf(sections));
    }

    @GetMapping("/projection/status") public ProjectionStatus projectionStatus(){tenantWide();return service.projectionStatus(guard().activeTenantId());}

    @GetMapping("/workloads/summary") public WorkloadSummary workloadSummary(
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required=false) String departmentId,@RequestParam(required=false) String groupId){scope(departmentId,groupId);return service.workloadSummary(guard().activeTenantId(),from,to,departmentId,groupId);}

    @GetMapping("/workloads/by-origin") public List<BreakdownRow> byOrigin(
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required=false) String departmentId,@RequestParam(required=false) String groupId,@RequestParam(defaultValue="100") int limit){scope(departmentId,groupId);return service.byOrigin(guard().activeTenantId(),from,to,departmentId,groupId,limit);}

    @GetMapping("/workloads/by-failure-domain") public List<BreakdownRow> byFailureDomain(
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required=false) String departmentId,@RequestParam(required=false) String groupId,@RequestParam(defaultValue="100") int limit){scope(departmentId,groupId);return service.byFailureDomain(guard().activeTenantId(),from,to,departmentId,groupId,limit);}

    @GetMapping("/workloads/by-department") public List<OrganizationRow> byDepartment(
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,@RequestParam(defaultValue="200") int limit){tenantWide();return service.byDepartment(guard().activeTenantId(),from,to,limit);}

    @GetMapping("/security/summary") public SecuritySummary securitySummary(
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,@RequestParam(required=false) String departmentId){scope(departmentId,null);return service.securitySummary(guard().activeTenantId(),from,to,departmentId);}


    @GetMapping("/workloads/trend") public List<TrendRow> workloadTrend(
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required=false) String departmentId,@RequestParam(required=false) String groupId,@RequestParam(defaultValue="DAY") String bucket){scope(departmentId,groupId);return service.workloadTrend(guard().activeTenantId(),from,to,departmentId,groupId,bucket);}

    @GetMapping("/workloads/by-group") public List<OrganizationRow> byGroup(
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required=false) String departmentId,@RequestParam(defaultValue="200") int limit){if(departmentId==null||departmentId.isBlank())tenantWide();else department(departmentId);return service.byGroup(guard().activeTenantId(),from,to,departmentId,limit);}

    @GetMapping("/workloads/by-agent") public List<RankedWorkloadRow> byAgent(
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required=false) String departmentId,@RequestParam(required=false) String groupId,@RequestParam(defaultValue="25") int limit){scope(departmentId,groupId);return service.byAgent(guard().activeTenantId(),from,to,departmentId,groupId,limit);}

    @GetMapping("/workloads/by-credential") public List<RankedWorkloadRow> byCredential(
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required=false) String departmentId,@RequestParam(required=false) String groupId,@RequestParam(defaultValue="25") int limit){scope(departmentId,groupId);return service.byCredential(guard().activeTenantId(),from,to,departmentId,groupId,limit);}

    @GetMapping("/workloads/by-source-system") public List<RankedWorkloadRow> bySourceSystem(
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required=false) String departmentId,@RequestParam(required=false) String groupId,@RequestParam(defaultValue="25") int limit){scope(departmentId,groupId);return service.bySourceSystem(guard().activeTenantId(),from,to,departmentId,groupId,limit);}

    @GetMapping("/workloads/recent") public RecentWorkloadPage recentWorkloads(
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required=false) String departmentId,@RequestParam(required=false) String groupId,
            @RequestParam(defaultValue="50") int limit,@RequestParam(required=false) String cursor){
        String dept=text(departmentId),grp=text(groupId);
        if(grp!=null) group(grp);
        if(dept!=null) department(dept);
        ResourceListScopeQueryPlan plan=analyticsPlan();
        if(dept==null&&grp==null&&!plan.tenantWide()) return service.scopedRecentWorkloads(guard().activeTenantId(),from,to,limit,cursor,plan);
        return service.recentWorkloads(guard().activeTenantId(),from,to,dept,grp,limit,cursor);
    }

    @GetMapping("/security/by-department") public List<SecurityOrganizationRow> securityByDepartment(
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,@RequestParam(defaultValue="100") int limit){tenantWide();return service.securityByDepartment(guard().activeTenantId(),from,to,limit);}

    @GetMapping("/performance/status") public DashboardPerformanceStatus dashboardPerformanceStatus(){tenantWide();return service.dashboardPerformanceStatus(guard().activeTenantId());}

        @GetMapping("/dimensions/{dimensionType}/{dimensionId}/history") public List<Map<String,Object>> dimensionHistory(@PathVariable String dimensionType,@PathVariable String dimensionId,@RequestParam(defaultValue="100") int limit){
        if("DEPARTMENT".equalsIgnoreCase(dimensionType)) department(dimensionId); else if("GROUP".equalsIgnoreCase(dimensionType)) group(dimensionId); else throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST,"dimensionType must be DEPARTMENT or GROUP");
        return service.dimensionHistory(guard().activeTenantId(),dimensionType,dimensionId,limit);
    }


    private ResourceListScopeQueryPlan analyticsPlan(){return guard().plan("resource.governance.read",ResourceType.TASK,VisibilityLevel.STANDARD,"ENTERPRISE_ANALYTICS_SCOPED_READ");}
    private static String text(String value){return value==null||value.isBlank()?null:value.trim();}
    private static <T> AnalyticsSection<T> safe(String section,String tenantId,Supplier<T> supplier){
        try { T data=supplier.get(); return AnalyticsSection.ready(data); }
        catch(RuntimeException ex){
            LOGGER.warn("analytics_section_unavailable section={} tenant={} errorClass={}", section, tenantId, ex.getClass().getName(), ex);
            return AnalyticsSection.unavailable("ANALYTICS_SECTION_UNAVAILABLE");
        }
    }

    public record AnalyticsSection<T>(String status,long revision,String errorCode,T data){
        static <T> AnalyticsSection<T> ready(T value){return new AnalyticsSection<>("READY",EnterpriseAnalyticsProjectionService.contentRevision(value),"",value);}
        static <T> AnalyticsSection<T> unavailable(String code){return new AnalyticsSection<>("UNAVAILABLE",0L,code,null);}
        static <T> AnalyticsSection<T> omitted(String code){return new AnalyticsSection<>("OMITTED",0L,code,null);}
    }
    public record EnterpriseAnalyticsOverviewView(AnalyticsAccessProfile access,String departmentId,String groupId,Map<String,AnalyticsSection<?>> sections){}

    private void scope(String departmentId,String groupId){if(groupId!=null&&!groupId.isBlank())group(groupId);if(departmentId!=null&&!departmentId.isBlank())department(departmentId);if((departmentId==null||departmentId.isBlank())&&(groupId==null||groupId.isBlank()))tenantWide();}
    private void department(String id){guard().authorize(ResourceType.DEPARTMENT,id,"resource.governance.read",ResourceAction.ActionKind.READ,false,VisibilityLevel.STANDARD,"ENTERPRISE_ANALYTICS_DEPARTMENT_READ");}
    private void group(String id){guard().authorize(ResourceType.GROUP,id,"resource.governance.read",ResourceAction.ActionKind.READ,false,VisibilityLevel.STANDARD,"ENTERPRISE_ANALYTICS_GROUP_READ");}
    private void tenantWide(){guard().requireTenantWide("resource.governance.read",ResourceType.TENANT,VisibilityLevel.STANDARD,"ENTERPRISE_ANALYTICS_TENANT_READ");}
    private ScopedBusinessResourceAccessCoordinator guard(){var g=scopedAccess.getIfAvailable();if(g==null)throw new StandardApiException(StandardApiErrorCode.DEPENDENCY_UNAVAILABLE,"Formal Resource Access is required for enterprise analytics.");return g;}
}
