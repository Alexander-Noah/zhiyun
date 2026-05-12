package org.example.backend.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.backend.entity.NoticeEntity;

import java.util.List;

@Mapper
public interface NoticeMapper {
    List<NoticeEntity> listNotices();

    NoticeEntity getNotice(@Param("id") Long id);

    int insertNotice(NoticeEntity notice);

    int updateNotice(@Param("id") Long id, @Param("notice") NoticeEntity notice);

    int deleteNotice(@Param("id") Long id);

    int deleteAllNotices();
}
