#!/usr/bin/env python3
"""Runs Lighthouse against the built MediDrop public journey.

The script uses the pinned Lighthouse CLI through npx, so the measurementdoes not depend on a globally installed package. It runs the home page and a
catalogue search three times each, keeps the median category scores, reports the
Core Web Vitals used by the performance register and fails below the documented
median thresholds (90 performance, 100 accessibility, 100 best-practices, 90
SEO).

Start the built app and API first, for example:
    cd frontend && VITE_PROXY_TARGET=http://127.0.0.1:8080 npm run preview -- --port 4174
    python3 infrastructure/scripts/lighthouse-check.py --base-url http://127.0.0.1:4174
"""

import argparse
import json
import shutil
import statistics
import subprocess
import sys
import tempfile
from datetime import datetime, timezone
from pathlib import Path

LIGHTHOUSE_VERSION = "12.8.2"
CATEGORIES = ("performance", "accessibility", "best-practices", "seo")
AUDIT_METRICS = {
    "first-contentful-paint": "fcp_ms",
    "largest-contentful-paint": "lcp_ms",
    "total-blocking-time": "tbt_ms",
    "cumulative-layout-shift": "cls",
    "speed-index": "speed_index_ms",
}


def median(values):
    return statistics.median(values) if values else 0.0


def run_lighthouse(npx, url, path):
    command = [
        npx,
        "--yes",
        f"lighthouse@{LIGHTHOUSE_VERSION}",
        url,
        "--quiet",
        "--output=json",
        f"--output-path={path}",
        "--chrome-flags=--headless=new --no-sandbox --disable-dev-shm-usage",
        "--only-categories=performance,accessibility,best-practices,seo",
        "--form-factor=mobile",
    ]
    result = subprocess.run(command, text=True, capture_output=True)
    if result.returncode != 0:
        raise RuntimeError(
            f"Lighthouse failed for {url} with exit {result.returncode}: "
            f"{(result.stderr or result.stdout).strip()[-1200:]}"
        )
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)


def summarise(report):
    categories = {
        name: round((report["categories"][name]["score"] or 0) * 100)
        for name in CATEGORIES
    }
    metrics = {}
    for audit_id, label in AUDIT_METRICS.items():
        value = report["audits"].get(audit_id, {}).get("numericValue")
        metrics[label] = round(value, 3) if value is not None else None
    return {
        "lighthouse_version": report.get("lighthouseVersion"),
        "categories": categories,
        "metrics": metrics,
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://127.0.0.1:4174")
    parser.add_argument("--runs", type=int, default=3)
    parser.add_argument(
        "--output",
        default=str(Path(__file__).resolve().parents[2] / "docs/assets/lighthouse-v018.json"),
    )
    parser.add_argument("--min-performance", type=int, default=90)
    parser.add_argument("--min-accessibility", type=int, default=100)
    parser.add_argument("--min-best-practices", type=int, default=100)
    parser.add_argument("--min-seo", type=int, default=90)
    args = parser.parse_args()

    if args.runs < 1:
        raise SystemExit("--runs must be positive")
    npx = shutil.which("npx")
    if not npx:
        raise SystemExit("npx is required (install Node.js 22+)")

    base = args.base_url.rstrip("/")
    urls = {
        "home": f"{base}/",
        "search": f"{base}/search?q=Paracetamol",
    }
    summary = {
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "base_url": base,
        "runs_per_url": args.runs,
        "form_factor": "mobile",
        "results": {},
    }

    with tempfile.TemporaryDirectory(prefix="medidrop-lighthouse-") as directory:
        for name, url in urls.items():
            samples = []
            for run in range(args.runs):
                path = Path(directory) / f"{name}-{run}.json"
                print(f"Running Lighthouse {name} ({run + 1}/{args.runs})...", flush=True)
                samples.append(summarise(run_lighthouse(npx, url, path)))
            categories = {
                category: median([sample["categories"][category] for sample in samples])
                for category in CATEGORIES
            }
            metrics = {
                metric: median(
                    [sample["metrics"][metric] for sample in samples if sample["metrics"][metric] is not None]
                )
                for metric in AUDIT_METRICS.values()
            }
            summary["results"][name] = {
                "url": url,
                "categories": {key: round(value) for key, value in categories.items()},
                "metrics": {key: round(value, 3) for key, value in metrics.items()},
                "samples": samples,
            }

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(summary["results"], indent=2))
    print(f"Wrote {output}")

    thresholds = {
        "performance": args.min_performance,
        "accessibility": args.min_accessibility,
        "best-practices": args.min_best_practices,
        "seo": args.min_seo,
    }
    failed = []
    for name, result in summary["results"].items():
        for category, minimum in thresholds.items():
            score = result["categories"][category]
            if score < minimum:
                failed.append(f"{name} {category} {score} < {minimum}")
    if failed:
        for failure in failed:
            print(f"Threshold failed: {failure}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
