package com.opensocket.aievent.core.uicapability.api;

import com.opensocket.aievent.core.uicapability.contract.*;
import com.opensocket.aievent.core.uicapability.core.*;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(prefix = "ui-capability", name = {"enabled", "projection-api-enabled"}, havingValue = "true")
@ConditionalOnBean({UiCapabilityProjectionService.class, UiCapabilityApiContextPort.class})
public final class UiCapabilityController {
    private final UiCapabilityProjectionService projection;
    private final UiCapabilityApiContextPort contexts;
    private final UiCapabilityBatchValidator validator = new UiCapabilityBatchValidator();
    public UiCapabilityController(UiCapabilityProjectionService projection, UiCapabilityApiContextPort contexts) {
        this.projection = projection; this.contexts = contexts;
    }

    @PostMapping(path = UiCapabilityApiPaths.CAPABILITY_BATCH,
            consumes = {MediaType.APPLICATION_JSON_VALUE, UiCapabilityContract.MEDIA_TYPE},
            produces = {UiCapabilityContract.MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE})
    public UiCapabilityBatchResponse generic(@RequestBody UiCapabilityBatchRequest request) {
        return project(request, UiCapabilityBatchValidator.Profile.GENERIC);
    }

    @PostMapping(path = UiCapabilityApiPaths.LIST_CAPABILITY_BATCH,
            consumes = {MediaType.APPLICATION_JSON_VALUE, UiCapabilityContract.MEDIA_TYPE},
            produces = {UiCapabilityContract.MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE})
    public UiCapabilityBatchResponse listRows(@RequestBody UiCapabilityBatchRequest request) {
        return project(request, UiCapabilityBatchValidator.Profile.LIST_ROW);
    }

    private UiCapabilityBatchResponse project(UiCapabilityBatchRequest request, UiCapabilityBatchValidator.Profile profile) {
        validator.validate(request, profile);
        UiCapabilityApiContext trusted = contexts.current();
        List<UiCapabilityEnvelope> envelopes = new ArrayList<>(request.contexts().size());
        for (UiCapabilityContextRequest context : request.contexts()) {
            envelopes.add(projection.project(new UiCapabilityProjectionCommand(request.contractVersion(),
                    trusted.authentication(), context, trusted.correlationId(), trusted.requestedAt())));
        }
        return new UiCapabilityBatchResponse(UiCapabilityContract.VERSION, envelopes);
    }
}
