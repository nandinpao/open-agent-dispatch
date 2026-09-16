package com.opensocket.aievent.core.iam.persistence.outbox;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;

class IamTransactionalOutboxWriterProxyabilityTest {

    @Test
    void concreteRepositoryBeanMustRemainSubclassableForSpringAop() {
        assertFalse(
                Modifier.isFinal(IamTransactionalOutboxWriter.class.getModifiers()),
                "@DatabaseRepositoryAdapter must not be final because "
                        + "Spring persistence-exception translation may require a CGLIB subclass"
        );
    }
}
