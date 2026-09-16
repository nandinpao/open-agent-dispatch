package com.opensocket.aievent.core.dispatch;
import org.springframework.stereotype.Component;
import com.opensocket.aievent.core.a2a.application.port.out.A2ADispatchRepairPort;
@Component public class DefaultA2ADispatchRepairAdapter implements A2ADispatchRepairPort { private final DispatchBridgeReconciliationService service; public DefaultA2ADispatchRepairAdapter(DispatchBridgeReconciliationService s){service=s;} public RepairResult reconcile(String id){boolean repaired=service.reconcileById(id);return new RepairResult(repaired,!repaired,"dispatch-reconcile:"+id,repaired?"DISPATCH_REPAIRED":"DISPATCH_NOT_DUE",repaired?"Dispatch Authority repaired the request":"Dispatch request was not repairable from current evidence");}}
