package com.backend.repository;

import com.backend.model.AddressEntity;
import com.backend.model.UserEntity; // Import
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;     // Import
import java.util.Optional;  // Import

@Repository
public interface AddressRepository extends JpaRepository<AddressEntity, Long> {

    // Chữ ký đúng nếu bạn cần phương thức này
    Optional<AddressEntity> findByUserAndAddressType(UserEntity user, Integer addressType);

    // (Tùy chọn) Tìm tất cả địa chỉ của user
    List<AddressEntity> findByUser(UserEntity user);

    // Xóa phương thức cũ findByUseridAndAddressType nếu còn
}