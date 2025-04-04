package com.backend.controller;

import com.backend.controller.request.SendTemplateEmailRequest;
import com.backend.service.BrevoEmailService;
import io.swagger.v3.oas.annotations.Operation; // Thêm import này nếu cần mô tả API
import io.swagger.v3.oas.annotations.tags.Tag; // Thêm import này nếu cần tag
import lombok.RequiredArgsConstructor; // Có thể dùng @RequiredArgsConstructor thay cho @Autowired
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
// **THÊM IMPORT CHO PHÂN QUYỀN**
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated; // Thêm nếu dùng validation ở mức class
import org.springframework.web.bind.annotation.*;
import sendinblue.ApiException;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor // Dùng cái này thay @Autowired nếu muốn
@Tag(name = "Email API (Admin)", description = "APIs for testing email sending (Admin Only)") // Thêm Tag cho Swagger
@Validated // Thêm nếu có validation ở mức phương thức/tham số
public class EmailController {

    // @Autowired // Không cần nếu dùng @RequiredArgsConstructor
    private final BrevoEmailService brevoEmailService;

    // --- Lấy các giá trị mặc định từ application.properties ---
    @Value("${app.email.defaults.login-link:https://yourdomain.com/login}")
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

    @Operation(summary = "Send Raw Email Test (Admin)", description = "Sends a simple HTML email directly. (Requires ADMIN role)") // Thêm mô tả Swagger
    @GetMapping("/send-email") // Giữ nguyên hoặc đổi sang POST nếu thấy phù hợp hơn
    @PreAuthorize("hasRole('ADMIN')") // **PHÂN QUYỀN ADMIN**
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

    @Operation(summary = "Send Template Email Test (Admin)", description = "Sends an email using a Brevo template. (Requires ADMIN role)") // Thêm mô tả Swagger
    @PostMapping("/send-template-email")
    @PreAuthorize("hasRole('ADMIN')") // **PHÂN QUYỀN ADMIN**
    public ResponseEntity<String> sendTemplateEmail(@RequestBody SendTemplateEmailRequest request) { // Nên @Valid nếu request có validation

        // Check null có thể bỏ nếu dùng @Valid và @NotNull/@NotBlank trong SendTemplateEmailRequest
        if (request.getToEmail() == null || request.getTemplateId() == null ||
                request.getUserName() == null || request.getVerificationLink() == null) {
            return ResponseEntity.badRequest().body("Missing required fields: toEmail, templateId, userName, verificationLink");
        }

        try {
            Map<String, Object> params = new HashMap<>();
            params.put("user_name", request.getUserName());
            params.put("verification_link", request.getVerificationLink());
            params.put("login_link", defaultLoginLink);
            params.put("service_name", defaultServiceName);
            params.put("company_name", defaultCompanyName);
            params.put("company_slogan", defaultCompanySlogan);
            params.put("company_address", defaultCompanyAddress);
            params.put("faq_link", defaultFaqLink);
            params.put("policy_link", defaultPolicyLink);
            params.put("support_phone", defaultSupportPhone);

            brevoEmailService.sendEmailWithTemplate(request.getToEmail(), request.getTemplateId(), params);
            return ResponseEntity.ok("Template email sent successfully to " + request.getToEmail());

        } catch (ApiException e) {
            System.err.println("API Exception sending template email: " + e.getResponseBody());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to send template email due to API error: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("General Exception sending template email: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An unexpected error occurred while sending template email: " + e.getMessage());
        }
    }
}