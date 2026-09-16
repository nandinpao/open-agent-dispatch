package com.opensocket.aievent.core.runtime.capability;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Non-sensitive bootstrap authority used before the Admin UI chooses an authentication session endpoint. */
@RestController
@RequestMapping("/api/platform/runtime-capabilities")
public final class RuntimeCapabilityController {
    private final RuntimeCapabilityService service;

    public RuntimeCapabilityController(RuntimeCapabilityService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<RuntimeCapabilitySnapshot> get() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache")
                .header("X-Content-Type-Options", "nosniff")
                .body(service.snapshot());
    }
}
