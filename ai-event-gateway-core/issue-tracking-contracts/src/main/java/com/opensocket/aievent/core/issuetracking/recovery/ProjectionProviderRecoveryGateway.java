package com.opensocket.aievent.core.issuetracking.recovery;
public interface ProjectionProviderRecoveryGateway { ProviderReadbackResult readback(ProviderReadbackCommand command); ReconciliationRepairResult repair(ReconciliationRepairCommand command); default String mode(){return "CUSTOM";} }
