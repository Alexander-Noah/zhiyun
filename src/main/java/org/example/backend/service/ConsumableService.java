package org.example.backend.service;

import org.example.backend.entity.ConsumableEntity;

import java.util.List;

public interface ConsumableService {
    List<ConsumableEntity> listConsumables();

    ConsumableEntity createConsumable(ConsumableEntity consumable);

    ConsumableEntity updateConsumable(Long id, ConsumableEntity consumable);

    List<ConsumableEntity> deleteConsumable(Long id);

    List<ConsumableEntity> replaceConsumables(List<ConsumableEntity> consumables);

    List<ConsumableEntity> resetConsumables();
}
