package com.opensocket.aievent.core.iam.token.domain;
public enum AccessTokenType {
    PERSONAL_ACCESS_TOKEN("pat", false), SERVICE_ACCOUNT_TOKEN("sat", false),
    INVITATION_TOKEN("invite", true), PASSWORD_RESET_TOKEN("reset", true), EMAIL_VERIFICATION_TOKEN("verify", true);
    private final String prefix; private final boolean oneTime;
    AccessTokenType(String prefix, boolean oneTime){this.prefix=prefix;this.oneTime=oneTime;}
    public String prefix(){return prefix;} public boolean oneTime(){return oneTime;}
}
