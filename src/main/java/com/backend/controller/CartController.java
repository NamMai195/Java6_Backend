package com.backend.controller;

import com.backend.controller.request.AddItemToCartRequest;
import com.backend.controller.request.UpdateCartItemRequest;
import com.backend.controller.response.CartResponse;
import com.backend.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement; // Thêm nếu cần security
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication; // Import để lấy Principal
import org.springframework.security.core.context.SecurityContextHolder; // Import để lấy Principal
import org.springframework.security.core.userdetails.UserDetails; // Ví dụ nếu dùng UserDetails
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/cart") // Base path cho giỏ hàng
@Tag(name = "Cart API v1", description = "APIs for managing the user shopping cart")
@RequiredArgsConstructor
@Validated
// @SecurityRequirement(name = "bearerAuth") // Thêm nếu API này yêu cầu xác thực JWT
public class CartController {

    private final CartService cartService;

    // --- Helper method để lấy User ID từ context ---
    // !!! Quan trọng: Thay thế bằng logic lấy User ID thực tế từ Principal của bạn !!!
    private Long getCurrentUserId() {
        // ---- CÁCH 1: Nếu dùng Spring Security Principal là UserDetails có ID ----
        /*
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserDetails) {
             // Giả sử UserDetails của bạn có phương thức getId() hoặc bạn có thể ép kiểu sang custom UserDetails
             // Ví dụ: CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
             // return userDetails.getId();
             // Tạm thời trả về giá trị cứng để test
             log.warn("!!! Using hardcoded User ID 1 for Cart operations. Replace with actual principal extraction. !!!");
             return 1L;
        }
        */

        // ---- CÁCH 2: Trả về giá trị cứng để test (KHÔNG DÙNG TRONG PRODUCTION) ----
        log.warn("!!! Using hardcoded User ID 1 for Cart operations. Replace with actual principal extraction. !!!");
        return 1L; // <<<< THAY THẾ BẰNG LOGIC LẤY USER ID THỰC TẾ

        // Nếu không lấy được user ID thì phải báo lỗi
        // throw new IllegalStateException("User ID could not be determined from security context.");
    }
    // --------------------------------------------------


    @Operation(summary = "Get User's Cart", description = "Retrieve the current user's shopping cart details.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Cart retrieved successfully",
                            content = @Content(schema = @Schema(implementation = CartResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized if user is not logged in", content = @Content)
            })
    @GetMapping
    public ResponseEntity<CartResponse> getUserCart() {
        Long userId = getCurrentUserId(); // Lấy ID của user đang đăng nhập
        log.info("Request received to get cart for user ID: {}", userId);
        CartResponse cart = cartService.getCartByUserId(userId);
        return ResponseEntity.ok(cart);
    }

    @Operation(summary = "Add Item to Cart", description = "Adds a product item to the current user's cart or updates quantity if item already exists.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Item added/updated successfully",
                            content = @Content(schema = @Schema(implementation = CartResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid input (e.g., negative quantity, not enough stock)", content = @Content),
                    @ApiResponse(responseCode = "404", description = "Product not found", content = @Content),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content)
            })
    @PostMapping("/items")
    public ResponseEntity<CartResponse> addItemToCart(
            @Valid @RequestBody AddItemToCartRequest request) {
        Long userId = getCurrentUserId();
        log.info("Request received to add item to cart for user ID: {}, ProductId: {}, Quantity: {}", userId, request.getProductId(), request.getQuantity());
        CartResponse updatedCart = cartService.addItemToCart(userId, request);
        return ResponseEntity.ok(updatedCart);
    }

    @Operation(summary = "Update Cart Item Quantity", description = "Updates the quantity of a specific item in the user's cart.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Quantity updated successfully",
                            content = @Content(schema = @Schema(implementation = CartResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid input (e.g., negative quantity, not enough stock)", content = @Content),
                    @ApiResponse(responseCode = "404", description = "Cart item not found in user's cart", content = @Content),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content)
            })
    @PutMapping("/items/{cartItemId}")
    public ResponseEntity<CartResponse> updateCartItemQuantity(
            @Parameter(description = "ID of the cart item to update", required = true)
            @PathVariable @Min(value = 1, message = "Cart Item ID must be positive") Long cartItemId,

            @Parameter(description = "Request body containing the new quantity", required = true)
            @Valid @RequestBody UpdateCartItemRequest request) {
        Long userId = getCurrentUserId();
        log.info("Request received to update quantity for cart item ID: {} for user ID: {}. New quantity: {}", cartItemId, userId, request.getQuantity());
        CartResponse updatedCart = cartService.updateCartItemQuantity(userId, cartItemId, request);
        return ResponseEntity.ok(updatedCart);
    }

    @Operation(summary = "Remove Item from Cart", description = "Removes a specific item from the user's cart.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Item removed successfully",
                            content = @Content(schema = @Schema(implementation = CartResponse.class))), // Trả về giỏ hàng sau khi xóa
                    @ApiResponse(responseCode = "404", description = "Cart item not found in user's cart", content = @Content),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content)
            })
    @DeleteMapping("/items/{cartItemId}")
    public ResponseEntity<CartResponse> removeItemFromCart(
            @Parameter(description = "ID of the cart item to remove", required = true)
            @PathVariable @Min(value = 1, message = "Cart Item ID must be positive") Long cartItemId) {
        Long userId = getCurrentUserId();
        log.info("Request received to remove cart item ID: {} for user ID: {}", cartItemId, userId);
        CartResponse updatedCart = cartService.removeItemFromCart(userId, cartItemId);
        return ResponseEntity.ok(updatedCart); // Trả về giỏ hàng cập nhật
    }

    @Operation(summary = "Clear Cart", description = "Removes all items from the current user's cart.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Cart cleared successfully", content = @Content),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content)
            })
    @DeleteMapping
    public ResponseEntity<Void> clearCart() {
        Long userId = getCurrentUserId();
        log.info("Request received to clear cart for user ID: {}", userId);
        cartService.clearCart(userId);
        return ResponseEntity.noContent().build(); // Trả về 204 No Content
    }
}