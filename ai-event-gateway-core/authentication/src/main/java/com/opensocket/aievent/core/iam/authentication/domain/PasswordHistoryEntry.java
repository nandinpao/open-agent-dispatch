package com.opensocket.aievent.core.iam.authentication.domain;
import java.time.Instant;
import java.util.Objects;
public record PasswordHistoryEntry(String historyId,CredentialSubjectType subjectType,String subjectId,PasswordHash passwordHash,Instant changedAt){public PasswordHistoryEntry{historyId=Text.required(historyId,"historyId",128);Objects.requireNonNull(subjectType,"subjectType");subjectId=Text.required(subjectId,"subjectId",128);Objects.requireNonNull(passwordHash,"passwordHash");Objects.requireNonNull(changedAt,"changedAt");}}
