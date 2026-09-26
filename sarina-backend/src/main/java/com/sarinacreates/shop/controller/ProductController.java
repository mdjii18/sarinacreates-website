package com.sarinacreates.shop.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarinacreates.shop.model.AdminUser;
import com.sarinacreates.shop.model.Product;
import com.sarinacreates.shop.repository.AdminUserRepository;
import com.sarinacreates.shop.repository.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductRepository productRepository;
    private final AdminUserRepository adminRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ProductController(ProductRepository productRepository, AdminUserRepository adminRepository) {
        this.productRepository = productRepository;
        this.adminRepository = adminRepository;
    }

    private boolean validateAdmin(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return false;
        }
        String token = authHeader.substring(7);
        return adminRepository.findBySessionsStrContaining(token).isPresent();
    }

    @GetMapping
    public ResponseEntity<List<Product>> getAllProducts() {
        return ResponseEntity.ok(productRepository.findAllSortedByTs());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getProductById(@PathVariable String id) {
        Optional<Product> productOpt = productRepository.findById(id);
        if (productOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Not found"));
        }
        return ResponseEntity.ok(productOpt.get());
    }

    @PostMapping
    public ResponseEntity<?> saveProduct(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(value = "id", required = false) String id,
            @RequestParam(value = "name", required = false, defaultValue = "") String name,
            @RequestParam(value = "category", required = false, defaultValue = "") String category,
            @RequestParam(value = "price", required = false, defaultValue = "0") double price,
            @RequestParam(value = "dims", required = false, defaultValue = "") String dims,
            @RequestParam(value = "stock", required = false, defaultValue = "0") int stock,
            @RequestParam(value = "desc", required = false, defaultValue = "") String desc,
            @RequestParam(value = "c1", required = false, defaultValue = "#123B3B") String c1,
            @RequestParam(value = "c2", required = false, defaultValue = "#1F7A6C") String c2,
            @RequestParam(value = "c3", required = false, defaultValue = "#EFEAE0") String c3,
            @RequestParam(value = "angle", required = false) Integer angle,
            @RequestParam(value = "existingImages", required = false) String existingImagesJson,
            @RequestParam(value = "images", required = false) MultipartFile[] files
    ) {
        if (!validateAdmin(authHeader)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authorized"));
        }

        String productId = (id != null && !id.trim().isEmpty()) ? id.trim() : null;
        Optional<Product> existingOpt = productId != null ? productRepository.findById(productId) : Optional.empty();

        if (existingOpt.isEmpty() && (productId == null || productId.isEmpty())) {
            productId = "p" + Long.toString(System.currentTimeMillis(), 36);
        }

        List<String> keptImages = new ArrayList<>();
        if (existingImagesJson != null && !existingImagesJson.isBlank()) {
            try {
                keptImages = objectMapper.readValue(existingImagesJson, new TypeReference<List<String>>() {});
            } catch (Exception ignored) {}
        }

        List<String> newImages = new ArrayList<>();
        if (files != null) {
            for (MultipartFile file : files) {
                if (!file.isEmpty()) {
                    try {
                        String contentType = file.getContentType() != null ? file.getContentType() : "image/jpeg";
                        String base64 = Base64.getEncoder().encodeToString(file.getBytes());
                        newImages.add("data:" + contentType + ";base64," + base64);
                    } catch (Exception e) {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Failed to process image: " + file.getOriginalFilename()));
                    }
                }
            }
        }

        List<String> allImages = new ArrayList<>(keptImages);
        allImages.addAll(newImages);

        int finalAngle = angle != null ? angle : new Random().nextInt(180);
        Long ts = existingOpt.isPresent() ? existingOpt.get().get_ts() : System.currentTimeMillis();

        Product product = new Product(
                productId,
                name.trim(),
                category.trim(),
                price,
                dims.trim(),
                stock,
                desc.trim(),
                allImages,
                List.of(c1, c2, c3),
                finalAngle,
                ts
        );

        productRepository.save(product);
        return ResponseEntity.ok(product);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteProduct(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String id
    ) {
        if (!validateAdmin(authHeader)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authorized"));
        }

        productRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
