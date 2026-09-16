package com.opensocket.aievent.core.resourceaccess.core;

import static org.junit.jupiter.api.Assertions.*;
import com.opensocket.aievent.core.resourceaccess.contract.*;
import org.junit.jupiter.api.Test;
class ResourcePolicyScaleTest {
    @Test void explicitScaleControlsClearanceAndVisibility(){
        assertTrue(ResourcePolicyScale.clearanceCovers(SensitivityLevel.SECRET,SensitivityLevel.RESTRICTED));
        assertFalse(ResourcePolicyScale.clearanceCovers(SensitivityLevel.INTERNAL,SensitivityLevel.CONFIDENTIAL));
        assertTrue(ResourcePolicyScale.visibilityCovers(VisibilityLevel.FULL,VisibilityLevel.SUMMARY));
        assertFalse(ResourcePolicyScale.visibilityCovers(VisibilityLevel.METADATA,VisibilityLevel.STANDARD));
        assertFalse(ResourcePolicyScale.visibilityCovers(VisibilityLevel.FULL,VisibilityLevel.SECRET_METADATA));
        assertTrue(ResourcePolicyScale.visibilityCovers(VisibilityLevel.SECRET_METADATA,VisibilityLevel.METADATA));
    }
}
