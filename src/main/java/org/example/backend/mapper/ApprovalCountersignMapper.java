package org.example.backend.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.backend.entity.ApprovalCountersignEntity;

import java.util.List;

@Mapper
public interface ApprovalCountersignMapper {
    int insertCountersign(ApprovalCountersignEntity countersign);

    ApprovalCountersignEntity getCountersign(@Param("id") Long id);

    List<ApprovalCountersignEntity> listCountersigns(
            @Param("businessType") String businessType,
            @Param("businessId") String businessId,
            @Param("assigneeName") String assigneeName,
            @Param("status") String status
    );

    int completeCountersign(
            @Param("id") Long id,
            @Param("status") String status,
            @Param("result") String result,
            @Param("resultRemark") String resultRemark
    );

    int cancelCountersign(@Param("id") Long id, @Param("resultRemark") String resultRemark);

    int countActiveByBusiness(@Param("businessType") String businessType, @Param("businessId") String businessId);
}
