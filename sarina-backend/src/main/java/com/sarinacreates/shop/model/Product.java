package com.sarinacreates.shop.model;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "products")
public class Product {

    @Id
    private String id;

    private String name;
    private String category;
    private double price;
    private String dims;
    private int stock;

    @Column(columnDefinition = "TEXT")
    private String desc;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "product_images", joinColumns = @JoinColumn(name = "product_id"))
    @Column(columnDefinition = "TEXT")
    private List<String> images = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "product_palette", joinColumns = @JoinColumn(name = "product_id"))
    private List<String> palette = new ArrayList<>();

    private int angle;

    private Long _ts;

    public Product() {}

    public Product(String id, String name, String category, double price, String dims, int stock, String desc, List<String> images, List<String> palette, int angle, Long _ts) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.price = price;
        this.dims = dims;
        this.stock = stock;
        this.desc = desc;
        this.images = images != null ? images : new ArrayList<>();
        this.palette = palette != null ? palette : new ArrayList<>();
        this.angle = angle;
        this._ts = _ts != null ? _ts : System.currentTimeMillis();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public String getDims() { return dims; }
    public void setDims(String dims) { this.dims = dims; }

    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }

    public String getDesc() { return desc; }
    public void setDesc(String desc) { this.desc = desc; }

    public List<String> getImages() { return images; }
    public void setImages(List<String> images) { this.images = images; }

    public List<String> getPalette() { return palette; }
    public void setPalette(List<String> palette) { this.palette = palette; }

    public int getAngle() { return angle; }
    public void setAngle(int angle) { this.angle = angle; }

    public Long get_ts() { return _ts; }
    public void set_ts(Long _ts) { this._ts = _ts; }
}
