package com.sarinacreates.shop.repository;

import com.sarinacreates.shop.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, String> {
    List<Order> findAllByOrderByCreatedAtDesc();
    List<Order> findByCustomerEmailIgnoreCaseOrderByCreatedAtDesc(String email);
}
