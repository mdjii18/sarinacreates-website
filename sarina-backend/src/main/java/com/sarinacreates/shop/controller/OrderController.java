package com.sarinacreates.shop.controller;

import com.sarinacreates.shop.model.CustomerInfo;
import com.sarinacreates.shop.model.Order;
import com.sarinacreates.shop.model.OrderItem;
import com.sarinacreates.shop.model.Product;
import com.sarinacreates.shop.repository.AdminUserRepository;
import com.sarinacreates.shop.repository.OrderRepository;
import com.sarinacreates.shop.repository.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final AdminUserRepository adminRepository;
    private final com.sarinacreates.shop.service.EmailService emailService;

    public OrderController(OrderRepository orderRepository, ProductRepository productRepository, AdminUserRepository adminRepository, com.sarinacreates.shop.service.EmailService emailService) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.adminRepository = adminRepository;
        this.emailService = emailService;
    }

    private boolean validateAdmin(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return false;
        }
        String token = authHeader.substring(7);
        return adminRepository.findBySessionsStrContaining(token).isPresent();
    }

    @GetMapping
    public ResponseEntity<?> getAllOrders(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!validateAdmin(authHeader)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authorized"));
        }
        return ResponseEntity.ok(orderRepository.findAllByOrderByCreatedAtDesc());
    }

    @GetMapping("/mine")
    public ResponseEntity<List<Order>> getMyOrders(@RequestParam(value = "email", required = false, defaultValue = "") String email) {
        String cleanEmail = email.trim().toLowerCase();
        if (cleanEmail.isEmpty()) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        return ResponseEntity.ok(orderRepository.findByCustomerEmailIgnoreCaseOrderByCreatedAtDesc(cleanEmail));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getOrderById(@PathVariable String id) {
        Optional<Order> orderOpt = orderRepository.findById(id);
        if (orderOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Not found"));
        }
        return ResponseEntity.ok(orderOpt.get());
    }

    @PostMapping
    public ResponseEntity<?> createOrder(@RequestBody Map<String, Object> body) {
        List<Map<String, Object>> itemsList = (List<Map<String, Object>>) body.get("items");
        Map<String, String> customerMap = (Map<String, String>) body.get("customer");

        if (itemsList == null || itemsList.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Your cart is empty."));
        }

        List<OrderItem> lines = new ArrayList<>();
        double total = 0.0;

        for (Map<String, Object> itemMap : itemsList) {
            String pId = (String) itemMap.get("id");
            Number requestedQty = (Number) itemMap.get("qty");
            if (pId == null || requestedQty == null) continue;

            Optional<Product> pOpt = productRepository.findById(pId);
            if (pOpt.isEmpty()) continue;

            Product p = pOpt.get();
            int qty = Math.min(requestedQty.intValue(), p.getStock());
            if (qty > 0) {
                lines.add(new OrderItem(p.getId(), p.getName(), p.getPrice(), qty));
                total += p.getPrice() * qty;
            }
        }

        if (lines.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Everything in your cart just sold out."));
        }

        CustomerInfo customer = new CustomerInfo();
        if (customerMap != null) {
            customer.setName(customerMap.getOrDefault("name", ""));
            customer.setEmail(customerMap.getOrDefault("email", ""));
            customer.setPhone(customerMap.getOrDefault("phone", ""));
            customer.setAddress(customerMap.getOrDefault("address", ""));
        }

        String orderId = "SC" + Long.toString(System.currentTimeMillis(), 36).toUpperCase();
        Order order = new Order(orderId, System.currentTimeMillis(), "Pending", customer, lines, total);

        orderRepository.save(order);

        // Deduct stock for ordered products
        for (OrderItem line : lines) {
            productRepository.findById(line.getId()).ifPresent(p -> {
                p.setStock(Math.max(0, p.getStock() - line.getQty()));
                productRepository.save(p);
            });
        }

        // Trigger automated email notification log & dispatch to studio admin
        try {
            emailService.sendNewOrderNotification(order);
        } catch (Exception e) {
            System.err.println("Failed to send order email notification: " + e.getMessage());
        }

        return ResponseEntity.ok(order);
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateOrderStatus(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String id,
            @RequestBody Map<String, String> body
    ) {
        if (!validateAdmin(authHeader)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authorized"));
        }

        Optional<Order> orderOpt = orderRepository.findById(id);
        if (orderOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Not found"));
        }

        Order order = orderOpt.get();
        String newStatus = body != null ? body.get("status") : order.getStatus();
        if (newStatus != null) {
            order.setStatus(newStatus);
            orderRepository.save(order);
        }

        return ResponseEntity.ok(order);
    }
}
