package com.opensocket.aievent.core.api.contract;
public record ApiResponseEnvelope<T>(T data,ApiResponseMetadata meta) {}
