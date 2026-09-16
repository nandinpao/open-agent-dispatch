package com.opensocket.aievent.core.resourceaccess.core;

import com.opensocket.aievent.core.resourceaccess.contract.*;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

/**
 * Node-local versioned cache with same-key single-flight, bounded concurrent loaders and explicit prewarm.
 * Epoch-bearing keys guarantee that stale allows are never reused.
 */
public final class InMemoryResourceAuthorizationCache implements ResourceAuthorizationCachePort {
    private record Entry(AuthorizationDecision decision, Instant expiresAt) {}

    private final Map<ResourceAuthorizationCacheKey,Entry> entries = new ConcurrentHashMap<>();
    private final Map<ResourceAuthorizationCacheKey,CompletableFuture<AuthorizationDecision>> inFlight = new ConcurrentHashMap<>();
    private final int maximumEntries;
    private final Semaphore loadBulkhead;
    private final Duration followerWaitTimeout;
    private final LongAdder hits = new LongAdder();
    private final LongAdder misses = new LongAdder();
    private final LongAdder coordinatedLoads = new LongAdder();
    private final LongAdder followerWaits = new LongAdder();
    private final LongAdder rejectedLoads = new LongAdder();
    private final LongAdder prewarmedEntries = new LongAdder();
    private final LongAdder invalidations = new LongAdder();

    public InMemoryResourceAuthorizationCache() {
        this(10_000, 128, Duration.ofSeconds(2));
    }

    public InMemoryResourceAuthorizationCache(int maximumEntries) {
        this(maximumEntries, 128, Duration.ofSeconds(2));
    }

    public InMemoryResourceAuthorizationCache(
            int maximumEntries, int maximumConcurrentLoads, Duration followerWaitTimeout) {
        if (maximumEntries < 100) throw new IllegalArgumentException("maximumEntries must be at least 100");
        if (maximumConcurrentLoads < 1) throw new IllegalArgumentException("maximumConcurrentLoads must be positive");
        if (followerWaitTimeout == null || followerWaitTimeout.isNegative() || followerWaitTimeout.isZero()) {
            throw new IllegalArgumentException("followerWaitTimeout must be positive");
        }
        this.maximumEntries = maximumEntries;
        this.loadBulkhead = new Semaphore(maximumConcurrentLoads, true);
        this.followerWaitTimeout = followerWaitTimeout;
    }

    @Override
    public Optional<AuthorizationDecision> get(ResourceAuthorizationCacheKey key, Instant now) {
        Entry entry = entries.get(key);
        if (entry == null) {
            misses.increment();
            return Optional.empty();
        }
        if (!entry.expiresAt().isAfter(now)
                || !entry.decision().securityEpoch().equals(key.securityEpoch())
                || !entry.decision().policyVersion().equals(key.policyVersion())) {
            entries.remove(key, entry);
            misses.increment();
            return Optional.empty();
        }
        hits.increment();
        return Optional.of(entry.decision());
    }

    @Override
    public void put(ResourceAuthorizationCacheKey key, AuthorizationDecision decision, Instant expiresAt) {
        if (decision.effect() == DecisionEffect.ALLOW
                && (!decision.securityEpoch().equals(key.securityEpoch())
                    || !decision.policyVersion().equals(key.policyVersion()))) {
            throw new IllegalArgumentException("STALE_EPOCH_ALLOW_REJECTED");
        }
        if (!expiresAt.isAfter(decision.evaluatedAt())) {
            throw new IllegalArgumentException("cache expiry must be after decision evaluation");
        }
        evictIfNecessary();
        entries.put(key, new Entry(decision, expiresAt));
    }

    @Override
    public AuthorizationDecision coordinate(
            ResourceAuthorizationCacheKey key, Supplier<AuthorizationDecision> loader) {
        CompletableFuture<AuthorizationDecision> leader = new CompletableFuture<>();
        CompletableFuture<AuthorizationDecision> existing = inFlight.putIfAbsent(key, leader);
        if (existing != null) {
            followerWaits.increment();
            try {
                return existing.get(followerWaitTimeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("RESOURCE_AUTHORIZATION_CACHE_WAIT_INTERRUPTED", exception);
            } catch (TimeoutException exception) {
                rejectedLoads.increment();
                throw new IllegalStateException("RESOURCE_AUTHORIZATION_CACHE_SINGLE_FLIGHT_TIMEOUT", exception);
            } catch (ExecutionException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof RuntimeException runtime) throw runtime;
                throw new IllegalStateException("RESOURCE_AUTHORIZATION_CACHE_LOAD_FAILED", cause);
            }
        }

        boolean acquired = false;
        try {
            acquired = loadBulkhead.tryAcquire(followerWaitTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!acquired) {
                rejectedLoads.increment();
                IllegalStateException failure = new IllegalStateException("RESOURCE_AUTHORIZATION_CACHE_BULKHEAD_REJECTED");
                leader.completeExceptionally(failure);
                throw failure;
            }
            coordinatedLoads.increment();
            AuthorizationDecision loaded = java.util.Objects.requireNonNull(loader.get(), "authorization cache loader returned null");
            leader.complete(loaded);
            return loaded;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            IllegalStateException failure = new IllegalStateException("RESOURCE_AUTHORIZATION_CACHE_LOAD_INTERRUPTED", exception);
            leader.completeExceptionally(failure);
            throw failure;
        } catch (RuntimeException exception) {
            leader.completeExceptionally(exception);
            throw exception;
        } catch (Error error) {
            leader.completeExceptionally(error);
            throw error;
        } finally {
            if (acquired) loadBulkhead.release();
            inFlight.remove(key, leader);
        }
    }

    @Override
    public int prewarm(Collection<ResourceAuthorizationCacheEntry> seeds) {
        if (seeds == null || seeds.isEmpty()) return 0;
        int loaded = 0;
        for (ResourceAuthorizationCacheEntry seed : seeds) {
            if (seed != null) {
                put(seed.key(), seed.decision(), seed.expiresAt());
                loaded++;
                prewarmedEntries.increment();
            }
        }
        return loaded;
    }

    @Override
    public void invalidateTenant(String tenantId) {
        long before = entries.size();
        entries.keySet().removeIf(key -> key.tenantId().equals(tenantId));
        invalidations.add(Math.max(0, before - entries.size()));
    }

    @Override
    public void invalidatePrincipal(String tenantId,String principalId) {
        long before = entries.size();
        entries.keySet().removeIf(key -> key.tenantId().equals(tenantId) && key.principalId().equals(principalId));
        invalidations.add(Math.max(0, before - entries.size()));
    }

    @Override
    public void invalidateResource(ResourceRef ref) {
        long before = entries.size();
        entries.keySet().removeIf(key -> key.tenantId().equals(ref.tenantId())
                && key.resourceType() == ref.resourceType() && key.resourceId().equals(ref.resourceId()));
        invalidations.add(Math.max(0, before - entries.size()));
    }

    @Override
    public ResourceAuthorizationCacheMetrics metrics() {
        return new ResourceAuthorizationCacheMetrics(
                hits.sum(), misses.sum(), coordinatedLoads.sum(), followerWaits.sum(), rejectedLoads.sum(),
                prewarmedEntries.sum(), invalidations.sum(), entries.size(), inFlight.size());
    }

    public int size() { return entries.size(); }

    private void evictIfNecessary() {
        if (entries.size() < maximumEntries) return;
        Instant now = Instant.now();
        entries.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        if (entries.size() < maximumEntries) return;
        Iterator<ResourceAuthorizationCacheKey> iterator = entries.keySet().iterator();
        if (iterator.hasNext()) entries.remove(iterator.next());
    }
}
