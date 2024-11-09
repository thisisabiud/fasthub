package com.quiz.fasthub.models;

import lombok.Data;

import java.util.List;

@Data
public class SMSRequest {
    private Auth auth;
    private List<Message> messages;

    @Data
    public static class Auth {
        private String clientId;
        private String clientSecret;
    }

    @Data
    public static class Message {
        private String text;
        private String msisdn;
        private String source;
        private String reference;
    }
}