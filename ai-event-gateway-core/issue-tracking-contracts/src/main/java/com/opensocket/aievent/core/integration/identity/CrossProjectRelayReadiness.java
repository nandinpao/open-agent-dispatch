package com.opensocket.aievent.core.integration.identity;
import java.util.List;
public record CrossProjectRelayReadiness(boolean ready,String sourceMappingId,String targetMappingId,List<String> blockers){ public CrossProjectRelayReadiness { blockers=blockers==null?List.of():List.copyOf(blockers); } }
