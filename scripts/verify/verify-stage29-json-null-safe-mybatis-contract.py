#!/usr/bin/env python3
from pathlib import Path
import html
import re
import sys

ROOT = Path(__file__).resolve().parents[2]
V1 = ROOT / "ai-event-gateway-core/database-platform/src/main/resources/db/migration/V1__clean_dispatch_flow_direct_baseline.sql"
MAPPER_ROOT = ROOT / "ai-event-gateway-core/database-platform/src/main/resources/mybatis/postgresql"


def split_top_level(text: str):
    parts = []
    current = []
    depth = 0
    single = False
    double = False
    i = 0
    while i < len(text):
        ch = text[i]
        if ch == "'" and not double:
            single = not single
        elif ch == '"' and not single:
            double = not double
        elif not single and not double:
            if ch == "(":
                depth += 1
            elif ch == ")" and depth > 0:
                depth -= 1
            elif ch == "," and depth == 0:
                value = "".join(current).strip()
                if value:
                    parts.append(value)
                current = []
                i += 1
                continue
        current.append(ch)
        i += 1
    value = "".join(current).strip()
    if value:
        parts.append(value)
    return parts


def json_not_null_defaults(sql: str):
    defaults = {}
    for match in re.finditer(r"create\s+table\s+if\s+not\s+exists\s+(\w+)\s*\((.*?)\);", sql, re.IGNORECASE | re.DOTALL):
        table = match.group(1)
        for segment in split_top_level(match.group(2)):
            column = re.match(r"\s*([A-Za-z_][A-Za-z0-9_]*)\s+(.*)", segment, re.DOTALL)
            if not column:
                continue
            name = column.group(1)
            rest = column.group(2)
            if name.lower() in {"primary", "unique", "foreign", "constraint", "check", "exclude"}:
                continue
            if "jsonb" in rest.lower() and "not null" in rest.lower():
                default = re.search(r"default\s+('(?:\[\]|\{\})'::jsonb)", rest, re.IGNORECASE)
                defaults[(table, name)] = default.group(1) if default else "'{}'::jsonb"
    for match in re.finditer(r"alter\s+table\s+(\w+)\s+add\s+column\s+if\s+not\s+exists\s+(\w+)\s+([^;]+);", sql, re.IGNORECASE):
        table = match.group(1)
        name = match.group(2)
        rest = match.group(3)
        if "jsonb" in rest.lower() and "not null" in rest.lower():
            default = re.search(r"default\s+('(?:\[\]|\{\})'::jsonb)", rest, re.IGNORECASE)
            defaults[(table, name)] = default.group(1) if default else "'{}'::jsonb"
    return defaults


def expand_includes(mapper_text: str):
    snippets = dict(re.findall(r"<sql\s+id=\"([^\"]+)\">(.*?)</sql>", mapper_text, re.DOTALL))
    return re.sub(r"<include\s+refid=\"([^\"]+)\"\s*/>", lambda m: snippets.get(m.group(1), ""), mapper_text)


def mapper_insert_columns_and_values(mapper_text: str):
    text = expand_includes(html.unescape(mapper_text))
    pattern = r"insert\s+into\s+(\w+)\s*\((.*?)\)\s*values\s*\((.*?)\)\s*(?:on\s+conflict|</insert>|;)"
    for match in re.finditer(pattern, text, re.IGNORECASE | re.DOTALL):
        table = match.group(1)
        raw_columns = re.sub(r"<[^>]+>", " ", match.group(2))
        columns = [part.strip() for part in split_top_level(raw_columns)]
        values = split_top_level(match.group(3))
        if len(columns) != len(values):
            # Dynamic foreach/batch statements are intentionally skipped here;
            # they are covered by integration tests and SQL-specific verifiers.
            continue
        yield table, columns, values


def main():
    sql = V1.read_text()
    defaults = json_not_null_defaults(sql)
    failures = []
    for mapper in sorted(MAPPER_ROOT.rglob("*.xml")):
        text = mapper.read_text()
        for table, columns, values in mapper_insert_columns_and_values(text):
            for column, value in zip(columns, values):
                key = (table, column)
                if key not in defaults:
                    continue
                lowered = value.lower()
                if "cast(" in lowered and "as jsonb" in lowered and "coalesce" not in lowered:
                    failures.append((mapper.relative_to(ROOT), table, column, defaults[key], value))
    if failures:
        print("MyBatis insert statements can explicitly send NULL into NOT NULL jsonb columns:")
        for mapper, table, column, default, value in failures:
            print(f" - {mapper}: {table}.{column} default={default} value={value}")
        sys.exit(1)

    routing = MAPPER_ROOT / "task/RoutingDecisionDao.xml"
    routing_text = routing.read_text()
    required_tokens = [
        "coalesce(cast(#{decision.userFacingErrorJson,jdbcType=VARCHAR} as jsonb), '{}'::jsonb)",
        "coalesce(cast(#{decision.candidatesJson} as jsonb), '[]'::jsonb)",
    ]
    for token in required_tokens:
        if token not in routing_text:
            print(f"RoutingDecisionDao.xml missing null-safe token: {token}")
            sys.exit(1)

    print("Stage 29 JSON null-safe MyBatis contract verified.")


if __name__ == "__main__":
    main()
