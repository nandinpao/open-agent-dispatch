#!/usr/bin/env python3
from __future__ import annotations
import json
import re
import sys
from pathlib import Path

try:
    import yaml  # type: ignore
except ImportError:  # pragma: no cover - exercised in minimal release environments
    yaml = None


class MinimalYamlError(ValueError):
    """Raised when the dependency-free YAML subset parser cannot read a file."""


def _strip_comment(line: str) -> str:
    in_single = False
    in_double = False
    escaped = False
    for index, char in enumerate(line):
        if escaped:
            escaped = False
            continue
        if char == "\\" and in_double:
            escaped = True
            continue
        if char == "'" and not in_double:
            in_single = not in_single
            continue
        if char == '"' and not in_single:
            in_double = not in_double
            continue
        if char == "#" and not in_single and not in_double and (index == 0 or line[index - 1].isspace()):
            return line[:index].rstrip()
    return line.rstrip()


def _yaml_lines(body: str) -> list[tuple[int, str]]:
    result: list[tuple[int, str]] = []
    for raw in body.splitlines():
        leading = raw[: len(raw) - len(raw.lstrip(" \t"))]
        if "\t" in leading:
            raise MinimalYamlError("tabs are not supported for indentation")
        stripped = _strip_comment(raw.rstrip("\r\n"))
        if not stripped.strip():
            continue
        indent = len(stripped) - len(stripped.lstrip(" "))
        result.append((indent, stripped.strip()))
    return result


def _split_inline_list(value: str) -> list[str]:
    items: list[str] = []
    buffer: list[str] = []
    quote: str | None = None
    escaped = False
    bracket_depth = 0
    for char in value:
        if escaped:
            buffer.append(char)
            escaped = False
            continue
        if char == "\\" and quote == '"':
            buffer.append(char)
            escaped = True
            continue
        if quote:
            buffer.append(char)
            if char == quote:
                quote = None
            continue
        if char in {"'", '"'}:
            quote = char
            buffer.append(char)
            continue
        if char == "[":
            bracket_depth += 1
            buffer.append(char)
            continue
        if char == "]":
            bracket_depth -= 1
            buffer.append(char)
            continue
        if char == "," and bracket_depth == 0:
            item = "".join(buffer).strip()
            if item:
                items.append(item)
            buffer = []
            continue
        buffer.append(char)
    item = "".join(buffer).strip()
    if item:
        items.append(item)
    return items


def _parse_scalar(value: str):
    value = value.strip()
    if value == "":
        return ""
    lowered = value.lower()
    if lowered == "true":
        return True
    if lowered == "false":
        return False
    if lowered in {"null", "~"}:
        return None
    if value.startswith("[") and value.endswith("]"):
        inner = value[1:-1].strip()
        if not inner:
            return []
        return [_parse_scalar(item) for item in _split_inline_list(inner)]
    if (value.startswith("'") and value.endswith("'")) or (value.startswith('"') and value.endswith('"')):
        return value[1:-1]
    if re.fullmatch(r"-?\d+", value):
        try:
            return int(value)
        except ValueError:
            return value
    return value


def _key_value(content: str) -> tuple[str, str] | None:
    # Treat only `key: value` or `key:` as a mapping. Values such as
    # `otel-sampler-a:4317` remain scalar list items.
    match = re.match(r"^([^:\[\]{}]+):(?:\s+(.*)|\s*)$", content)
    if not match:
        return None
    return match.group(1).strip(), (match.group(2) or "").strip()


def _parse_yaml_block(lines: list[tuple[int, str]], index: int, indent: int):
    if index >= len(lines) or lines[index][0] < indent:
        return {}, index
    if lines[index][0] == indent and lines[index][1].startswith("- "):
        values = []
        while index < len(lines) and lines[index][0] == indent and lines[index][1].startswith("- "):
            content = lines[index][1][2:].strip()
            index += 1
            key_value = _key_value(content)
            if content == "":
                value, index = _parse_yaml_block(lines, index, indent + 2)
                values.append(value)
            elif key_value:
                key, raw_value = key_value
                item = {}
                if raw_value == "":
                    value, index = _parse_yaml_block(lines, index, indent + 2)
                else:
                    value = _parse_scalar(raw_value)
                item[key] = value
                if index < len(lines) and lines[index][0] > indent:
                    nested, index = _parse_yaml_block(lines, index, indent + 2)
                    if isinstance(nested, dict):
                        item.update(nested)
                    else:
                        item["items"] = nested
                values.append(item)
            else:
                values.append(_parse_scalar(content))
                if index < len(lines) and lines[index][0] > indent:
                    _, index = _parse_yaml_block(lines, index, indent + 2)
        return values, index

    mapping = {}
    while index < len(lines) and lines[index][0] == indent and not lines[index][1].startswith("- "):
        key_value = _key_value(lines[index][1])
        if not key_value:
            raise MinimalYamlError(f"invalid mapping line: {lines[index][1]}")
        key, raw_value = key_value
        index += 1
        if raw_value == "":
            if index < len(lines) and lines[index][0] > indent:
                value, index = _parse_yaml_block(lines, index, lines[index][0])
            else:
                value = {}
        else:
            value = _parse_scalar(raw_value)
        if key in mapping:
            raise MinimalYamlError(f"duplicate key: {key}")
        mapping[key] = value
    return mapping, index


def _minimal_yaml_safe_load(body: str):
    lines = _yaml_lines(body)
    if not lines:
        return None
    value, index = _parse_yaml_block(lines, 0, lines[0][0])
    if index != len(lines):
        raise MinimalYamlError(f"could not parse line {index + 1}: {lines[index][1]}")
    return value

ROOT = Path(__file__).resolve().parents[2]

def fail(msg: str) -> None:
    print(f"[FAIL] {msg}", file=sys.stderr)
    raise SystemExit(1)

def require(path: str) -> Path:
    p = ROOT / path
    if not p.is_file():
        fail(f"missing required file: {path}")
    return p

def load_yaml(path: str):
    p = require(path)
    try:
        body = p.read_text(encoding="utf-8")
        if yaml is not None:
            return yaml.safe_load(body)
        return _minimal_yaml_safe_load(body)
    except Exception as exc:
        fail(f"invalid YAML {path}: {exc}")

def text(path: str) -> str:
    return require(path).read_text()

def nested(mapping, *keys):
    current = mapping
    for key in keys:
        if not isinstance(current, dict) or key not in current:
            fail(f"missing YAML path: {'/'.join(keys)}")
        current = current[key]
    return current

GATEWAY_PATH = "deploy/observability/otel-collector-gateway.yml"
SAMPLER_PATH = "deploy/observability/otel-collector-sampler.yml"
OVERLAY_PATH = "deploy/docker-compose.observability.release.yml"
ENV_PATH = "deploy/env/.env.observability.release.example"

gateway = load_yaml(GATEWAY_PATH)
sampler = load_yaml(SAMPLER_PATH)
overlay = load_yaml(OVERLAY_PATH)

# Secure receiver: mTLS + bearer token on both HTTP and gRPC.
for protocol in ("grpc", "http"):
    cfg = nested(gateway, "receivers", "otlp/secure", "protocols", protocol)
    tls = cfg.get("tls", {})
    for field in ("cert_file", "key_file", "client_ca_file"):
        if not tls.get(field):
            fail(f"gateway {protocol} receiver must configure TLS {field}")
    if str(tls.get("min_version")) not in {"1.2", "1.3"}:
        fail(f"gateway {protocol} receiver must require TLS 1.2+")
    if nested(cfg, "auth", "authenticator") != "bearertokenauth/ingest":
        fail(f"gateway {protocol} receiver must require bearer authentication")

# Internal sampler hop is independently authenticated with mTLS.
internal = nested(sampler, "receivers", "otlp/internal", "protocols", "grpc")
for field in ("cert_file", "key_file", "client_ca_file"):
    if not internal.get("tls", {}).get(field):
        fail(f"sampler receiver must configure TLS {field}")
if nested(internal, "auth", "authenticator") != "bearertokenauth/internal":
    fail("sampler receiver must require the internal bearer authenticator")

# Persistent storage and durable queues on every external boundary.
for config_name, config, storage_name in (
    ("gateway", gateway, "file_storage/gateway"),
    ("sampler", sampler, "file_storage/sampler"),
):
    storage = nested(config, "extensions", storage_name)
    if storage.get("fsync") is not True or storage.get("create_directory") is not True:
        fail(f"{config_name} file storage must create directories and fsync writes")
    if int(storage.get("max_size", 0)) <= 0:
        fail(f"{config_name} file storage must have a finite positive max_size")
    compaction = storage.get("compaction", {})
    if compaction.get("on_rebound") is not True or not compaction.get("check_interval"):
        fail(f"{config_name} rebound compaction must define a positive check_interval")

for name, exporter in nested(gateway, "exporters").items():
    queue = exporter.get("sending_queue", {})
    retry = exporter.get("retry_on_failure", {})
    if queue.get("storage") != "file_storage/gateway":
        fail(f"gateway exporter {name} must use persistent storage")
    if retry.get("enabled") is not True or str(retry.get("max_elapsed_time")) != "0s":
        fail(f"gateway exporter {name} must retry indefinitely with backoff")

for name, exporter in nested(sampler, "exporters").items():
    queue = exporter.get("sending_queue", {})
    retry = exporter.get("retry_on_failure", {})
    if queue.get("storage") != "file_storage/sampler":
        fail(f"sampler exporter {name} must use persistent storage")
    if retry.get("enabled") is not True or str(retry.get("max_elapsed_time")) != "0s":
        fail(f"sampler exporter {name} must retry indefinitely with backoff")
    tls = exporter.get("tls", {})
    if not all(tls.get(k) for k in ("ca_file", "cert_file", "key_file")):
        fail(f"sampler exporter {name} must use backend mTLS")
    if not exporter.get("auth", {}).get("authenticator"):
        fail(f"sampler exporter {name} must use backend authentication")

# Trace-ID affinity is required before stateful tail sampling.
trace_lb = nested(gateway, "exporters", "load_balancing/traces")
if trace_lb.get("routing_key") != "traceID":
    fail("trace load balancer must route by traceID")
hosts = nested(trace_lb, "resolver", "static", "hostnames")
if sorted(hosts) != ["otel-sampler-a:4317", "otel-sampler-b:4317"]:
    fail("trace load balancer must use both sampler replicas")
policies = nested(sampler, "processors", "tail_sampling", "policies")
policy_types = {p.get("type") for p in policies}
for required_type in {"status_code", "latency", "string_attribute", "probabilistic"}:
    if required_type not in policy_types:
        fail(f"tail sampling missing policy type: {required_type}")

# Sensitive data controls must be defense in depth at both layers.
for config_name, config in (("gateway", gateway), ("sampler", sampler)):
    redaction = nested(config, "processors", "redaction/defense_in_depth")
    patterns = "\n".join(redaction.get("blocked_key_patterns", []))
    for needle in ("authorization", "cookie", "password", "token", "api"):
        if needle not in patterns.lower():
            fail(f"{config_name} redaction must block {needle}-like attributes")
    if redaction.get("summary") != "silent":
        fail(f"{config_name} redaction summary must not leak attribute names")
    if not nested(redaction, "url_sanitizer", "enabled"):
        fail(f"{config_name} URL sanitizer must be enabled")

services = nested(overlay, "services")
for service in ("otel-lb", "otel-gateway-a", "otel-gateway-b", "otel-sampler-a", "otel-sampler-b"):
    if service not in services:
        fail(f"production observability overlay missing service {service}")
for service in ("otel-gateway-a", "otel-gateway-b", "otel-sampler-a", "otel-sampler-b"):
    volumes = services[service].get("volumes", [])
    storage_mounts = [v for v in volumes if "/var/lib/otelcol" in v]
    if len(storage_mounts) != 1:
        fail(f"{service} must have one dedicated persistent storage mount")
if services["otel-gateway-a"]["volumes"] == services["otel-gateway-b"]["volumes"]:
    fail("gateway replicas must not share the same persistent queue volume")
if services["otel-sampler-a"]["volumes"] == services["otel-sampler-b"]["volumes"]:
    fail("sampler replicas must not share the same persistent queue volume")

for service, cert_prefix in (("core", "core"), ("netty", "netty"), ("adapter-worker", "adapter-worker")):
    env = services[service].get("environment", {})
    for key in ("OTEL_EXPORTER_OTLP_TRACES_ENDPOINT", "OTEL_EXPORTER_OTLP_METRICS_ENDPOINT"):
        if not str(env.get(key, "")).startswith("https://otel-lb:4318/"):
            fail(f"{service} {key} must use HTTPS through the HA endpoint")
    if "Bearer ${OTEL_INGEST_TOKEN" not in str(env.get("OTEL_EXPORTER_OTLP_AUTHORIZATION", "")):
        fail(f"{service} must send the Collector bearer token")
    if env.get("MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_SSL_BUNDLE") != "otel-client":
        fail(f"{service} trace exporter must use the SSL bundle")
    if env.get("MANAGEMENT_OTLP_METRICS_EXPORT_SSL_BUNDLE") != "otel-client":
        fail(f"{service} metric exporter must use the SSL bundle")
    cert = str(env.get("SPRING_SSL_BUNDLE_PEM_OTEL_CLIENT_KEYSTORE_CERTIFICATE", ""))
    if f"/{cert_prefix}-client.crt" not in cert:
        fail(f"{service} must use a distinct client certificate")

# Application property contract for trace and metric auth headers.
apps = [
    "ai-event-gateway-core/control-plane-app/src/main/resources/application.yml",
    "ai-event-gateway-core/adapter-worker-app/src/main/resources/application.yml",
    "ai-event-gateway-netty/gateway-app/src/main/resources/application.yml",
]
for app in apps:
    body = text(app)
    if body.count("Authorization: ${OTEL_EXPORTER_OTLP_AUTHORIZATION:}") != 2:
        fail(f"{app} must configure both trace and metric Authorization headers")

# Operational artifacts and secret hygiene.
json.loads(text("deploy/observability/grafana/dashboards/opendispatch-observability-overview.json"))
alerts = load_yaml("deploy/observability/prometheus/alerts/opendispatch-observability-slo.rules.yml")
if not alerts.get("groups"):
    fail("observability alert rules must define at least one group")
for required in (
    "scripts/observability/generate-otel-pki.sh",
    "scripts/observability/validate-production-otel.sh",
    "docs/architecture/P5-A_PRODUCTION_OTEL_HARDENING.md",
    ENV_PATH,
):
    require(required)
secret_dir = ROOT / "deploy/observability/secrets"
for p in secret_dir.iterdir():
    if p.name != "README.md":
        fail(f"secret material must not be committed: {p.relative_to(ROOT)}")
env_body = text(ENV_PATH)
for key in ("OTEL_SECRET_DIR", "OTEL_INGEST_TOKEN", "OTEL_BACKEND_TRACES_ENDPOINT", "OTEL_BACKEND_METRICS_ENDPOINT", "OTEL_BACKEND_LOGS_ENDPOINT"):
    if f"{key}=REPLACE_WITH_" not in env_body:
        fail(f"production observability env must fail closed for {key}")


# Release package must include the production observability deployment assets.
build_release = text("scripts/release/build-release-package.sh")
verify_package = text("scripts/release/verify-release-package.sh")
for needle in (
    "deploy/docker-compose.observability.release.yml",
    "deploy/env/.env.observability.release.example",
    "deploy/observability",
    "generate-otel-pki.sh",
    "validate-production-otel.sh",
    "runtime/adapter-worker/ai-event-gateway-adapter-worker.jar",
):
    if needle not in build_release:
        fail(f"release builder does not package {needle}")
    if needle not in verify_package:
        fail(f"release verifier does not require {needle}")
for service_name, service in services.items():
    if not service_name.startswith("otel-"):
        continue
    image = str(service.get("image", ""))
    if image.endswith(":latest") or image == "":
        fail(f"production observability service {service_name} must use a pinned non-latest image")

print("[PASS] P5-A production OpenTelemetry hardening policy")
print("       mTLS + bearer auth, two-layer HA, persistent queues, tail sampling, redaction, SLO artifacts")
