package com.opensocket.aievent.core.iam.token.domain;
public record ServiceAccountId(String value){public ServiceAccountId{value=TokenText.required(value,"serviceAccountId",128);}}
