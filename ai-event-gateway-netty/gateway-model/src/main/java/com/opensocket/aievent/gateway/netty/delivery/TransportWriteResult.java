package com.opensocket.aievent.gateway.netty.delivery;

/** Result of writing a serialized command envelope to a local transport channel. */
public record TransportWriteResult(
        TransportWriteStatus status,
        String message
) {
    public static TransportWriteResult sent() {
        return new TransportWriteResult(TransportWriteStatus.SENT, "Command frame written to transport channel");
    }

    public static TransportWriteResult notWritable(String message) {
        return new TransportWriteResult(TransportWriteStatus.NOT_WRITABLE, message);
    }

    public static TransportWriteResult timeout(String message) {
        return new TransportWriteResult(TransportWriteStatus.TIMEOUT, message);
    }

    public static TransportWriteResult failed(String message) {
        return new TransportWriteResult(TransportWriteStatus.FAILED, message);
    }
}
