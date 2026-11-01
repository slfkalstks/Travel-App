#!/usr/bin/env python3
"""Validate document examples, rejection cases, and optional read-only live metadata."""
import argparse
from datetime import datetime
import json
from pathlib import Path
from urllib.request import Request, urlopen

from jsonschema import Draft202012Validator, FormatChecker
from referencing import Registry, Resource

ROOT = Path(__file__).resolve().parents[1]


def format_checker():
    checker = FormatChecker()

    # jsonschema's optional RFC3339 dependency is not required by this repo.
    # The schema enforces UTC syntax/precision; stdlib checks the calendar.
    @checker.checks("date-time", raises=(ValueError, TypeError))
    def valid_instant(value):
        if not isinstance(value, str):
            return True  # The independent type constraint rejects non-strings.
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
        return parsed.tzinfo is not None

    return checker


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", help="e.g. http://127.0.0.1:8088/api/demo; GET only")
    args = parser.parse_args()
    schemas = {kind: json.loads((ROOT / path).read_text(encoding="utf-8")) for kind, path in {
        "record": "contracts/document.schema.json", "update": "contracts/document-update.schema.json"}.items()}
    for schema in schemas.values():
        Draft202012Validator.check_schema(schema)
    # An explicit registry resolves only these local schemas; no remote retrieval.
    registry = Registry().with_resources((schema["$id"], Resource.from_contents(schema)) for schema in schemas.values())
    validators = {kind: Draft202012Validator(schema, registry=registry, format_checker=format_checker())
                  for kind, schema in schemas.items()}
    fixture_count = 0
    for path in sorted((ROOT / "contracts/examples").glob("document-*.json")):
        kind = "update" if path.stem.endswith("-update") else "record"
        validators[kind].validate(json.loads(path.read_text(encoding="utf-8")))
        fixture_count += 1
    baseline = {"name": "예약 확인서", "reservationId": ""}
    invalid_updates = []
    for field in baseline:
        for value in [None, 7, 1.25, True, [], {}]:
            invalid_updates.append(dict(baseline, **{field: value}))
        invalid_updates.append({key: value for key, value in baseline.items() if key != field})
    for field in ["id", "mediaType", "sizeBytes", "sha256", "createdAt", "unexpected"]:
        invalid_updates.append(dict(baseline, **{field: "forbidden"}))
    for name in ["", " ", "\t", ".", "..", "a/b", "a\\b", "a\n", "\u0000a", "a\u0085b", "a\u2028b", "x" * 201]:
        invalid_updates.append(dict(baseline, name=name))
    for payload in invalid_updates:
        if validators["update"].is_valid(payload):
            raise AssertionError(f"Accepted invalid update: {payload!r}")
    for name in ["예약 확인서", "예약 확인서.pdf", "숙소 예약: 확인", "x" * 200]:
        validators["update"].validate(dict(baseline, name=name))
    record = json.loads((ROOT / "contracts/examples/document-unlinked-response.json").read_text(encoding="utf-8"))
    invalid_record_count = 0
    for field, values in {
        "id": ["../file", "ABC", record["id"] + "\n"],
        "mediaType": ["text/html", "image/svg+xml"],
        "sizeBytes": [0, 20971521, 1.5, "1", None],
        "sha256": ["A" * 64, "f" * 63, "f" * 65],
        "createdAt": ["2026-11-01T01:00:00+00:00", "2026-13-01T01:00:00Z", "2026-11-01T01:00:00.1234567890Z"],
    }.items():
        for value in values:
            if validators["record"].is_valid(dict(record, **{field: value})):
                raise AssertionError(f"Accepted invalid record {field}: {value!r}")
            invalid_record_count += 1
    for size in [1, 20971520]:
        validators["record"].validate(dict(record, sizeBytes=size))
    print(f"Canonical document fixtures: {fixture_count} valid")
    print(f"Invalid document updates: {len(invalid_updates)} rejected; invalid response fields: {invalid_record_count} rejected")
    if args.base_url:
        base = args.base_url.rstrip("/")
        with urlopen(base + "/trips", timeout=10) as stream:
            trips = json.load(stream)
        count = 0
        for trip in trips:
            with urlopen(Request(base + "/workspace", headers={"X-Trip-Id": trip["id"]}), timeout=10) as stream:
                workspace = json.load(stream)
            for document in workspace.get("documents", []):
                validators["record"].validate(document)
                count += 1
        print(f"Live documents across {len(trips)} trips: {count} valid")


if __name__ == "__main__":
    main()
