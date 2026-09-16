package com.opensocket.aievent.core.governance;
public final class OutboxResponsibilityBoundary {private OutboxResponsibilityBoundary(){}
 public enum OutboxKind { MODULE_OUTBOX, EXPORTED_DOMAIN_EVENT_OUTBOX, PROVIDER_MUTATION_OUTBOX }
 public static String authority(OutboxKind kind){return switch(kind){case MODULE_OUTBOX->"Reliable cross-module Domain Event dispatch inside OpenDispatch.";case EXPORTED_DOMAIN_EVENT_OUTBOX->"Versioned Domain Event export to approved external consumers.";case PROVIDER_MUTATION_OUTBOX->"Effectful Jira, Redmine, or GitLab Issue mutations with provider retry evidence.";};}
}
