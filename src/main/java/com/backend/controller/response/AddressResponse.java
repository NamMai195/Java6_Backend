package com.backend.controller.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AddressResponse {

     private Long id; // Bỏ comment nếu bạn muốn trả về ID địa chỉ

    // Các trường đã có trong file bạn cung cấp
    private String apartmentNumber;
    private String floor;
    private String building;
    private String streetNumber;
    private String street;
    private String city;       // Tên Tỉnh/Thành phố
    private String country;
    private Integer addressType; // Thêm lại nếu bạn cần trả về trường này
    // --- BỔ SUNG CÁC TRƯỜNG CÒN THIẾU ---
    private String ward;       // Trường mới cho Phường/Xã
    private String district;   // Trường mới cho Quận/Huyện

}