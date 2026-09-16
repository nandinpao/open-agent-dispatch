package com.opensocket.aievent.core.enforcement.activation.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import com.opensocket.aievent.core.enforcement.activation.contract.AuthorityRouteDefinition;
import com.opensocket.aievent.core.enforcement.activation.contract.AuthorityRouteKey;

public final class AuthoritySnapshotFactory {
    public AuthoritySnapshot create(AuthoritySnapshotData data) {
        Map<AuthorityRouteKey, AuthorityRouteDefinition> exact = new LinkedHashMap<>();
        List<AuthorityRouteDefinition> wildcard = new ArrayList<>();
        for (AuthorityRouteDefinition route : data.routes()) {
            if (exact.containsKey(route.key()) || wildcard.stream().anyMatch(value -> value.key().equals(route.key()))) {
                throw new IllegalArgumentException("duplicate authority route: " + route.key().canonicalValue());
            }
            if (route.key().isExact()) exact.put(route.key(), route); else wildcard.add(route);
        }
        wildcard.sort(Comparator.comparingInt((AuthorityRouteDefinition value) -> value.key().specificity()).reversed()
                .thenComparing(value -> value.key().canonicalValue()));
        String calculated = checksum(data.revision(), data.routes());
        if (!data.checksum().isBlank() && !constantTimeEquals(data.checksum(), calculated)) {
            throw new IllegalArgumentException("authority snapshot checksum mismatch");
        }
        return new AuthoritySnapshot(data.revision(), data.publishedAt(), calculated, exact, wildcard);
    }

    public String checksum(long revision, List<AuthorityRouteDefinition> routes) {
        List<AuthorityRouteDefinition> sorted = routes.stream().sorted(Comparator.comparing(value -> value.key().canonicalValue())).toList();
        StringBuilder canonical = new StringBuilder().append(revision).append('\n');
        for (AuthorityRouteDefinition route : sorted) {
            canonical.append(route.key().canonicalValue()).append('|').append(route.mode()).append('|')
                    .append(route.targetBasisPoints()).append('|')
                    .append(String.join(",", new TreeSet<>(route.includeCohorts()))).append('|')
                    .append(String.join(",", new TreeSet<>(route.excludeCohorts()))).append('|')
                    .append(route.reasonCode()).append('\n');
        }
        try {
            return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }
}
