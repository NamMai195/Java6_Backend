package com.backend.service.impl;

import com.backend.common.OrderStatus;
import com.backend.common.PaymentStatus;
import com.backend.controller.request.OrderCreationRequest;
import com.backend.controller.response.*;
import com.backend.exception.InvalidDataException;
import com.backend.exception.ResourceNotFoundException;
import com.backend.model.*;
import com.backend.repository.*;
// << THÊM IMPORT NÀY >>
import com.backend.service.BrevoEmailService;
import com.backend.service.CartService;
import com.backend.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value; // << THÊM IMPORT NÀY >>
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async; // << THÊM IMPORT NÀY (Cho Async nếu dùng) >>
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.text.NumberFormat; // Cho format tiền tệ
import java.text.SimpleDateFormat; // Cho format ngày tháng
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Service
@Slf4j(topic = "ORDER-SERVICE")
@RequiredArgsConstructor // Đảm bảo BrevoEmailService được inject
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final AddressRepository addressRepository;
    private final CartService cartService;
    private final CartItemRepository cartItemRepository;
    // << INJECT BREVO EMAIL SERVICE >>
    private final BrevoEmailService brevoEmailService;

    // Sử dụng Lock để tránh race condition khi cập nhật tồn kho
    private final Map<Long, Lock> productLocks = new HashMap<>();

    // << (TÙY CHỌN) INJECT CÁC GIÁ TRỊ MẶC ĐỊNH TỪ CONFIG >>
    @Value("${app.email.defaults.service-name:PETSHOP}")
    private String defaultServiceName;

    @Value("${app.email.defaults.company-name:PETSHOP Inc.}")
    private String defaultCompanyName;

    @Value("${app.email.defaults.login-link:#}") // Link trang login
    private String defaultLoginLink;

    @Value("${app.email.defaults.support-email:support@petshop.com}") // Email hỗ trợ
    private String defaultSupportEmail;

    @Value("${app.email.defaults.support-phone:1900 XXXX}") // Phone hỗ trợ
    private String defaultSupportPhone;

    private Lock getProductLock(Long productId) {
        return productLocks.computeIfAbsent(productId, k -> new ReentrantLock());
    }

    // --- Helper Methods ---

    // mapOrderToResponse, mapOrderItemToResponse, mapAddressToResponse giữ nguyên như trước

    private OrderResponse mapOrderToResponse(OrderEntity order) {
        if (order == null) return null;

        List<OrderItemResponse> itemResponses = Collections.emptyList();
        if (order.getOrderItems() != null) {
            itemResponses = order.getOrderItems().stream()
                    .map(this::mapOrderItemToResponse)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } else {
            log.warn("OrderItems collection is null for Order ID: {}", order.getId());
        }

        return OrderResponse.builder()
                .id(order.getId())
                .orderCode(order.getOrderCode())
                .userId(order.getUser().getId())
                .orderDate(order.getOrderDate())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus())
                .shippingAddress(mapAddressToResponse(order.getShippingAddress()))
                .billingAddress(mapAddressToResponse(order.getBillingAddress()))
                .paymentMethod(order.getPaymentMethod())
                .paymentStatus(order.getPaymentStatus())
                .notes(order.getNotes())
                .orderItems(itemResponses)
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    private OrderItemResponse mapOrderItemToResponse(OrderItemEntity item) {
        if (item == null || item.getProduct() == null) {
            log.warn("Skipping mapping: OrderItem or its Product is null. OrderItemID: {}", item != null ? item.getId() : "N/A");
            return null;
        }
        ProductEntity product = item.getProduct();
        String imageUrl = null;
        List<String> imageUrls = product.getImageURLs();
        if (imageUrls != null && !imageUrls.isEmpty()) {
            imageUrl = imageUrls.get(0);
        } else {
            log.warn("Product ID {} associated with OrderItem ID {} has no image URLs.", product.getId(), item.getId());
        }
        return OrderItemResponse.builder()
                .orderItemId(item.getId())
                .productId(product.getId())
                .productName(product.getName())
                .productSku(product.getSku())
                .productImageUrl(imageUrl)
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
                .ward(address.getWard())
                .district(address.getDistrict())
                .city(address.getCity())
                .country(address.getCountry())
                .addressType(address.getAddressType())
                .build();
    }
    // --- Service Implementations ---

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderResponse createOrderFromCart(Long userId, OrderCreationRequest request) {
        log.info("Attempting to create order from cart for user ID: {}", userId);

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        CartResponse cart = cartService.getCartByUserId(userId);
        if (CollectionUtils.isEmpty(cart.getItems())) {
            log.warn("Cannot create order: Cart is empty for user ID: {}", userId);
            throw new InvalidDataException("Cannot create order from an empty cart.");
        }

        AddressEntity shippingAddress = addressRepository.findById(request.getShippingAddressId())
                .orElseThrow(() -> new ResourceNotFoundException("Shipping address not found with ID: " + request.getShippingAddressId()));
        if (!shippingAddress.getUser().getId().equals(userId)) {
            log.error("Security violation: User {} trying to use address ID {}", userId, request.getShippingAddressId());
            throw new InvalidDataException("Invalid shipping address specified.");
        }

        AddressEntity billingAddress = shippingAddress; // Mặc định
        if (request.getBillingAddressId() != null && !request.getBillingAddressId().equals(request.getShippingAddressId())) {
            billingAddress = addressRepository.findById(request.getBillingAddressId())
                    .orElseThrow(() -> new ResourceNotFoundException("Billing address not found with ID: " + request.getBillingAddressId()));
            if (!billingAddress.getUser().getId().equals(userId)) {
                log.error("Security violation: User {} trying to use billing address ID {}", userId, request.getBillingAddressId());
                throw new InvalidDataException("Invalid billing address specified.");
            }
        }

        OrderEntity order = new OrderEntity();
        order.setUser(user);
        order.setOrderCode(generateOrderCode());
        order.setOrderDate(new Date());
        order.setStatus(OrderStatus.PENDING);
        order.setPaymentMethod(request.getPaymentMethod());
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setShippingAddress(shippingAddress);
        order.setBillingAddress(billingAddress);
        order.setNotes(request.getNotes());
        order.setOrderItems(new HashSet<>());

        BigDecimal totalOrderAmount = BigDecimal.ZERO;
        List<ProductEntity> productsToUpdateStock = new ArrayList<>();
        List<Map<String, Object>> orderItemsForEmail = new ArrayList<>(); // << Chuẩn bị list item cho email

        for (CartItemResponse cartItem : cart.getItems()) {
            Long productId = cartItem.getProductId();
            Integer quantityToOrder = cartItem.getQuantity();
            Lock productLock = getProductLock(productId);
            productLock.lock();
            try {
                ProductEntity product = productRepository.findById(productId)
                        .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + productId + " during order creation."));

                if (product.getStockQuantity() < quantityToOrder) {
                    log.warn("Order creation failed: Not enough stock for product ID {}. Requested: {}, Available: {}", productId, quantityToOrder, product.getStockQuantity());
                    throw new InvalidDataException("Not enough stock available for product: " + product.getName());
                }

                int newStock = product.getStockQuantity() - quantityToOrder;
                product.setStockQuantity(newStock);
                productsToUpdateStock.add(product);

                OrderItemEntity orderItem = new OrderItemEntity();
                orderItem.setOrder(order);
                orderItem.setProduct(product);
                orderItem.setQuantity(quantityToOrder);
                orderItem.setPriceAtOrder(product.getPrice());
                BigDecimal subtotal = product.getPrice().multiply(BigDecimal.valueOf(quantityToOrder)); // Tính subtotal
                orderItem.setSubtotal(subtotal); // Lưu subtotal vào item

                order.getOrderItems().add(orderItem);
                totalOrderAmount = totalOrderAmount.add(subtotal); // Cộng dồn subtotal

                // << THÊM ITEM VÀO LIST CHO EMAIL >>
                Map<String, Object> itemMapForEmail = new HashMap<>();
                itemMapForEmail.put("productName", product.getName()); // Key phải khớp template {{ item.productName }}
                itemMapForEmail.put("productSku", product.getSku()); // Key phải khớp template {{ item.productSku }}
                itemMapForEmail.put("quantity", quantityToOrder);    // Key phải khớp template {{ item.quantity }}
                itemMapForEmail.put("subTotal", formatCurrency(subtotal)); // Key phải khớp template {{ item.subTotal }} - Format tiền tệ
                // Lấy ảnh (nếu có)
                String imageUrl = null;
                List<String> imageUrls = product.getImageURLs();
                if (imageUrls != null && !imageUrls.isEmpty()) {
                    imageUrl = imageUrls.get(0);
                }
                itemMapForEmail.put("productImageUrl", imageUrl != null ? imageUrl : "https://via.placeholder.com/70x70.png?text=N/A"); // {{ item.productImageUrl }}
                orderItemsForEmail.add(itemMapForEmail);
                // << KẾT THÚC THÊM ITEM CHO EMAIL >>

            } finally {
                productLock.unlock();
            }
        }

        order.setTotalAmount(totalOrderAmount);

        OrderEntity savedOrder = orderRepository.save(order);
        log.info("Order entity and items saved successfully. Order ID: {}", savedOrder.getId());

        if (!productsToUpdateStock.isEmpty()) {
            log.info("Updating stock for {} products.", productsToUpdateStock.size());
            productRepository.saveAllAndFlush(productsToUpdateStock);
            log.info("Stock updated successfully.");
        }

        log.info("Clearing cart for user ID: {}", userId);
        cartService.clearCart(userId);

        // << GỌI HÀM GỬI EMAIL SAU KHI MỌI THỨ THÀNH CÔNG >>
        sendOrderConfirmationEmail(savedOrder, orderItemsForEmail); // Truyền cả list item đã chuẩn bị

        return mapOrderToResponse(savedOrder);
    }

    // << TÁCH RIÊNG HÀM GỬI MAIL >>
    // << CÓ THỂ THÊM @Async Ở ĐÂY NẾU ĐÃ CẤU HÌNH >>
    // @Async
    public void sendOrderConfirmationEmail(OrderEntity order, List<Map<String, Object>> orderItemsForEmail) {
        try {
            log.info("Attempting to send order confirmation email for Order ID: {}", order.getId());
            UserEntity customer = order.getUser();
            if (customer == null || customer.getEmail() == null) {
                log.error("Cannot send confirmation email for order {}: Customer or email is null.", order.getId());
                return;
            }

            // === Chuẩn bị Params ===
            Map<String, Object> emailParams = new HashMap<>();
            emailParams.put("customer_name", customer.getFirstName() != null ? customer.getFirstName() : customer.getUsername()); // {{ params.customer_name }}
            emailParams.put("customer_email", customer.getEmail());               // {{ params.customer_email }}
            emailParams.put("order_code", order.getOrderCode());                 // {{ params.order_code }}
            emailParams.put("order_date", formatDate(order.getOrderDate()));     // {{ params.order_date }}
            emailParams.put("total_amount", formatCurrency(order.getTotalAmount())); // {{ params.total_amount }}
            emailParams.put("shipping_address", formatAddressForEmail(order.getShippingAddress())); // {{ params.shipping_address }}
            emailParams.put("payment_method", order.getPaymentMethod().name());  // {{ params.payment_method }}
            emailParams.put("order_items", orderItemsForEmail);                 // {{ params.order_items }} - List Map đã chuẩn bị

            // Các tham số mặc định/link khác
            emailParams.put("service_name", defaultServiceName); // {{ params.service_name }}
            emailParams.put("company_name", defaultCompanyName); // {{ params.company_name }}
            emailParams.put("company_address", "Địa chỉ PETSHOP của anh"); // {{ params.company_address }} - Thay bằng địa chỉ thật
            emailParams.put("support_email", defaultSupportEmail); // {{ params.support_email }}
            emailParams.put("support_phone", defaultSupportPhone); // {{ params.support_phone }}
            // Thay bằng các link thật của anh
            emailParams.put("shop_link", "http://localhost:5173"); // {{ params.shop_link }}
            emailParams.put("policy_link", "http://localhost:5173/policy"); // {{ params.policy_link }}
            emailParams.put("contact_link", "http://localhost:5173/contact"); // {{ params.contact_link }}
            emailParams.put("view_order_link", "http://localhost:5173/my-orders/" + order.getId()); // {{ params.view_order_link }}
            emailParams.put("track_order_link", "#"); // {{ params.track_order_link }} - Thay bằng link thật nếu có
            emailParams.put("unsubscribe_link", "#"); // {{ params.unsubscribe_link }} - Thay bằng link thật nếu có

            // === Lấy Template ID ===
            // !! QUAN TRỌNG: THAY BẰNG ID THỰC TẾ CỦA TEMPLATE XÁC NHẬN ĐƠN HÀNG TRÊN BREVO !!
            Long orderConfirmationTemplateId = 4L; // <<< ANH PHẢI THAY SỐ NÀY

            // === Gọi Service Gửi Mail ===
            brevoEmailService.sendEmailWithTemplate(
                    customer.getEmail(),
                    orderConfirmationTemplateId,
                    emailParams
            );
            log.info("Order confirmation email sent successfully to {} for Order ID {}", customer.getEmail(), order.getId());

        } catch (Exception e) {
            // Chỉ log lỗi, không làm crash luồng chính
            log.error("Failed to send order confirmation email for Order ID {}: {}", order.getId(), e.getMessage(), e);
            // Có thể thêm cơ chế retry hoặc thông báo cho admin
        }
    }

    // << HÀM HELPER FORMAT ĐỊA CHỈ >>
    private String formatAddressForEmail(AddressEntity address) {
        if (address == null) return "N/A";
        List<String> parts = new ArrayList<>();
        if (address.getApartmentNumber() != null && !address.getApartmentNumber().isBlank()) parts.add(address.getApartmentNumber());
        if (address.getStreet() != null && !address.getStreet().isBlank()) parts.add(address.getStreet());
        if (address.getWard() != null && !address.getWard().isBlank()) parts.add(address.getWard());
        if (address.getDistrict() != null && !address.getDistrict().isBlank()) parts.add(address.getDistrict());
        if (address.getCity() != null && !address.getCity().isBlank()) parts.add(address.getCity());
        // if (address.getCountry() != null && !address.getCountry().isBlank()) parts.add(address.getCountry());
        return String.join(", ", parts);
    }

    // << HÀM HELPER FORMAT TIỀN TỆ (VÍ DỤ VND) >>
    private String formatCurrency(BigDecimal amount) {
        if (amount == null) return "0 ₫";
        // Locale("vi", "VN") để format theo kiểu Việt Nam
        NumberFormat currencyFormatter = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
        return currencyFormatter.format(amount);
    }

    // << HÀM HELPER FORMAT NGÀY THÁNG (VÍ DỤ dd/MM/yyyy) >>
    private String formatDate(Date date) {
        if (date == null) return "N/A";
        // Anh có thể chọn định dạng khác nếu muốn
        SimpleDateFormat dateFormatter = new SimpleDateFormat("dd/MM/yyyy HH:mm");
        return dateFormatter.format(date);
    }


    // --- Các phương thức khác (getOrdersByUserId, getOrderDetails, ...) giữ nguyên như trước ---
    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> getOrdersByUserId(Long userId, Pageable pageable) {
        log.info("Fetching orders for user ID: {}, page: {}, size: {}", userId, pageable.getPageNumber(), pageable.getPageSize());
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found with ID: " + userId);
        }
        Page<OrderEntity> orderPage = orderRepository.findByUserIdOrderByOrderDateDesc(userId, pageable);
        log.info("Found {} orders for user ID {} on page {}", orderPage.getNumberOfElements(), userId, pageable.getPageNumber());
        List<OrderResponse> orderResponses = orderPage.getContent().stream()
                .map(this::mapOrderToResponse)
                .collect(Collectors.toList());
        return new PageImpl<>(orderResponses, pageable, orderPage.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderDetails(Long orderId, Long userId) {
        log.info("Fetching order details for Order ID: {}, User ID: {}", orderId, userId);
        OrderEntity order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with ID: " + orderId + " for this user."));
        return mapOrderToResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> getAllOrders(Pageable pageable) {
        log.info("ADMIN: Fetching all orders, page: {}, size: {}", pageable.getPageNumber(), pageable.getPageSize());
        Page<OrderEntity> orderPage = orderRepository.findAll(pageable);
        log.info("ADMIN: Found {} total orders on page {}", orderPage.getNumberOfElements(), pageable.getPageNumber());
        List<OrderResponse> orderResponses = orderPage.getContent().stream()
                .map(this::mapOrderToResponse)
                .collect(Collectors.toList());
        return new PageImpl<>(orderResponses, pageable, orderPage.getTotalElements());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderResponse updateOrderStatus(Long orderId, OrderStatus newStatus) {
        log.info("ADMIN: Updating status for Order ID: {} to {}", orderId, newStatus);
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with ID: " + orderId));

        OrderStatus oldStatus = order.getStatus(); // Lấy status cũ để kiểm tra nếu cần

        if (oldStatus == newStatus) {
            log.warn("ADMIN: Order ID {} already has status {}. No update performed.", orderId, newStatus);
            return mapOrderToResponse(order); // Không cần làm gì thêm
        }

        log.info("Updating order {} status from {} to {}", order.getOrderCode(), oldStatus, newStatus);
        order.setStatus(newStatus);
        OrderEntity updatedOrder = orderRepository.save(order); // Lưu trạng thái mới

        // *** GỌI HÀM GỬI EMAIL THÔNG BÁO CẬP NHẬT TRẠNG THÁI ***
        sendOrderStatusUpdateEmail(updatedOrder); // Gửi email sau khi đã lưu thành công

        return mapOrderToResponse(updatedOrder);
    }
    public void sendOrderStatusUpdateEmail(OrderEntity order) {
        try {
            UserEntity customer = order.getUser();
            if (customer == null || customer.getEmail() == null) {
                log.error("Cannot send status update email for order {}: Customer or email is null.", order.getId());
                return;
            }

            // Lấy trạng thái mới dưới dạng text dễ hiểu
            String newStatusText = mapOrderStatusToText(order.getStatus());
            // Lấy thời gian cập nhật (tùy chọn)
            // LocalDateTime updateTime = LocalDateTime.now();
            // DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy");
            // String formattedUpdateTime = updateTime.format(formatter);

            log.info("Attempting to send order status update email for Order ID: {} to status '{}'", order.getId(), newStatusText);

            // === Chuẩn bị Params ===
            Map<String, Object> emailParams = new HashMap<>();
            emailParams.put("customer_name", customer.getFirstName() != null ? customer.getFirstName() : customer.getUsername()); // {{ params.customer_name }}
            emailParams.put("order_code", order.getOrderCode());             // {{ params.order_code }}
            emailParams.put("new_status_text", newStatusText);             // {{ params.new_status_text }} - Trạng thái mới dạng text
            emailParams.put("view_order_link", "http://localhost:5173/my-orders/" + order.getId()); // {{ params.view_order_link }} - Sửa lại link cho đúng

            // Các tham số mặc định/link khác (tùy chọn, có thể đã có trong template)
            emailParams.put("service_name", defaultServiceName);        // {{ params.service_name }}
            emailParams.put("company_name", defaultCompanyName);       // {{ params.company_name }}
            emailParams.put("support_email", defaultSupportEmail);      // {{ params.support_email }}
            emailParams.put("support_phone", defaultSupportPhone);      // {{ params.support_phone }}
            // emailParams.put("update_time", formattedUpdateTime);      // {{ params.update_time }} - Nếu muốn thêm thời gian cập nhật

            // === Gọi Service Gửi Mail ===
            brevoEmailService.sendEmailWithTemplate(
                    customer.getEmail(),
                    7L, // ID của template mới
                    emailParams
            );
            log.info("Order status update email sent successfully to {} for Order ID {}", customer.getEmail(), order.getId());

        } catch (Exception e) {
            // Chỉ log lỗi, không làm crash luồng chính
            log.error("Failed to send order status update email for Order ID {}: {}", order.getId(), e.getMessage(), e);
        }
    }
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderResponse cancelOrder(Long orderId, Long userId) {
        log.info("User ID {} attempting to cancel Order ID: {}", userId, orderId);
        OrderEntity order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with ID: " + orderId + " for this user."));
        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.PROCESSING) {
            log.warn("Cannot cancel order ID {}: Status is already {}", orderId, order.getStatus());
            throw new InvalidDataException("Order cannot be cancelled because its status is: " + order.getStatus());
        }
        order.setStatus(OrderStatus.CANCELLED);
        OrderEntity cancelledOrder = orderRepository.save(order);
        // << CÓ THỂ GỬI EMAIL THÔNG BÁO HỦY ĐƠN Ở ĐÂY >>
        // sendOrderCancellationEmail(cancelledOrder);
        log.info("Order ID {} cancelled successfully by user ID {}", orderId, userId);
        return mapOrderToResponse(cancelledOrder);
    }

    private String generateOrderCode() {
        return "ORD-" + System.currentTimeMillis();
    }

    private boolean restoreStockForOrder(OrderEntity order) {
        // Giữ nguyên logic này
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
            return false;
        }
    }
    private String mapOrderStatusToText(OrderStatus status) {
        if (status == null) return "Không xác định";
        return switch (status) {
            case PENDING -> "Chờ xác nhận";
            case PROCESSING -> "Đang xử lý";
            case SHIPPED -> "Đang giao hàng";
            case DELIVERED -> "Đã giao thành công";
            case CANCELLED -> "Đã hủy";
            case RETURNED -> "Đã hoàn trả";
            default -> status.name(); // Trả về tên enum nếu chưa định nghĩa text
        };
    }
}