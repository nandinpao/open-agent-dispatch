package com.opensocket.aievent.core.api;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.opensocket.aievent.core.source.SourceSystemManagementService;
import com.opensocket.aievent.core.source.SourceSystemView;

@RestController
@RequestMapping("/admin/source-systems")
public class SourceSystemController {
    private final SourceSystemManagementService sourceSystemManagementService;

    public SourceSystemController(SourceSystemManagementService sourceSystemManagementService) {
        this.sourceSystemManagementService = sourceSystemManagementService;
    }

    @GetMapping
    public List<SourceSystemView> list(@RequestParam String tenantId) {
        return sourceSystemManagementService.list(tenantId);
    }

    @GetMapping("/{sourceSystemId}")
    public SourceSystemView detail(@PathVariable String sourceSystemId,
                                   @RequestParam String tenantId) {
        return sourceSystemManagementService.find(tenantId, sourceSystemId)
                .orElseThrow(() -> new StandardApiException(StandardApiErrorCode.NOT_FOUND, "Source System not found: " + sourceSystemId));
    }

    @PostMapping
    public SourceSystemView create(@RequestParam String tenantId,
                                   @RequestBody(required = false) SourceSystemView request) {
        try {
            SourceSystemView body = request == null ? new SourceSystemView() : request;
            body.setTenantId(tenantId);
            return sourceSystemManagementService.create(tenantId, body);
        } catch (IllegalArgumentException ex) {
            throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST, ex.getMessage());
        }
    }

    @PutMapping("/{sourceSystemId}")
    public SourceSystemView update(@PathVariable String sourceSystemId,
                                   @RequestParam String tenantId,
                                   @RequestBody(required = false) SourceSystemView request) {
        try {
            SourceSystemView body = request == null ? new SourceSystemView() : request;
            body.setTenantId(tenantId);
            body.setSourceSystemId(sourceSystemId);
            return sourceSystemManagementService.update(tenantId, sourceSystemId, body);
        } catch (IllegalArgumentException ex) {
            throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST, ex.getMessage());
        }
    }

    @DeleteMapping("/{sourceSystemId}")
    public Map<String, Object> retire(@PathVariable String sourceSystemId,
                                      @RequestParam String tenantId) {
        sourceSystemManagementService.retire(tenantId, sourceSystemId);
        return Map.of("sourceSystemId", sourceSystemId, "status", "RETIRED");
    }
}
