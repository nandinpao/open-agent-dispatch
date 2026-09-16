package com.opensocket.aievent.database.persistence.a2a.dao;
import java.util.List;import org.apache.ibatis.annotations.Mapper;import org.apache.ibatis.annotations.Param;import com.opensocket.aievent.database.persistence.a2a.po.*;
@Mapper public interface A2ACutoverDao {
 A2ACutoverStatePo find(@Param("scopeId")String scopeId);
 int upsert(@Param("value")A2ACutoverStatePo value);
 int updateExpectedVersion(@Param("value")A2ACutoverStatePo value,@Param("expectedVersion")long expectedVersion);
 int appendEvidence(@Param("value")A2ACutoverEvidencePo value);
 A2ACutoverEvidencePo findEvidence(@Param("scopeId")String scopeId,@Param("evidenceId")String evidenceId);
 List<A2ACutoverEvidencePo> evidence(@Param("scopeId")String scopeId,@Param("limit")int limit);
}
