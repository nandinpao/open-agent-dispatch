package com.opensocket.aievent.core.capability;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PC-S4 pre-tenant PUSH callback authentication boundary.
 *
 * <p>This service is deliberately the only runtime reader of the non-RLS callback routing table.
 * It authenticates the opaque route and Bearer token before returning tenant/tracking identity.
 * Raw callback credentials are never persisted.</p>
 */
@Service
public class A2APushCallbackRouteService {
    private static final int MAX_REQUESTS_PER_MINUTE = 120;
    private static final int MAX_AUTH_FAILURES = 10;
    private final NamedParameterJdbcTemplate jdbc;

    public A2APushCallbackRouteService(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public void register(String tenant, String trackingId, String callbackHandle, String tokenHash) {
        jdbc.update("update a2a_push_callback_routes set route_status='REVOKED',updated_at=now() where tenant_id=:tenant and tracking_id=:tracking and route_status='ACTIVE'",
                new MapSqlParameterSource("tenant",tenant).addValue("tracking",trackingId));
        jdbc.update("""
                insert into a2a_push_callback_routes(callback_handle,tenant_id,tracking_id,token_hash,route_status,created_at,updated_at)
                values(:handle,:tenant,:tracking,:hash,'ACTIVE',now(),now())
                on conflict(callback_handle) do update set tenant_id=excluded.tenant_id,tracking_id=excluded.tracking_id,
                    token_hash=excluded.token_hash,route_status='ACTIVE',expires_at=null,request_window_started_at=null,
                    request_count=0,auth_failure_count=0,locked_until=null,updated_at=now()
                """, new MapSqlParameterSource("handle", callbackHandle).addValue("tenant", tenant)
                .addValue("tracking", trackingId).addValue("hash", tokenHash));
    }

    @Transactional
    public void revoke(String callbackHandle) {
        jdbc.update("update a2a_push_callback_routes set route_status='REVOKED',updated_at=now() where callback_handle=:handle",
                new MapSqlParameterSource("handle",callbackHandle));
    }

    /** Authenticate before any tenant SET LOCAL is applied. */
    @Transactional
    public Route authenticate(String callbackHandle, String bearer) {
        if (callbackHandle == null || callbackHandle.isBlank() || bearer == null || bearer.isBlank()) throw authFailed();
        MapSqlParameterSource p = new MapSqlParameterSource("handle", callbackHandle.trim());
        RouteRow row;
        try {
            row = jdbc.queryForObject("""
                    select callback_handle,tenant_id,tracking_id,token_hash,route_status,expires_at,
                           request_window_started_at,request_count,auth_failure_count,locked_until
                      from a2a_push_callback_routes where callback_handle=:handle for update
                    """, p, (rs,n) -> new RouteRow(rs.getString("callback_handle"),rs.getString("tenant_id"),rs.getString("tracking_id"),
                    rs.getString("token_hash"),rs.getString("route_status"),rs.getObject("expires_at",OffsetDateTime.class),
                    rs.getObject("request_window_started_at",OffsetDateTime.class),rs.getInt("request_count"),rs.getInt("auth_failure_count"),
                    rs.getObject("locked_until",OffsetDateTime.class)));
        } catch (EmptyResultDataAccessException ex) { throw authFailed(); }
        OffsetDateTime now = OffsetDateTime.now();
        if (!"ACTIVE".equals(row.status()) || (row.expiresAt()!=null&&!row.expiresAt().isAfter(now))) throw authFailed();
        if (row.lockedUntil()!=null&&row.lockedUntil().isAfter(now)) throw new IllegalArgumentException("A2A_PUSH_RATE_LIMITED");
        boolean newWindow = row.windowStartedAt()==null || row.windowStartedAt().plusMinutes(1).isBefore(now);
        int requestCount = newWindow ? 1 : row.requestCount()+1;
        if (requestCount > MAX_REQUESTS_PER_MINUTE) {
            jdbc.update("update a2a_push_callback_routes set locked_until=:locked,updated_at=:now where callback_handle=:handle",
                    p.addValue("locked",now.plusMinutes(1)).addValue("now",now));
            throw new IllegalArgumentException("A2A_PUSH_RATE_LIMITED");
        }
        String actual = sha256(bearer);
        boolean tokenOk = MessageDigest.isEqual(row.tokenHash().getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
        if (!tokenOk) {
            int failures=row.authFailures()+1;
            OffsetDateTime locked=failures>=MAX_AUTH_FAILURES?now.plusMinutes(5):null;
            jdbc.update("""
                    update a2a_push_callback_routes
                       set request_window_started_at=:window,request_count=:requests,auth_failure_count=:failures,
                           locked_until=:locked,updated_at=:now where callback_handle=:handle
                    """, p.addValue("window",newWindow?now:row.windowStartedAt()).addValue("requests",requestCount)
                    .addValue("failures",failures).addValue("locked",locked).addValue("now",now));
            throw authFailed();
        }
        jdbc.update("""
                update a2a_push_callback_routes
                   set request_window_started_at=:window,request_count=:requests,auth_failure_count=0,locked_until=null,updated_at=:now
                 where callback_handle=:handle
                """, p.addValue("window",newWindow?now:row.windowStartedAt()).addValue("requests",requestCount).addValue("now",now));
        return new Route(row.tenantId(), row.trackingId(), row.callbackHandle(), sha256(row.callbackHandle()).substring(0,24));
    }

    private static IllegalArgumentException authFailed(){return new IllegalArgumentException("A2A_PUSH_AUTHENTICATION_FAILED");}
    private static String sha256(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception ex){throw new IllegalStateException(ex);}}
    public record Route(String tenantId,String trackingId,String callbackHandle,String authorizationFingerprint){}
    private record RouteRow(String callbackHandle,String tenantId,String trackingId,String tokenHash,String status,OffsetDateTime expiresAt,OffsetDateTime windowStartedAt,int requestCount,int authFailures,OffsetDateTime lockedUntil){}
}
