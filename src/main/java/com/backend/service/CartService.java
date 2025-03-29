package com.backend.service;

import com.backend.controller.request.AddItemToCartRequest;
import com.backend.controller.request.UpdateCartItemRequest;
import com.backend.controller.response.CartResponse;

public interface CartService {

    // Lấy giỏ hàng của người dùng (tạo nếu chưa có)
    CartResponse getCartByUserId(Long userId);

    // Thêm sản phẩm vào giỏ hàng
    CartResponse addItemToCart(Long userId, AddItemToCartRequest request);

    // Cập nhật số lượng sản phẩm trong giỏ hàng
    CartResponse updateCartItemQuantity(Long userId, Long cartItemId, UpdateCartItemRequest request);

    // Xóa sản phẩm khỏi giỏ hàng
    CartResponse removeItemFromCart(Long userId, Long cartItemId);

    // Xóa toàn bộ giỏ hàng
    void clearCart(Long userId);
}