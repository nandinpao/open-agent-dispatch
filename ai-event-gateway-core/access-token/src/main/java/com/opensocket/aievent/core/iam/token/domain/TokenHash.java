package com.opensocket.aievent.core.iam.token.domain;
public record TokenHash(String algorithm,String encoded){public TokenHash{algorithm=TokenText.required(algorithm,"algorithm",32);encoded=TokenText.required(encoded,"encoded",512);}}
