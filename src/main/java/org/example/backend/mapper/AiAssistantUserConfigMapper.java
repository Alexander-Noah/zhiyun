package org.example.backend.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.backend.entity.AiAssistantUserConfigEntity;

@Mapper
public interface AiAssistantUserConfigMapper {
    AiAssistantUserConfigEntity getByUserId(@Param("userId") Integer userId);

    int upsertConfig(AiAssistantUserConfigEntity config);
}
