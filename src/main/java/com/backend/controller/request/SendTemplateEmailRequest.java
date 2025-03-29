package com.backend.controller.request;

// Lớp DTO (Data Transfer Object) để nhận dữ liệu yêu cầu gửi email template
// Giúp cấu trúc dữ liệu rõ ràng hơn thay vì dùng nhiều @RequestParam
public class SendTemplateEmailRequest {
    private String toEmail;
    private Long templateId;
    private String userName;
    private String verificationLink;
    // Thêm các trường khác tương ứng với params template nếu cần
    // Ví dụ:
    // private String loginLink;
    // private String serviceName;

    // Getters and Setters (Bắt buộc cho việc binding dữ liệu từ JSON)
    public String getToEmail() {
        return toEmail;
    }

    public void setToEmail(String toEmail) {
        this.toEmail = toEmail;
    }

    public Long getTemplateId() {
        return templateId;
    }

    public void setTemplateId(Long templateId) {
        this.templateId = templateId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getVerificationLink() {
        return verificationLink;
    }

    public void setVerificationLink(String verificationLink) {
        this.verificationLink = verificationLink;
    }
    // Getters/Setters cho các trường khác...
}
