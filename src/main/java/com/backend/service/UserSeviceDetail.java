package com.backend.service;

import com.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean; // **THÊM IMPORT NÀY**
// Bỏ import org.springframework.context.annotation.Configuration; nếu không cần
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;


@Service // Giữ @Service vì nó chứa logic nghiệp vụ tìm user
@RequiredArgsConstructor
public class UserSeviceDetail { // Sửa tên class thành UserDetailsServiceImplementation hoặc tương tự sẽ rõ ràng hơn

    private final UserRepository userRepository;

    // Đổi tên phương thức thành userDetailsService (theo convention)
    // và đánh dấu là @Bean để Spring quản lý
    @Bean // **THÊM ANNOTATION @Bean Ở ĐÂY**
    public UserDetailsService userDetailsService() {
        // Trả về một lambda expression triển khai UserDetailsService
        return username -> userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));
    }
}