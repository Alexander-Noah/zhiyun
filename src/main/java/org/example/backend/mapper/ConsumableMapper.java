package org.example.backend.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.backend.entity.ConsumableEntity;

import java.util.List;

@Mapper
public interface ConsumableMapper {
    List<ConsumableEntity> listConsumables();

    ConsumableEntity getConsumable(@Param("id") Long id);

    int insertConsumable(ConsumableEntity consumable);

    int updateConsumable(@Param("id") Long id, @Param("consumable") ConsumableEntity consumable);

    int deleteConsumable(@Param("id") Long id);

    int deleteAllConsumables();
}
