package com.opensocket.aievent.service.adapter;

public record AdapterWorkerFailureRequest(String workerId, String error, Boolean retryable) {
}
