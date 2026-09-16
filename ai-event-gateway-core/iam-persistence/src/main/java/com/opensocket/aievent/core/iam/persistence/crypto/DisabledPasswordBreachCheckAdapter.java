package com.opensocket.aievent.core.iam.persistence.crypto;
import com.opensocket.aievent.core.iam.authentication.application.port.out.PasswordBreachCheckPort;
/** Explicit disabled adapter; production may replace it with an offline or privacy-preserving breach checker. */
public final class DisabledPasswordBreachCheckAdapter implements PasswordBreachCheckPort { public boolean breached(char[] password){return false;} }
