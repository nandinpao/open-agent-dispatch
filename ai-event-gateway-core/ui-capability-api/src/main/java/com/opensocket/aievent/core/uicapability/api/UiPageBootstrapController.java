package com.opensocket.aievent.core.uicapability.api;

import com.opensocket.aievent.core.uicapability.contract.*;
import com.opensocket.aievent.core.uicapability.core.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(prefix = "ui-capability", name = {"enabled", "projection-api-enabled", "page-bootstrap-enabled"}, havingValue = "true")
@ConditionalOnBean({UiPageBootstrapService.class, UiCapabilityApiContextPort.class})
public final class UiPageBootstrapController {
    private static final System.Logger LOG = System.getLogger(UiPageBootstrapController.class.getName());

    private final UiPageBootstrapService bootstraps;
    private final UiCapabilityApiContextPort contexts;
    public UiPageBootstrapController(UiPageBootstrapService bootstraps, UiCapabilityApiContextPort contexts) {
        this.bootstraps = bootstraps; this.contexts = contexts;
    }

    @GetMapping(path = UiCapabilityApiPaths.PAGE_BOOTSTRAP, produces = {UiCapabilityContract.MEDIA_TYPE, "application/json"})
    public ResponseEntity<UiPageBootstrap> bootstrap(
            @PathVariable String pageContext,
            @RequestParam String resourceId,
            @RequestParam(required = false) Long resourceVersion,
            @RequestParam(required = false) Long principalEpoch,
            @RequestHeader(name = "X-UI-Capability-Contract", defaultValue = UiCapabilityContract.VERSION) String contractVersion) {
        UiCapabilityApiContext trusted = contexts.current();
        LOG.log(System.Logger.Level.INFO,
                "ui_page_bootstrap_requested pageContext=" + pageContext
                        + " resourceId=" + resourceId
                        + " resourceVersion=" + resourceVersion
                        + " correlationId=" + trusted.correlationId());
        UiPageBootstrap result = bootstraps.bootstrap(new UiPageBootstrapCommand(trusted.authentication(),
                new UiPageBootstrapRequest(contractVersion, pageContext, resourceId, resourceVersion, principalEpoch),
                trusted.correlationId(), trusted.requestedAt()));
        LOG.log(System.Logger.Level.INFO,
                "ui_page_bootstrap_resolved pageContext=" + pageContext
                        + " resourceId=" + resourceId
                        + " outcome=" + result.outcome()
                        + " correlationId=" + trusted.correlationId());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.VARY, "Cookie")
                .header("X-Content-Type-Options", "nosniff")
                .body(result);
    }
}
