package com.backend.repository;

import com.backend.model.OrderEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor; // Thêm nếu cần tìm kiếm phức tạp
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, Long>, JpaSpecificationExecutor<OrderEntity> {

    // Tìm đơn hàng của một user, sắp xếp theo ngày mới nhất
    Page<OrderEntity> findByUserIdOrderByOrderDateDesc(Long userId, Pageable pageable);

    // Tìm đơn hàng theo ID và User ID (để kiểm tra quyền sở hữu)
    Optional<OrderEntity> findByIdAndUserId(Long orderId, Long userId);

    // Tìm đơn hàng theo mã đơn hàng (orderCode)
    Optional<OrderEntity> findByOrderCode(String orderCode);

    boolean existsByShippingAddressId(Long shippingAddressId);

    /**
     * Kiểm tra xem có đơn hàng nào dùng địa chỉ này làm địa chỉ thanh toán không.
     */
    boolean existsByBillingAddressId(Long billingAddressId);
}