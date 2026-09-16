package com.opensocket.aievent.core.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Runtime artifact identity used by Phase 12 release certification and incident diagnostics.
 *
 * <p>The contributor intentionally exposes no secrets or credential material. Production
 * access to {@code /actuator/info} remains governed by the existing actuator security chain.
 */
@Component
public final class OpenDispatchReleaseInfoContributor implements InfoContributor {
    public static final String RELEASE_CONTRACT = "phase12-enterprise-release-v1";
    public static final String IAM_AUTHORITY_CONTRACT = "human-rbac-machine-iam-v1";

    private final ObjectProvider<BuildProperties> buildProperties;
    private final ObjectProvider<JdbcTemplate> jdbcTemplate;
    private final Environment environment;
    private final String artifactSha256;

    public OpenDispatchReleaseInfoContributor(
            ObjectProvider<BuildProperties> buildProperties,
            ObjectProvider<JdbcTemplate> jdbcTemplate,
            Environment environment) {
        this.buildProperties = buildProperties;
        this.jdbcTemplate = jdbcTemplate;
        this.environment = environment;
        this.artifactSha256 = computeArtifactSha256();
    }

    @Override
    public void contribute(Info.Builder builder) {
        BuildProperties build = buildProperties.getIfAvailable();
        Map<String, Object> release = new LinkedHashMap<>();
        release.put("contract", RELEASE_CONTRACT);
        release.put("iamAuthorityContract", IAM_AUTHORITY_CONTRACT);
        release.put("artifact", build == null ? "control-plane-app" : build.getArtifact());
        release.put("artifactSha256", artifactSha256);
        release.put("version", build == null ? "development" : build.getVersion());
        release.put("buildTime", build == null || build.getTime() == null ? "unknown" : build.getTime().toString());
        release.put("gitCommit", property("OPENDISPATCH_BUILD_GIT_COMMIT", buildValue(build, "gitCommit", "unknown")));
        release.put("buildId", property("OPENDISPATCH_BUILD_ID", buildValue(build, "buildId", "local")));
        release.put("schemaVersion", currentSchemaVersion());
        release.put("reportedAt", Instant.now().toString());
        builder.withDetail("opendispatch", release);
    }

    private String currentSchemaVersion() {
        JdbcTemplate jdbc = jdbcTemplate.getIfAvailable();
        if (jdbc == null) {
            return "unavailable";
        }
        try {
            String version = jdbc.query(
                    "select version from flyway_schema_history where success=true order by installed_rank desc limit 1",
                    rs -> rs.next() ? rs.getString(1) : null);
            return version == null || version.isBlank() ? "none" : "V" + version;
        } catch (DataAccessException ex) {
            return "unavailable";
        }
    }

    private static String computeArtifactSha256() {
        try {
            Path source = new ApplicationHome(OpenDispatchReleaseInfoContributor.class).getSource().toPath();
            if (!Files.isRegularFile(source)) {
                return "unavailable";
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(source); DigestInputStream hashing = new DigestInputStream(in, digest)) {
                hashing.transferTo(java.io.OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException | RuntimeException ex) {
            return "unavailable";
        }
    }

    private static String buildValue(BuildProperties build, String name, String fallback) {
        if (build == null) return fallback;
        String value = build.get(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String property(String name, String fallback) {
        String value = environment.getProperty(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
