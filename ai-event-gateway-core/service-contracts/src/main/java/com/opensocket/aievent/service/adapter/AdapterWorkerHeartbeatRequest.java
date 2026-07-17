package com.opensocket.aievent.service.adapter;

public record AdapterWorkerHeartbeatRequest(String workerId, Long leaseSeconds) {
}
