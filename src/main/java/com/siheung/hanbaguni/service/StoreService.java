package com.siheung.hanbaguni.service;

import com.siheung.hanbaguni.dto.StoreForm;
import com.siheung.hanbaguni.entity.Store;
import com.siheung.hanbaguni.entity.User;
import com.siheung.hanbaguni.repository.StoreRepository;
import com.siheung.hanbaguni.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class StoreService {

    private final StoreRepository storeRepository;
    private final UserRepository userRepository;

    public StoreService(StoreRepository storeRepository, UserRepository userRepository) {
        this.storeRepository = storeRepository;
        this.userRepository = userRepository;
    }

    public List<Store> findStoresBySellerId(Long sellerId) {
        return storeRepository.findBySellerId(sellerId);
    }

    public Store getStoreById(Long storeId) {
        return storeRepository.findById(storeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "매장을 찾을 수 없습니다."));
    }

    public Store getStoreWithOwnershipCheck(Long storeId, Long sellerId) {
        Store store = getStoreById(storeId);
        if (!store.isOwnedBy(sellerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "해당 매장의 관리 권한이 없습니다.");
        }
        return store;
    }

    @Transactional
    public Long createStore(Long sellerId, StoreForm form) {
        User seller = userRepository.findById(sellerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        Store store = new Store(
                seller,
                form.getName().trim(),
                form.getBusinessNumber() != null ? form.getBusinessNumber().trim() : null,
                form.getPhone().trim(),
                form.getAddress().trim(),
                form.getAddressDetail() != null ? form.getAddressDetail().trim() : null,
                form.getDescription()
        );

        Store saved = storeRepository.save(store);
        return saved.getId();
    }

    @Transactional
    public void updateStore(Long storeId, Long sellerId, StoreForm form) {
        Store store = getStoreWithOwnershipCheck(storeId, sellerId);
        store.update(
                form.getName().trim(),
                form.getBusinessNumber() != null ? form.getBusinessNumber().trim() : null,
                form.getPhone().trim(),
                form.getAddress().trim(),
                form.getAddressDetail() != null ? form.getAddressDetail().trim() : null,
                form.getDescription(),
                form.getStatus()
        );
    }
}
