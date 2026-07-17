# OpenDispatch Observability Deployment

`otel-collector-config.yml` is the local/CI debug Collector used by the existing smoke test.

Production uses the hardened two-layer topology:

- `otel-collector-gateway.yml`: mTLS and bearer-authenticated OTLP ingress, first-pass redaction, durable queues, and trace-ID-aware load balancing.
- `otel-collector-sampler.yml`: stateful tail sampling, second-pass redaction, durable backend queues, and authenticated mTLS export.
- `haproxy/otel-lb.cfg`: highly available TCP ingress across the two gateway replicas.
- `prometheus/alerts/opendispatch-observability-slo.rules.yml`: Collector and telemetry delivery alert rules.
- `grafana/dashboards/opendispatch-observability-overview.json`: baseline operational dashboard.

Generate deployment-only PKI and tokens outside the repository:

```bash
./scripts/observability/generate-otel-pki.sh /secure/opendispatch/otel
```

Validate the Collector configuration with the pinned Collector image:

```bash
make validate-p5a-production-otel-runtime
```

Deploy by combining the application release Compose file and the production observability overlay. Do not commit the generated secret directory.
