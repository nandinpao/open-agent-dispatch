package com.opensocket.aievent.core.issuetracking.core;
import static org.junit.jupiter.api.Assertions.*;import org.junit.jupiter.api.Test;
class ExternalSyncMarkerTest {@Test void markerRoundTripsAndSuppressesEcho(){String marker=ExternalSyncMarker.create("tenant","connection","COMMENT","source-1");String body="Comment\n\n"+ExternalSyncMarker.bodyMarker(marker);assertEquals(marker,ExternalSyncMarker.extract(body));assertTrue(ExternalSyncMarker.isOpenDispatchMarker(marker));}}
