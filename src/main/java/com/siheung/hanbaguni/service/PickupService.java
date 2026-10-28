package com.siheung.hanbaguni.service;

import com.siheung.hanbaguni.entity.GroupPurchase;
import com.siheung.hanbaguni.entity.Pickup;
import com.siheung.hanbaguni.entity.Store;
import com.siheung.hanbaguni.entity.User;
import com.siheung.hanbaguni.repository.PickupRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class PickupService {

    private static final String CODE_CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PickupRepository pickupRepository;
    private final StoreService storeService;

    public PickupService(PickupRepository pickupRepository, StoreService storeService) {
        this.pickupRepository = pickupRepository;
        this.storeService = storeService;
    }

    public List<Pickup> findByStoreId(Long storeId, Long sellerId) {
        storeService.getStoreWithOwnershipCheck(storeId, sellerId);
        return pickupRepository.findByStoreIdOrderByIdDesc(storeId);
    }

    public Pickup getByCodeAndStore(String pickupCode, Long storeId, Long sellerId) {
        storeService.getStoreWithOwnershipCheck(storeId, sellerId);
        String cleanCode = pickupCode != null ? pickupCode.trim().toUpperCase() : "";
        return pickupRepository.findByPickupCodeAndStoreId(cleanCode, storeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "해당 매장의 수령 건을 찾을 수 없습니다. (코드: " + cleanCode + ")"));
    }

    public Pickup getByIdWithOwnershipCheck(Long pickupId, Long sellerId) {
        Pickup pickup = pickupRepository.findById(pickupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "수령 정보를 찾을 수 없습니다."));
        if (!pickup.getStore().isOwnedBy(sellerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "해당 수령 건에 대한 매장 관리 권한이 없습니다.");
        }
        return pickup;
    }

    @Transactional
    public void markReady(Long pickupId, Long sellerId) {
        Pickup pickup = getByIdWithOwnershipCheck(pickupId, sellerId);
        try {
            pickup.markReady();
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @Transactional
    public void markPickedUp(Long pickupId, Long sellerId) {
        Pickup pickup = getByIdWithOwnershipCheck(pickupId, sellerId);
        try {
            pickup.markPickedUp();
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @Transactional
    public Pickup markPickedUpByCode(String pickupCode, Long storeId, Long sellerId) {
        Pickup pickup = getByCodeAndStore(pickupCode, storeId, sellerId);
        try {
            pickup.markPickedUp();
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        return pickup;
    }

    @Transactional
    public Pickup createTestPickup(Store store, GroupPurchase groupPurchase, User customer, int quantity, String customerName, String customerPhone) {
        String code = generateUniquePickupCode();
        Pickup pickup = new Pickup(
                store,
                groupPurchase,
                customer,
                code,
                quantity,
                customerName != null ? customerName : "테스트구매자",
                customerPhone != null ? customerPhone : "010-1234-5678"
        );
        return pickupRepository.save(pickup);
    }

    public String generateUniquePickupCode() {
        String datePrefix = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE); // YYYYMMDD
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder sb = new StringBuilder("PK-").append(datePrefix).append("-");
            for (int i = 0; i < 4; i++) {
                sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
            }
            String candidate = sb.toString();
            if (!pickupRepository.existsByPickupCode(candidate)) {
                return candidate;
            }
        }
        return "PK-" + datePrefix + "-" + System.currentTimeMillis() % 10000;
    }
}
