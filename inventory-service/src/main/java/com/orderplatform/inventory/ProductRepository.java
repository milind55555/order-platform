package com.orderplatform.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsBySku(String sku);

    /** Atomic conditional decrement - prevents overselling under concurrent orders. Returns rows updated (0 = not enough stock). */
    @Modifying
    @Query("update Product p set p.availableQuantity = p.availableQuantity - :qty "
            + "where p.id = :id and p.availableQuantity >= :qty")
    int decrementStock(@Param("id") Long id, @Param("qty") int qty);

    @Modifying
    @Query("update Product p set p.availableQuantity = p.availableQuantity + :qty where p.id = :id")
    int incrementStock(@Param("id") Long id, @Param("qty") int qty);
}
