package org.example.backend.service;

import org.example.backend.entity.NoticeEntity;
import org.example.backend.entity.NoticeRecipientEntity;

import java.util.List;
import java.util.Map;

public interface NoticeService {
    List<NoticeEntity> listNotices();

    NoticeEntity createNotice(NoticeEntity notice);

    NoticeEntity updateNotice(Long id, NoticeEntity notice);

    List<NoticeEntity> deleteNotice(Long id);

    List<NoticeEntity> replaceNotices(List<NoticeEntity> notices);

    List<NoticeEntity> resetNotices();

    NoticeEntity publishNotice(Long id);

    NoticeEntity withdrawNotice(Long id);

    NoticeEntity archiveNotice(Long id);

    List<NoticeRecipientEntity> listRecipients(Long noticeId);

    List<NoticeRecipientEntity> listUserNotices(Integer userId, String roleCode, String readStatus);

    NoticeRecipientEntity markRead(Long noticeId, Integer userId, String roleCode);

    Map<String, Object> getNoticeStats(Integer userId, String roleCode);

    Map<String, Object> syncBusinessReminders();
}
