package com.opensocket.aievent.worker;
public record AdapterWorkResult(boolean success, boolean retryable, String responseRef, String error) { public static AdapterWorkResult success(String ref){return new AdapterWorkResult(true,false,ref,null);} public static AdapterWorkResult failure(String error,boolean retryable){return new AdapterWorkResult(false,retryable,null,error);} }
