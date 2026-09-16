package com.opensocket.aievent.core.iam.runtime.machine;

import com.opensocket.aievent.core.iam.security.contract.SecurityEpoch;
import com.opensocket.aievent.core.iam.token.application.port.out.MachineJwtCodecPort;
import com.opensocket.aievent.core.iam.token.domain.MachineSigningKey;
import com.opensocket.aievent.core.iam.token.domain.MachineTokenClaims;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import tools.jackson.databind.ObjectMapper;

/** Spring Security/Nimbus RS256 adapter. The domain module remains framework-free. */
public final class SpringSecurityMachineJwtCodec implements MachineJwtCodecPort {
    private final ObjectMapper json;
    public SpringSecurityMachineJwtCodec(ObjectMapper json) { this.json = Objects.requireNonNull(json); }

    @Override
    public String encode(SigningMaterial material, MachineTokenClaims claims) {
        try {
            RSAPublicKey pub = publicKey(material.publicKeyDerBase64());
            RSAPrivateKey priv = privateKey(material.privateKeyPkcs8Base64());
            NimbusJwtEncoder encoder = NimbusJwtEncoder.withKeyPair(pub, priv)
                    .algorithm(SignatureAlgorithm.RS256)
                    .jwkPostProcessor(jwk -> jwk.keyID(material.keyId()))
                    .build();
            JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                    .keyId(material.keyId()).type("at+jwt").build();
            JwtClaimsSet set = JwtClaimsSet.builder()
                    .issuer(claims.issuer()).subject(claims.subject())
                    .audience(new ArrayList<>(claims.audiences()))
                    .issuedAt(claims.issuedAt()).notBefore(claims.notBefore()).expiresAt(claims.expiresAt())
                    .id(claims.jwtId())
                    .claim("principal_type", claims.principalType())
                    .claim("tenant_id", claims.tenantId())
                    .claim("credential_id", claims.credentialId())
                    .claim("client_id", claims.clientId())
                    .claim("scope", String.join(" ", claims.scopes()))
                    .claim("source_systems", new ArrayList<>(claims.sourceSystems()))
                    .claim("api_prefixes", new ArrayList<>(claims.apiPrefixes()))
                    .claim("cidrs", new ArrayList<>(claims.cidrs()))
                    .claim("sec_ge", claims.securityEpoch().globalEpoch())
                    .claim("sec_te", claims.securityEpoch().tenantEpoch())
                    .claim("sec_pe", claims.securityEpoch().principalEpoch())
                    .build();
            return encoder.encode(JwtEncoderParameters.from(header, set)).getTokenValue();
        } catch (Exception e) {
            throw new IllegalArgumentException("MACHINE_JWT_ENCODING_FAILED", e);
        }
    }

    @Override
    public DecodedToken decodeAndVerify(String compactToken, List<MachineSigningKey> publishableKeys,
                                        String expectedIssuer, Duration clockSkew, Instant now) {
        try {
            Map<String,Object> header = parseHeader(compactToken);
            if (!"RS256".equals(String.valueOf(header.get("alg")))) throw invalid();
            if (!"at+jwt".equals(String.valueOf(header.get("typ")))) throw invalid();
            String kid = string(header.get("kid"));
            if (kid.isBlank()) throw invalid();
            MachineSigningKey key = publishableKeys.stream().filter(k -> k.keyId().equals(kid) && k.publishableAt(now)).findFirst().orElseThrow(SpringSecurityMachineJwtCodec::invalid);
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey(key.publicKeyDerBase64()))
                    .signatureAlgorithm(SignatureAlgorithm.RS256).build();
            JwtTimestampValidator timestamps = new JwtTimestampValidator(clockSkew);
            timestamps.setAllowEmptyExpiryClaim(false);
            timestamps.setAllowEmptyNotBeforeClaim(false);
            timestamps.setClock(java.time.Clock.fixed(now, java.time.ZoneOffset.UTC));
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamps, new JwtIssuerValidator(expectedIssuer)));
            Jwt jwt = decoder.decode(compactToken);
            if (!expectedIssuer.equals(jwt.getIssuer() == null ? "" : jwt.getIssuer().toString())) throw invalid();
            Instant exp = jwt.getExpiresAt(), nbf = jwt.getNotBefore(), iat = jwt.getIssuedAt();
            if (exp == null || nbf == null || iat == null) throw invalid();
            if (now.minus(clockSkew).isAfter(exp)) throw invalid();
            if (now.plus(clockSkew).isBefore(nbf)) throw invalid();
            Set<String> scopes = splitScope(string(jwt.getClaims().get("scope")));
            MachineTokenClaims claims = new MachineTokenClaims(
                    expectedIssuer, jwt.getSubject(), string(jwt.getClaims().get("principal_type")),
                    string(jwt.getClaims().get("tenant_id")), string(jwt.getClaims().get("credential_id")),
                    string(jwt.getClaims().get("client_id")), jwt.getId(), scopes,
                    Set.copyOf(jwt.getAudience()), strings(jwt.getClaims().get("source_systems")),
                    strings(jwt.getClaims().get("api_prefixes")), strings(jwt.getClaims().get("cidrs")),
                    new SecurityEpoch(number(jwt,"sec_ge"), number(jwt,"sec_te"), number(jwt,"sec_pe")),
                    iat, nbf, exp);
            return new DecodedToken(kid, claims);
        } catch (RuntimeException e) {
            if ("MACHINE_JWT_INVALID".equals(e.getMessage())) throw e;
            throw invalid(e);
        } catch (Exception e) {
            throw invalid(e);
        }
    }

    @Override
    public String jwksJson(List<MachineSigningKey> publishableKeys) {
        try {
            List<Map<String,Object>> keys = new ArrayList<>();
            for (MachineSigningKey key : publishableKeys) {
                RSAPublicKey pub = publicKey(key.publicKeyDerBase64());
                Map<String,Object> jwk = new LinkedHashMap<>();
                jwk.put("kty","RSA"); jwk.put("use","sig"); jwk.put("alg","RS256"); jwk.put("kid",key.keyId());
                jwk.put("n", unsigned(pub.getModulus())); jwk.put("e", unsigned(pub.getPublicExponent()));
                keys.add(jwk);
            }
            return json.writeValueAsString(Map.of("keys", keys));
        } catch (Exception e) { throw new IllegalStateException("MACHINE_JWKS_RENDER_FAILED", e); }
    }

    @SuppressWarnings("unchecked")
    private Map<String,Object> parseHeader(String token) throws Exception {
        if (token == null || token.length() > 16384) throw invalid();
        String[] parts = token.split("\\.",-1); if (parts.length != 3) throw invalid();
        byte[] bytes = Base64.getUrlDecoder().decode(parts[0]);
        if (bytes.length > 4096) throw invalid();
        return json.readValue(bytes, Map.class);
    }
    private static RSAPublicKey publicKey(String b64) throws Exception { return (RSAPublicKey)KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(b64))); }
    private static RSAPrivateKey privateKey(String b64) throws Exception { return (RSAPrivateKey)KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(b64))); }
    private static String unsigned(BigInteger value) { byte[] b=value.toByteArray(); if(b.length>1&&b[0]==0)b=Arrays.copyOfRange(b,1,b.length); return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
    private static String string(Object v){return v==null?"":String.valueOf(v);}
    private static long number(Jwt jwt,String name){Object v=jwt.getClaims().get(name); return v instanceof Number n?n.longValue():Long.parseLong(string(v));}
    private static Set<String> strings(Object value){if(!(value instanceof Collection<?> c))return Set.of();TreeSet<String>s=new TreeSet<>();for(Object v:c)if(v!=null&&!String.valueOf(v).isBlank())s.add(String.valueOf(v));return Set.copyOf(s);}
    private static Set<String> splitScope(String scope){if(scope==null||scope.isBlank())return Set.of();return Set.copyOf(Arrays.asList(scope.trim().split("\\s+")));}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("MACHINE_JWT_INVALID");}
    private static IllegalArgumentException invalid(Throwable cause){return new IllegalArgumentException("MACHINE_JWT_INVALID",cause);}
}
