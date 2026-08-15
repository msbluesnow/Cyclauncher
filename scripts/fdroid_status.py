#!/usr/bin/env python3
"""Show F-Droid build/publication status for Cyclauncher.

Checks:
  1. f-droid.org API  — which versions are published in the store
  2. monitor.f-droid.org  — current build queue (running / waiting)
  3. monitor.f-droid.org build logs  — completed build logs per version

Usage:
  python fdroid_status.py                    # one-shot check
  python fdroid_status.py --watch            # refresh every 10 minutes
  python fdroid_status.py --watch 5          # refresh every 5 minutes
  python fdroid_status.py --notify           # one-shot + Telegram message on status change
                                              # (needs TELEGRAM_BOT_TOKEN and TELEGRAM_CHAT_ID env vars,
                                              #  state file keeps last seen status)

To stop scheduled checks: in GitHub repo go to Actions -> "F-Droid status check"
-> "..." (top right) -> Disable workflow.
"""

import argparse
import json
import os
import re
import sys
import time
import urllib.parse
import urllib.request

PACKAGE = "dev.msbs.cyclauncher"
API_URL = f"https://f-droid.org/api/v1/packages/{PACKAGE}"
MONITOR_BASE = "https://monitor.f-droid.org"
LOGS_URL = f"{MONITOR_BASE}/buildlogs/{PACKAGE}/"


def fetch(url: str) -> str:
    req = urllib.request.Request(url, headers={"User-Agent": "fdroid-status-checker"})
    with urllib.request.urlopen(req, timeout=30) as resp:
        return resp.read().decode("utf-8", errors="replace")


def published_versions() -> list[dict]:
    data = json.loads(fetch(API_URL))
    return data.get("packages", [])


def queue_status() -> list[str]:
    """Return lines mentioning the package on the running/waiting queue pages."""
    hits = []
    for state in ("running", "waiting"):
        try:
            html = fetch(f"{MONITOR_BASE}/builds/{state}")
        except Exception:
            continue
        for m in re.finditer(
            rf'<a[^>]*href="[^"]*{re.escape(PACKAGE)}[^"]*"[^>]*>(.*?)</a>', html
        ):
            entry = re.sub(r"<[^>]+>", "", m.group(1)).strip()
            if entry:
                hits.append(f"[{state}] {entry}")
    return hits


def build_logs() -> list[str]:
    """Return version/build log filenames listed on the monitor."""
    try:
        html = fetch(LOGS_URL)
    except Exception:
        return []
    names = re.findall(rf'href="(/?buildlogs/{re.escape(PACKAGE)}/[^"]+)"', html)
    return sorted({n.rsplit("/", 1)[-1] for n in names})


def report() -> None:
    print(f"=== F-Droid status for {PACKAGE} ===")
    print(time.strftime("%Y-%m-%d %H:%M:%S"), end="\n\n")

    try:
        versions = published_versions()
        print("[Store] published versions:")
        for v in versions:
            print(f"  - {v.get('versionName')}  (versionCode {v.get('versionCode')})")
        if not versions:
            print("  (none)")
    except Exception as e:
        print(f"[Store] failed to query f-droid.org API: {e}")

    try:
        logs = build_logs()
        print("\n[Monitor] build logs on server:")
        for name in logs:
            print(f"  - {name}")
        if not logs:
            print("  (no build logs yet)")
    except Exception as e:
        print(f"\n[Monitor] failed to query build logs: {e}")

    hits = queue_status()
    print("\n[Queue] current build queue:")
    if hits:
        for h in hits:
            print(f"  - {h}")
    else:
        print("  (package not in running/waiting queue)")

    suggested = max(
        (v.get("versionCode", 0) for v in published_versions()), default=None
    )
    if suggested is not None:
        print(f"\nLatest versionCode in store: {suggested}. "
              f"A new build appears here once F-Droid publishes it.")


def collect_status() -> dict:
    """Gather the current status snapshot (never raises on network errors)."""
    status = {"store": [], "logs": [], "queue": []}
    try:
        status["store"] = [
            {"versionName": v.get("versionName"), "versionCode": v.get("versionCode")}
            for v in published_versions()
        ]
    except Exception:
        pass
    try:
        status["logs"] = build_logs()
    except Exception:
        pass
    try:
        status["queue"] = queue_status()
    except Exception:
        pass
    return status


def format_status(status: dict) -> str:
    lines = [f"F-Droid status for {PACKAGE}:", ""]
    lines.append("Store:")
    for v in status["store"]:
        lines.append(f"  {v['versionName']} (code {v['versionCode']})")
    if not status["store"]:
        lines.append("  (none)")
    lines.append("Build logs:")
    lines += [f"  {n}" for n in status["logs"]] or ["  (none)"]
    lines.append("Queue:")
    lines += [f"  {h}" for h in status["queue"]] or ["  (not in queue)"]
    return "\n".join(lines)


def send_telegram(text: str) -> None:
    token = os.environ.get("TELEGRAM_BOT_TOKEN")
    chat_id = os.environ.get("TELEGRAM_CHAT_ID")
    if not token or not chat_id:
        print("Telegram env vars not set, skipping notification", file=sys.stderr)
        return
    url = f"https://api.telegram.org/bot{token}/sendMessage"
    data = urllib.parse.urlencode(
        {"chat_id": chat_id, "text": text}
    ).encode()
    req = urllib.request.Request(url, data=data)
    with urllib.request.urlopen(req, timeout=30) as resp:
        result = json.loads(resp.read())
    if not result.get("ok"):
        print(f"Telegram API error: {result}", file=sys.stderr)


STATE_FILE = os.environ.get(
    "FDROID_STATE_FILE",
    os.path.join(os.path.dirname(os.path.abspath(__file__)), "fdroid_state.json"),
)


def notify_if_changed() -> None:
    """Compare current status with the saved state, notify on change, update state."""
    status = collect_status()
    report_extra = format_status(status)
    print(report_extra)

    old = None
    if os.path.exists(STATE_FILE):
        try:
            with open(STATE_FILE, encoding="utf-8") as f:
                old = json.load(f)
        except Exception:
            pass

    if old != status:
        with open(STATE_FILE, "w", encoding="utf-8") as f:
            json.dump(status, f, ensure_ascii=False, indent=2)
        if old is not None:
            # First run just records the baseline, no notification.
            lines = ["F-Droid status changed!", "" + report_extra]
            send_telegram("\n".join(lines))
            print("Status changed, Telegram notification sent")
        else:
            print("Baseline saved, no notification on first run")
    else:
        print("No changes")


def main() -> None:
    parser = argparse.ArgumentParser(description="F-Droid status for Cyclauncher")
    parser.add_argument(
        "--watch", nargs="?", const=10, type=int, metavar="MIN",
        help="keep refreshing every MIN minutes (default 10)",
    )
    parser.add_argument(
        "--notify", action="store_true",
        help="one-shot check, send Telegram message if status changed since last run",
    )
    args = parser.parse_args()

    if args.notify:
        notify_if_changed()
        return

    while True:
        report()
        if args.watch is None:
            break
        print(f"\nRefreshing in {args.watch} minutes... (Ctrl+C to stop)")
        try:
            time.sleep(args.watch * 60)
        except KeyboardInterrupt:
            sys.exit(0)


if __name__ == "__main__":
    main()
