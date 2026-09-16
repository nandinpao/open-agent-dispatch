package com.opensocket.aievent.core.iam.runtime.machine;

public final class MachineOAuthException extends RuntimeException {
    private final String oauthError;
    private final String reasonCode;
    private final int httpStatus;
    private final boolean authenticateChallenge;
    public MachineOAuthException(String oauthError,String reasonCode,int httpStatus,String message,boolean authenticateChallenge){super(message);this.oauthError=oauthError;this.reasonCode=reasonCode;this.httpStatus=httpStatus;this.authenticateChallenge=authenticateChallenge;}
    public String oauthError(){return oauthError;} public String reasonCode(){return reasonCode;} public int httpStatus(){return httpStatus;} public boolean authenticateChallenge(){return authenticateChallenge;}
    public static MachineOAuthException invalidClient(){return new MachineOAuthException("invalid_client","MACHINE_OAUTH_INVALID_CLIENT",401,"Client authentication failed.",true);}
    public static MachineOAuthException unsupportedGrant(){return new MachineOAuthException("unsupported_grant_type","MACHINE_OAUTH_UNSUPPORTED_GRANT_TYPE",400,"Only client_credentials is supported.",false);}
    public static MachineOAuthException invalidScope(){return new MachineOAuthException("invalid_scope","MACHINE_OAUTH_INVALID_SCOPE",400,"Requested scope is not allowed.",false);}
    public static MachineOAuthException invalidTarget(){return new MachineOAuthException("invalid_target","MACHINE_OAUTH_INVALID_TARGET",400,"Requested audience is not allowed.",false);}
    public static MachineOAuthException rateLimited(){return new MachineOAuthException("temporarily_unavailable","MACHINE_OAUTH_RATE_LIMITED",429,"Token exchange rate limit exceeded.",false);}
    public static MachineOAuthException incidentSuspended(){return new MachineOAuthException("invalid_client","MACHINE_OAUTH_INCIDENT_SUSPENDED",401,"Client authentication failed.",true);}
    public static MachineOAuthException serverError(){return new MachineOAuthException("server_error","MACHINE_OAUTH_SERVER_ERROR",503,"Machine token service is temporarily unavailable.",false);}
}
