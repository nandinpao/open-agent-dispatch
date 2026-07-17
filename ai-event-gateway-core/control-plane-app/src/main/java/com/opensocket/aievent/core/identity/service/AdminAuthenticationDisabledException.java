package com.opensocket.aievent.core.identity.service;

public class AdminAuthenticationDisabledException extends RuntimeException {
    public AdminAuthenticationDisabledException() {
        super("Core Admin authentication is disabled.");
    }
}
