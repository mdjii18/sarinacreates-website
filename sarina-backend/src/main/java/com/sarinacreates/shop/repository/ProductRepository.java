package com.sarinacreates.shop.repository;

import com.sarinacreates.shop.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, String> {
    @Query("SELECT p FROM Product p ORDER BY p._ts DESC")
    List<Product> findAllSortedByTs();
}
