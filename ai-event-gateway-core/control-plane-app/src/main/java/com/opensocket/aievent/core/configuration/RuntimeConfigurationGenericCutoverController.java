package com.opensocket.aievent.core.configuration;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.opensocket.aievent.core.api.StandardApiErrorCode;
import com.opensocket.aievent.core.api.StandardApiException;

import jakarta.servlet.http.HttpServletRequest;

/** Generic Config Set authority cutover API. Revision creation/approval/publish remains on the normal governed configuration API. */
@RestController
@RequestMapping("/api/platform/runtime-configuration/config-sets/{configSetId}/cutover")
public class RuntimeConfigurationGenericCutoverController {
    private final RuntimeConfigurationGenericCutoverService service;
    public RuntimeConfigurationGenericCutoverController(RuntimeConfigurationGenericCutoverService service){this.service=service;}

    @GetMapping public RuntimeConfigurationGenericCutoverService.CutoverStatus status(@PathVariable String configSetId){return invoke(()->service.statusByConfigSetId(configSetId));}
    /** Convenience alias: POST cutover means PREPARE, never an implicit final authority switch. */
    @PostMapping public RuntimeConfigurationGenericCutoverService.CutoverStatus prepareDefault(@PathVariable String configSetId,@RequestBody ReasonRequest body,
            Authentication authentication,HttpServletRequest request){return prepare(configSetId,body,authentication,request);}
    @PostMapping("/prepare") public RuntimeConfigurationGenericCutoverService.CutoverStatus prepare(@PathVariable String configSetId,@RequestBody ReasonRequest body,
            Authentication authentication,HttpServletRequest request){return invoke(()->service.prepareByConfigSetId(configSetId,actor(authentication),body.reason(),correlationId(request)));}
    @PostMapping("/finalize") public RuntimeConfigurationGenericCutoverService.CutoverStatus finalizeCutover(@PathVariable String configSetId,@RequestBody ReasonRequest body,
            Authentication authentication,HttpServletRequest request){return invoke(()->service.finalizeByConfigSetId(configSetId,actor(authentication),body.reason(),correlationId(request)));}
    @PostMapping("/cancel") public RuntimeConfigurationGenericCutoverService.CutoverStatus cancel(@PathVariable String configSetId,@RequestBody ReasonRequest body,
            Authentication authentication,HttpServletRequest request){return invoke(()->service.cancelByConfigSetId(configSetId,actor(authentication),body.reason(),correlationId(request)));}

    private <T>T invoke(Action<T> action){try{return action.run();}catch(IllegalArgumentException|IllegalStateException ex){throw new StandardApiException(StandardApiErrorCode.VALIDATION_ERROR,ex.getMessage(),ex);}}
    private static String actor(Authentication a){return a==null||a.getName()==null||a.getName().isBlank()?"unknown-platform-operator":a.getName();}
    private static String correlationId(HttpServletRequest request){String v=request.getHeader("X-Correlation-Id");return v==null||v.isBlank()?UUID.randomUUID().toString():v.trim();}
    public record ReasonRequest(String reason){}
    @FunctionalInterface private interface Action<T>{T run();}
}
