package com.opensocket.aievent.core.resourceaccess.contract;
/** Evaluation intent. SIMULATION and SHADOW evidence can never be used as an execution token. */
public enum AuthorizationDecisionMode { FORMAL, EXPLAIN, SIMULATION, SHADOW }
