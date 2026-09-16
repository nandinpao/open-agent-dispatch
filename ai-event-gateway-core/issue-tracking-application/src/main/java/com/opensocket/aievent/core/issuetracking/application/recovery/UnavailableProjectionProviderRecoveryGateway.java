package com.opensocket.aievent.core.issuetracking.application.recovery;
import com.opensocket.aievent.core.issuetracking.recovery.*;
public final class UnavailableProjectionProviderRecoveryGateway implements ProjectionProviderRecoveryGateway { public ProviderReadbackResult readback(ProviderReadbackCommand c){return ProviderReadbackResult.unavailable();} public ReconciliationRepairResult repair(ReconciliationRepairCommand c){return ReconciliationRepairResult.unavailable();} public String mode(){return "UNAVAILABLE_FAIL_CLOSED";} }
