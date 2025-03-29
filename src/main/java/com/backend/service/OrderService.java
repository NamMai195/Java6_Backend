package com.backend.service;

import com.backend.controller.request.OrderCreationRequest;
import com.backend.controller.request.UpdateOrderStatusRequest;
import com.backend.controller.response.OrderResponse;
import com.backend.common.OrderStatus; // Import OrderStatus
import org.springframework.data.domain.Page; // Import Page
import org.springframework.data.domain.Pageable;

// Interface cho OrderService
public interface OrderService {

    // Tạo đơn hàng từ giỏ hàng của người dùng
    OrderResponse createOrderFromCart(Long userId, OrderCreationRequest request);

    // Lấy danh sách đơn hàng của người dùng (phân trang)
    Page<OrderResponse> getOrdersByUserId(Long userId, Pageable pageable);

    // Lấy chi tiết một đơn hàng cụ thể (của người dùng hoặc admin)
    OrderResponse getOrderDetails(Long orderId, Long userId); // userId để kiểm tra quyền nếu cần

    // Lấy tất cả đơn hàng (cho admin, có phân trang, lọc)
    Page<OrderResponse> getAllOrders(Pageable pageable /*, Thêm các tham số filter nếu cần */);

    // Cập nhật trạng thái đơn hàng (cho admin)
    OrderResponse updateOrderStatus(Long orderId, OrderStatus newStatus);

    // Hủy đơn hàng (cho người dùng)
    OrderResponse cancelOrder(Long orderId, Long userId);
}