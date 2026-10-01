package com.opensocket.aievent.core.configuration;

import com.opensocket.aievent.core.api.StandardApiErrorCode;
import com.opensocket.aievent.core.api.StandardApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** V40-9A operator API. No bulk transition or arbitrary definition creation endpoint exists by design. */
@RestController
@RequestMapping("/api/platform/runtime-configuration/inventory")
public class ConfigurationMigrationGovernanceController {
    private final ConfigurationMigrationGovernanceService service;
    public ConfigurationMigrationGovernanceController(ConfigurationMigrationGovernanceService service){this.service=service;}

    @GetMapping
    public List<ConfigurationMigrationGovernanceService.InventorySummary> list(
            @RequestParam(required=false) String status,@RequestParam(required=false) String namespace,
            @RequestParam(required=false) String query,@RequestParam(required=false) Integer limit){return service.list(status,namespace,query,limit);}

    @GetMapping("/{key:.+}")
    public ConfigurationMigrationGovernanceService.InventoryDetail detail(@PathVariable String key){try{return service.detail(key);}catch(RuntimeException ex){throw map(ex);}}

    @PostMapping("/{key:.+}/classify")
    public ConfigurationMigrationGovernanceService.InventoryDetail classify(@PathVariable String key,@RequestBody ConfigurationMigrationGovernanceService.ClassificationRequest body,Authentication authentication,HttpServletRequest request){try{return service.classify(key,body,actor(authentication));}catch(RuntimeException ex){throw map(ex);}}

    @PostMapping("/{key:.+}/owner-review")
    public ConfigurationMigrationGovernanceService.InventoryDetail ownerReview(@PathVariable String key,@RequestBody ConfigurationMigrationGovernanceService.ReviewRequest body,Authentication authentication,HttpServletRequest request){try{return service.ownerReview(key,body,actor(authentication));}catch(RuntimeException ex){throw map(ex);}}

    @PostMapping("/{key:.+}/architecture-approve")
    public ConfigurationMigrationGovernanceService.InventoryDetail architectureApprove(@PathVariable String key,@RequestBody ConfigurationMigrationGovernanceService.ReviewRequest body,Authentication authentication,HttpServletRequest request){try{return service.architectureApprove(key,body,actor(authentication));}catch(RuntimeException ex){throw map(ex);}}

    @PostMapping("/{key:.+}/migration-authorize")
    public ConfigurationMigrationGovernanceService.InventoryDetail authorizeMigration(@PathVariable String key,@RequestBody ConfigurationMigrationGovernanceService.ReviewRequest body,Authentication authentication,HttpServletRequest request){try{return service.authorizeMigration(key,body,actor(authentication));}catch(RuntimeException ex){throw map(ex);}}

    private static String actor(Authentication a){return a==null||a.getName()==null||a.getName().isBlank()?"unknown-platform-operator":a.getName();}
    private static StandardApiException map(RuntimeException ex){
        StandardApiErrorCode code = ex instanceof IllegalArgumentException ? StandardApiErrorCode.VALIDATION_ERROR : StandardApiErrorCode.RESOURCE_VERSION_CONFLICT;
        return new StandardApiException(code,ex.getMessage(),ex);
    }
}
