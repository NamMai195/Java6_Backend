package com.backend.service.impl;

import com.backend.controller.request.AddItemToCartRequest;
import com.backend.controller.request.UpdateCartItemRequest;
import com.backend.controller.response.CartItemResponse;
import com.backend.controller.response.CartResponse;
import com.backend.exception.ResourceNotFoundException;
import com.backend.model.*; // Import các model cần thiết
import com.backend.repository.*; // Import các repository cần thiết
import com.backend.service.CartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

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
    private final UserRepository userRepository; // Để tìm user
    private final ProductRepository productRepository; // Để tìm product

    // Helper: Tìm hoặc tạo giỏ hàng cho user
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

    // Helper: Map CartEntity sang CartResponse
    private CartResponse mapCartToResponse(CartEntity cart) {
        if (cart == null) {
            return null; // Hoặc trả về giỏ hàng rỗng tùy logic
        }

        List<CartItemResponse> itemResponses = Collections.emptyList();
        BigDecimal totalAmount = BigDecimal.ZERO;
        int totalItemsCount = 0;

        // Lấy danh sách cart items (có thể cần fetch nếu là LAZY)
        // Cách 1: Dùng cart.getCartItems() nếu FetchType.EAGER hoặc session còn mở
        List<CartItemEntity> items = (cart.getCartItems() != null) ? new ArrayList<>(cart.getCartItems()) : Collections.emptyList();
        // Cách 2: Query riêng nếu FetchType.LAZY và không muốn dùng EAGER
        // List<CartItemEntity> items = cartItemRepository.findByCart(cart);


        if (!CollectionUtils.isEmpty(items)) {
            itemResponses = items.stream()
                    .map(this::mapCartItemToResponse)
                    .collect(Collectors.toList());

            totalAmount = itemResponses.stream()
                    .map(CartItemResponse::getSubTotal)
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

    // Helper: Map CartItemEntity sang CartItemResponse
    private CartItemResponse mapCartItemToResponse(CartItemEntity item) {
        if (item == null || item.getProduct() == null) return null;
        ProductEntity product = item.getProduct();
        BigDecimal price = product.getPrice(); // Lấy giá hiện tại của sản phẩm
        BigDecimal subTotal = price.multiply(BigDecimal.valueOf(item.getQuantity()));

        // Lấy ảnh đầu tiên (nếu dùng cấu trúc nhiều ảnh)
        String imageUrl = null;
        // if (product.getImages() != null && !product.getImages().isEmpty()) {
        //     imageUrl = product.getImages().iterator().next().getUrl();
        // }

        return CartItemResponse.builder()
                .cartItemId(item.getId())
                .productId(product.getId())
                .productName(product.getName())
                .productPrice(price)
                .productImageUrl(imageUrl) // Thay bằng logic lấy ảnh của bạn
                .quantity(item.getQuantity())
                .subTotal(subTotal)
                .build();
    }


    @Override
    @Transactional(readOnly = true)
    public CartResponse getCartByUserId(Long userId) {
        log.info("Fetching cart for user ID: {}", userId);
        CartEntity cart = findOrCreateCartByUserId(userId);
        // Cần fetch cart items nếu là LAZY loading
        // Hibernate.initialize(cart.getCartItems()); // Cách 1: initialize
        // Hoặc query riêng cartItemRepository.findByCart(cart); // Cách 2: query riêng
        return mapCartToResponse(cart);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CartResponse addItemToCart(Long userId, AddItemToCartRequest request) {
        log.info("Adding item to cart for user ID: {}, Product ID: {}, Quantity: {}", userId, request.getProductId(), request.getQuantity());
        CartEntity cart = findOrCreateCartByUserId(userId);
        ProductEntity product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + request.getProductId()));

        // Kiểm tra số lượng tồn kho (quan trọng)
        if (product.getStockQuantity() < request.getQuantity()) {
            log.warn("Cannot add item: Not enough stock for product ID {}. Requested: {}, Available: {}", request.getProductId(), request.getQuantity(), product.getStockQuantity());
            throw new IllegalArgumentException("Not enough stock available for product: " + product.getName());
        }

        // Tìm xem sản phẩm đã có trong giỏ hàng chưa
        Optional<CartItemEntity> existingItemOpt = cart.getCartItems().stream()
                .filter(item -> item.getProduct().getId().equals(request.getProductId()))
                .findFirst();
        // Hoặc dùng query: cartItemRepository.findByCartAndProduct(cart, product);

        if (existingItemOpt.isPresent()) {
            // Nếu có -> cập nhật số lượng
            CartItemEntity existingItem = existingItemOpt.get();
            int newQuantity = existingItem.getQuantity() + request.getQuantity();
            // Kiểm tra lại tồn kho cho tổng số lượng mới
            if (product.getStockQuantity() < newQuantity) {
                log.warn("Cannot add item: Not enough stock for product ID {}. Requested total: {}, Available: {}", request.getProductId(), newQuantity, product.getStockQuantity());
                throw new IllegalArgumentException("Not enough stock available to add desired quantity for product: " + product.getName());
            }
            existingItem.setQuantity(newQuantity);
            cartItemRepository.save(existingItem);
            log.info("Updated quantity for existing item. CartItem ID: {}, New Quantity: {}", existingItem.getId(), newQuantity);
        } else {
            // Nếu chưa có -> tạo mới CartItemEntity
            CartItemEntity newItem = new CartItemEntity();
            newItem.setCart(cart);
            newItem.setProduct(product);
            newItem.setQuantity(request.getQuantity());
            CartItemEntity savedNewItem = cartItemRepository.save(newItem);
            cart.getCartItems().add(savedNewItem); // Thêm vào collection của Cart
            log.info("Added new item to cart. CartItem ID: {}", savedNewItem.getId());
        }

        // Không cần cartRepository.save(cart) nếu CartItemEntity quản lý quan hệ và cascade đúng
        // Tuy nhiên, save cart lại cũng không sao

        return mapCartToResponse(cart);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CartResponse updateCartItemQuantity(Long userId, Long cartItemId, UpdateCartItemRequest request) {
        log.info("Updating quantity for CartItem ID: {} for user ID: {}. New Quantity: {}", cartItemId, userId, request.getQuantity());
        CartEntity cart = findOrCreateCartByUserId(userId); // Lấy giỏ hàng của user

        // Tìm cart item và đảm bảo nó thuộc về giỏ hàng của user này
        CartItemEntity cartItem = cartItemRepository.findByIdAndCart(cartItemId, cart)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found with ID: " + cartItemId + " in user's cart " + cart.getId()));


        ProductEntity product = cartItem.getProduct();
        if (product == null) { // Defensive check
            log.error("Product associated with CartItem ID {} is null.", cartItemId);
            throw new IllegalStateException("Product not found for the cart item.");
        }
        // Kiểm tra tồn kho
        if (product.getStockQuantity() < request.getQuantity()) {
            log.warn("Cannot update quantity: Not enough stock for product ID {}. Requested: {}, Available: {}", product.getId(), request.getQuantity(), product.getStockQuantity());
            throw new IllegalArgumentException("Not enough stock available for product: " + product.getName());
        }


        cartItem.setQuantity(request.getQuantity());
        cartItemRepository.save(cartItem);
        log.info("CartItem ID {} quantity updated to {}", cartItemId, request.getQuantity());

        return mapCartToResponse(cart);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CartResponse removeItemFromCart(Long userId, Long cartItemId) {
        log.info("Removing CartItem ID: {} for user ID: {}", cartItemId, userId);
        CartEntity cart = findOrCreateCartByUserId(userId);

        // Tìm cart item và đảm bảo nó thuộc về giỏ hàng của user này
        CartItemEntity cartItemToRemove = cartItemRepository.findByIdAndCart(cartItemId, cart)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found with ID: " + cartItemId + " in user's cart " + cart.getId()));

        // Cách 1: Dùng orphanRemoval=true trên CartEntity.cartItems
        boolean removed = cart.getCartItems().remove(cartItemToRemove);
        if (removed) {
            // cartRepository.save(cart); // Lưu lại cart để kích hoạt orphanRemoval nếu cần
            log.info("Removed CartItem ID {} from cart collection.", cartItemId);
        } else {
            log.warn("CartItem ID {} was found but could not be removed from the cart's collection.", cartItemId);
        }
        // Cách 2: Xóa trực tiếp (không cần orphanRemoval)
        // cartItemRepository.delete(cartItemToRemove);

        // Cần chắc chắn cartItem bị xóa khỏi DB
        cartItemRepository.deleteById(cartItemId); // Đảm bảo xóa

        // Nạp lại cart để lấy trạng thái mới nhất
        CartEntity updatedCart = cartRepository.findById(cart.getId()).orElse(cart);

        return mapCartToResponse(updatedCart);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearCart(Long userId) {
        log.info("Clearing cart for user ID: {}", userId);
        CartEntity cart = findOrCreateCartByUserId(userId);

        // Cách 1: Dùng orphanRemoval=true
        if (cart.getCartItems() != null && !cart.getCartItems().isEmpty()) {
            log.info("Clearing {} items from cart ID {}", cart.getCartItems().size(), cart.getId());
            cart.getCartItems().clear();
            cartRepository.save(cart); // Lưu để kích hoạt orphanRemoval
        } else {
            log.info("Cart ID {} for user ID {} is already empty.", cart.getId(), userId);
        }

        // Cách 2: Xóa trực tiếp bằng repo (không cần orphanRemoval)
        // cartItemRepository.deleteByCart(cart);
    }
}