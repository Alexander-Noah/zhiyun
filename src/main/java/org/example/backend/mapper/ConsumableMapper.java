package org.example.backend.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.backend.entity.ConsumableEntity;
import org.example.backend.entity.ConsumableStockRecordEntity;

import java.util.List;

@Mapper
public interface ConsumableMapper {
    void createStockRecordTableIfNotExists();

    List<ConsumableEntity> listConsumables();

    ConsumableEntity getConsumable(@Param("id") Long id);

    int insertConsumable(ConsumableEntity consumable);

    int updateConsumable(@Param("id") Long id, @Param("consumable") ConsumableEntity consumable);

    int updateConsumableStock(
            @Param("id") Long id,
            @Param("stock") Integer stock,
            @Param("status") String status,
            @Param("tagType") String tagType
    );

    int deleteConsumable(@Param("id") Long id);

    int deleteAllConsumables();

    int insertStockRecord(ConsumableStockRecordEntity record);

    List<ConsumableStockRecordEntity> listStockRecords(@Param("consumableId") Long consumableId);
}
