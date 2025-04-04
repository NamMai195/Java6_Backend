package com.backend.service.impl;

import com.backend.controller.request.AddItemToCartRequest;
import com.backend.controller.request.UpdateCartItemRequest;
import com.backend.controller.response.CartItemResponse;
import com.backend.controller.response.CartResponse;
import com.backend.exception.ResourceNotFoundException;
import com.backend.model.*;
import com.backend.repository.*;
import com.backend.service.CartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
// Import EntityManager nếu dùng giải pháp flush/refresh (hiện tại không cần)
// import jakarta.persistence.EntityManager;
// import jakarta.persistence.PersistenceContext;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j(topic = "CART-SERVICE")
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    // Inject EntityManager nếu cần dùng flush/refresh
    // @PersistenceContext
    // private EntityManager entityManager;

    private CartEntity findOrCreateCartByUserId(Long userId) {
        return cartRepository.findByUserId(userId).orElseGet(() -> {
            log.info("No cart found for user ID: {}. Creating a new cart.", userId);
            UserEntity user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId + " when creating cart."));
            CartEntity newCart = new CartEntity();
            newCart.setUser(user);
            return cartRepository.save(newCart);
        });
    }

    private CartResponse mapCartToResponse(CartEntity cart) {
        if (cart == null) {
            return null;
        }

        List<CartItemResponse> itemResponses = Collections.emptyList();
        BigDecimal totalAmount = BigDecimal.ZERO;
        int totalItemsCount = 0;

        // Đảm bảo collection được load (quan trọng nếu LAZY)
        // Cần fetch cart items nếu là LAZY loading và session đã đóng
        // Hoặc dùng @Transactional trên phương thức gọi để giữ session
        List<CartItemEntity> items = cartItemRepository.findByCart(cart); // Query riêng để chắc chắn lấy dữ liệu mới nhất

        if (!CollectionUtils.isEmpty(items)) {
            itemResponses = items.stream()
                    .map(this::mapCartItemToResponse)
                    .collect(Collectors.toList());

            totalAmount = itemResponses.stream()
                    .map(CartItemResponse::getSubTotal)
                    .filter(java.util.Objects::nonNull) // Thêm filter để tránh NullPointerException
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            totalItemsCount = itemResponses.stream()
                    .mapToInt(CartItemResponse::getQuantity)
                    .sum();
        }

        return CartResponse.builder()
                .cartId(cart.getId())
                .userId(cart.getUser().getId())
                .items(itemResponses)
                .totalAmount(totalAmount)
                .totalItems(totalItemsCount)
                .build();
    }

    private CartItemResponse mapCartItemToResponse(CartItemEntity item) {
        if (item == null || item.getProduct() == null) return null;
        ProductEntity product = item.getProduct();
        BigDecimal price = product.getPrice();
        BigDecimal subTotal = BigDecimal.ZERO;
        if (price != null && item.getQuantity() > 0) { // Kiểm tra null và quantity
            subTotal = price.multiply(BigDecimal.valueOf(item.getQuantity()));
        }


        String imageUrl = null;
         if (product.getImages() != null && !product.getImages().isEmpty()) {
             imageUrl = product.getImages().iterator().next().getUrl();
         }

        return CartItemResponse.builder()
                .cartItemId(item.getId())
                .productId(product.getId())
                .productName(product.getName())
                .productPrice(price)
                .productImageUrl(imageUrl)
                .quantity(item.getQuantity())
                .subTotal(subTotal)
                .build();
    }


    @Override
    @Transactional
    public CartResponse getCartByUserId(Long userId) {
        log.info("Fetching cart for user ID: {}", userId);
        CartEntity cart = findOrCreateCartByUserId(userId);
        return mapCartToResponse(cart);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CartResponse addItemToCart(Long userId, AddItemToCartRequest request) {
        log.info("Adding item to cart for user ID: {}, Product ID: {}, Quantity: {}", userId, request.getProductId(), request.getQuantity());
        CartEntity cart = findOrCreateCartByUserId(userId);
        ProductEntity product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + request.getProductId()));

        if (product.getStockQuantity() == null || product.getStockQuantity() < request.getQuantity()) {
            log.warn("Cannot add item: Not enough stock for product ID {}. Requested: {}, Available: {}", request.getProductId(), request.getQuantity(), product.getStockQuantity());
            throw new IllegalArgumentException("Not enough stock available for product: " + product.getName());
        }

        // Query trực tiếp thay vì dựa vào collection có thể chưa được load/cập nhật
        Optional<CartItemEntity> existingItemOpt = cartItemRepository.findByCartAndProduct(cart, product);

        if (existingItemOpt.isPresent()) {
            CartItemEntity existingItem = existingItemOpt.get();
            int newQuantity = existingItem.getQuantity() + request.getQuantity();
            if (product.getStockQuantity() < newQuantity) {
                log.warn("Cannot add item: Not enough stock for product ID {}. Requested total: {}, Available: {}", request.getProductId(), newQuantity, product.getStockQuantity());
                throw new IllegalArgumentException("Not enough stock available to add desired quantity for product: " + product.getName());
            }
            existingItem.setQuantity(newQuantity);
            cartItemRepository.save(existingItem);
            log.info("Updated quantity for existing item. CartItem ID: {}, New Quantity: {}", existingItem.getId(), newQuantity);
        } else {
            CartItemEntity newItem = new CartItemEntity();
            newItem.setCart(cart);
            newItem.setProduct(product);
            newItem.setQuantity(request.getQuantity());
            cartItemRepository.save(newItem);
            log.info("Added new item to cart.");
        }

        // Gọi lại findById để đảm bảo lấy được CartEntity với collection đã cập nhật (nếu cần)
        // Hoặc dựa vào mapCartToResponse query lại items
        CartEntity updatedCart = cartRepository.findById(cart.getId()).orElse(cart);
        return mapCartToResponse(updatedCart);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CartResponse updateCartItemQuantity(Long userId, Long cartItemId, UpdateCartItemRequest request) {
        log.info("Updating quantity for CartItem ID: {} for user ID: {}. New Quantity: {}", cartItemId, userId, request.getQuantity());
        CartEntity cart = findOrCreateCartByUserId(userId);

        CartItemEntity cartItem = cartItemRepository.findByIdAndCart(cartItemId, cart)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found with ID: " + cartItemId + " in user's cart " + cart.getId()));

        ProductEntity product = cartItem.getProduct();
        if (product == null) {
            log.error("Product associated with CartItem ID {} is null.", cartItemId);
            throw new IllegalStateException("Product not found for the cart item.");
        }
        if (product.getStockQuantity() == null || product.getStockQuantity() < request.getQuantity()) {
            log.warn("Cannot update quantity: Not enough stock for product ID {}. Requested: {}, Available: {}", product.getId(), request.getQuantity(), product.getStockQuantity());
            throw new IllegalArgumentException("Not enough stock available for product: " + product.getName());
        }

        cartItem.setQuantity(request.getQuantity());
        cartItemRepository.save(cartItem);
        log.info("CartItem ID {} quantity updated to {}", cartItemId, request.getQuantity());

        CartEntity updatedCart = cartRepository.findById(cart.getId()).orElse(cart);
        return mapCartToResponse(updatedCart);
    }

    // --- PHƯƠNG THỨC XÓA ĐÃ SỬA ---
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CartResponse removeItemFromCart(Long userId, Long cartItemId) {
        log.info("Attempting to remove CartItem ID: {} for user ID: {}", cartItemId, userId);
        CartEntity cart = findOrCreateCartByUserId(userId);

        // Tìm cart item cần xóa, đảm bảo nó thuộc về cart của user này
        CartItemEntity cartItemToRemove = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found with ID: " + cartItemId));

        // Kiểm tra xem cart item có thực sự thuộc về cart của user không
        if (!cartItemToRemove.getCart().getId().equals(cart.getId())) {
            log.error("CartItem ID {} does not belong to cart ID {}.", cartItemId, cart.getId());
            throw new SecurityException("Cannot remove item that does not belong to the user's cart.");
        }

        // Thực hiện xóa
        cartItemRepository.delete(cartItemToRemove);
        log.info("Successfully deleted CartItem ID {}.", cartItemId);

        // Flush để đảm bảo thay đổi được ghi xuống DB trước khi đọc lại (tuỳ chọn, nhưng an toàn hơn)
        // entityManager.flush(); // Bỏ comment nếu đã inject EntityManager

        // Nạp lại cart từ DB để đảm bảo lấy trạng thái mới nhất sau khi xóa
        // (Hoặc dựa vào mapCartToResponse query lại items như đã sửa ở trên)
        CartEntity updatedCart = cartRepository.findById(cart.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found after item removal for ID: " + cart.getId())); // Nên throw lỗi nếu cart biến mất

        return mapCartToResponse(updatedCart);
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearCart(Long userId) {
        log.info("Clearing cart for user ID: {}", userId);
        CartEntity cart = findOrCreateCartByUserId(userId);

        List<CartItemEntity> items = cartItemRepository.findByCart(cart); // Lấy danh sách items để log số lượng

        if (!CollectionUtils.isEmpty(items)) {
            log.info("Deleting {} items from cart ID {}", items.size(), cart.getId());
            cartItemRepository.deleteAll(items); // Xóa tất cả items đã tìm thấy
            log.info("Cart ID {} for user ID {} cleared.", cart.getId(), userId);
        } else {
            log.info("Cart ID {} for user ID {} is already empty.", cart.getId(), userId);
        }
    }
}