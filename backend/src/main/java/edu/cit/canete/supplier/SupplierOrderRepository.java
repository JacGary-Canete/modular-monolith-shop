package edu.cit.canete.supplier;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SupplierOrderRepository extends JpaRepository<SupplierOrder, Long> {
    List<SupplierOrder> findByStatus(SupplierOrderStatus status);
}