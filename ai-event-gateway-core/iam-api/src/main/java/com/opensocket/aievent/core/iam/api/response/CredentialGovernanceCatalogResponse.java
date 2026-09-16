package com.opensocket.aievent.core.iam.api.response;
import java.util.List;
public record CredentialGovernanceCatalogResponse(
        List<CredentialApiProductResponse> apiProducts,
        List<CredentialAudienceResponse> audiences,
        List<CredentialMachineScopeResponse> machineScopes,
        List<CredentialSourceSystemResponse> sourceSystems){
    public CredentialGovernanceCatalogResponse{
        apiProducts=apiProducts==null?List.of():List.copyOf(apiProducts);
        audiences=audiences==null?List.of():List.copyOf(audiences);
        machineScopes=machineScopes==null?List.of():List.copyOf(machineScopes);
        sourceSystems=sourceSystems==null?List.of():List.copyOf(sourceSystems);
    }
}
