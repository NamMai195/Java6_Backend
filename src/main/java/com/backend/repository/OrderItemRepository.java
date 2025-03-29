package com.backend.repository;

import com.backend.model.OrderItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItemEntity, Long> {
    // Các phương thức CRUD cơ bản là đủ, vì OrderItem thường được quản lý qua OrderEntity
}