package com.siheung.hanbaguni.dto;

import com.siheung.hanbaguni.entity.Store;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StoreForm {

    @NotBlank(message = "매장 이름을 입력해주세요.")
    @Size(max = 100, message = "매장 이름은 100자 이하여야 합니다.")
    private String name;

    @Size(max = 30, message = "사업자등록번호는 30자 이하여야 합니다.")
    private String businessNumber;

    @NotBlank(message = "매장 전화번호를 입력해주세요.")
    @Size(max = 20, message = "전화번호는 20자 이하여야 합니다.")
    private String phone;

    @NotBlank(message = "매장 주소를 입력해주세요.")
    @Size(max = 255, message = "주소는 255자 이하여야 합니다.")
    private String address;

    @Size(max = 255, message = "상세 주소는 255자 이하여야 합니다.")
    private String addressDetail;

    private String description;

    private Store.Status status = Store.Status.ACTIVE;

    public static StoreForm from(Store store) {
        StoreForm form = new StoreForm();
        form.setName(store.getName());
        form.setBusinessNumber(store.getBusinessNumber());
        form.setPhone(store.getPhone());
        form.setAddress(store.getAddress());
        form.setAddressDetail(store.getAddressDetail());
        form.setDescription(store.getDescription());
        form.setStatus(store.getStatus());
        return form;
    }
}
