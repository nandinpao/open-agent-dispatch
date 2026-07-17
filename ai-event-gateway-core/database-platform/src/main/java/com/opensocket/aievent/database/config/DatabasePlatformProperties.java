package com.opensocket.aievent.database.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aeg.database-platform")
public class DatabasePlatformProperties {
    private boolean enabled = true;
    private boolean healthEnabled = true;
    private boolean validateOnStartup;
    private boolean requireDataSource = true;
    private boolean requireSqlSessionFactory = true;
    private boolean requireFlyway = true;
    private Duration validationTimeout = Duration.ofSeconds(2);

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isHealthEnabled() { return healthEnabled; }
    public void setHealthEnabled(boolean healthEnabled) { this.healthEnabled = healthEnabled; }
    public boolean isValidateOnStartup() { return validateOnStartup; }
    public void setValidateOnStartup(boolean validateOnStartup) { this.validateOnStartup = validateOnStartup; }
    public boolean isRequireDataSource() { return requireDataSource; }
    public void setRequireDataSource(boolean requireDataSource) { this.requireDataSource = requireDataSource; }
    public boolean isRequireSqlSessionFactory() { return requireSqlSessionFactory; }
    public void setRequireSqlSessionFactory(boolean requireSqlSessionFactory) { this.requireSqlSessionFactory = requireSqlSessionFactory; }
    public boolean isRequireFlyway() { return requireFlyway; }
    public void setRequireFlyway(boolean requireFlyway) { this.requireFlyway = requireFlyway; }
    public Duration getValidationTimeout() { return validationTimeout; }
    public void setValidationTimeout(Duration validationTimeout) { this.validationTimeout = validationTimeout == null ? Duration.ofSeconds(2) : validationTimeout; }
}
