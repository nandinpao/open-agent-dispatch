package com.opensocket.aievent.core.iam.runtime.machine;

import com.opensocket.aievent.core.iam.runtime.config.IamMachineTokenProperties;
import com.opensocket.aievent.core.iam.token.application.result.IssuedMachineAccessTokenResult;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** RFC-style client_credentials endpoint. Human sessions, CSRF and Human R3 are intentionally not involved. */
@RestController
@ConditionalOnProperty(prefix="aeg.iam.machine-token",name={"enabled","legacy-token-endpoint-enabled"},havingValue="true")
public class MachineOAuthTokenController {
    private final IamMachineTokenRuntimeOrchestrator orchestrator;
    private final com.opensocket.aievent.core.iam.token.application.service.MachineJwtApplicationService jwt;
    private final IamMachineTokenProperties properties;
    private final TrustedClientIpResolver clientIp;
    public MachineOAuthTokenController(IamMachineTokenRuntimeOrchestrator orchestrator,com.opensocket.aievent.core.iam.token.application.service.MachineJwtApplicationService jwt,IamMachineTokenProperties properties,TrustedClientIpResolver clientIp){this.orchestrator=orchestrator;this.jwt=jwt;this.properties=properties;this.clientIp=clientIp;}

    @PostMapping(path="/oauth/token",consumes=MediaType.APPLICATION_FORM_URLENCODED_VALUE,produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> token(@RequestParam(name="grant_type",required=false) String grantType,
                                   @RequestParam(name="scope",required=false) String scope,
                                   @RequestParam(name="audience",required=false) String audience,
                                   HttpServletRequest request){
        String correlation=correlation(request); ClientSecret client=basic(request.getHeader(HttpHeaders.AUTHORIZATION));
        try{
            IssuedMachineAccessTokenResult issued=orchestrator.exchange(grantType,client.clientId(),client.secret(),scopes(scope),audience,clientIp.resolve(request),correlation);
            Map<String,Object> body=new LinkedHashMap<>(); body.put("access_token",issued.accessToken());body.put("token_type","Bearer");body.put("expires_in",issued.expiresInSeconds());body.put("scope",String.join(" ",issued.scopes()));body.put("audience",issued.audience());
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(HttpHeaders.PRAGMA,"no-cache").header("X-Correlation-Id",correlation).body(body);
        }catch(MachineOAuthException e){return error(e,correlation);}
    }

    @GetMapping(path="/.well-known/jwks.json",produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> jwks(){return ResponseEntity.ok().cacheControl(CacheControl.maxAge(java.time.Duration.ofSeconds(properties.getJwksCacheMaxAgeSeconds())).cachePublic()).body(jwt.jwksJson());}

    private ResponseEntity<Map<String,Object>> error(MachineOAuthException e,String correlation){Map<String,Object>b=new LinkedHashMap<>();b.put("error",e.oauthError());b.put("error_description",e.getMessage());b.put("correlation_id",correlation);ResponseEntity.BodyBuilder r=ResponseEntity.status(e.httpStatus()).cacheControl(CacheControl.noStore()).header(HttpHeaders.PRAGMA,"no-cache").header("X-Correlation-Id",correlation);if(e.authenticateChallenge())r.header(HttpHeaders.WWW_AUTHENTICATE,"Basic realm=\"OpenDispatch OAuth\"");if(e.httpStatus()==429)r.header(HttpHeaders.RETRY_AFTER,"60");return r.body(b);}
    private static ClientSecret basic(String header){try{if(header==null||!header.regionMatches(true,0,"Basic ",0,6))return new ClientSecret("","");byte[] raw=Base64.getDecoder().decode(header.substring(6).trim());if(raw.length>1024)return new ClientSecret("","");String decoded=new String(raw,StandardCharsets.UTF_8);int colon=decoded.indexOf(':');if(colon<1)return new ClientSecret("","");return new ClientSecret(URLDecoder.decode(decoded.substring(0,colon),StandardCharsets.UTF_8),URLDecoder.decode(decoded.substring(colon+1),StandardCharsets.UTF_8));}catch(Exception e){return new ClientSecret("","");}}
    private static Set<String> scopes(String scope){if(scope==null||scope.isBlank())return Set.of();TreeSet<String>s=new TreeSet<>();for(String v:scope.trim().split("\\s+"))if(!v.isBlank())s.add(v);return Set.copyOf(s);}
    private static String correlation(HttpServletRequest r){String v=r.getHeader("X-Correlation-Id");return v!=null&&v.matches("[A-Za-z0-9._:-]{1,128}")?v:UUID.randomUUID().toString();}
    private record ClientSecret(String clientId,String secret){}
}
