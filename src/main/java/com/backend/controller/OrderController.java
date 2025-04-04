package com.backend.controller;

import com.backend.common.OrderStatus; // Import OrderStatus
import com.backend.controller.request.OrderCreationRequest;
import com.backend.controller.request.UpdateOrderStatusRequest;
import com.backend.controller.response.OrderResponse;
// Import UserEntity nếu dùng làm Principal
// import com.backend.model.UserEntity;
import com.backend.model.UserEntity;
import com.backend.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
// Import nếu cần SecurityRequirement
// import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page; // Import Page
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
// **THÊM IMPORT CHO PHÂN QUYỀN VÀ SECURITY CONTEXT**
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
// Import Principal/UserDetails nếu cần
// import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@Slf4j
@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Order API v1", description = "APIs for managing customer orders")
@RequiredArgsConstructor
@Validated
// @SecurityRequirement(name = "bearerAuth") // Bật nếu tất cả API đều yêu cầu xác thực
public class OrderController {

    private final OrderService orderService;

    // --- Helper lấy User ID (Ví dụ - Cần điều chỉnh theo Principal thực tế) ---
    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            log.error("User not authenticated for this operation.");
            throw new IllegalStateException("User must be authenticated to perform this operation.");
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof UserEntity) {
            UserEntity currentUser = (UserEntity) principal;
            // log.debug("Authenticated User ID: {}", currentUser.getId()); // Bỏ log debug nếu không cần
            return currentUser.getId(); // Trả về ID thực sự
        } else {
            // Nếu principal không phải là UserEntity, báo lỗi
            log.error("Could not determine User ID from principal type: {}", principal.getClass().getName());
            throw new IllegalStateException("Could not determine User ID from principal.");
        }
    }
    // ------------------------------------------------------------------------


    @Operation(summary = "Create Order", description = "Creates a new order from the user's current cart.")
    @PostMapping
    // Chỉ cần người dùng đã đăng nhập (authenticated() trong AppConfig là đủ)
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody OrderCreationRequest request) {
        Long userId = getCurrentUserId();
        log.info("Request received to create order for user ID: {}", userId);
        OrderResponse createdOrder = orderService.createOrderFromCart(userId, request);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(createdOrder.getId())
                .toUri();
        log.info("Order {} created successfully for user ID {}", createdOrder.getOrderCode(), userId);
        return ResponseEntity.created(location).body(createdOrder);
    }

    @Operation(summary = "Get My Orders", description = "Retrieves the order history for the currently logged-in user.")
    @GetMapping("/my-orders")
    // Chỉ cần người dùng đã đăng nhập
    public ResponseEntity<Page<OrderResponse>> getMyOrders(
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Number of orders per page") @RequestParam(defaultValue = "10") int size) {
        Long userId = getCurrentUserId();
        log.info("Request received to get orders for user ID: {}, page: {}, size: {}", userId, page, size);
        Pageable pageable = PageRequest.of(page, size);
        Page<OrderResponse> orders = orderService.getOrdersByUserId(userId, pageable);
        return ResponseEntity.ok(orders);
    }

    @Operation(summary = "Get Order Details", description = "Retrieves details for a specific order belonging to the current user.")
    @GetMapping("/{orderId}")
    // Chỉ cần người dùng đã đăng nhập. Logic kiểm tra quyền sở hữu nên nằm trong service.
    public ResponseEntity<OrderResponse> getOrderDetails(
            @Parameter(description = "ID of the order to retrieve", required = true)
            @PathVariable @Min(1) Long orderId) {
        Long userId = getCurrentUserId();
        log.info("Request received to get details for order ID: {} by user ID: {}", orderId, userId);
        // Service sẽ kiểm tra xem orderId có thuộc userId không
        OrderResponse order = orderService.getOrderDetails(orderId, userId);
        return ResponseEntity.ok(order);
    }


    @Operation(summary = "Cancel Order", description = "Allows a user to cancel their own order if it's in a cancellable state.")
    @PutMapping("/{orderId}/cancel")
    // Chỉ cần người dùng đã đăng nhập. Logic kiểm tra quyền sở hữu nên nằm trong service.
    public ResponseEntity<OrderResponse> cancelMyOrder(
            @Parameter(description = "ID of the order to cancel", required = true)
            @PathVariable @Min(1) Long orderId) {
        Long userId = getCurrentUserId();
        log.info("Request received from user ID {} to cancel order ID: {}", userId, orderId);
        // Service sẽ kiểm tra xem orderId có thuộc userId không và trạng thái có cho phép hủy không
        OrderResponse cancelledOrder = orderService.cancelOrder(orderId, userId);
        return ResponseEntity.ok(cancelledOrder);
    }


    // --- Admin Endpoints ---

    @Operation(summary = "Get All Orders (Admin)", description = "Retrieves all orders. (Requires ADMIN role)")
    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')") // **GIỮ NGUYÊN PHÂN QUYỀN ADMIN**
    public ResponseEntity<Page<OrderResponse>> getAllOrders(
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Number of orders per page") @RequestParam(defaultValue = "20") int size) {
        log.info("ADMIN request received to get all orders, page: {}, size: {}", page, size);
        Pageable pageable = PageRequest.of(page, size);
        Page<OrderResponse> orders = orderService.getAllOrders(pageable);
        return ResponseEntity.ok(orders);
    }


    @Operation(summary = "Update Order Status (Admin)", description = "Updates the status of an order. (Requires ADMIN role)")
    @PatchMapping("/admin/{orderId}/status")
    @PreAuthorize("hasRole('ADMIN')") // **GIỮ NGUYÊN PHÂN QUYỀN ADMIN**
    public ResponseEntity<OrderResponse> updateOrderStatus(
            @Parameter(description = "ID of the order to update status", required = true)
            @PathVariable @Min(1) Long orderId,
            @Parameter(description = "Request body containing the new status", required = true)
            @Valid @RequestBody UpdateOrderStatusRequest request) {
        log.info("ADMIN request received to update status for order ID: {} to {}", orderId, request.getStatus());
        OrderResponse updatedOrder = orderService.updateOrderStatus(orderId, request.getStatus());
        return ResponseEntity.ok(updatedOrder);
    }

}