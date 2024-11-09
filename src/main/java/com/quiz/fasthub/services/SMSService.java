package com.quiz.fasthub.services;

import com.quiz.fasthub.models.MessageStatus;
import com.quiz.fasthub.models.SMSRequest;
import com.quiz.fasthub.models.SMSResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class SMSService {
    private final RestTemplate restTemplate;
    private final Map<String, MessageStatus> messageStatusMap = new ConcurrentHashMap<>();
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY = 60000; // 1 minute

    @Value("${sms.client.id}")
    private String clientId;

    @Value("${sms.client.secret}")
    private String clientSecret;

    @Value("${sms.endpoints.send}")
    private String sendEndpoint;



    @Autowired
    public SMSService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void processPhoneNumbersFile(String filePath) {
        try {
            List<String> lines = Files.readAllLines(Paths.get(filePath));
            String phoneNumbersLine = lines.get(0);
            String messageTemplateLine = lines.get(1);

            String[] phoneNumbers = phoneNumbersLine.substring(phoneNumbersLine.indexOf(":") + 1)
                    .split(",");
            String messageTemplate = messageTemplateLine.substring(messageTemplateLine.indexOf(":") + 1)
                    .trim();

            for (String phoneNumber : phoneNumbers) {
                String trimmedNumber = phoneNumber.trim();
                String reference = UUID.randomUUID().toString();
                sendSMS(trimmedNumber, messageTemplate.replace("{PHONE}", trimmedNumber), reference);
            }
        } catch (Exception e) {
            log.error("Error processing phone numbers file: {}", e.getMessage(), e);
        }
    }

    private void sendSMS(String phoneNumber, String message, String reference) {
        try {
            SMSRequest request = getSmsRequest(phoneNumber, message, reference);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<SMSRequest> entity = new HttpEntity<>(request, headers);

            SMSResponse response = restTemplate.postForObject(sendEndpoint, entity, SMSResponse.class);

            MessageStatus status = new MessageStatus();
            status.setMsisdn(phoneNumber);
            status.setReference(reference);
            status.setRetryCount(0);
            status.setLastRetryTime(System.currentTimeMillis());
            status.setStatus(response != null && response.getStatus() ? "SENT" : "FAILED");

            messageStatusMap.put(reference, status);
            log.info("SMS sent to {}: {}", phoneNumber, status.getStatus());
        } catch (Exception e) {
            log.error("Error sending SMS to {}: {}", phoneNumber, e.getMessage(), e);
            MessageStatus status = new MessageStatus();
            status.setMsisdn(phoneNumber);
            status.setReference(reference);
            status.setStatus("FAILED");
            status.setDescription(e.getMessage());
            messageStatusMap.put(reference, status);
        }
    }

    private SMSRequest getSmsRequest(String phoneNumber, String message, String reference) {
        SMSRequest request = new SMSRequest();
        SMSRequest.Auth auth = new SMSRequest.Auth();
        auth.setClientId(clientId);
        auth.setClientSecret(clientSecret);
        request.setAuth(auth);

        SMSRequest.Message smsMessage = new SMSRequest.Message();
        smsMessage.setMsisdn(phoneNumber);
        smsMessage.setText(message);
        smsMessage.setReference(reference);
        smsMessage.setSource("255748986533");

        request.setMessages(Collections.singletonList(smsMessage));
        return request;
    }

    @Scheduled(fixedDelay = 30000)
    public void checkAndRetryFailedMessages() {
        long currentTime = System.currentTimeMillis();

        messageStatusMap.forEach((reference, status) -> {
            if ("FAILED".equals(status.getStatus()) &&
                    status.getRetryCount() < MAX_RETRIES &&
                    currentTime - status.getLastRetryTime() >= RETRY_DELAY) {

                log.info("Retrying SMS for reference: {}, attempt: {}",
                        reference, status.getRetryCount() + 1);

                status.setRetryCount(status.getRetryCount() + 1);
                status.setLastRetryTime(currentTime);

                sendSMS(status.getMsisdn(),
                        "Retry " + status.getRetryCount() + ": " + status.getStatus(),
                        reference);
            }
        });
    }

    public void generateReport() {
        StringBuilder report = new StringBuilder();
        report.append("SMS Delivery Report - ").append(new Date()).append("\n");
        report.append("----------------------------------------\n");

        messageStatusMap.forEach((reference, status) -> {
            report.append(String.format("Reference: %s\n", reference));
            report.append(String.format("Phone Number: %s\n", status.getMsisdn()));
            report.append(String.format("Status: %s\n", status.getStatus()));
            report.append(String.format("Retry Count: %d\n", status.getRetryCount()));
            report.append(String.format("Last Retry: %s\n", new Date(status.getLastRetryTime())));
            report.append("----------------------------------------\n");
        });

        try {
            Files.write(Paths.get("sms_report_" + System.currentTimeMillis() + ".txt"),
                    report.toString().getBytes());
        } catch (Exception e) {
            log.error("Error generating report: {}", e.getMessage(), e);
        }
    }
}

