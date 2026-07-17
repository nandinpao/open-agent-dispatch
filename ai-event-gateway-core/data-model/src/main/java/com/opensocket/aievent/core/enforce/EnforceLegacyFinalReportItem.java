package com.opensocket.aievent.core.enforce;

import java.util.List;

public record EnforceLegacyFinalReportItem(String category, long count, String severity, List<String> sampleRefs) {
}
