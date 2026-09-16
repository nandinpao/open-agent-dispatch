package com.opensocket.aievent.core.integration.identity;
import java.util.Arrays;
public final class ResolvedIntegrationSecret implements AutoCloseable { private final char[] value; public ResolvedIntegrationSecret(char[] value){this.value=value==null?new char[0]:value.clone();} public char[] value(){return value.clone();} public String reveal(){return new String(value);} public void close(){Arrays.fill(value,'\0');} }
