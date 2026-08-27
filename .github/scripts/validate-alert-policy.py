#!/usr/bin/env python3
"""Validate the production alert catalog without third-party dependencies."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
POLICY = ROOT / "ops" / "observability" / "alerts.production.json"
IDENTIFIER = re.compile(r"^[a-z][a-z0-9_.-]{2,127}$")
SEVERITIES = {"SEV-0", "SEV-1", "SEV-2", "SEV-3"}
OPERATORS = {"gt", "gte", "lt", "lte", "eq"}


def require(condition: bool, message: str, failures: list[str]) -> None:
    if not condition:
        failures.append(message)


def main() -> int:
    failures: list[str] = []
    try:
        document = json.loads(POLICY.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        print(f"Unable to read alert policy: {error}", file=sys.stderr)
        return 1

    require(document.get("schemaVersion") == 1, "schemaVersion must equal 1", failures)
    require(document.get("environment") == "production", "environment must be production", failures)
    defaults = document.get("defaults", {})
    require(isinstance(defaults.get("noDataBehavior"), str), "defaults.noDataBehavior is required", failures)
    require(defaults.get("noDataBehavior") in {"alert", "warn"}, "no-data cannot silently pass", failures)

    rules = document.get("rules")
    require(isinstance(rules, list) and bool(rules), "at least one alert rule is required", failures)
    if not isinstance(rules, list):
        rules = []

    seen: set[str] = set()
    for index, rule in enumerate(rules):
        prefix = f"rules[{index}]"
        if not isinstance(rule, dict):
            failures.append(f"{prefix} must be an object")
            continue
        rule_id = rule.get("id")
        require(isinstance(rule_id, str) and bool(IDENTIFIER.fullmatch(rule_id)), f"{prefix}.id is invalid", failures)
        if isinstance(rule_id, str):
            require(rule_id not in seen, f"duplicate rule id: {rule_id}", failures)
            seen.add(rule_id)
        require(rule.get("severity") in SEVERITIES, f"{prefix}.severity is invalid", failures)
        require(isinstance(rule.get("owner"), str) and bool(rule["owner"].strip()), f"{prefix}.owner is required", failures)
        require(isinstance(rule.get("signal"), str) and bool(IDENTIFIER.fullmatch(rule["signal"])), f"{prefix}.signal is invalid", failures)
        require(rule.get("operator") in OPERATORS, f"{prefix}.operator is invalid", failures)
        threshold = rule.get("threshold")
        require(isinstance(threshold, (int, float)) and not isinstance(threshold, bool), f"{prefix}.threshold must be numeric", failures)
        require(isinstance(rule.get("windowMinutes"), int) and rule["windowMinutes"] > 0, f"{prefix}.windowMinutes must be positive", failures)
        require(isinstance(rule.get("minimumSamples"), int) and rule["minimumSamples"] >= 0, f"{prefix}.minimumSamples is invalid", failures)
        require(isinstance(rule.get("forMinutes"), int) and rule["forMinutes"] >= 0, f"{prefix}.forMinutes is invalid", failures)
        require(isinstance(rule.get("cooldownMinutes"), int) and rule["cooldownMinutes"] > 0, f"{prefix}.cooldownMinutes is invalid", failures)
        runbook = rule.get("runbook")
        require(isinstance(runbook, str) and runbook.startswith("docs/") and (ROOT / runbook).is_file(), f"{prefix}.runbook must reference an existing docs file", failures)
        routes = rule.get("routes")
        require(isinstance(routes, list) and bool(routes), f"{prefix}.routes must not be empty", failures)
        if isinstance(routes, list):
            for route in routes:
                require(isinstance(route, str) and bool(IDENTIFIER.fullmatch(route)), f"{prefix}.routes contains an invalid logical route", failures)
                if isinstance(route, str):
                    require("http" not in route and "@" not in route, f"{prefix}.routes must not contain an endpoint", failures)
        dimensions = rule.get("dimensions", [])
        require(isinstance(dimensions, list) and len(dimensions) <= 6, f"{prefix}.dimensions exceeds cardinality policy", failures)
        if isinstance(dimensions, list):
            forbidden = {"user_id", "telegram_id", "purchase_token", "ip", "hostname", "config"}
            require(not forbidden.intersection(dimensions), f"{prefix}.dimensions contains sensitive/high-cardinality data", failures)

    if failures:
        print("Production alert policy validation failed:", file=sys.stderr)
        print("\n".join(f"- {failure}" for failure in failures), file=sys.stderr)
        return 1

    print(f"Validated {len(rules)} production alert rules with fail-closed no-data policy.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
