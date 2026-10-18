package com.orderplatform.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {

    boolean existsByOrderId(Long orderId);

    List<StockReservation> findByOrderIdAndReleasedFalse(Long orderId);
}
