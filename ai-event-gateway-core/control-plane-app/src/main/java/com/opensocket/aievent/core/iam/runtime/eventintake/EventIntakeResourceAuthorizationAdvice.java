package com.opensocket.aievent.core.iam.runtime.eventintake;

import com.opensocket.aievent.core.api.EventIntakeController;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Stable external error contract for Phase 9 Event Intake machine authorization. */
@RestControllerAdvice(assignableTypes = EventIntakeController.class)
public final class EventIntakeResourceAuthorizationAdvice {
    @ExceptionHandler(EventIntakeResourceAuthorizationException.class)
    ResponseEntity<Map<String,Object>> handle(EventIntakeResourceAuthorizationException e,HttpServletRequest request){
        String correlation=request.getHeader("X-Correlation-Id");
        if(correlation==null||correlation.isBlank())correlation="event-intake-"+UUID.randomUUID();
        Map<String,Object> body=new LinkedHashMap<>();
        body.put("code",e.reasonCode()); body.put("error_code",e.reasonCode()); body.put("message",e.getMessage()); body.put("correlationId",correlation);
        var builder=ResponseEntity.status(e.httpStatus()).header("X-Correlation-Id",correlation);
        if(e.httpStatus()==401)builder.header("WWW-Authenticate","Bearer realm=\"opendispatch-event-api\", error=\"invalid_token\"");
        return builder.body(body);
    }
}
