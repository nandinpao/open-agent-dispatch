package com.opensocket.aievent.core.iam.persistence.crypto;

import com.opensocket.aievent.core.iam.authentication.application.port.out.PasswordHashingPort;
import com.opensocket.aievent.core.iam.authentication.domain.PasswordHash;
import java.nio.CharBuffer;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

/** Argon2id adapter; encoded value includes salt and cost parameters. Caller owns and clears char arrays. */
public final class SpringSecurityArgon2PasswordHashingAdapter implements PasswordHashingPort {
    private final Argon2PasswordEncoder encoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    @Override public PasswordHash hash(char[] rawPassword) {
        if (rawPassword == null || rawPassword.length == 0) throw new IllegalArgumentException("rawPassword is required");
        return new PasswordHash("ARGON2ID", encoder.encode(CharBuffer.wrap(rawPassword)));
    }
    @Override public boolean matches(char[] rawPassword, PasswordHash hash) {
        return rawPassword != null && hash != null && "ARGON2ID".equals(hash.algorithm())
                && encoder.matches(CharBuffer.wrap(rawPassword), hash.encoded());
    }
}
