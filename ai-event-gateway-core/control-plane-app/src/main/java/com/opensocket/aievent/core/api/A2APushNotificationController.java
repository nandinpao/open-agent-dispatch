package com.opensocket.aievent.core.api;

import com.opensocket.aievent.core.capability.A2APushCallbackRouteService;
import com.opensocket.aievent.core.capability.A2APushInboxService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** PC-S4 A2A PUSH webhook. Opaque route + bearer authentication occurs before tenant binding. */
@RestController
@RequestMapping("/internal/a2a/push")
public class A2APushNotificationController {
    private final A2APushCallbackRouteService routes; private final A2APushInboxService inbox; private final ObjectMapper json;
    public A2APushNotificationController(A2APushCallbackRouteService routes,A2APushInboxService inbox,ObjectMapper json){this.routes=routes;this.inbox=inbox;this.json=json;}

    @PostMapping(value="/{callbackHandle}",consumes={"application/a2a+json",MediaType.APPLICATION_JSON_VALUE})
    public Map<String,Object> accept(@PathVariable String callbackHandle,@RequestHeader("Authorization") String authorization,
                                     @RequestHeader(name="Idempotency-Key",required=false) String deliveryIdentity,
                                     @RequestHeader(name="X-A2A-Event-Id",required=false) String remoteEventIdentity,
                                     @RequestBody byte[] rawBody){
        String token=bearer(authorization);A2APushCallbackRouteService.Route route=routes.authenticate(callbackHandle,token);Map<String,Object> body=parse(rawBody);boolean accepted=inbox.accept(route,deliveryIdentity,remoteEventIdentity,body);return Map.of("accepted",accepted,"trackingId",route.trackingId());
    }

    /** Pre-S4 callback shape is intentionally retired; it bound attacker-controlled tenant path data before authentication. */
    @PostMapping(value="/{tenantId}/{trackingId}")
    public void legacy(@PathVariable String tenantId,@PathVariable String trackingId){throw new ResponseStatusException(HttpStatus.GONE,"A2A_PUSH_LEGACY_CALLBACK_RETIRED");}

    private Map<String,Object> parse(byte[] raw){try{return json.readValue(raw==null?new byte[0]:raw,new TypeReference<Map<String,Object>>(){});}catch(Exception ex){throw new IllegalArgumentException("A2A_PUSH_PAYLOAD_INVALID",ex);}}
    private static String bearer(String value){if(value==null||!value.regionMatches(true,0,"Bearer ",0,7))throw new IllegalArgumentException("A2A_PUSH_BEARER_REQUIRED");String token=value.substring(7).trim();if(token.isBlank())throw new IllegalArgumentException("A2A_PUSH_BEARER_REQUIRED");return token;}
}
