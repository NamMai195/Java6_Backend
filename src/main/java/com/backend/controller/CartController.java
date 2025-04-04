package com.backend.controller;

import com.backend.controller.request.AddItemToCartRequest;
import com.backend.controller.request.UpdateCartItemRequest;
import com.backend.controller.response.CartResponse;
// Import UserEntity nếu dùng làm Principal
// import com.backend.model.UserEntity;
import com.backend.model.UserEntity;
import com.backend.service.CartService;
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
import org.springframework.http.ResponseEntity;
// **THÊM IMPORT CHO SECURITY CONTEXT**
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
// Import Principal/UserDetails nếu cần
// import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/cart")
@Tag(name = "Cart API v1", description = "APIs for managing the user shopping cart")
@RequiredArgsConstructor
@Validated
// @SecurityRequirement(name = "bearerAuth") // Bật nếu tất cả API đều yêu cầu xác thực
public class CartController {

    private final CartService cartService;

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

    @Operation(summary = "Get User's Cart", description = "Retrieve the current user's shopping cart details.")
    @GetMapping
    // Không cần @PreAuthorize cụ thể - chỉ cần user đã đăng nhập
    public ResponseEntity<CartResponse> getUserCart() {
        Long userId = getCurrentUserId();
        log.info("Request received to get cart for user ID: {}", userId);
        CartResponse cart = cartService.getCartByUserId(userId);
        return ResponseEntity.ok(cart);
    }

    @Operation(summary = "Add Item to Cart", description = "Adds a product item to the current user's cart.")
    @PostMapping("/items")
    // Không cần @PreAuthorize cụ thể - chỉ cần user đã đăng nhập
    public ResponseEntity<CartResponse> addItemToCart(
            @Valid @RequestBody AddItemToCartRequest request) {
        Long userId = getCurrentUserId();
        log.info("Request received to add item to cart for user ID: {}, ProductId: {}, Quantity: {}", userId, request.getProductId(), request.getQuantity());
        CartResponse updatedCart = cartService.addItemToCart(userId, request);
        return ResponseEntity.ok(updatedCart);
    }

    @Operation(summary = "Update Cart Item Quantity", description = "Updates the quantity of a specific item in the user's cart.")
    @PutMapping("/items/{cartItemId}")
    // Không cần @PreAuthorize cụ thể - chỉ cần user đã đăng nhập
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

    @Operation(summary = "Remove Item from Cart", description = "Removes a specific item from the user's cart.")
    @DeleteMapping("/items/{cartItemId}")
    // Không cần @PreAuthorize cụ thể - chỉ cần user đã đăng nhập
    public ResponseEntity<CartResponse> removeItemFromCart(
            @Parameter(description = "ID of the cart item to remove", required = true)
            @PathVariable @Min(value = 1, message = "Cart Item ID must be positive") Long cartItemId) {
        Long userId = getCurrentUserId();
        log.info("Request received to remove cart item ID: {} for user ID: {}", cartItemId, userId);
        CartResponse updatedCart = cartService.removeItemFromCart(userId, cartItemId);
        return ResponseEntity.ok(updatedCart);
    }

    @Operation(summary = "Clear Cart", description = "Removes all items from the current user's cart.")
    @DeleteMapping
    // Không cần @PreAuthorize cụ thể - chỉ cần user đã đăng nhập
    public ResponseEntity<Void> clearCart() {
        Long userId = getCurrentUserId();
        log.info("Request received to clear cart for user ID: {}", userId);
        cartService.clearCart(userId);
        return ResponseEntity.noContent().build();
    }
}