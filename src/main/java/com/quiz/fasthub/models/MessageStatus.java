package com.quiz.fasthub.models;


import lombok.Data;

@Data
public class MessageStatus {
    private String status;
    private String reference;
    private String description;
    private String msisdn;
    private int retryCount;
    private long lastRetryTime;
}
