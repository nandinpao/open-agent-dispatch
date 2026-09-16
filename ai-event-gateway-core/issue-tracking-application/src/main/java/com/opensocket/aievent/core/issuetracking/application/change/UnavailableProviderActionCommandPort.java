package com.opensocket.aievent.core.issuetracking.application.change;
import com.opensocket.aievent.core.issuetracking.change.*;
public final class UnavailableProviderActionCommandPort implements ProviderActionCommandPort { public ProviderActionCommandResult execute(ProviderActionCommand c){return ProviderActionCommandResult.unavailable();} public String mode(){return "UNAVAILABLE";} }
