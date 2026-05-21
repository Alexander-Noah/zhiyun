package org.example.backend.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.backend.entity.NoticeEntity;
import org.example.backend.entity.NoticeRecipientEntity;

import java.util.List;
import java.util.Map;

@Mapper
public interface NoticeMapper {
    List<NoticeEntity> listNotices();

    NoticeEntity getNotice(@Param("id") Long id);

    int insertNotice(NoticeEntity notice);

    int updateNotice(@Param("id") Long id, @Param("notice") NoticeEntity notice);

    int deleteNotice(@Param("id") Long id);

    int deleteAllNotices();

    int publishNotice(@Param("id") Long id);

    int updateNoticeStatus(@Param("id") Long id, @Param("status") String status);

    int archiveNotice(@Param("id") Long id);

    int logicalDeleteNotice(@Param("id") Long id);

    int deleteRecipientsByNotice(@Param("noticeId") Long noticeId);

    int deleteAllRecipients();

    int insertRecipientsForRole(@Param("noticeId") Long noticeId, @Param("targetRole") String targetRole);

    int syncUserRecipients(@Param("userId") Integer userId, @Param("roleCode") String roleCode);

    List<NoticeRecipientEntity> listRecipients(@Param("noticeId") Long noticeId);

    List<NoticeRecipientEntity> listUserNotices(
            @Param("userId") Integer userId,
            @Param("readStatus") String readStatus
    );

    int markRead(@Param("noticeId") Long noticeId, @Param("userId") Integer userId);

    Map<String, Object> getNoticeStats(@Param("userId") Integer userId);

    int countNoticeBySource(@Param("sourceModule") String sourceModule, @Param("sourceId") String sourceId);

    List<Map<String, Object>> listLowStockConsumables();

    List<Map<String, Object>> listPendingReservations();

    List<Map<String, Object>> listActiveRepairs();
}
