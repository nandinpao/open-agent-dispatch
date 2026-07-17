package com.opensocket.aievent.database.persistence.incident.dao;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.opensocket.aievent.database.persistence.incident.po.IncidentOccurrenceSummaryPo;

@Mapper
public interface IncidentOccurrenceSummaryDao {
    int upsertOccurrence(@Param("summary") IncidentOccurrenceSummaryPo summary);
    List<IncidentOccurrenceSummaryPo> findByIncidentId(@Param("incidentId") String incidentId, @Param("limit") int limit);
}
