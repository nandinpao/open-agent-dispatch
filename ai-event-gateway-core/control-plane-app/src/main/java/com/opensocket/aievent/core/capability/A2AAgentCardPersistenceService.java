package com.opensocket.aievent.core.capability;

import com.opensocket.aievent.core.security.outbound.OutboundDestinationPolicy;
import com.opensocket.aievent.core.security.outbound.OutboundDestinationValidator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Stage 6 atomic Agent Card persistence boundary. Network I/O is deliberately outside this transaction. */
@Service
public class A2AAgentCardPersistenceService {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;
    private final OutboundDestinationValidator destinations;

    public A2AAgentCardPersistenceService(NamedParameterJdbcTemplate jdbc,ObjectMapper json,OutboundDestinationValidator destinations){
        this.jdbc=jdbc;this.json=json;this.destinations=destinations;
    }

    @Transactional
    public void persist(String tenant,String peerId,Map<String,Object> card,String raw){
        bind(tenant,"a2a-peer-card-persist");
        List<DiscoveredInterface> discovered=validatedInterfaces(peerId,card);
        if(discovered.isEmpty())throw new IllegalArgumentException("A2A_AGENT_CARD_HAS_NO_SUPPORTED_INTERFACES");

        String snapshot="a2a-card-"+UUID.randomUUID();String digest=sha(raw);
        jdbc.update("insert into a2a_agent_card_snapshots(tenant_id,snapshot_id,peer_id,card_json,card_sha256,protocol_version,fetched_at) values(:tenant,:id,:peer,cast(:card as jsonb),:digest,null,now())",
                new MapSqlParameterSource("tenant",tenant).addValue("id",snapshot).addValue("peer",peerId).addValue("card",write(card)).addValue("digest",digest));

        List<String> interfaceIds=new ArrayList<>();
        for(DiscoveredInterface it:discovered){
            interfaceIds.add(it.interfaceId());
            jdbc.update("""
              insert into a2a_peer_interfaces(tenant_id,interface_id,peer_id,url,protocol_binding,protocol_version,interface_tenant,streaming_supported,push_notifications_supported,status,trust_status,created_at,updated_at)
              values(:tenant,:iid,:peer,:url,:binding,:version,:it,:stream,:push,'PROPOSED','INTEGRITY_RECORDED',now(),now())
              on conflict(tenant_id,peer_id,url,protocol_binding,protocol_version) do update set
                interface_tenant=excluded.interface_tenant,
                streaming_supported=excluded.streaming_supported,
                push_notifications_supported=excluded.push_notifications,
                updated_at=now(),
                status=case when a2a_peer_interfaces.status='RETIRED' then 'RETIRED' else a2a_peer_interfaces.status end,
                trust_status=case
                  when a2a_peer_interfaces.trust_status='REVOKED' then 'REVOKED'
                  when a2a_peer_interfaces.trust_status in ('MANUAL_TRUSTED','IDENTITY_VERIFIED','TRUST_APPROVED') then a2a_peer_interfaces.trust_status
                  else 'INTEGRITY_RECORDED' end
              """,new MapSqlParameterSource("tenant",tenant).addValue("iid",it.interfaceId()).addValue("peer",peerId).addValue("url",it.url()).addValue("binding",it.binding()).addValue("version",it.version()).addValue("it",it.interfaceTenant()).addValue("stream",it.streaming()).addValue("push",it.push()));
        }

        // A disappeared endpoint is never silently retained as execution-eligible.
        jdbc.update("""
          update a2a_peer_interfaces set status='SUSPENDED',updated_at=now()
           where tenant_id=:tenant and peer_id=:peer and status not in ('RETIRED','SUSPENDED')
             and interface_id not in (:ids)
          """,new MapSqlParameterSource("tenant",tenant).addValue("peer",peerId).addValue("ids",interfaceIds));

        jdbc.update("""
          update a2a_peer_registrations set
            trust_status=case
              when trust_status='REVOKED' then 'REVOKED'
              when trust_status in ('MANUAL_TRUSTED','IDENTITY_VERIFIED','TRUST_APPROVED') then trust_status
              else 'INTEGRITY_RECORDED' end,
            last_card_refresh_at=now(),updated_at=now()
           where tenant_id=:tenant and peer_id=:peer
          """,new MapSqlParameterSource("tenant",tenant).addValue("peer",peerId));
    }

    @SuppressWarnings("unchecked")
    private List<DiscoveredInterface> validatedInterfaces(String peerId,Map<String,Object> card){
        Object v=card.get("supportedInterfaces");if(!(v instanceof List<?> list))return List.of();
        Map<String,Object> caps=card.get("capabilities") instanceof Map<?,?> m?(Map<String,Object>)m:Map.of();
        boolean streaming=Boolean.TRUE.equals(caps.get("streaming"));boolean push=Boolean.TRUE.equals(caps.get("pushNotifications"));
        List<DiscoveredInterface> out=new ArrayList<>();
        for(Object o:list){
            if(!(o instanceof Map<?,?> raw))continue;Map<String,Object> it=(Map<String,Object>)raw;
            String binding=str(it.get("protocolBinding")),version=str(it.get("protocolVersion")),url=str(it.get("url"));
            if(url==null||binding==null||version==null||!version.matches("[0-9]{1,6}\\.[0-9]{1,6}")||!List.of("HTTP+JSON","JSONRPC").contains(binding))continue;
            String normalized=destinations.requireAllowed(url,OutboundDestinationPolicy.externalHttp(),"A2A_AGENT_INTERFACE").toString();
            String iid="a2a-interface-"+sha(peerId+"|"+normalized+"|"+binding+"|"+version).substring(0,32);
            out.add(new DiscoveredInterface(iid,normalized,binding,version,str(it.get("tenant")),streaming,push));
        }
        return List.copyOf(out);
    }

    private void bind(String tenant,String actor){jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_tenant_id', ?, true)",String.class,tenant);jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_actor_id', ?, true)",String.class,actor);}
    private String write(Object v){try{return json.writeValueAsString(v);}catch(Exception e){throw new IllegalArgumentException("JSON encode failed",e);}}
    private static String sha(String v){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private static String str(Object v){return v==null?null:String.valueOf(v);}
    private record DiscoveredInterface(String interfaceId,String url,String binding,String version,String interfaceTenant,boolean streaming,boolean push){}
}
