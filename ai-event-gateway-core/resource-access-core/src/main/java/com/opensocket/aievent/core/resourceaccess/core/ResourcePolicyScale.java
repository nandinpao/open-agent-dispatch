package com.opensocket.aievent.core.resourceaccess.core;

import com.opensocket.aievent.core.resourceaccess.contract.SensitivityLevel;
import com.opensocket.aievent.core.resourceaccess.contract.VisibilityLevel;
import java.util.Map;

/** Explicit policy ordering; callers must not depend on enum ordinal. */
public final class ResourcePolicyScale {
    private static final Map<SensitivityLevel,Integer> SENSITIVITY=Map.of(
            SensitivityLevel.PUBLIC,0,SensitivityLevel.INTERNAL,1,SensitivityLevel.CONFIDENTIAL,2,
            SensitivityLevel.RESTRICTED,3,SensitivityLevel.SECRET,4);
    private static final Map<VisibilityLevel,Integer> VISIBILITY=Map.of(
            VisibilityLevel.NONE,0,VisibilityLevel.METADATA,1,VisibilityLevel.SUMMARY,2,
            VisibilityLevel.STANDARD,3,VisibilityLevel.SENSITIVE,4,VisibilityLevel.FULL,5,
            VisibilityLevel.SECRET_METADATA,4);
    private ResourcePolicyScale(){}
    public static boolean clearanceCovers(SensitivityLevel clearance,SensitivityLevel resource){return SENSITIVITY.get(clearance)>=SENSITIVITY.get(resource);}
    public static VisibilityLevel lowerVisibility(VisibilityLevel a,VisibilityLevel b){
        if(a==VisibilityLevel.SECRET_METADATA||b==VisibilityLevel.SECRET_METADATA){
            if(a==b)return a;
            return VISIBILITY.get(a)<=VISIBILITY.get(b)?a:b;
        }
        return VISIBILITY.get(a)<=VISIBILITY.get(b)?a:b;
    }
    public static VisibilityLevel higherVisibility(VisibilityLevel a,VisibilityLevel b){
        if(a==VisibilityLevel.SECRET_METADATA||b==VisibilityLevel.SECRET_METADATA){
            if(a==b)return a;
            return VISIBILITY.get(a)>=VISIBILITY.get(b)?a:b;
        }
        return VISIBILITY.get(a)>=VISIBILITY.get(b)?a:b;
    }
    public static boolean visibilityCovers(VisibilityLevel granted,VisibilityLevel requested){
        if(requested==VisibilityLevel.SECRET_METADATA)return granted==VisibilityLevel.SECRET_METADATA;
        if(granted==VisibilityLevel.SECRET_METADATA)return requested==VisibilityLevel.NONE||requested==VisibilityLevel.METADATA;
        return VISIBILITY.get(granted)>=VISIBILITY.get(requested);
    }
}
