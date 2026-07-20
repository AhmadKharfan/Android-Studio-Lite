#!/usr/bin/env python3
"""Builds every shipped project template on the real build server and reports which ones work.

Each template is generated locally, zipped, and driven through the same REST flow the app uses
(POST /v1/builds -> PUT uploadUrl -> start -> stream), so a template that would fail at Run on a
device fails here instead -- without driving the wizard by hand once per template.

The verdict comes from `GET /v1/builds/{id}`; the log comes from the WebSocket, captured live. Both
are needed: the status endpoint reports a bare "FAILED" with no error or problems, and the stream
neither survives a long build (the LB drops it during quiet stretches) nor replays once the build has
finished. So the stream reconnects for the log, and REST decides pass/fail.

Usage:
    tools/template_build_check.py                    # every template
    tools/template_build_check.py empty-compose bottom-nav
    tools/template_build_check.py --keep-logs        # write full logs to .template-check/logs

    SERVER=http://host   override the build server (default: the deployed LB)

Exits non-zero if any template failed. Requires: websockets (pip install websockets), zip.
"""
from __future__ import annotations

import argparse
import asyncio
import json
import os
import shutil
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

SERVER = os.environ.get("SERVER", "https://build.androidstudiolite.me")
ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / ".template-check"
LOG_DIR = OUT / "logs"
BUILD_TIMEOUT_SECONDS = int(os.environ.get("BUILD_TIMEOUT_SECONDS", "900"))


def http(method: str, path: str, token: str | None = None, body: dict | None = None) -> dict:
    url = path if path.startswith("http") else f"{SERVER}{path}"
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    if data is None and method == "POST":
        req.add_header("Content-Length", "0")
    with urllib.request.urlopen(req, timeout=60) as resp:
        raw = resp.read()
    return json.loads(raw) if raw else {}


def upload(url: str, zip_path: Path) -> None:
    req = urllib.request.Request(url, data=zip_path.read_bytes(), method="PUT")
    req.add_header("Content-Type", "application/zip")
    with urllib.request.urlopen(req, timeout=300):
        pass


def generate(templates: list[str]) -> list[str]:
    print("==> generating templates", flush=True)
    subprocess.run(
        [str(ROOT / "gradlew"), "-p", str(ROOT), ":app:testDebugUnitTest",
         "--tests", "*TemplateBuildCheckGenerator*", f"-Dasl.templateCheck.out={OUT}",
         "--rerun-tasks", "-q"],
        check=True,
    )
    found = sorted(d.name for d in OUT.iterdir() if d.is_dir() and d.name != "logs")
    if not templates:
        return found
    missing = [t for t in templates if t not in found]
    if missing:
        sys.exit(f"no such template(s): {', '.join(missing)} (have: {', '.join(found)})")
    return templates


TERMINAL = {"SUCCEEDED", "FAILED", "CANCELLED", "TIMED_OUT"}


def fmt_duration(seconds: float) -> str:
    return f"{int(seconds) // 60}m {int(seconds) % 60:02d}s"


def rest_status(token: str, build_id: str) -> str:
    try:
        return http("GET", f"/v1/builds/{build_id}", token).get("status", "UNKNOWN")
    except (urllib.error.HTTPError, urllib.error.URLError):
        return "UNKNOWN"


async def follow(token: str, build_id: str) -> tuple[str, list[str], list[str]]:
    """Follows a build to completion. Returns (status, log lines, problem messages).

    The status comes from REST, not from the stream: the socket gets dropped on long builds (the LB
    times it out during a quiet stretch) and a dropped stream is not a failed build. The stream is
    only here for the log — it's the sole source of *why* a build failed — so it reconnects if it
    drops while the build is still going. There's no replay on reconnect, so a drop costs the log
    lines emitted while disconnected, never the verdict.
    """
    import websockets

    lines: list[str] = []
    problems: list[str] = []
    url = f"{SERVER.replace('http', 'ws', 1)}/v1/builds/{build_id}/stream"
    deadline = asyncio.get_event_loop().time() + BUILD_TIMEOUT_SECONDS

    while asyncio.get_event_loop().time() < deadline:
        try:
            async with websockets.connect(
                url, additional_headers={"Authorization": f"Bearer {token}"}, max_size=None
            ) as ws:
                while True:
                    event = json.loads(await asyncio.wait_for(ws.recv(), timeout=300))
                    kind = event.get("type")
                    if kind == "output":
                        lines.append(event.get("line", ""))
                    elif kind == "problem":
                        problems.append(event.get("message") or json.dumps(event))
                    elif kind == "finished":
                        return rest_status(token, build_id), lines, problems
        except Exception:
            status = rest_status(token, build_id)
            if status in TERMINAL:
                return status, lines, problems
            await asyncio.sleep(5)  # still building: pick the log back up

    return rest_status(token, build_id), lines, problems


def failure_reason(lines: list[str], problems: list[str]) -> str:
    """The first line a human would actually look at."""
    if problems:
        return problems[0].strip()[:200]
    for i, line in enumerate(lines):
        low = line.lower()
        if "what went wrong" in low:
            rest = [x.strip() for x in lines[i + 1:i + 6] if x.strip()]
            return " / ".join(rest)[:200] if rest else line[:200]
    for line in lines:
        if line.strip().startswith("e:") or "error:" in line.lower() or "FAILURE:" in line:
            return line.strip()[:200]
    return "(no error line in log)"


async def check(template: str, token: str, keep_logs: bool) -> tuple[str, str, float]:
    src = OUT / template
    zip_path = OUT / f"{template}.zip"
    if zip_path.exists():
        zip_path.unlink()
    subprocess.run(["zip", "-qr", str(zip_path), "."], cwd=src, check=True)

    created = http("POST", "/v1/builds", token, {
        "sourceType": "zip", "modulePath": ":app", "variantName": "debug",
        "kind": "ASSEMBLE", "tasks": ["assembleDebug"],
    })
    build_id = created["buildId"]
    upload(created["uploadUrl"], zip_path)
    # Wall time is measured from `start` to the terminal event: it spans queue wait, pod
    # scheduling and the Gradle run — i.e. what the user waits through after hitting Run.
    started_at = time.monotonic()
    http("POST", f"/v1/builds/{build_id}/start", token)
    print(f"==> {template}: building ({build_id})", flush=True)

    status, lines, problems = await follow(token, build_id)
    elapsed = time.monotonic() - started_at

    if keep_logs or status != "SUCCEEDED":
        LOG_DIR.mkdir(parents=True, exist_ok=True)
        (LOG_DIR / f"{template}.log").write_text("\n".join(lines))

    if status == "SUCCEEDED":
        print(f"    {template}: SUCCEEDED in {fmt_duration(elapsed)}", flush=True)
        return status, "", elapsed
    reason = failure_reason(lines, problems)
    print(f"    {template}: {status} in {fmt_duration(elapsed)} — {reason}", flush=True)
    return status, reason, elapsed


async def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("templates", nargs="*", help="template ids (default: all)")
    parser.add_argument("--keep-logs", action="store_true", help="keep logs for successful builds too")
    args = parser.parse_args()

    if not shutil.which("zip"):
        sys.exit("zip is required")
    try:
        import websockets  # noqa: F401
    except ImportError:
        sys.exit("websockets is required: pip install websockets")

    templates = generate(args.templates)
    token = http("POST", "/v1/devices", body={"deviceId": "template-build-check", "model": "ci"})["deviceToken"]

    results: list[tuple[str, str, str, float]] = []
    for template in templates:
        try:
            status, reason, elapsed = await check(template, token, args.keep_logs)
        except (urllib.error.HTTPError, urllib.error.URLError, subprocess.CalledProcessError) as exc:
            status, reason, elapsed = "HARNESS-ERROR", str(exc)[:200], 0.0
        results.append((template, status, reason, elapsed))

    width = max(len(t) for t, _, _, _ in results)
    print()
    print(f"{'TEMPLATE'.ljust(width)}  {'STATUS'.ljust(12)}  {'TIME'.ljust(8)}  DETAIL")
    print(f"{'-' * width}  {'-' * 12}  {'-' * 8}  ------")
    for template, status, reason, elapsed in results:
        print(f"{template.ljust(width)}  {status.ljust(12)}  {fmt_duration(elapsed).ljust(8)}  {reason}")
    ok = [e for _, s, _, e in results if s == "SUCCEEDED"]
    if ok:
        print(f"\nsucceeded: {len(ok)}  total {fmt_duration(sum(ok))}  "
              f"median {fmt_duration(sorted(ok)[len(ok) // 2])}")
    failed = [t for t, s, _, _ in results if s != "SUCCEEDED"]
    if failed:
        print(f"\n{len(failed)} failed: {', '.join(failed)} (logs in {LOG_DIR})")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(asyncio.run(main()))
