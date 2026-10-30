package com.siheung.hanbaguni.repository;

import com.siheung.hanbaguni.entity.GroupPurchase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface GroupPurchaseRepository extends JpaRepository<GroupPurchase, Long>, JpaSpecificationExecutor<GroupPurchase> {

    @Override
    @EntityGraph(attributePaths = {"store", "product"})
    Page<GroupPurchase> findAll(Specification<GroupPurchase> spec, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"store", "product"})
    Optional<GroupPurchase> findOne(Specification<GroupPurchase> spec);

    List<GroupPurchase> findByStoreIdOrderByIdDesc(Long storeId);

    List<GroupPurchase> findByProductIdOrderByIdDesc(Long productId);

    List<GroupPurchase> findByStatusOrderByIdDesc(GroupPurchase.Status status);

    Optional<GroupPurchase> findByIdAndStoreId(Long id, Long storeId);
}
