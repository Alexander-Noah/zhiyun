package org.example.backend.service;

import org.example.backend.entity.NoticeEntity;

import java.util.List;

public interface NoticeService {
    List<NoticeEntity> listNotices();

    NoticeEntity createNotice(NoticeEntity notice);

    NoticeEntity updateNotice(Long id, NoticeEntity notice);

    List<NoticeEntity> deleteNotice(Long id);

    List<NoticeEntity> replaceNotices(List<NoticeEntity> notices);

    List<NoticeEntity> resetNotices();
}
