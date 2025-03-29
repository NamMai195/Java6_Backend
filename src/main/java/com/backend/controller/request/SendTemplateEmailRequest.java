package com.backend.controller.request;


public class SendTemplateEmailRequest {
    private String toEmail;
    private Long templateId;
    private String userName;
    private String verificationLink;

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
}
