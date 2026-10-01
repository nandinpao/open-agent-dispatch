package com.opensocket.aievent.core.configuration.distribution;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class V41C3R3BAuthorityContractNegotiationTest {
    @Test void v2ControlPlaneServesV1ToLegacyNode(){assertThat(RuntimeConfigurationSnapshotService.negotiateAuthorityContractVersion(2,1)).isEqualTo(1);}
    @Test void v2ControlPlaneServesV2ToUpgradedNode(){assertThat(RuntimeConfigurationSnapshotService.negotiateAuthorityContractVersion(2,2)).isEqualTo(2);}
    @Test void absentCapabilityDefaultsToV1(){assertThat(RuntimeConfigurationSnapshotService.negotiateAuthorityContractVersion(2,0)).isEqualTo(1);}
    @Test void nodeCannotForceControlPlaneAboveConfiguredMaximum(){assertThat(RuntimeConfigurationSnapshotService.negotiateAuthorityContractVersion(1,2)).isEqualTo(1);}
}
