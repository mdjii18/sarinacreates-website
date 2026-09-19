package com.sarinacreates.shop.init;

import com.sarinacreates.shop.model.AdminUser;
import com.sarinacreates.shop.model.Product;
import com.sarinacreates.shop.repository.AdminUserRepository;
import com.sarinacreates.shop.repository.ProductRepository;
import com.sarinacreates.shop.util.SecurityUtils;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    private final AdminUserRepository adminRepository;
    private final ProductRepository productRepository;

    public DataInitializer(AdminUserRepository adminRepository, ProductRepository productRepository) {
        this.adminRepository = adminRepository;
        this.productRepository = productRepository;
    }

    @Override
    public void run(String... args) {
        // Initialize Admin Account if missing
        if (adminRepository.findById("admin").isEmpty()) {
            String defaultPassword = System.getenv("ADMIN_PASSWORD");
            if (defaultPassword == null || defaultPassword.isBlank()) {
                defaultPassword = "sarina2026";
            }
            AdminUser admin = new AdminUser(
                    "admin",
                    "sarinaquadri71@gmail.com",
                    SecurityUtils.sha256(defaultPassword),
                    new ArrayList<>()
            );
            adminRepository.save(admin);
            System.out.println("Default admin user created successfully.");
        }

        // Initialize Seed Products if empty
        if (productRepository.count() == 0) {
            System.out.println("Seeding initial resin art products into PostgreSQL...");

            List<Product> seeds = List.of(
                    new Product("p1", "Low Tide", "Coasters", 2499.0, "10cm round · set of 4", 6,
                            "A set of four coasters poured in layered teal and sand, each one catching the light a little differently — like the shoreline at low tide.",
                            new ArrayList<>(), List.of("#123B3B", "#1F7A6C", "#EFEAE0"), 135, System.currentTimeMillis()),

                    new Product("p2", "Amber Vein", "Wall Art", 11999.0, "30cm × 40cm panel", 2,
                            "Deep amber and brass resin poured over a walnut panel, with fine gold veining running through like light through honey.",
                            new ArrayList<>(), List.of("#7a4a17", "#B9903E", "#12211F"), 100, System.currentTimeMillis()),

                    new Product("p3", "Geode Bloom", "Wall Art", 17499.0, "40cm round panel", 1,
                            "A geode-style pour built up in twelve layers, with crushed glass and gold leaf edging for a piece that looks lit from within.",
                            new ArrayList<>(), List.of("#1F7A6C", "#12211F", "#D8B876"), 60, System.currentTimeMillis()),

                    new Product("p4", "Tidepool Tray", "Homeware", 4999.0, "24cm × 15cm", 4,
                            "A serving tray with a poured resin base in cool tidepool tones, sealed under a glass-smooth, food-safe top coat.",
                            new ArrayList<>(), List.of("#0e2b2b", "#1F7A6C", "#9fd6c4"), 45, System.currentTimeMillis()),

                    new Product("p5", "Copper Fracture", "Jewelry", 2999.0, "Pendant, 3.5cm × 2.8cm", 9,
                            "A hand-cut resin pendant with suspended copper leaf fracture lines, hung on a waxed cotton cord.",
                            new ArrayList<>(), List.of("#9C4222", "#B9903E", "#12211F"), 160, System.currentTimeMillis()),

                    new Product("p6", "Deep Current", "Wall Art", 14499.0, "35cm × 50cm panel", 1,
                            "Dark, moody blues built in deep layers to mimic the way light disappears the further you go under water.",
                            new ArrayList<>(), List.of("#081c1c", "#123B3B", "#3aa08c"), 120, System.currentTimeMillis()),

                    new Product("p7", "Sandbar Set", "Coasters", 2699.0, "10cm round · set of 4", 5,
                            "Warm sand tones with a single thread of gold through each piece — a quieter companion to Low Tide.",
                            new ArrayList<>(), List.of("#d9d1ba", "#B9903E", "#12211F"), 75, System.currentTimeMillis()),

                    new Product("p8", "Ember Pour", "Homeware", 4299.0, "Bookend pair, 12cm h", 3,
                            "A pair of bookends with a warm brick-and-brass pour, weighted and felt-backed to protect shelves.",
                            new ArrayList<>(), List.of("#9C4222", "#B9903E", "#EFEAE0"), 30, System.currentTimeMillis())
            );

            productRepository.saveAll(seeds);
            System.out.println("Product seeding complete.");
        }
    }
}
