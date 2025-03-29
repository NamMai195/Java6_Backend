package com.backend.service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import sendinblue.ApiClient;
import sendinblue.ApiException;
import sendinblue.Configuration;
import sendinblue.auth.ApiKeyAuth;
import sibApi.TransactionalEmailsApi;
import sibModel.SendSmtpEmail;
import sibModel.SendSmtpEmailSender;
import sibModel.SendSmtpEmailTo;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class BrevoEmailService {

    @Value("${brevo.api-key}")
    private String apiKey;

    public void sendEmail(String toEmail, String subject, String content) throws ApiException {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        ApiKeyAuth apiKeyAuth = (ApiKeyAuth) defaultClient.getAuthentication("api-key");
        apiKeyAuth.setApiKey(apiKey);

        TransactionalEmailsApi apiInstance = new TransactionalEmailsApi();
        SendSmtpEmail sendSmtpEmail = new SendSmtpEmail();

        SendSmtpEmailSender sender = new SendSmtpEmailSender();
        sender.setEmail("mnam3239@gmail.com"); // Email người gửi
        sender.setName("Nam Mai"); // Tên người gửi
        sendSmtpEmail.setSender(sender);

        List<SendSmtpEmailTo> to = new ArrayList<>();
        SendSmtpEmailTo toEmailAddress = new SendSmtpEmailTo();
        toEmailAddress.setEmail(toEmail); // Email người nhận
        to.add(toEmailAddress);
        sendSmtpEmail.setTo(to);

        sendSmtpEmail.setSubject(subject);
        sendSmtpEmail.setHtmlContent(content);

        try {
            apiInstance.sendTransacEmail(sendSmtpEmail);
            System.out.println("Email sent successfully!");
        } catch (ApiException e) {
            System.err.println("Exception when calling TransactionalEmailsApi#sendTransacEmail");
            e.printStackTrace();
            throw e;
        }
    }
    public void sendEmailWithTemplate(String toEmail, Long templateId, Map<String, Object> params) throws ApiException {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        ApiKeyAuth apiKeyAuth = (ApiKeyAuth) defaultClient.getAuthentication("api-key");
        apiKeyAuth.setApiKey(apiKey);

        TransactionalEmailsApi apiInstance = new TransactionalEmailsApi();
        SendSmtpEmail sendSmtpEmail = new SendSmtpEmail();

        // Cân nhắc đưa thông tin người gửi vào application.properties
        SendSmtpEmailSender sender = new SendSmtpEmailSender();
        sender.setEmail("mnam3239@gmail.com"); // Email người gửi (NÊN LÀ EMAIL ĐÃ XÁC THỰC TRÊN BREVO)
        sender.setName("Nam Mai"); // Tên người gửi
        sendSmtpEmail.setSender(sender);

        // --- Thông tin người nhận ---
        List<SendSmtpEmailTo> toList = new ArrayList<>();
        SendSmtpEmailTo recipient = new SendSmtpEmailTo();
        recipient.setEmail(toEmail);
        // recipient.setName("Tên người nhận nếu có"); // Tùy chọn: đặt tên người nhận
        toList.add(recipient);
        sendSmtpEmail.setTo(toList);

        // --- Chỉ định Template ID và Tham số ---
        sendSmtpEmail.setTemplateId(templateId);
        sendSmtpEmail.setParams(params); // Truyền Map tham số vào đây

        // Lưu ý: KHÔNG setSubject() hoặc setHtmlContent() ở đây, vì chúng sẽ được lấy từ template.
        // Nếu bạn setSubject(), nó sẽ ghi đè chủ đề của template.

        try {
            // Gọi API để gửi email giao dịch
            apiInstance.sendTransacEmail(sendSmtpEmail);
            System.out.println("Email (Template ID: " + templateId + ") sent successfully to " + toEmail);
        } catch (ApiException e) {
            System.err.println("Exception when calling TransactionalEmailsApi#sendTransacEmail (Template ID: " + templateId + ")");
            System.err.println("Status code: " + e.getCode());
            System.err.println("Reason: " + e.getResponseBody());
            System.err.println("Response headers: " + e.getResponseHeaders());
            e.printStackTrace();
            throw e; // Re-throw để lớp gọi có thể xử lý
        }
    }


}