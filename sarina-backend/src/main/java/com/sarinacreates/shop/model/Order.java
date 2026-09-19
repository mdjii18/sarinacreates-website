package com.sarinacreates.shop.model;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    private String id;

    private Long createdAt;
    private String status;

    @Embedded
    private CustomerInfo customer;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "order_items", joinColumns = @JoinColumn(name = "order_id"))
    private List<OrderItem> items = new ArrayList<>();

    private double total;

    public Order() {}

    public Order(String id, Long createdAt, String status, CustomerInfo customer, List<OrderItem> items, double total) {
        this.id = id;
        this.createdAt = createdAt != null ? createdAt : System.currentTimeMillis();
        this.status = status != null ? status : "Pending";
        this.customer = customer;
        this.items = items != null ? items : new ArrayList<>();
        this.total = total;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Long getCreatedAt() { return createdAt; }
    public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public CustomerInfo getCustomer() { return customer; }
    public void setCustomer(CustomerInfo customer) { this.customer = customer; }

    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) { this.items = items; }

    public double getTotal() { return total; }
    public void setTotal(double total) { this.total = total; }
}
