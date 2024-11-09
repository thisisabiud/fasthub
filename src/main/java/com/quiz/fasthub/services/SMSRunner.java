package com.quiz.fasthub.services;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class SMSRunner implements CommandLineRunner {
    private final SMSService smsService;
    private String filePath;

    @Autowired
    public SMSRunner(SMSService smsService) {
        this.smsService = smsService;
    }

    @Value("${phone.numbers.file.path}")
    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    @Override
    public void run(String... args) {
        smsService.processPhoneNumbersFile(filePath);
        smsService.generateReport();
    }
}
