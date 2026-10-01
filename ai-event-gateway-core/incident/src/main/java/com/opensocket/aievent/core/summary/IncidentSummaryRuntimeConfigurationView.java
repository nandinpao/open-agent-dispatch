package com.opensocket.aievent.core.summary;

import java.time.Duration;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationAuthorityRegistry;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSetKeys;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSnapshotValues;

/** V41-C3R2N typed Runtime Configuration view for incident occurrence summarization. */
@Component
public final class IncidentSummaryRuntimeConfigurationView {
 public static final String SET_KEY=RuntimeConfigurationSetKeys.INCIDENT_SYSTEM;
 public static final String WINDOW="incident.summary.window";
 public static final Set<String> ALL=Set.of(WINDOW);
 private final RuntimeConfigurationSnapshotValues values; private final RuntimeConfigurationAuthorityRegistry authority; private final IncidentSummaryProperties startup;
 @Autowired public IncidentSummaryRuntimeConfigurationView(RuntimeConfigurationSnapshotValues values,RuntimeConfigurationAuthorityRegistry authority,IncidentSummaryProperties startup){this.values=values;this.authority=authority;this.startup=startup;}
 public IncidentSummaryRuntimeConfigurationView(IncidentSummaryProperties startup){this(null,null,startup==null?new IncidentSummaryProperties():startup);}
 public Duration window(){Duration fallback=startup.getWindow(); Duration value;
  if(values==null)value=fallback; else if(authority!=null&&authority.isRuntimeAuthoritative(WINDOW)){if(!values.hasSnapshot(SET_KEY))throw incomplete();values.requireKeys(SET_KEY,ALL);value=values.durationValue(SET_KEY,WINDOW).orElseThrow(IncidentSummaryRuntimeConfigurationView::incomplete);} else value=values.durationValue(SET_KEY,WINDOW).orElse(fallback);
  if(value==null||value.compareTo(Duration.ofSeconds(1))<0||value.compareTo(Duration.ofHours(24))>0)throw new IllegalStateException("RUNTIME_CONFIG_VALIDATION_FAILED key="+WINDOW+" value="+value); return value;}
 private static IllegalStateException incomplete(){return new IllegalStateException("CONFIGURATION_INCOMPLETE setKey="+SET_KEY+" key="+WINDOW);}
}
