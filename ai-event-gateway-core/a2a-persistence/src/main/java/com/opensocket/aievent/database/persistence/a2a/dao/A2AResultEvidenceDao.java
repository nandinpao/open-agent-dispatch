package com.opensocket.aievent.database.persistence.a2a.dao;
import java.util.List; import org.apache.ibatis.annotations.*; import com.opensocket.aievent.database.persistence.a2a.po.A2AResultEvidencePo;
@Mapper public interface A2AResultEvidenceDao { int insert(@Param("evidence") A2AResultEvidencePo evidence); List<A2AResultEvidencePo> findByAttempt(@Param("tenantId")String tenantId,@Param("attemptId")String attemptId,@Param("limit")int limit); }
