package com.opensocket.aievent.core.issuetracking.relay;
public interface RelayProviderGateway { RelayProviderResult execute(RelayProviderCommand command); default String mode(){return "CUSTOM";} }
