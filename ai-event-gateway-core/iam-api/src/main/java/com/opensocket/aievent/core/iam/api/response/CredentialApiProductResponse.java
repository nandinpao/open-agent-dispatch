package com.opensocket.aievent.core.iam.api.response;
import java.util.Set;
public record CredentialApiProductResponse(String productCode,String displayName,String description,Set<String> audiences,Set<String> apiPrefixes){public CredentialApiProductResponse{audiences=audiences==null?Set.of():Set.copyOf(audiences);apiPrefixes=apiPrefixes==null?Set.of():Set.copyOf(apiPrefixes);}}
