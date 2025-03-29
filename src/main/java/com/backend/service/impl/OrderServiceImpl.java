package com.backend.service.impl;

import com.backend.common.OrderStatus;
import com.backend.common.PaymentStatus; // Import PaymentStatus
import com.backend.controller.request.OrderCreationRequest;
import com.backend.controller.response.*; // Import các response DTOs
import com.backend.exception.InvalidDataException;
import com.backend.exception.ResourceNotFoundException;
import com.backend.model.*; // Import các model
import com.backend.repository.*; // Import các repository
import com.backend.service.CartService; // Import CartService
import com.backend.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl; // Import PageImpl
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.*; // Import Set, HashSet, ArrayList, Date
import java.util.concurrent.locks.Lock; // Import Lock
import java.util.concurrent.locks.ReentrantLock; // Import ReentrantLock
import java.util.stream.Collectors;

@Service
@Slf4j(topic = "ORDER-SERVICE")
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository; // Inject OrderItemRepository
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final AddressRepository addressRepository;
    private final CartService cartService; // Inject CartService
    private final CartItemRepository cartItemRepository; // Inject để lấy cart items

    // Sử dụng Lock để tránh race condition khi cập nhật tồn kho
    private final Map<Long, Lock> productLocks = new HashMap<>();

    private Lock getProductLock(Long productId) {
        return productLocks.computeIfAbsent(productId, k -> new ReentrantLock());
    }

    // --- Helper Methods ---

    private OrderResponse mapOrderToResponse(OrderEntity order) {
        if (order == null) return null;

        List<OrderItemResponse> itemResponses = order.getOrderItems().stream()
                .map(this::mapOrderItemToResponse)
                .collect(Collectors.toList());

        return OrderResponse.builder()
                .id(order.getId())
                .orderCode(order.getOrderCode())
                .userId(order.getUser().getId())
                .orderDate(order.getOrderDate())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus())
                .shippingAddress(mapAddressToResponse(order.getShippingAddress()))
                .billingAddress(mapAddressToResponse(order.getBillingAddress())) // Có thể null
                .paymentMethod(order.getPaymentMethod())
                .paymentStatus(order.getPaymentStatus())
                .notes(order.getNotes())
                .orderItems(itemResponses)
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    private OrderItemResponse mapOrderItemToResponse(OrderItemEntity item) {
        if (item == null || item.getProduct() == null) return null;
        return OrderItemResponse.builder()
                .orderItemId(item.getId())
                .productId(item.getProduct().getId())
                .productName(item.getProduct().getName())
                .productSku(item.getProduct().getSku())
                .priceAtOrder(item.getPriceAtOrder())
                .quantity(item.getQuantity())
                .subTotal(item.getSubtotal())
                .build();
    }

    private AddressResponse mapAddressToResponse(AddressEntity address) {
        if (address == null) return null;
        return AddressResponse.builder()
                .id(address.getId())
                .apartmentNumber(address.getApartmentNumber())
                .floor(address.getFloor())
                .building(address.getBuilding())
                .streetNumber(address.getStreetNumber())
                .street(address.getStreet())
                .city(address.getCity())
                .country(address.getCountry())
                .build();
    }
    // --- Service Implementations ---

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderResponse createOrderFromCart(Long userId, OrderCreationRequest request) {
        log.info("Attempting to create order from cart for user ID: {}", userId);

        // 1. Lấy thông tin user
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        // 2. Lấy giỏ hàng của user (qua CartService để đảm bảo logic nhất quán)
        CartResponse cart = cartService.getCartByUserId(userId); // Lấy CartResponse
        if (CollectionUtils.isEmpty(cart.getItems())) {
            log.warn("Cannot create order: Cart is empty for user ID: {}", userId);
            throw new InvalidDataException("Cannot create order from an empty cart.");
        }

        // 3. Lấy thông tin địa chỉ
        AddressEntity shippingAddress = addressRepository.findById(request.getShippingAddressId())
                .orElseThrow(() -> new ResourceNotFoundException("Shipping address not found with ID: " + request.getShippingAddressId()));
        // Kiểm tra xem địa chỉ có thuộc user không (quan trọng về bảo mật)
        if (!shippingAddress.getUser().getId().equals(userId)) {
            log.error("Security violation: User {} attempting to use address ID {} belonging to user {}", userId, request.getShippingAddressId(), shippingAddress.getUser().getId());
            throw new InvalidDataException("Invalid shipping address specified.");
        }

        AddressEntity billingAddress = null;
        if (request.getBillingAddressId() != null) {
            billingAddress = addressRepository.findById(request.getBillingAddressId())
                    .orElseThrow(() -> new ResourceNotFoundException("Billing address not found with ID: " + request.getBillingAddressId()));
            if (!billingAddress.getUser().getId().equals(userId)) {
                log.error("Security violation: User {} attempting to use billing address ID {} belonging to user {}", userId, request.getBillingAddressId(), billingAddress.getUser().getId());
                throw new InvalidDataException("Invalid billing address specified.");
            }
        } else {
            billingAddress = shippingAddress; // Mặc định giống địa chỉ giao hàng
        }

        // 4. Tạo OrderEntity ban đầu
        OrderEntity order = new OrderEntity();
        order.setUser(user);
        order.setOrderCode(generateOrderCode()); // Tạo mã đơn hàng duy nhất
        order.setOrderDate(new Date()); // Ngày giờ hiện tại
        order.setStatus(OrderStatus.PENDING); // Trạng thái chờ xử lý
        order.setPaymentMethod(request.getPaymentMethod());
        order.setPaymentStatus(PaymentStatus.PENDING); // Chờ thanh toán
        order.setShippingAddress(shippingAddress);
        order.setBillingAddress(billingAddress);
        order.setNotes(request.getNotes());
        order.setOrderItems(new HashSet<>()); // Khởi tạo Set rỗng

        BigDecimal totalOrderAmount = BigDecimal.ZERO;
        List<ProductEntity> productsToUpdateStock = new ArrayList<>(); // List để cập nhật tồn kho sau

        // 5. Xử lý từng mục trong giỏ hàng -> Tạo OrderItemEntity và Kiểm tra/Giảm tồn kho
        for (CartItemResponse cartItem : cart.getItems()) {
            Long productId = cartItem.getProductId();
            Integer quantityToOrder = cartItem.getQuantity();
            Lock productLock = getProductLock(productId); // Lấy lock cho sản phẩm này
            productLock.lock(); // Khóa sản phẩm
            try {
                // Fetch lại sản phẩm MỚI NHẤT từ DB bên trong transaction và lock
                ProductEntity product = productRepository.findById(productId)
                        .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + productId + " during order creation."));

                // Kiểm tra tồn kho MỘT LẦN NỮA (quan trọng vì có thể có thay đổi)
                if (product.getStockQuantity() < quantityToOrder) {
                    log.warn("Order creation failed: Not enough stock for product ID {}. Requested: {}, Available: {}", productId, quantityToOrder, product.getStockQuantity());
                    throw new InvalidDataException("Not enough stock available for product: " + product.getName());
                }

                // Giảm số lượng tồn kho
                int newStock = product.getStockQuantity() - quantityToOrder;
                product.setStockQuantity(newStock);
                productsToUpdateStock.add(product); // Thêm vào list để save lát nữa

                // Tạo OrderItemEntity
                OrderItemEntity orderItem = new OrderItemEntity();
                orderItem.setOrder(order); // Liên kết với Order
                orderItem.setProduct(product);
                orderItem.setQuantity(quantityToOrder);
                orderItem.setPriceAtOrder(product.getPrice()); // Lấy giá HIỆN TẠI của sản phẩm
                orderItem.setSubtotal(product.getPrice().multiply(BigDecimal.valueOf(quantityToOrder)));

                order.getOrderItems().add(orderItem); // Thêm vào Set của Order
                totalOrderAmount = totalOrderAmount.add(orderItem.getSubtotal());

            } finally {
                productLock.unlock(); // Luôn mở khóa sản phẩm
            }
        }

        // 6. Cập nhật tổng tiền cho đơn hàng
        order.setTotalAmount(totalOrderAmount);

        // 7. Lưu đơn hàng (Cascade sẽ lưu cả OrderItems)
        OrderEntity savedOrder = orderRepository.save(order);
        log.info("Order entity and items saved successfully. Order ID: {}", savedOrder.getId());

        // 8. Cập nhật tồn kho cho các sản phẩm (sau khi order đã chắc chắn được lưu)
        if (!productsToUpdateStock.isEmpty()) {
            log.info("Updating stock for {} products.", productsToUpdateStock.size());
            productRepository.saveAllAndFlush(productsToUpdateStock); // Save và flush để cập nhật ngay
            log.info("Stock updated successfully.");
        }

        // 9. Xóa giỏ hàng của người dùng
        log.info("Clearing cart for user ID: {}", userId);
        cartService.clearCart(userId); // Gọi service để xóa cart

        // 10. Map và trả về kết quả
        return mapOrderToResponse(savedOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> getOrdersByUserId(Long userId, Pageable pageable) {
        log.info("Fetching orders for user ID: {}, page: {}, size: {}", userId, pageable.getPageNumber(), pageable.getPageSize());
        // Kiểm tra user tồn tại nếu cần
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found with ID: " + userId);
        }

        Page<OrderEntity> orderPage = orderRepository.findByUserIdOrderByOrderDateDesc(userId, pageable);
        log.info("Found {} orders for user ID {} on page {}", orderPage.getNumberOfElements(), userId, pageable.getPageNumber());

        // Map Page<Entity> sang Page<Response>
        List<OrderResponse> orderResponses = orderPage.getContent().stream()
                .map(this::mapOrderToResponse)
                .collect(Collectors.toList());

        return new PageImpl<>(orderResponses, pageable, orderPage.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderDetails(Long orderId, Long userId) {
        log.info("Fetching order details for Order ID: {}, User ID: {}", orderId, userId);
        // Tìm order và đảm bảo nó thuộc về user này (hoặc user là admin - cần logic role check)
        // TODO: Thêm logic kiểm tra quyền (ví dụ: admin có thể xem mọi đơn hàng)
        OrderEntity order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with ID: " + orderId + " for this user."));

        // Nạp OrderItems nếu LAZY
        // Hibernate.initialize(order.getOrderItems());

        return mapOrderToResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> getAllOrders(Pageable pageable /*, filters */) {
        // TODO: Thêm logic kiểm tra quyền ADMIN ở đây (ví dụ dùng Spring Security @PreAuthorize)
        log.info("ADMIN: Fetching all orders, page: {}, size: {}", pageable.getPageNumber(), pageable.getPageSize());
        Page<OrderEntity> orderPage = orderRepository.findAll(pageable); // Thêm Specification nếu cần filter
        log.info("ADMIN: Found {} total orders on page {}", orderPage.getNumberOfElements(), pageable.getPageNumber());

        List<OrderResponse> orderResponses = orderPage.getContent().stream()
                .map(this::mapOrderToResponse)
                .collect(Collectors.toList());
        return new PageImpl<>(orderResponses, pageable, orderPage.getTotalElements());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderResponse updateOrderStatus(Long orderId, OrderStatus newStatus) {
        // TODO: Thêm logic kiểm tra quyền ADMIN ở đây
        log.info("ADMIN: Updating status for Order ID: {} to {}", orderId, newStatus);
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with ID: " + orderId));

        // TODO: Thêm logic kiểm tra tính hợp lệ của việc chuyển trạng thái
        // Ví dụ: Không thể chuyển từ DELIVERED về PENDING
        log.info("Updating order {} status from {} to {}", order.getOrderCode(), order.getStatus(), newStatus);
        order.setStatus(newStatus);

        // Xử lý thêm nếu cần (vd: cập nhật PaymentStatus khi đơn hàng SHIPPED/DELIVERED?)

        OrderEntity updatedOrder = orderRepository.save(order);
        return mapOrderToResponse(updatedOrder);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderResponse cancelOrder(Long orderId, Long userId) {
        log.info("User ID {} attempting to cancel Order ID: {}", userId, orderId);
        OrderEntity order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with ID: " + orderId + " for this user."));

        // Kiểm tra xem có được phép hủy không
        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.PROCESSING) {
            log.warn("Cannot cancel order ID {}: Status is already {}", orderId, order.getStatus());
            throw new InvalidDataException("Order cannot be cancelled because its status is: " + order.getStatus());
        }

        // TODO (Tùy chọn): Hoàn lại số lượng sản phẩm vào kho
        // boolean stockRestored = restoreStockForOrder(order);
        // if (!stockRestored) { /* Xử lý lỗi hoàn kho */ }

        order.setStatus(OrderStatus.CANCELLED);
        // Cập nhật cả PaymentStatus nếu cần (ví dụ: thành REFUNDED nếu đã thanh toán)
        // order.setPaymentStatus(PaymentStatus.REFUNDED);

        OrderEntity cancelledOrder = orderRepository.save(order);
        log.info("Order ID {} cancelled successfully by user ID {}", orderId, userId);
        return mapOrderToResponse(cancelledOrder);
    }

    // Helper để tạo mã đơn hàng duy nhất (ví dụ đơn giản)
    private String generateOrderCode() {
        // Ví dụ: "ORD-" + NămThángNgàyGiờPhútGiây + SốNgẫuNhiên
        // Cần đảm bảo tính duy nhất cao hơn trong môi trường thực tế
        return "ORD-" + System.currentTimeMillis();
    }

    // TODO (Tùy chọn): Implement logic hoàn kho khi hủy đơn
    private boolean restoreStockForOrder(OrderEntity order) {
        log.info("Attempting to restore stock for cancelled order ID: {}", order.getId());
        try {
            for (OrderItemEntity item : order.getOrderItems()) {
                Long productId = item.getProduct().getId();
                Integer quantityToRestore = item.getQuantity();
                Lock productLock = getProductLock(productId);
                productLock.lock();
                try {
                    ProductEntity product = productRepository.findById(productId)
                            .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + productId + " during stock restore."));
                    int newStock = product.getStockQuantity() + quantityToRestore;
                    product.setStockQuantity(newStock);
                    productRepository.save(product);
                    log.info("Restored stock for Product ID {}: +{} units. New stock: {}", productId, quantityToRestore, newStock);
                } finally {
                    productLock.unlock();
                }
            }
            return true;
        } catch (Exception e) {
            log.error("Failed to restore stock completely for order ID {}: {}", order.getId(), e.getMessage(), e);
            // Có thể cần cơ chế retry hoặc thông báo lỗi đặc biệt
            return false;
        }
    }

}