package com.opensocket.aievent.core.capability;

/** Phase 8 Task-lineage bridge. A runtime Step must obtain an authoritative OpenDispatch child Task before adapter submission. */
public interface PlanChildTaskPort { PlanChildTaskReference createChildTask(PlanChildTaskRequest request); }
