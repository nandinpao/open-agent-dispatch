package com.opensocket.aievent.core.iam.persistence.dao;

import java.util.Map;
import org.apache.ibatis.annotations.Param;

public interface IamOutboxDao {
    int insert(@Param("row") Map<String,Object> row);
}
