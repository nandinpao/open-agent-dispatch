package com.opensocket.aievent.core.iam.runtime.machine;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationConverter;

/** Extracts client_secret_basic while attaching trusted network/correlation evidence to client authentication. */
public final class OpenDispatchClientSecretBasicAuthenticationConverter implements AuthenticationConverter {
    public static final String SOURCE_IP="opendispatch_source_ip";
    public static final String CORRELATION_ID="opendispatch_correlation_id";
    private final TrustedClientIpResolver clientIp;
    public OpenDispatchClientSecretBasicAuthenticationConverter(TrustedClientIpResolver clientIp){this.clientIp=clientIp;}

    @Override public Authentication convert(HttpServletRequest request){
        String header=request.getHeader(HttpHeaders.AUTHORIZATION);
        if(header==null||!header.regionMatches(true,0,"Basic ",0,6))return null;
        ClientSecret client=basic(header);
        Map<String,Object> additional=new LinkedHashMap<>();
        additional.put(SOURCE_IP,clientIp.resolve(request));
        additional.put(CORRELATION_ID,correlation(request));
        return new OAuth2ClientAuthenticationToken(client.clientId(),ClientAuthenticationMethod.CLIENT_SECRET_BASIC,client.secret(),additional);
    }

    private static ClientSecret basic(String header){
        try{
            byte[] raw=Base64.getDecoder().decode(header.substring(6).trim());
            if(raw.length>1024)return new ClientSecret("","");
            String decoded=new String(raw,StandardCharsets.UTF_8);int colon=decoded.indexOf(':');
            if(colon<1)return new ClientSecret("","");
            return new ClientSecret(URLDecoder.decode(decoded.substring(0,colon),StandardCharsets.UTF_8),
                    URLDecoder.decode(decoded.substring(colon+1),StandardCharsets.UTF_8));
        }catch(Exception e){return new ClientSecret("","");}
    }
    private static String correlation(HttpServletRequest request){String v=request.getHeader("X-Correlation-Id");return v!=null&&v.matches("[A-Za-z0-9._:-]{1,128}")?v:UUID.randomUUID().toString();}
    private record ClientSecret(String clientId,String secret){}
}
