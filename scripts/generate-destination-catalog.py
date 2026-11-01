#!/usr/bin/env python3
"""Build the offline destination index from attributed primary sources (Python 3.10+)."""
import argparse
import concurrent.futures
import datetime
import hashlib
import io
import json
from pathlib import Path
import subprocess
import tempfile
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
CLDR_JSON = "7d2b9cb11cec08f4d333ad73209deb3df0711c3b"
CLDR = "0c0d98c9d1fd2efaa2cb191a6bf2626d9d1e288b"
GN = "https://download.geonames.org/export/dump/"
SOURCES = {
    "countryInfo.txt": GN + "countryInfo.txt",
    "cities15000.zip": GN + "cities15000.zip",
    "admin1CodesASCII.txt": GN + "admin1CodesASCII.txt",
    "alternateNamesV2.zip": GN + "alternateNamesV2.zip",
    "geonames-readme.txt": GN + "readme.txt",
    "cldr-ko.json": f"https://raw.githubusercontent.com/unicode-org/cldr-json/{CLDR_JSON}/cldr-json/cldr-localenames-full/main/ko/territories.json",
    "cldr-en.json": f"https://raw.githubusercontent.com/unicode-org/cldr-json/{CLDR_JSON}/cldr-json/cldr-localenames-full/main/en/territories.json",
    "cldr-region.xml": f"https://raw.githubusercontent.com/unicode-org/cldr/{CLDR}/common/validity/region.xml",
    "UNICODE-3.0.txt": f"https://raw.githubusercontent.com/unicode-org/cldr-json/{CLDR_JSON}/LICENSE",
}


def digest(path):
    sha = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            sha.update(chunk)
    return {"bytes": path.stat().st_size, "sha256": sha.hexdigest()}


def normalize(value):
    return "".join(value.lower().split())


def unique(values):
    seen = set()
    result = []
    for value in values:
        value = " ".join(value.split()).replace("|", " ")
        key = normalize(value)
        if key and key not in seen:
            seen.add(key)
            result.append(value)
    return result


def write_tsv(path, rows):
    for row in rows:
        assert all("\t" not in str(v) and "\n" not in str(v) for v in row)
    path.write_text("".join("\t".join(map(str, row)) + "\n" for row in rows), encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cache", type=Path, default=Path(tempfile.gettempdir()) / "travelapp-geo-catalog")
    parser.add_argument("--refresh", action="store_true", help="Download current GeoNames snapshot again")
    args = parser.parse_args()
    args.cache.mkdir(parents=True, exist_ok=True)

    def fetch(item):
        name, url = item
        path = args.cache / name
        if args.refresh or not path.exists():
            temporary = path.with_suffix(path.suffix + ".part")
            subprocess.run(["curl", "--fail", "--location", "--silent", "--show-error", "--retry", "3", url, "--output", str(temporary)], check=True)
            temporary.replace(path)
        return name, {"url": url, **digest(path)}

    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        source_manifest = dict(pool.map(fetch, SOURCES.items()))

    def text(name):
        return (args.cache / name).read_text(encoding="utf-8")

    territories = {}
    for lang in ("ko", "en"):
        territories[lang] = json.loads(text(f"cldr-{lang}.json"))["main"][lang]["localeDisplayNames"]["territories"]

    regular = ET.fromstring(text("cldr-region.xml")).find(".//id[@type='region'][@idStatus='regular']")
    codes = set()
    for token in regular.text.split():
        if "~" in token:
            first, last = token.split("~")
            assert first[:-len(last)] or len(first) == len(last)
            last = first[:-len(last)] + last
            assert first[:-1] == last[:-1]
            codes.update(first[:-1] + chr(i) for i in range(ord(first[-1]), ord(last[-1]) + 1))
        else:
            codes.add(token)
    assert all(len(code) == 2 and code.isalpha() for code in codes)

    country_source = {}
    for line in text("countryInfo.txt").splitlines():
        if line and not line.startswith("#"):
            fields = line.split("\t")
            country_source[fields[0]] = fields
    country_rows = []
    for code in sorted(codes):
        assert code in territories["ko"] and code in territories["en"], code
        names = [code, territories["ko"][code], territories["en"][code]]
        if code in country_source:
            names.extend([country_source[code][1], country_source[code][4]])
        for lang in ("ko", "en"):
            names.extend(value for key, value in territories[lang].items() if key.startswith(code + "-alt-"))
        country_rows.append([code, territories["ko"][code], territories["en"][code], "|".join(unique(names))])

    admins = {}
    wanted = set()
    for line in text("admin1CodesASCII.txt").splitlines():
        code, name, ascii_name, geoname_id = line.split("\t")
        if code.split(".")[0] in codes:
            admins[code] = (name, ascii_name, geoname_id)
            wanted.add(geoname_id)
    cities = []
    with zipfile.ZipFile(args.cache / "cities15000.zip") as archive:
        for line in io.TextIOWrapper(archive.open("cities15000.txt"), encoding="utf-8"):
            fields = line.rstrip("\n").split("\t")
            if fields[8] in codes:
                cities.append(fields)
                wanted.add(fields[0])

    # Stream the large upstream table; only keep ko/en names of bundled places.
    aliases = {}
    with zipfile.ZipFile(args.cache / "alternateNamesV2.zip") as archive:
        for line in io.TextIOWrapper(archive.open("alternateNamesV2.txt"), encoding="utf-8"):
            fields = line.rstrip("\n").split("\t")
            if len(fields) < 8 or fields[1] not in wanted or fields[2] not in ("ko", "en"):
                continue
            # The source's historic and colloquial forms remain searchable, but
            # never become the displayed current name.
            aliases.setdefault(fields[1], {}).setdefault(fields[2], []).append(fields)

    def label(geoname_id, lang, fallback):
        names = [row for row in aliases.get(geoname_id, {}).get(lang, []) if row[6] != "1" and row[7] != "1" and (len(row) < 10 or not row[9])]
        names.sort(key=lambda row: (row[4] != "1", row[5] != "1", int(row[0])))
        return names[0][3] if names else fallback

    def search_aliases(geoname_id, extra):
        names = list(extra)
        for lang in ("ko", "en"):
            names.extend(row[3] for row in aliases.get(geoname_id, {}).get(lang, []))
        return "|".join(unique(names))

    admin_labels = {code: label(value[2], "ko", value[0]) for code, value in admins.items()}
    place_rows = []
    # Keep all input records. Equal names are valid distinct places; IDs prevent
    # accidental deduplication. Source population ranks cities within a country.
    for city in sorted(cities, key=lambda row: (row[8], -int(row[14] or "0"), int(row[0]))):
        geoname_id, original, ascii_name = city[:3]
        display = label(geoname_id, "ko", original)
        english = label(geoname_id, "en", ascii_name or original)
        region = admin_labels.get(city[8] + "." + city[10], "")
        place_rows.append(["city:" + geoname_id, city[8], display, english, region, "city", search_aliases(geoname_id, [display, english, original, ascii_name])])
    for code, (original, ascii_name, geoname_id) in sorted(admins.items()):
        display = admin_labels[code]
        english = label(geoname_id, "en", ascii_name or original)
        place_rows.append(["region:" + code, code.split(".")[0], display, english, "", "region", search_aliases(geoname_id, [display, english, original, ascii_name])])

    output = ROOT / "app/composeApp/src/commonMain/composeResources/files/destinations"
    output.mkdir(parents=True, exist_ok=True)
    write_tsv(output / "countries.tsv", country_rows)
    write_tsv(output / "places.tsv", place_rows)
    attribution = (
        "Destination data attribution\n\n"
        "City, first-level administrative region, population ordering and place alias data: GeoNames, https://www.geonames.org/\n"
        "Source: https://download.geonames.org/export/dump/\n"
        "Licensed under Creative Commons Attribution 4.0 International (CC BY 4.0): https://creativecommons.org/licenses/by/4.0/\n"
        "Changes: selected cities15000 and admin1 records; retained ko/en aliases; localized display names; filtered country codes using CLDR regular validity; transformed to compact TSV; sorted cities by source population. No endorsement implied.\n\n"
        "Country/territory labels and code validity: Unicode CLDR, https://cldr.unicode.org/\n"
        "Licensed under Unicode License V3 (Unicode-3.0). Full notice follows.\n\n"
        + text("UNICODE-3.0.txt")
    )
    (output / "ATTRIBUTION.txt").write_text(attribution, encoding="utf-8")
    counts = {
        "countriesAndTerritories": len(country_rows),
        "cities": len(cities),
        "firstLevelRegions": len(admins),
        "places": len(place_rows),
        "countriesWithCities": len({city[8] for city in cities}),
        "citiesWithKoreanNames": sum(label(city[0], "ko", "") != "" for city in cities),
        "regionsWithKoreanNames": sum(label(value[2], "ko", "") != "" for value in admins.values()),
    }
    manifest = {
        "schemaVersion": 1,
        "generatedAtUtc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "counts": counts,
        "sources": source_manifest,
        "resources": {name: digest(output / name) for name in ("countries.tsv", "places.tsv", "ATTRIBUTION.txt")},
    }
    (output / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    license_dir = ROOT / "docs/licenses"
    license_dir.mkdir(parents=True, exist_ok=True)
    (license_dir / "UNICODE-3.0.txt").write_text(text("UNICODE-3.0.txt"), encoding="utf-8")
    (license_dir / "DESTINATION_ATTRIBUTION.txt").write_text(attribution, encoding="utf-8")
    print(json.dumps({"counts": counts, "resources": manifest["resources"]}, ensure_ascii=False, indent=2))
    for needle in ("Tokyo", "Osaka", "Fukuoka", "Sapporo", "Seoul", "Busan"):
        print(needle, [(row[0], row[2], row[3]) for row in place_rows if row[5] == "city" and normalize(row[3]) == normalize(needle)])


if __name__ == "__main__":
    main()
