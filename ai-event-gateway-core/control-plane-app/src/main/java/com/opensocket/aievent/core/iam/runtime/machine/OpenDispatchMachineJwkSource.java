package com.opensocket.aievent.core.iam.runtime.machine;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.opensocket.aievent.core.iam.token.application.port.out.MachineSigningKeyRepository;
import com.opensocket.aievent.core.iam.token.application.service.MachineJwtApplicationService;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.springframework.transaction.support.TransactionTemplate;

/** Publishes the same canonical signing keys used by MachineJwtApplicationService at /oauth2/jwks. */
public final class OpenDispatchMachineJwkSource implements JWKSource<SecurityContext> {
    private final MachineSigningKeyRepository keys;
    private final MachineJwtApplicationService jwt;
    private final Clock clock;
    private final TransactionTemplate transactions;
    public OpenDispatchMachineJwkSource(MachineSigningKeyRepository keys,MachineJwtApplicationService jwt,Clock clock,TransactionTemplate transactions){
        this.keys=keys;this.jwt=jwt;this.clock=clock;this.transactions=transactions;
    }

    @Override public List<JWK> get(com.nimbusds.jose.jwk.JWKSelector selector,SecurityContext context){
        try{
            List<JWK> selected=transactions.execute(status->{
                jwt.ensureSigningKey();
                List<JWK> jwks=new ArrayList<>();
                for(var key:keys.publishable(clock.instant())){
                    try {
                        byte[] der=Base64.getDecoder().decode(key.publicKeyDerBase64());
                        RSAPublicKey publicKey=(RSAPublicKey)KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
                        jwks.add(new RSAKey.Builder(publicKey).keyID(key.keyId()).keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.RS256).build());
                    } catch (Exception keyFailure) {
                        throw new IllegalStateException("Unable to decode machine JWK " + key.keyId(), keyFailure);
                    }
                }
                if(jwks.isEmpty()) throw new IllegalStateException("MACHINE_JWKS_EMPTY_AFTER_SIGNING_KEY_BOOTSTRAP");
                return selector.select(new JWKSet(jwks));
            });
            if(selected==null) throw new IllegalStateException("MACHINE_JWKS_TRANSACTION_RETURNED_NULL");
            return selected;
        }catch(Exception e){throw new IllegalStateException("Unable to publish machine JWK set",e);}
    }
}
