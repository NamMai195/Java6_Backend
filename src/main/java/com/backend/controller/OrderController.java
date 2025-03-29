package com.backend.controller;

import com.backend.common.OrderStatus; // Import OrderStatus
import com.backend.controller.request.OrderCreationRequest;
import com.backend.controller.request.UpdateOrderStatusRequest;
import com.backend.controller.response.OrderResponse;
import com.backend.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page; // Import Page
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize; // Ví dụ dùng PreAuthorize
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@Slf4j
@RestController
@RequestMapping("/api/v1/orders") // Base path cho đơn hàng
@Tag(name = "Order API v1", description = "APIs for managing customer orders")
@RequiredArgsConstructor
@Validated
// @SecurityRequirement(name = "bearerAuth") // Yêu cầu xác thực cho tất cả API đơn hàng
public class OrderController {

    private final OrderService orderService;

    // --- Helper method để lấy User ID ---
    // !!! Quan trọng: Thay thế bằng logic lấy User ID thực tế !!!
    private Long getCurrentUserId() {
        log.warn("!!! Using hardcoded User ID 1 for Order operations. Replace with actual principal extraction. !!!");
        return 1L; // <<<< THAY THẾ LOGIC THỰC TẾ
        // throw new IllegalStateException("User ID could not be determined.");
    }
    // -----------------------------------


    @Operation(summary = "Create Order", description = "Creates a new order from the user's current cart.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Order created successfully",
                            content = @Content(schema = @Schema(implementation = OrderResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid input (e.g., empty cart, invalid address, out of stock)", content = @Content),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content)
            })
    @PostMapping
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

    @Operation(summary = "Get My Orders", description = "Retrieves the order history for the currently logged-in user.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Orders retrieved successfully"), // Swagger UI sẽ hiển thị kiểu Page<OrderResponse> nếu dùng Page
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content)
            })
    @GetMapping("/my-orders")
    public ResponseEntity<Page<OrderResponse>> getMyOrders(
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Number of orders per page") @RequestParam(defaultValue = "10") int size) {
        Long userId = getCurrentUserId();
        log.info("Request received to get orders for user ID: {}, page: {}, size: {}", userId, page, size);
        Pageable pageable = PageRequest.of(page, size);
        Page<OrderResponse> orders = orderService.getOrdersByUserId(userId, pageable);
        return ResponseEntity.ok(orders);
    }

    @Operation(summary = "Get Order Details", description = "Retrieves details for a specific order belonging to the current user.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Order details retrieved",
                            content = @Content(schema = @Schema(implementation = OrderResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
                    @ApiResponse(responseCode = "404", description = "Order not found or does not belong to user", content = @Content)
            })
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrderDetails(
            @Parameter(description = "ID of the order to retrieve", required = true)
            @PathVariable @Min(1) Long orderId) {
        Long userId = getCurrentUserId(); // Dùng để kiểm tra quyền sở hữu trong service
        log.info("Request received to get details for order ID: {} by user ID: {}", orderId, userId);
        OrderResponse order = orderService.getOrderDetails(orderId, userId);
        return ResponseEntity.ok(order);
    }


    @Operation(summary = "Cancel Order", description = "Allows a user to cancel their own order if it's in a cancellable state.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Order cancelled successfully",
                            content = @Content(schema = @Schema(implementation = OrderResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Order cannot be cancelled (e.g., already shipped)", content = @Content),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
                    @ApiResponse(responseCode = "404", description = "Order not found or does not belong to user", content = @Content)
            })
    @PutMapping("/{orderId}/cancel") // Dùng PUT hoặc PATCH
    public ResponseEntity<OrderResponse> cancelMyOrder(
            @Parameter(description = "ID of the order to cancel", required = true)
            @PathVariable @Min(1) Long orderId) {
        Long userId = getCurrentUserId();
        log.info("Request received from user ID {} to cancel order ID: {}", userId, orderId);
        OrderResponse cancelledOrder = orderService.cancelOrder(orderId, userId);
        return ResponseEntity.ok(cancelledOrder);
    }


    // --- Admin Endpoints (Ví dụ, cần phân quyền) ---

    @Operation(summary = "Get All Orders (Admin)", description = "Retrieves all orders in the system. Requires ADMIN role.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Orders retrieved successfully"),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
                    @ApiResponse(responseCode = "403", description = "Forbidden (User is not Admin)", content = @Content)
            })
    @GetMapping("/admin/all") // Đường dẫn riêng cho admin
    @PreAuthorize("hasRole('ADMIN')") // Ví dụ phân quyền bằng Spring Security
    public ResponseEntity<Page<OrderResponse>> getAllOrders(
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Number of orders per page") @RequestParam(defaultValue = "20") int size
            /* Thêm các tham số filter nếu cần */) {
        log.info("ADMIN request received to get all orders, page: {}, size: {}", page, size);
        Pageable pageable = PageRequest.of(page, size);
        Page<OrderResponse> orders = orderService.getAllOrders(pageable /*, filters */);
        return ResponseEntity.ok(orders);
    }


    @Operation(summary = "Update Order Status (Admin)", description = "Updates the status of a specific order. Requires ADMIN role.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Order status updated successfully",
                            content = @Content(schema = @Schema(implementation = OrderResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid status transition", content = @Content),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
                    @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content),
                    @ApiResponse(responseCode = "404", description = "Order not found", content = @Content)
            })
    @PatchMapping("/admin/{orderId}/status") // Dùng PATCH vì chỉ cập nhật một phần
    @PreAuthorize("hasRole('ADMIN')")
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