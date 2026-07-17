package com.opensocket.aievent.core.dispatch;

public record GatewayDispatchResult(
        boolean success,
        int httpStatus,
        String gatewayStatus,
        String message,
        GatewayDispatchResponse response
) {
    public static GatewayDispatchResult success(int httpStatus, GatewayDispatchResponse response) {
        return new GatewayDispatchResult(true, httpStatus, response == null ? "DISPATCH_ACCEPTED" : response.getStatus(), response == null ? "Dispatch accepted" : response.getMessage(), response);
    }

    public static GatewayDispatchResult failure(int httpStatus, String gatewayStatus, String message) {
        return new GatewayDispatchResult(false, httpStatus, gatewayStatus, message, null);
    }
}
