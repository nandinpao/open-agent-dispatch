package com.opensocket.aievent.core.issuetracking.relay;
public interface A2ARelayPolicySignalPort { RelayPolicySignalResult publish(RelayPolicySignal signal); default String mode(){return "CUSTOM";} }
