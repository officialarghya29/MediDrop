package com.medidrop.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {

    List<InventoryTransaction> findTop50ByInventoryIdOrderByCreatedAtDesc(Long inventoryId);
}
