package com.opensocket.aievent.core.identity.service;
public class AdminSessionNotFoundException extends RuntimeException {
    public AdminSessionNotFoundException(String sessionReference) { super("Admin session was not found: " + sessionReference); }
}
