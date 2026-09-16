package com.opensocket.aievent.core.issuetracking.change;
public interface ProviderActionCommandPort { ProviderActionCommandResult execute(ProviderActionCommand command); default String mode(){return "CUSTOM";} }
