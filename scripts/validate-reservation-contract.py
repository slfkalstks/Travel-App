#!/usr/bin/env python3
"""Validate local examples and optional read-only live responses against canonical schemas."""
import argparse
import json
from pathlib import Path
from urllib.request import Request, urlopen
from jsonschema import Draft202012Validator
from referencing import Registry, Resource

ROOT = Path(__file__).resolve().parents[1]

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--base-url', help='e.g. http://127.0.0.1:8088/api/demo; GET only')
    args = parser.parse_args()
    request = json.loads((ROOT/'contracts/reservation-input.schema.json').read_text(encoding="utf-8"))
    response = json.loads((ROOT/'contracts/reservation.schema.json').read_text(encoding="utf-8"))
    Draft202012Validator.check_schema(request)
    Draft202012Validator.check_schema(response)
    # Explicit local registry: no remote schema retrieval or path expansion.
    registry = Registry().with_resource(request['$id'], Resource.from_contents(request))
    validators = {
        'input': Draft202012Validator(request, registry=registry),
        'response': Draft202012Validator(response, registry=registry),
    }
    count = 0
    for file in sorted((ROOT/'contracts/examples').glob('reservation-*.json')):
        kind = 'input' if file.stem.endswith('-input') else 'response'
        validators[kind].validate(json.loads(file.read_text(encoding="utf-8")))
        count += 1
    print(f'Canonical reservation fixtures: {count} valid')
    baseline = json.loads((ROOT/'contracts/examples/reservation-minimal-input.json').read_text(encoding="utf-8"))
    invalid_count = 0
    for field in request['properties']:
        for wrong in [None, 7, 1.25, True, [], {}]:
            invalid = dict(baseline, **{field: wrong})
            assert not validators['input'].is_valid(invalid), (field, wrong)
            invalid_count += 1
    for field in ['id', 'source', 'unexpected']:
        assert not validators['input'].is_valid(dict(baseline, **{field: 'not-writable'}))
        invalid_count += 1
    print(f'Wrong types and unknown request fields: {invalid_count} rejected')
    if args.base_url:
        base = args.base_url.rstrip('/')
        with urlopen(base+'/trips', timeout=10) as stream:
            trips = json.load(stream)
        live_count = 0
        for trip in trips:
            with urlopen(Request(base+'/workspace', headers={'X-Trip-Id': trip['id']}), timeout=10) as stream:
                workspace = json.load(stream)
            for reservation in workspace['reservations']:
                validators['response'].validate(reservation)
                live_count += 1
        print(f'Live reservations across {len(trips)} trips: {live_count} valid')

if __name__ == '__main__':
    main()
