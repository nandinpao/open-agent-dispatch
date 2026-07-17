package com.opensocket.aievent.database.persistence.eventprocessing.dao;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.opensocket.aievent.database.persistence.eventprocessing.po.EventDecisionPo;

@Mapper
public interface EventDecisionDao {
    int upsert(@Param("decision") EventDecisionPo decision);
    List<EventDecisionPo> findRecent(@Param("limit") int limit);
}
