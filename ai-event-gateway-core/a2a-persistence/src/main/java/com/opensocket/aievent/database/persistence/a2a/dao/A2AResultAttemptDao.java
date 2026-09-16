package com.opensocket.aievent.database.persistence.a2a.dao;
import java.util.List; import org.apache.ibatis.annotations.*; import com.opensocket.aievent.database.persistence.a2a.po.A2AResultAttemptPo;
@Mapper public interface A2AResultAttemptDao { int insert(@Param("attempt") A2AResultAttemptPo attempt); List<A2AResultAttemptPo> findByRequest(@Param("tenantId")String tenantId,@Param("requestId")String requestId,@Param("limit")int limit); }
