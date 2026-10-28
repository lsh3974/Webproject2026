package com.siheung.hanbaguni.dto;

import com.siheung.hanbaguni.entity.Product;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
public class ProductForm {

    @NotBlank(message = "상품명을 입력해주세요.")
    @Size(max = 150, message = "상품명은 150자 이하여야 합니다.")
    private String name;

    @NotBlank(message = "카테고리를 선택하거나 입력해주세요.")
    @Size(max = 50, message = "카테고리는 50자 이하여야 합니다.")
    private String category;

    @NotNull(message = "정상 가격을 입력해주세요.")
    @Min(value = 0, message = "가격은 0원 이상이어야 합니다.")
    private Integer regularPrice;

    @NotBlank(message = "판매 단위/규격을 입력해주세요. (예: 1kg, 1박스)")
    @Size(max = 50, message = "단위는 50자 이하여야 합니다.")
    private String unit;

    private String description;

    @Size(max = 500, message = "이미지 주소는 500자 이하여야 합니다.")
    private String imageUrl;

    private MultipartFile imageFile;

    private Product.Status status = Product.Status.ON_SALE;

    public static ProductForm from(Product product) {
        ProductForm form = new ProductForm();
        form.setName(product.getName());
        form.setCategory(product.getCategory());
        form.setRegularPrice(product.getRegularPrice());
        form.setUnit(product.getUnit());
        form.setDescription(product.getDescription());
        form.setImageUrl(product.getImageUrl());
        form.setStatus(product.getStatus());
        return form;
    }
}
