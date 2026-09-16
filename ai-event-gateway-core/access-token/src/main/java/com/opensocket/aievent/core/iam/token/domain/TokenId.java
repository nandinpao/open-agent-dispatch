package com.opensocket.aievent.core.iam.token.domain;
public record TokenId(String value){public TokenId{value=TokenText.required(value,"tokenId",128);}}
