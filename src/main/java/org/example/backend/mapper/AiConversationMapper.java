package org.example.backend.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.backend.entity.AiConversationEntity;

import java.util.List;

@Mapper
public interface AiConversationMapper {
    List<AiConversationEntity> listByUser(@Param("userId") Integer userId);

    int upsertConversation(AiConversationEntity conversation);

    int trimUserConversations(@Param("userId") Integer userId, @Param("maxCount") int maxCount);
}
