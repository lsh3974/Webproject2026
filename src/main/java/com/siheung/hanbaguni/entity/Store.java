package com.siheung.hanbaguni.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "stores")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Store {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "business_number", length = 30)
    private String businessNumber;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false, length = 255)
    private String address;

    @Column(name = "address_detail", length = 255)
    private String addressDetail;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    public Store(User seller, String name, String businessNumber, String phone,
                 String address, String addressDetail, String description) {
        this.seller = seller;
        this.name = name;
        this.businessNumber = businessNumber;
        this.phone = phone;
        this.address = address;
        this.addressDetail = addressDetail;
        this.description = description;
        this.status = Status.ACTIVE;
    }

    public void update(String name, String businessNumber, String phone,
                       String address, String addressDetail, String description,
                       Status status) {
        this.name = name;
        this.businessNumber = businessNumber;
        this.phone = phone;
        this.address = address;
        this.addressDetail = addressDetail;
        this.description = description;
        if (status != null) {
            this.status = status;
        }
    }

    public boolean isOwnedBy(Long userId) {
        return this.seller != null && this.seller.getId().equals(userId);
    }

    public enum Status {
        ACTIVE, CLOSED, SUSPENDED
    }
}
