package edu.cit.canete.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

// Package-private is fine here too, but public keeps it simple to test/wire.
public interface InventoryRepository extends JpaRepository<InventoryItem, String> {
}
