package com.quiz.fasthub.models;

import lombok.Data;

@Data
public class SMSResponse {
    private Boolean status;
    private String message;
    private Object data;
}
