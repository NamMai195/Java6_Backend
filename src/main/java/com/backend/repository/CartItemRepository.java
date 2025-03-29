package com.backend.repository;

import com.backend.model.CartEntity;
import com.backend.model.CartItemEntity;
import com.backend.model.ProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List; // Import List

@Repository
public interface CartItemRepository extends JpaRepository<CartItemEntity, Long> {

    // Tìm cart item cụ thể trong một giỏ hàng dựa vào sản phẩm
    Optional<CartItemEntity> findByCartAndProduct(CartEntity cart, ProductEntity product);

    // Tìm cart item bằng ID và đảm bảo nó thuộc về đúng giỏ hàng (bảo mật)
    Optional<CartItemEntity> findByIdAndCart(Long cartItemId, CartEntity cart);

    // Tùy chọn: Xóa tất cả item của một giỏ hàng
    void deleteByCart(CartEntity cart);

    // Tùy chọn: Lấy tất cả item của một giỏ hàng (nếu không dùng eager fetch)
    List<CartItemEntity> findByCart(CartEntity cart);
}