#!/usr/bin/env python3
"""
Builds the PocketOS service catalog from services.source.json.

Brand glyphs come from Simple Icons (https://simpleicons.org, CC0-1.0 SVG
data). Only the single <path d="..."> of each 24x24 icon is embedded, after
strict validation, so the app never downloads or renders arbitrary images.
Brand names and logos remain trademarks of their respective owners and are
shown only to identify the service the user pays for.

Usage:  python tools/catalog/build_catalog.py [--simple-icons-version 16.33.0]
Writes:
  android/app/src/main/assets/service_catalog.json  (bundled offline copy)
  backend/resources/service_catalog.json            (served by GET /api/v1/catalog/services)
"""
import argparse
import datetime
import json
import pathlib
import re
import sys
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[2]
SOURCE = ROOT / "tools" / "catalog" / "services.source.json"
OUTPUTS = [
    ROOT / "app" / "src" / "main" / "assets" / "service_catalog.json",
]
PATH_RE = re.compile(r"^[MmZzLlHhVvCcSsQqTtAa0-9eE.,\-\s]+$")
COLOR_RE = re.compile(r"^#[0-9A-Fa-f]{6}$")
ID_RE = re.compile(r"^[a-z0-9_]{2,64}$")
CDN = "https://cdn.jsdelivr.net/npm/simple-icons@{version}/{path}"


def fetch(url: str) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": "PocketOS-catalog-builder"})
    with urllib.request.urlopen(req, timeout=30) as resp:
        if resp.status != 200:
            raise RuntimeError(f"{url} -> HTTP {resp.status}")
        return resp.read()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--simple-icons-version", default="16.33.0")
    args = parser.parse_args()
    version = args.simple_icons_version

    source = json.loads(SOURCE.read_text(encoding="utf-8"))
    si_data = json.loads(fetch(CDN.format(version=version, path="data/simple-icons.json")))
    hex_by_slug = {item["slug"]: "#" + item["hex"].upper() for item in si_data if "slug" in item}

    category_ids = {c["id"] for c in source["categories"]}
    seen = set()
    services = []
    missing_icons = []
    for s in source["services"]:
        sid = s["id"]
        if not ID_RE.match(sid) or sid in seen:
            raise SystemExit(f"invalid or duplicate id: {sid}")
        seen.add(sid)
        if s["category"] not in category_ids:
            raise SystemExit(f"{sid}: unknown category {s['category']}")

        icon = None
        color = s.get("color")
        slug = s.get("si")
        if slug:
            try:
                svg = fetch(CDN.format(version=version, path=f"icons/{slug}.svg")).decode("utf-8")
                match = re.search(r'<path d="([^"]+)"', svg)
                if not match or 'viewBox="0 0 24 24"' not in svg:
                    raise ValueError("unexpected SVG structure")
                path = match.group(1)
                if not PATH_RE.match(path) or len(path) > 12000:
                    raise ValueError("path data failed validation")
                icon = {"path": path, "viewbox": 24, "source": "simple-icons"}
                color = color or hex_by_slug.get(slug)
            except Exception as exc:  # noqa: BLE001 - report and fall back to a monogram
                missing_icons.append(f"{sid} ({slug}): {exc}")
        if color is not None and not COLOR_RE.match(color):
            raise SystemExit(f"{sid}: bad colour {color}")

        billing = s.get("billing")
        if billing is not None and (billing["unit"] not in ("day", "week", "month", "year") or not 1 <= billing["interval"] <= 365):
            raise SystemExit(f"{sid}: bad billing {billing}")

        services.append({
            "id": sid,
            "name": s["name"],
            "aliases": s.get("aliases", []),
            "category": s["category"],
            "website": s.get("website"),
            "color": color.upper() if color else None,
            "icon": icon,
            "billing": billing,
            "regions": s.get("regions", []),
            "keywords": s.get("keywords", []),
            "generic": bool(s.get("generic", False)),
        })

    now = datetime.datetime.now(datetime.timezone.utc)
    catalog = {
        "version": int(now.strftime("%Y%m%d%H")),
        "generated_at": now.strftime("%Y-%m-%dT%H:%M:%SZ"),
        "icon_source": {
            "name": "Simple Icons",
            "version": version,
            "license": "CC0-1.0",
            "url": "https://simpleicons.org",
            "notice": "Brand names and logos are trademarks of their respective owners.",
        },
        "categories": source["categories"],
        "services": services,
    }
    text = json.dumps(catalog, ensure_ascii=False, separators=(",", ":"))
    for out in OUTPUTS:
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(text, encoding="utf-8")
        print(f"wrote {out.relative_to(ROOT)} ({len(text) // 1024} KiB)")
    with_icons = sum(1 for s in services if s["icon"])
    print(f"{len(services)} services, {with_icons} with brand glyphs")
    for line in missing_icons:
        print("  no glyph:", line)
    return 0


if __name__ == "__main__":
    sys.exit(main())
