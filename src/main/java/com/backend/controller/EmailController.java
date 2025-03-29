package com.backend.controller;

import com.backend.controller.request.SendTemplateEmailRequest;
import com.backend.service.BrevoEmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value; // Thêm import @Value
import org.springframework.http.HttpStatus; // Thêm import HttpStatus
import org.springframework.http.ResponseEntity; // Thêm import ResponseEntity
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping; // Sử dụng POST cho hành động gửi
import org.springframework.web.bind.annotation.RequestBody; // Sử dụng RequestBody để nhận dữ liệu phức tạp hơn
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sendinblue.ApiException;

import java.util.HashMap;
import java.util.Map;


@RestController
public class EmailController {

    @Autowired
    private BrevoEmailService brevoEmailService;

    // --- Lấy các giá trị mặc định từ application.properties ---
    // (Bạn cần định nghĩa các giá trị này trong file application.properties)
    @Value("${app.email.defaults.login-link:https://yourdomain.com/login}") // Ví dụ giá trị mặc định
    private String defaultLoginLink;

    @Value("${app.email.defaults.service-name:My Awesome Service}")
    private String defaultServiceName;

    @Value("${app.email.defaults.company-name:My Company LLC}")
    private String defaultCompanyName;

    @Value("${app.email.defaults.company-address:123 Main St, Anytown}")
    private String defaultCompanyAddress;

    @Value("${app.email.defaults.support-phone:N/A}")
    private String defaultSupportPhone;

    @Value("${app.email.defaults.faq-link:#}")
    private String defaultFaqLink;

    @Value("${app.email.defaults.policy-link:#}")
    private String defaultPolicyLink;

    @Value("${app.email.defaults.company-slogan:}")
    private String defaultCompanySlogan;
    // --- Kết thúc phần lấy giá trị mặc định ---


    // Endpoint cũ để gửi nội dung HTML trực tiếp (Giữ lại nếu cần)
    @GetMapping("/send-email")
    public ResponseEntity<String> sendRawEmail(@RequestParam String toEmail, @RequestParam String subject, @RequestParam String content) {
        try {
            brevoEmailService.sendEmail(toEmail, subject, content);
            return ResponseEntity.ok("Email sent successfully!");
        } catch (ApiException e) {
            System.err.println("API Exception sending raw email: " + e.getResponseBody());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to send email: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("General Exception sending raw email: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An unexpected error occurred: " + e.getMessage());
        }
    }

    /**
     * Endpoint để gửi email sử dụng template của Brevo.
     * Sử dụng phương thức POST vì nó thực hiện một hành động (gửi email).
     * Sử dụng @RequestBody để nhận dữ liệu có cấu trúc.
     */
    @PostMapping("/send-template-email")
    // Sử dụng ResponseEntity để trả về cả trạng thái HTTP và nội dung phản hồi
    public ResponseEntity<String> sendTemplateEmail(@RequestBody SendTemplateEmailRequest request) {

        if (request.getToEmail() == null || request.getTemplateId() == null ||
                request.getUserName() == null || request.getVerificationLink() == null) {
            return ResponseEntity.badRequest().body("Missing required fields: toEmail, templateId, userName, verificationLink");
        }

        try {
            // Tạo Map chứa các tham số cho template
            Map<String, Object> params = new HashMap<>();

            // Các tham số bắt buộc từ request
            params.put("user_name", request.getUserName()); // Key khớp với {{ params.user_name }}
            params.put("verification_link", request.getVerificationLink()); // Key khớp với {{ params.verification_link }}

            // Các tham số khác (lấy từ request nếu có, nếu không dùng giá trị mặc định từ @Value)
            // (Trong ví dụ này, chúng ta dùng giá trị mặc định)
            params.put("login_link", defaultLoginLink);
            params.put("service_name", defaultServiceName);
            params.put("company_name", defaultCompanyName);
            params.put("company_slogan", defaultCompanySlogan); // Có thể là "" nếu không có
            params.put("company_address", defaultCompanyAddress);
            params.put("faq_link", defaultFaqLink);
            params.put("policy_link", defaultPolicyLink);
            params.put("support_phone", defaultSupportPhone);

            // Gọi service để gửi email
            brevoEmailService.sendEmailWithTemplate(request.getToEmail(), request.getTemplateId(), params);

            // Trả về phản hồi thành công
            return ResponseEntity.ok("Template email sent successfully to " + request.getToEmail());

        } catch (ApiException e) {
            // Log lỗi chi tiết hơn phía server
            System.err.println("API Exception sending template email: " + e.getResponseBody());
            e.printStackTrace();
            // Trả về lỗi cho client
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to send template email due to API error: " + e.getMessage());
        } catch (Exception e) {
            // Bắt các lỗi không mong muốn khác
            System.err.println("General Exception sending template email: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An unexpected error occurred while sending template email: " + e.getMessage());
        }
    }
}