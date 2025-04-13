package com.backend.service.impl;

// ... (Giữ nguyên các import khác) ...
import com.backend.model.CartEntity;
import com.backend.model.CartItemEntity;
import com.backend.model.ProductEntity;
import com.backend.model.UserEntity;

import java.math.BigDecimal;
import java.util.ArrayList; // Vẫn cần để chuyển đổi nếu muốn kiểm tra thứ tự/index
import java.util.HashSet;   // <<<< SỬ DỤNG HashSet
import java.util.List;
import java.util.Set;     // <<<< SỬ DỤNG Set
import java.util.Optional;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
// ... (Giữ nguyên các import khác) ...
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import com.backend.controller.request.AddItemToCartRequest;
import com.backend.controller.request.UpdateCartItemRequest;
import com.backend.controller.response.CartResponse;
import com.backend.exception.ResourceNotFoundException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import com.backend.repository.CartItemRepository;
import com.backend.repository.CartRepository;
import com.backend.repository.ProductRepository;
import com.backend.repository.UserRepository;
import java.util.Collections; // Có thể cần cho Collections.emptySet()

public class CartServiceImplTest {

    // --- Mocks ---
    @Mock private CartRepository cartRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private UserRepository userRepository;
    @Mock private ProductRepository productRepository;

    // --- Class Under Test ---
    @InjectMocks private CartServiceImpl cartService;

    // --- Argument Captors ---
    @Captor private ArgumentCaptor<CartEntity> cartEntityCaptor;
    @Captor private ArgumentCaptor<CartItemEntity> cartItemEntityCaptor;
    // === SỬA: Đổi kiểu Captor ===
    @Captor private ArgumentCaptor<Iterable<CartItemEntity>> cartItemIterableCaptor;

    // --- Test Data ---
    private UserEntity testUser;
    private CartEntity testCart;
    private ProductEntity testProduct1;
    private ProductEntity testProduct2;
    private CartItemEntity testCartItem1;
    private Long userId = 1L;
    private Long productId1 = 101L;
    private Long productId2 = 102L;
    private Long cartItemId1 = 1001L;


    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        testUser = new UserEntity();
        testUser.setId(userId);
        testUser.setUsername("testcartuser");

        testCart = new CartEntity();
        testCart.setId(50L);
        testCart.setUser(testUser);
        // === SỬA: Dùng setCartItems và HashSet ===
        testCart.setCartItems(new HashSet<>());

        testProduct1 = new ProductEntity();
        testProduct1.setId(productId1);
        testProduct1.setName("Product A");
        testProduct1.setPrice(BigDecimal.valueOf(100.0));

        testProduct2 = new ProductEntity();
        testProduct2.setId(productId2);
        testProduct2.setName("Product B");
        testProduct2.setPrice(BigDecimal.valueOf(50.0));

        testCartItem1 = new CartItemEntity();
        testCartItem1.setId(cartItemId1);
        testCartItem1.setCart(testCart);
        testCartItem1.setProduct(testProduct1);
        testCartItem1.setQuantity(2);
    }

    @AfterMethod
    public void tearDown() {
        reset(cartRepository, cartItemRepository, userRepository, productRepository);
    }


    // =========================================
    // Tests for getCartByUserId
    // =========================================

    @Test(description = "getCartByUserId: Tìm thấy Cart có sẵn của User")
    public void testGetCartByUserId_ExistingCart() {
        // Arrange
        // === SỬA: Dùng getCartItems().add() ===
        testCart.getCartItems().add(testCartItem1);
        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(testCart));
        // Mock findByCart nếu service của bạn dùng nó để lấy items
        // when(cartItemRepository.findByCart(testCart)).thenReturn(testCart.getCartItems());

        // Act
        CartResponse result = cartService.getCartByUserId(userId);

        // Assert
        Assert.assertNotNull(result);
        // Giả sử CartResponse.getItems() trả về Collection hoặc List
        Assert.assertFalse(result.getItems().isEmpty(), "Giỏ hàng không được rỗng");
        Assert.assertEquals(result.getItems().size(), 1);

        // Verify
        verify(cartRepository).findByUserId(userId);
        // verify(cartItemRepository).findByCart(testCart); // Nếu service có gọi
    }

    @Test(description = "getCartByUserId: Không tìm thấy Cart, tạo mới cho User")
    public void testGetCartByUserId_NewCartCreated() {
        // Arrange
        when(cartRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(cartRepository.save(any(CartEntity.class))).thenAnswer(invocation -> {
            CartEntity cartToSave = invocation.getArgument(0);
            cartToSave.setId(51L);
            Assert.assertEquals(cartToSave.getUser(), testUser);
            // === KIỂM TRA: cartItems phải được khởi tạo (là Set) ===
            Assert.assertNotNull(cartToSave.getCartItems(), "CartItems phải được khởi tạo");
            Assert.assertTrue(cartToSave.getCartItems() instanceof Set, "CartItems phải là kiểu Set");
            return cartToSave;
        });

        // Act
        CartResponse result = cartService.getCartByUserId(userId);

        // Assert
        Assert.assertNotNull(result);
        Assert.assertTrue(result.getItems().isEmpty(), "Giỏ hàng mới phải rỗng");

        // Verify
        verify(cartRepository).findByUserId(userId);
        verify(userRepository).findById(userId);
        verify(cartRepository).save(cartEntityCaptor.capture());
        Assert.assertEquals(cartEntityCaptor.getValue().getUser(), testUser);
    }

    @Test(description = "getCartByUserId: User không tồn tại",
            expectedExceptions = ResourceNotFoundException.class)
    public void testGetCartByUserId_UserNotFound() {
        // Arrange
        when(cartRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        // Act
        cartService.getCartByUserId(userId);
        // Assert: TestNG kiểm tra exception
        // Verify
        verify(cartRepository).findByUserId(userId);
        verify(userRepository).findById(userId);
        verify(cartRepository, never()).save(any());
    }

    // =========================================
    // Tests for addItemToCart
    // =========================================

    @Test(description = "addItemToCart: Thêm sản phẩm mới vào giỏ hàng")
    public void testAddItemToCart_AddNewItem() {
        // Arrange
        AddItemToCartRequest request = new AddItemToCartRequest();
        request.setProductId(productId1);
        request.setQuantity(1);

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(testCart));
        when(productRepository.findById(productId1)).thenReturn(Optional.of(testProduct1));
        when(cartItemRepository.findByCartAndProduct(testCart, testProduct1)).thenReturn(Optional.empty());
        when(cartItemRepository.save(any(CartItemEntity.class))).thenAnswer(invocation -> {
            CartItemEntity newItem = invocation.getArgument(0);
            newItem.setId(1002L);
            // === SỬA: Dùng getCartItems().add() ===
            testCart.getCartItems().add(newItem); // Mô phỏng thêm vào Set
            return newItem;
        });
        // when(cartItemRepository.findByCart(testCart)).thenReturn(testCart.getCartItems()); // Mock nếu cần

        // Act
        CartResponse result = cartService.addItemToCart(userId, request);

        // Assert
        Assert.assertNotNull(result);
        Assert.assertEquals(result.getItems().size(), 1);

        // Verify
        verify(cartRepository).findByUserId(userId);
        verify(productRepository).findById(productId1);
        verify(cartItemRepository).findByCartAndProduct(testCart, testProduct1);
        verify(cartItemRepository).save(cartItemEntityCaptor.capture());
        CartItemEntity savedItem = cartItemEntityCaptor.getValue();
        Assert.assertEquals(savedItem.getProduct(), testProduct1);
        Assert.assertEquals(savedItem.getQuantity(), 1);
        Assert.assertEquals(savedItem.getCart(), testCart);
    }

    @Test(description = "addItemToCart: Tăng số lượng sản phẩm đã có")
    public void testAddItemToCart_IncreaseQuantity() {
        // Arrange
        AddItemToCartRequest request = new AddItemToCartRequest();
        request.setProductId(productId1);
        request.setQuantity(1); // Thêm 1

        // === SỬA: Dùng getCartItems().add() ===
        testCart.getCartItems().add(testCartItem1); // Item ban đầu có quantity = 2

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(testCart));
        when(productRepository.findById(productId1)).thenReturn(Optional.of(testProduct1));
        when(cartItemRepository.findByCartAndProduct(testCart, testProduct1)).thenReturn(Optional.of(testCartItem1));
        when(cartItemRepository.save(any(CartItemEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        // when(cartItemRepository.findByCart(testCart)).thenReturn(testCart.getCartItems()); // Mock nếu cần

        // Act
        CartResponse result = cartService.addItemToCart(userId, request);

        // Assert
        Assert.assertNotNull(result);
        Assert.assertEquals(result.getItems().size(), 1); // Vẫn là 1 loại item

        // Verify
        verify(cartRepository).findByUserId(userId);
        verify(productRepository).findById(productId1);
        verify(cartItemRepository).findByCartAndProduct(testCart, testProduct1);
        verify(cartItemRepository).save(cartItemEntityCaptor.capture());
        CartItemEntity savedItem = cartItemEntityCaptor.getValue();
        Assert.assertEquals(savedItem.getId(), cartItemId1);
        Assert.assertEquals(savedItem.getQuantity(), 3); // Số lượng mới
    }

    @Test(description = "addItemToCart: Sản phẩm không tồn tại",
            expectedExceptions = ResourceNotFoundException.class)
    public void testAddItemToCart_ProductNotFound() {
        // Arrange
        AddItemToCartRequest request = new AddItemToCartRequest();
        Long nonExistentProductId = 999L;
        request.setProductId(nonExistentProductId);
        request.setQuantity(1);

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(testCart));
        when(productRepository.findById(nonExistentProductId)).thenReturn(Optional.empty());

        // Act
        cartService.addItemToCart(userId, request);

        // Assert: TestNG xử lý exception
        // Verify
        verify(cartRepository).findByUserId(userId);
        verify(productRepository).findById(nonExistentProductId);
        verify(cartItemRepository, never()).save(any());
    }

    // =========================================
    // Tests for updateCartItemQuantity
    // =========================================
    @Test(description = "updateCartItemQuantity: Cập nhật số lượng thành công")
    public void testUpdateCartItemQuantity_Success() {
        // Arrange
        UpdateCartItemRequest request = new UpdateCartItemRequest();
        int newQuantity = 5;
        request.setQuantity(newQuantity);

        // === SỬA: Dùng getCartItems().add() ===
        testCart.getCartItems().add(testCartItem1);

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(testCart));
        when(cartItemRepository.findByIdAndCart(cartItemId1, testCart)).thenReturn(Optional.of(testCartItem1));
        when(cartItemRepository.save(any(CartItemEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        // when(cartItemRepository.findByCart(testCart)).thenReturn(testCart.getCartItems()); // Mock nếu cần

        // Act
        CartResponse result = cartService.updateCartItemQuantity(userId, cartItemId1, request);

        // Assert
        Assert.assertNotNull(result);
        // Thêm assert chi tiết cho response nếu cần

        // Verify
        verify(cartRepository).findByUserId(userId);
        verify(cartItemRepository).findByIdAndCart(cartItemId1, testCart);
        verify(cartItemRepository).save(cartItemEntityCaptor.capture());
        Assert.assertEquals(cartItemEntityCaptor.getValue().getQuantity(), newQuantity);
        verify(cartItemRepository, never()).delete(any());
    }

    @Test(description = "updateCartItemQuantity: Cập nhật số lượng <= 0 -> Xóa item")
    public void testUpdateCartItemQuantity_RemoveItemIfZeroOrLess() {
        // Arrange
        UpdateCartItemRequest request = new UpdateCartItemRequest();
        request.setQuantity(0);

        // === SỬA: Dùng getCartItems().add() ===
        testCart.getCartItems().add(testCartItem1);

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(testCart));
        when(cartItemRepository.findByIdAndCart(cartItemId1, testCart)).thenReturn(Optional.of(testCartItem1));
        // Giả lập findByCart trả về set rỗng sau khi xóa
        // when(cartItemRepository.findByCart(testCart)).thenReturn(Collections.emptySet());

        // Act
        CartResponse result = cartService.updateCartItemQuantity(userId, cartItemId1, request);

        // Assert
        Assert.assertNotNull(result);
        Assert.assertTrue(result.getItems().isEmpty());

        // Verify
        verify(cartRepository).findByUserId(userId);
        verify(cartItemRepository).findByIdAndCart(cartItemId1, testCart);
        verify(cartItemRepository).delete(eq(testCartItem1)); // Verify xóa đúng item
        verify(cartItemRepository, never()).save(any());
    }

    @Test(description = "updateCartItemQuantity: Item không tìm thấy trong giỏ",
            expectedExceptions = ResourceNotFoundException.class)
    public void testUpdateCartItemQuantity_ItemNotFound() {
        // Arrange
        UpdateCartItemRequest request = new UpdateCartItemRequest();
        request.setQuantity(5);
        Long nonExistentItemId = 9999L;

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(testCart));
        when(cartItemRepository.findByIdAndCart(nonExistentItemId, testCart)).thenReturn(Optional.empty());

        // Act
        cartService.updateCartItemQuantity(userId, nonExistentItemId, request);

        // Assert: TestNG xử lý exception
        // Verify
        verify(cartRepository).findByUserId(userId);
        verify(cartItemRepository).findByIdAndCart(nonExistentItemId, testCart);
        verify(cartItemRepository, never()).save(any());
        verify(cartItemRepository, never()).delete(any());
    }

    // =========================================
    // Tests for removeItemFromCart
    // =========================================
    @Test(description = "removeItemFromCart: Xóa item thành công")
    public void testRemoveItemFromCart_Success() {
        // Arrange
        // === SỬA: Dùng getCartItems().add() ===
        testCart.getCartItems().add(testCartItem1);

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(testCart));
        when(cartItemRepository.findByIdAndCart(cartItemId1, testCart)).thenReturn(Optional.of(testCartItem1));
        // Giả lập findByCart trả về set rỗng sau khi xóa
        // when(cartItemRepository.findByCart(testCart)).thenReturn(Collections.emptySet());

        // Act
        CartResponse result = cartService.removeItemFromCart(userId, cartItemId1);

        // Assert
        Assert.assertNotNull(result);
        Assert.assertTrue(result.getItems().isEmpty());

        // Verify
        verify(cartRepository).findByUserId(userId);
        verify(cartItemRepository).findByIdAndCart(cartItemId1, testCart);
        verify(cartItemRepository).delete(eq(testCartItem1));
    }

    @Test(description = "removeItemFromCart: Item không tìm thấy",
            expectedExceptions = ResourceNotFoundException.class)
    public void testRemoveItemFromCart_ItemNotFound() {
        // Arrange
        Long nonExistentItemId = 9999L;
        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(testCart));
        when(cartItemRepository.findByIdAndCart(nonExistentItemId, testCart)).thenReturn(Optional.empty());

        // Act
        cartService.removeItemFromCart(userId, nonExistentItemId);

        // Assert: TestNG xử lý exception
        // Verify
        verify(cartRepository).findByUserId(userId);
        verify(cartItemRepository).findByIdAndCart(nonExistentItemId, testCart);
        verify(cartItemRepository, never()).delete(any());
    }


    // =========================================
    // Tests for clearCart
    // =========================================
    @Test(description = "clearCart: Xóa tất cả item thành công")
    public void testClearCart_Success() {
        // Arrange
        CartItemEntity item2 = new CartItemEntity(); item2.setId(1002L); item2.setProduct(testProduct2); item2.setQuantity(1); item2.setCart(testCart);
        // === SỬA: Dùng getCartItems().add() ===
        testCart.getCartItems().add(testCartItem1);
        testCart.getCartItems().add(item2);
        // === SỬA: Dùng Set cho itemsInCart ===
        Set<CartItemEntity> itemsInCartSet = new HashSet<>(testCart.getCartItems());

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(testCart));
        // Giả sử service gọi findByCart để lấy items trước khi xóa
        when(cartItemRepository.findByCart(testCart)).thenReturn((List<CartItemEntity>) itemsInCartSet);
        // Mock phương thức void deleteAll
        // doNothing().when(cartItemRepository).deleteAll(any(Iterable.class));

        // Act
        cartService.clearCart(userId);

        // Verify
        verify(cartRepository).findByUserId(userId);
        // Giả sử service gọi findByCart rồi deleteAll(items)
        verify(cartItemRepository).findByCart(testCart);
        verify(cartItemRepository).deleteAll(cartItemIterableCaptor.capture()); // Bắt Iterable
        // Kiểm tra nội dung của Iterable đã bắt
        List<CartItemEntity> deletedItems = new ArrayList<>();
        cartItemIterableCaptor.getValue().forEach(deletedItems::add);
        Assert.assertEquals(deletedItems.size(), 2);
        // Kiểm tra bằng contains vì Set không có thứ tự
        Assert.assertTrue(deletedItems.contains(testCartItem1));
        Assert.assertTrue(deletedItems.contains(item2));

        // Hoặc nếu service gọi deleteAllByCart(cart):
        // verify(cartItemRepository).deleteAllByCart(eq(testCart));
    }

    @Test(description = "clearCart: Cart không tồn tại, không làm gì cả")
    public void testClearCart_CartNotFound() {
        // Arrange
        when(cartRepository.findByUserId(userId)).thenReturn(Optional.empty());

        // Act
        cartService.clearCart(userId);

        // Verify
        verify(cartRepository).findByUserId(userId);
        verify(cartItemRepository, never()).findByCart(any());
        verify(cartItemRepository, never()).deleteAll(any(Iterable.class));
        // verify(cartItemRepository, never()).deleteAllByCart(any());
    }
}