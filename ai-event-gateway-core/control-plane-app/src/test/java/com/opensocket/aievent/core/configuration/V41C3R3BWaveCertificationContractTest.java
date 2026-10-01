package com.opensocket.aievent.core.configuration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import com.opensocket.aievent.core.kernel.configuration.cutover.RuntimeConfigurationCutoverWaveCertification;

class V41C3R3BWaveCertificationContractTest {
    @Test void certificationRejectsIncompleteConvergence(){
        assertThatThrownBy(()->new RuntimeConfigurationCutoverWaveCertification("c","C3R3-W1","PASS",2,6,12,6,5,"{}","operator","reason",OffsetDateTime.now()))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("complete");
    }
    @Test void certificationRejectsAuthorityContractV1(){
        assertThatThrownBy(()->new RuntimeConfigurationCutoverWaveCertification("c","C3R3-W1","PASS",1,6,12,6,6,"{}","operator","reason",OffsetDateTime.now()))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("v2");
    }
}
