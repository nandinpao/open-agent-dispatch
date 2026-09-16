package com.opensocket.aievent.core.resourceaccess.contract;
/** SQL strategy selected for Issue/Integration list authorization. */
public enum IntegrationScopeQueryStrategy { DENY_ALL, TENANT, DIRECT_HIERARCHY_JOIN, EXPLICIT_RESOURCE_SET, HYBRID }
