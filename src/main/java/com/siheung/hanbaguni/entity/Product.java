package com.siheung.hanbaguni.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(name = "regular_price", nullable = false)
    private Integer regularPrice;

    @Column(nullable = false, length = 50)
    private String unit;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ON_SALE;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    public Product(Store store, String name, String category, Integer regularPrice,
                   String unit, String description, String imageUrl) {
        this.store = store;
        this.name = name;
        this.category = category;
        this.regularPrice = regularPrice;
        this.unit = unit;
        this.description = description;
        this.imageUrl = imageUrl;
        this.status = Status.ON_SALE;
    }

    public void update(String name, String category, Integer regularPrice,
                       String unit, String description, String imageUrl,
                       Status status) {
        this.name = name;
        this.category = category;
        this.regularPrice = regularPrice;
        this.unit = unit;
        this.description = description;
        this.imageUrl = imageUrl;
        if (status != null) {
            this.status = status;
        }
    }

    public void updateStatus(Status status) {
        if (status != null) {
            this.status = status;
        }
    }

    public enum Status {
        ON_SALE, SOLD_OUT, HIDDEN
    }
}
