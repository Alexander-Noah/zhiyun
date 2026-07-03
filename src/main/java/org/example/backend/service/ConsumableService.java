package org.example.backend.service;

import org.example.backend.entity.ConsumableEntity;
import org.example.backend.entity.ConsumableStockRecordEntity;

import java.util.List;

public interface ConsumableService {
    List<ConsumableEntity> listConsumables();

    ConsumableEntity createConsumable(ConsumableEntity consumable);

    ConsumableEntity updateConsumable(Long id, ConsumableEntity consumable);

    List<ConsumableEntity> deleteConsumable(Long id);

    List<ConsumableEntity> replaceConsumables(List<ConsumableEntity> consumables);

    List<ConsumableEntity> resetConsumables();

    List<ConsumableStockRecordEntity> listStockRecords(Long consumableId);

    ConsumableMovementResult recordConsumableMovement(Long id, ConsumableStockRecordEntity record);

    class ConsumableMovementResult {
        private ConsumableEntity consumable;
        private ConsumableStockRecordEntity record;
        private List<ConsumableStockRecordEntity> records;

        public ConsumableMovementResult(
                ConsumableEntity consumable,
                ConsumableStockRecordEntity record,
                List<ConsumableStockRecordEntity> records
        ) {
            this.consumable = consumable;
            this.record = record;
            this.records = records;
        }

        public ConsumableEntity getConsumable() {
            return consumable;
        }

        public void setConsumable(ConsumableEntity consumable) {
            this.consumable = consumable;
        }

        public ConsumableStockRecordEntity getRecord() {
            return record;
        }

        public void setRecord(ConsumableStockRecordEntity record) {
            this.record = record;
        }

        public List<ConsumableStockRecordEntity> getRecords() {
            return records;
        }

        public void setRecords(List<ConsumableStockRecordEntity> records) {
            this.records = records;
        }
    }
}
