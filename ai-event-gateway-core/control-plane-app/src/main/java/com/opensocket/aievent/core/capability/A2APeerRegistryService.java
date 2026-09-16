package com.opensocket.aievent.core.capability;

import com.opensocket.aievent.core.security.outbound.OutboundDestinationPolicy;
import com.opensocket.aievent.core.security.outbound.OutboundDestinationValidator;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Stage 6 hardened F0 administration. Agent Card discovery is evidence, never authorization authority. */
@Service
public class A2APeerRegistryService {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;
    private final OutboundDestinationValidator destinations;
    private final A2AAgentCardPersistenceService cardPersistence;
    private final A2ATrustAssurancePolicyService assurance;
    private final HttpClient http;

    public A2APeerRegistryService(NamedParameterJdbcTemplate jdbc,ObjectMapper json,OutboundDestinationValidator destinations,A2AAgentCardPersistenceService cardPersistence,A2ATrustAssurancePolicyService assurance){
        this.jdbc=jdbc;this.json=json;this.destinations=destinations;this.cardPersistence=cardPersistence;this.assurance=assurance;
        this.http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Transactional(readOnly=true)
    public List<Map<String,Object>> peers(String tenant){bind(tenant,"a2a-peer-registry-read");return jdbc.queryForList("select peer_id,display_name,agent_card_url,trust_status,status,last_card_refresh_at,created_at,updated_at from a2a_peer_registrations where tenant_id=:tenant order by display_name,peer_id",new MapSqlParameterSource("tenant",tenant));}

    @Transactional
    public Map<String,Object> upsertPeer(String tenant,String peerId,String displayName,String cardUrl){
        bind(tenant,"a2a-peer-registry-write");required(peerId,"peerId");required(displayName,"displayName");required(cardUrl,"agentCardUrl");
        String normalized=destinations.requireAllowed(cardUrl,OutboundDestinationPolicy.externalHttp(),"A2A_AGENT_CARD").toString();
        jdbc.update("""
          insert into a2a_peer_registrations(tenant_id,peer_id,display_name,agent_card_url,trust_status,status,created_at,updated_at)
          values(:tenant,:peer,:name,:url,'PROPOSED','DRAFT',now(),now())
          on conflict(tenant_id,peer_id) do update set
            display_name=excluded.display_name,
            agent_card_url=excluded.agent_card_url,
            trust_status=case when a2a_peer_registrations.agent_card_url is distinct from excluded.agent_card_url then 'PROPOSED' else a2a_peer_registrations.trust_status end,
            status=case when a2a_peer_registrations.agent_card_url is distinct from excluded.agent_card_url and a2a_peer_registrations.status='ACTIVE' then 'SUSPENDED' else a2a_peer_registrations.status end,
            updated_at=now()
          """,new MapSqlParameterSource("tenant",tenant).addValue("peer",peerId).addValue("name",displayName).addValue("url",normalized));
        return peer(tenant,peerId);
    }

    /** Network fetch is deliberately outside the database transaction. Redirects are fail-closed. */
    public Map<String,Object> refreshAgentCard(String tenant,String peerId){
        Map<String,Object> p=peerTx(tenant,peerId);URI uri=destinations.requireAllowed(String.valueOf(p.get("agent_card_url")),OutboundDestinationPolicy.externalHttp(),"A2A_AGENT_CARD");
        try{
            HttpRequest req=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).GET().header("Accept","application/json").build();
            HttpResponse<String> res=http.send(req,HttpResponse.BodyHandlers.ofString());
            if(res.statusCode()>=300&&res.statusCode()<400)throw new IllegalArgumentException("A2A_AGENT_CARD_REDIRECT_NOT_ALLOWED");
            if(res.statusCode()<200||res.statusCode()>=300)throw new IllegalArgumentException("A2A_AGENT_CARD_HTTP_"+res.statusCode());
            @SuppressWarnings("unchecked") Map<String,Object> card=json.readValue(res.body(),Map.class);
            cardPersistence.persist(tenant,peerId,card,res.body());
            return peerTx(tenant,peerId);
        }catch(IllegalArgumentException ex){throw ex;}catch(Exception ex){throw new IllegalArgumentException("A2A_AGENT_CARD_REFRESH_FAILED: "+ex.getMessage(),ex);}
    }

    @Transactional(readOnly=true)
    public List<Map<String,Object>> interfaces(String tenant,String peerId){bind(tenant,"a2a-peer-interface-read");return jdbc.queryForList("select interface_id,peer_id,url,protocol_binding,protocol_version,interface_tenant,streaming_supported,push_notifications_supported,status,trust_status,endpoint_region,security_scheme_refs_json,required_extensions_json,supported_extensions_json,outbound_destination_policy_ref,priority,health_status,conformance_status,circuit_state,conformance_profile_id,conformance_run_id,conformance_valid_until,contract_version,created_at,updated_at from a2a_peer_interfaces where tenant_id=:tenant and peer_id=:peer order by priority,interface_id",new MapSqlParameterSource("tenant",tenant).addValue("peer",peerId));}

    @Transactional
    public Map<String,Object> approveInterface(String tenant,String interfaceId,String trust){
        bind(tenant,"a2a-peer-interface-approve");
        if(trust!=null&&!trust.isBlank())throw new IllegalArgumentException("A2A_LEGACY_TRUST_STATUS_WRITE_RETIRED");
        int n=jdbc.update("update a2a_peer_interfaces set status='APPROVED',updated_at=now() where tenant_id=:tenant and interface_id=:id and trust_status<>'REVOKED'",new MapSqlParameterSource("tenant",tenant).addValue("id",interfaceId));
        if(n!=1)throw new IllegalArgumentException("A2A_INTERFACE_NOT_APPROVABLE");
        return jdbc.queryForMap("select * from a2a_peer_interfaces where tenant_id=:tenant and interface_id=:id",new MapSqlParameterSource("tenant",tenant).addValue("id",interfaceId));
    }

    @Transactional
    public Map<String,Object> linkProvider(String tenant,String providerId,String peerId,String interfaceId){
        bind(tenant,"a2a-peer-provider-link");
        assurance.requireReadAllowed(tenant,peerId,interfaceId);
        Integer ok=jdbc.queryForObject("""
          select count(*) from capability_providers p
          join a2a_peer_registrations ap on ap.tenant_id=p.tenant_id and ap.peer_id=:peer
          join a2a_peer_interfaces i on i.tenant_id=p.tenant_id and i.interface_id=:interface and i.peer_id=ap.peer_id
          where p.tenant_id=:tenant and p.provider_id=:provider and p.provider_type='REMOTE_A2A_AGENT' and p.catalog_status='REGISTERED'
            and ap.status='ACTIVE' and i.status='APPROVED'
            and i.protocol_binding='HTTP+JSON'
            and a2a_interface_current_contract_eligible(p.tenant_id,i.interface_id)
          """,new MapSqlParameterSource("tenant",tenant).addValue("provider",providerId).addValue("peer",peerId).addValue("interface",interfaceId),Integer.class);
        if(ok==null||ok!=1)throw new IllegalArgumentException("A2A_PROVIDER_LINK_NOT_GOVERNED");
        jdbc.update("""
          insert into a2a_peer_provider_links(tenant_id,provider_id,peer_id,interface_id,status,created_at,updated_at) values(:tenant,:provider,:peer,:interface,'ACTIVE',now(),now())
          on conflict(tenant_id,provider_id) do update set peer_id=excluded.peer_id,interface_id=excluded.interface_id,status='ACTIVE',updated_at=now()
          """,new MapSqlParameterSource("tenant",tenant).addValue("provider",providerId).addValue("peer",peerId).addValue("interface",interfaceId));
        return jdbc.queryForMap("select * from a2a_peer_provider_links where tenant_id=:tenant and provider_id=:provider",new MapSqlParameterSource("tenant",tenant).addValue("provider",providerId));
    }

    @Transactional public void activatePeer(String tenant,String peerId){
        bind(tenant,"a2a-peer-activate");
        assurance.requirePeerReadAllowed(tenant,peerId);
        int n=jdbc.update("update a2a_peer_registrations set status='ACTIVE',updated_at=now() where tenant_id=:tenant and peer_id=:peer and trust_status<>'REVOKED'",new MapSqlParameterSource("tenant",tenant).addValue("peer",peerId));
        if(n!=1)throw new IllegalArgumentException("A2A_PEER_NOT_ACTIVATABLE");
    }

    @Transactional(readOnly=true) protected Map<String,Object> peerTx(String tenant,String id){bind(tenant,"a2a-peer-registry-read");return peer(tenant,id);}
    private Map<String,Object> peer(String tenant,String id){List<Map<String,Object>> rows=jdbc.queryForList("select * from a2a_peer_registrations where tenant_id=:tenant and peer_id=:peer",new MapSqlParameterSource("tenant",tenant).addValue("peer",id));if(rows.isEmpty())throw new IllegalArgumentException("A2A_PEER_NOT_FOUND");return rows.get(0);}
    private void bind(String tenant,String actor){jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_tenant_id', ?, true)",String.class,tenant);jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_actor_id', ?, true)",String.class,actor);}
    private static void required(String v,String n){if(v==null||v.isBlank())throw new IllegalArgumentException(n+" is required");}
}
