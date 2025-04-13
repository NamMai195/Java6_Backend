package com.backend.service;

import lombok.extern.slf4j.Slf4j;
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
@Slf4j(topic = "BREVO-EMAIL-SERVICE")
public class BrevoEmailService {

    @Value("${brevo.api-key}")
    private String apiKey;

    @Value("${brevo.sender-email}")
    private String senderEmail;

    @Value("${brevo.sender-name}")
    private String senderName;

    public void sendEmail(String toEmail, String subject, String content) throws ApiException {
        log.info("Attempting to send plain email to {} with subject '{}'", toEmail, subject);
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        ApiKeyAuth apiKeyAuth = (ApiKeyAuth) defaultClient.getAuthentication("api-key");
        apiKeyAuth.setApiKey(apiKey);

        TransactionalEmailsApi apiInstance = new TransactionalEmailsApi();
        SendSmtpEmail sendSmtpEmail = new SendSmtpEmail();

        SendSmtpEmailSender sender = new SendSmtpEmailSender();
        sender.setEmail(senderEmail); // Use configured sender email
        sender.setName(senderName);   // Use configured sender name
        sendSmtpEmail.setSender(sender);

        List<SendSmtpEmailTo> to = new ArrayList<>();
        SendSmtpEmailTo toEmailAddress = new SendSmtpEmailTo();
        toEmailAddress.setEmail(toEmail); // Recipient email
        to.add(toEmailAddress);
        sendSmtpEmail.setTo(to);

        sendSmtpEmail.setSubject(subject);
        sendSmtpEmail.setHtmlContent(content);

        try {
            apiInstance.sendTransacEmail(sendSmtpEmail);
            log.info("Plain email sent successfully to {}", toEmail);
        } catch (ApiException e) {
            log.error("Exception sending plain email to {}: Code={}, Body={}, Headers={}",
                    toEmail, e.getCode(), e.getResponseBody(), e.getResponseHeaders(), e);
            throw e;
        }
    }

    public void sendEmailWithTemplate(String toEmail, Long templateId, Map<String, Object> params) throws ApiException {
        log.info("Attempting to send template email (ID: {}) to {} with params: {}", templateId, toEmail, params);
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        ApiKeyAuth apiKeyAuth = (ApiKeyAuth) defaultClient.getAuthentication("api-key");
        apiKeyAuth.setApiKey(apiKey);

        TransactionalEmailsApi apiInstance = new TransactionalEmailsApi();
        SendSmtpEmail sendSmtpEmail = new SendSmtpEmail();

        SendSmtpEmailSender sender = new SendSmtpEmailSender();
        sender.setEmail(senderEmail); // Use configured sender email
        sender.setName(senderName);   // Use configured sender name
        sendSmtpEmail.setSender(sender);

        // Recipient info
        List<SendSmtpEmailTo> toList = new ArrayList<>();
        SendSmtpEmailTo recipient = new SendSmtpEmailTo();
        recipient.setEmail(toEmail);
        // Optional: recipient.setName("Recipient Name");
        toList.add(recipient);
        sendSmtpEmail.setTo(toList);

        // Template ID and Parameters
        sendSmtpEmail.setTemplateId(templateId);
        sendSmtpEmail.setParams(params); // Pass the parameters map

        // Note: Do not set subject or htmlContent here, they come from the template.
        // Setting subject here would override the template's subject.

        try {
            apiInstance.sendTransacEmail(sendSmtpEmail);
            log.info("Template email (ID: {}) sent successfully to {}", templateId, toEmail);
        } catch (ApiException e) {
            log.error("Exception sending template email (ID: {}) to {}: Code={}, Body={}, Headers={}",
                    templateId, toEmail, e.getCode(), e.getResponseBody(), e.getResponseHeaders(), e);
            throw e; // Re-throw for the calling layer to handle
        }
    }

}
