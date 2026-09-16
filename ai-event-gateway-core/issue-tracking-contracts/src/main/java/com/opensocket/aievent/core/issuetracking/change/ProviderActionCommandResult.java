package com.opensocket.aievent.core.issuetracking.change;
public record ProviderActionCommandResult(boolean accepted,boolean retryable,String evidenceReference,String reasonCode,String safeMessage) {
 public static ProviderActionCommandResult accepted(String evidence){return new ProviderActionCommandResult(true,false,evidence,"COMMAND_ACCEPTED","Controlled command accepted.");}
 public static ProviderActionCommandResult unavailable(){return new ProviderActionCommandResult(false,true,"","COMMAND_PORT_UNAVAILABLE","Controlled Task/A2A command port is unavailable.");}
}
