package com.opensocket.aievent.core.a2a;

/**
 * Framework-neutral RS6 scope classifier. Organization membership is not authorization; this class only
 * classifies the governed relationship so the IAM/Resource Access kernel can apply the correct authority.
 */
public final class A2AGovernanceScopeClassifier {
    private A2AGovernanceScopeClassifier() {}

    public static A2AGovernanceScopeClass classify(String sourceDepartmentId, String sourceGroupId,
                                                     String targetDepartmentId, String targetGroupId) {
        String sd=normalize(sourceDepartmentId), sg=normalize(sourceGroupId);
        String td=normalize(targetDepartmentId), tg=normalize(targetGroupId);
        boolean sourceScoped=!sd.isEmpty() || !sg.isEmpty();
        boolean targetScoped=!td.isEmpty() || !tg.isEmpty();
        if (!sourceScoped && !targetScoped) return A2AGovernanceScopeClass.TENANT;
        if (sourceScoped && targetScoped && sd.equals(td) && sg.equals(tg)) return A2AGovernanceScopeClass.SAME_SCOPE;
        return A2AGovernanceScopeClass.CROSS_SCOPE;
    }

    public static String ownerDepartmentId(String sourceDepartmentId, String targetDepartmentId) {
        String source=normalize(sourceDepartmentId);
        return source.isEmpty()?normalize(targetDepartmentId):source;
    }

    public static String ownerGroupId(String sourceGroupId, String targetGroupId) {
        String source=normalize(sourceGroupId);
        return source.isEmpty()?normalize(targetGroupId):source;
    }

    private static String normalize(String value) {
        if (value==null || value.isBlank() || "UNASSIGNED".equalsIgnoreCase(value.trim())) return "";
        return value.trim();
    }
}
