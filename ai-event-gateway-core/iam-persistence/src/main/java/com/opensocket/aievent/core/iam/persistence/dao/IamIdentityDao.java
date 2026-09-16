package com.opensocket.aievent.core.iam.persistence.dao;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

/** Instance-scope IAM identity mapper. It is intentionally not tenant-intercepted. */
public interface IamIdentityDao {
    Map<String,Object> findRoot();
    int insertRoot(@Param("row") Map<String,Object> row);
    int updateRoot(@Param("row") Map<String,Object> row, @Param("expectedVersion") long expectedVersion);

    Map<String,Object> findUserById(@Param("userId") String userId);
    Map<String,Object> findUserByNormalizedUsername(@Param("value") String value);
    Map<String,Object> findUserByNormalizedEmail(@Param("value") String value);
    int countUserByNormalizedUsername(@Param("value") String value);
    int countUserByNormalizedEmail(@Param("value") String value);
    int insertUser(@Param("row") Map<String,Object> row);
    int updateUser(@Param("row") Map<String,Object> row, @Param("expectedVersion") long expectedVersion);
    List<Map<String,Object>> searchUsers(@Param("text") String text, @Param("status") String status,
                                         @Param("cursor") String cursor, @Param("limit") int limit);
}
