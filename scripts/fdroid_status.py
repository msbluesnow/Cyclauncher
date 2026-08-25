#!/usr/bin/env python3
"""Show F-Droid build/publication status for Cyclauncher.

Checks:
  1. f-droid.org API  — which versions are published in the store
  2. gitlab.com fdroiddata MRs — bot update MRs (checkupdates-bot) & CI pipeline status
  3. monitor.f-droid.org  — current build queue (running / waiting)
  4. monitor.f-droid.org build logs  — completed build logs per version

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
GITLAB_API_BASE = "https://gitlab.com/api/v4/projects/fdroid%2Ffdroiddata"


def fetch(url: str) -> str:
    req = urllib.request.Request(url, headers={"User-Agent": "fdroid-status-checker"})
    with urllib.request.urlopen(req, timeout=30) as resp:
        return resp.read().decode("utf-8", errors="replace")


def published_versions() -> list[dict]:
    data = json.loads(fetch(API_URL))
    return data.get("packages", [])


def extract_mr_version(title: str, source_branch: str = "") -> str:
    """Extract human-readable version/tag (e.g. 'v0.8.1-alpha (13)') from MR title or branch."""
    m = re.search(r'to\s+([vV]?\d+[\w\.\-]+(?:\s*\(\d+\))?)', title, re.IGNORECASE)
    if m:
        return m.group(1).strip()

    m_code = re.search(r'\((\d+)\)', title)
    m_ver = re.search(r'([vV]\d+\.[\w\.\-]+|\b\d+\.\d+(?:\.\d+)?[\w\.\-]*)', title)
    if m_ver and m_code:
        return f"{m_ver.group(1)} ({m_code.group(1)})"
    elif m_ver:
        return m_ver.group(1)
    elif m_code:
        return f"code {m_code.group(1)}"

    if source_branch:
        clean_b = source_branch.replace(PACKAGE, "").replace("checkupdates-bot-", "").strip("-_")
        if clean_b:
            return clean_b
    return ""


def gitlab_mr_status() -> list[dict]:
    """Fetch Merge Requests in fdroid/fdroiddata matching the package, including bot updates and CI pipeline state."""
    mrs_by_iid = {}
    urls_to_try = [
        f"{GITLAB_API_BASE}/merge_requests?search={urllib.parse.quote(PACKAGE)}&per_page=10",
        f"{GITLAB_API_BASE}/merge_requests?source_branch={urllib.parse.quote(PACKAGE)}&per_page=10",
    ]
    for url in urls_to_try:
        try:
            raw = fetch(url)
            items = json.loads(raw)
            if isinstance(items, list):
                for item in items:
                    iid = item.get("iid")
                    if iid and iid not in mrs_by_iid:
                        mrs_by_iid[iid] = item
        except Exception:
            continue

    sorted_mrs = sorted(
        mrs_by_iid.values(),
        key=lambda x: x.get("updated_at", ""),
        reverse=True
    )

    results = []
    for mr in sorted_mrs[:5]:
        iid = mr.get("iid")
        title = mr.get("title", "")
        state = mr.get("state", "")
        author = mr.get("author", {}).get("username", "") if isinstance(mr.get("author"), dict) else ""
        web_url = mr.get("web_url", "")
        created_at = mr.get("created_at", "")
        updated_at = mr.get("updated_at", "")
        merged_at = mr.get("merged_at", "")
        labels = mr.get("labels", [])
        source_branch = mr.get("source_branch", "")
        version = extract_mr_version(title, source_branch)

        pipeline_status = None
        pipeline_url = None
        try:
            pipelines_raw = fetch(f"{GITLAB_API_BASE}/merge_requests/{iid}/pipelines")
            pipelines = json.loads(pipelines_raw)
            if isinstance(pipelines, list) and len(pipelines) > 0:
                latest_p = pipelines[0]
                pipeline_status = latest_p.get("status")
                pipeline_url = latest_p.get("web_url")
        except Exception:
            pass

        results.append({
            "iid": iid,
            "title": title,
            "state": state,
            "author": author,
            "web_url": web_url,
            "created_at": created_at,
            "updated_at": updated_at,
            "merged_at": merged_at,
            "labels": labels,
            "source_branch": source_branch,
            "version": version,
            "pipeline_status": pipeline_status,
            "pipeline_url": pipeline_url,
        })
    return results


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
        print("[Store] Published versions in F-Droid:")
        for v in versions:
            print(f"  • {v.get('versionName')}  (versionCode {v.get('versionCode')})")
        if not versions:
            print("  (none)")
    except Exception as e:
        print(f"[Store] failed to query f-droid.org API: {e}")

    try:
        mrs = gitlab_mr_status()
        print("\n[GitLab] Merge requests on fdroiddata (Bot & Manual Updates):")
        for mr in mrs:
            ver_tag = f"[{mr['version']}] " if mr.get("version") else ""
            pipeline_info = f" | CI: {mr['pipeline_status']}" if mr.get("pipeline_status") else ""
            state_str = mr['state'].upper()
            print(f"  • {ver_tag}MR !{mr['iid']}: \"{mr['title']}\" [{state_str}]{pipeline_info}")
            if mr.get("author"):
                print(f"    Author: @{mr['author']} | Labels: {', '.join(mr.get('labels', [])) or '(none)'}")
            if mr.get("pipeline_url"):
                print(f"    Pipeline: {mr['pipeline_url']}")
            print(f"    URL: {mr['web_url']}")
        if not mrs:
            print("  (no merge requests found)")
    except Exception as e:
        print(f"\n[GitLab] failed to query GitLab merge requests: {e}")

    try:
        logs = build_logs()
        print("\n[Monitor] Build logs on server:")
        for name in logs:
            print(f"  • {name}")
        if not logs:
            print("  (no build logs yet)")
    except Exception as e:
        print(f"\n[Monitor] failed to query build logs: {e}")

    hits = queue_status()
    print("\n[Queue] Current build queue:")
    if hits:
        for h in hits:
            print(f"  • {h}")
    else:
        print("  (package not in running/waiting queue)")

    suggested = max(
        (v.get("versionCode", 0) for v in published_versions()), default=None
    )
    if suggested is not None:
        print(f"\nLatest versionCode in store: {suggested}. "
              f"A new build appears here once F-Droid publishes it.")


def normalize_status(state: dict | None) -> dict | None:
    """Standardize state representation so comparisons are 100% deterministic."""
    if not state or not isinstance(state, dict):
        return None

    store_list = [
        {"versionName": v.get("versionName"), "versionCode": v.get("versionCode")}
        for v in state.get("store", [])
        if isinstance(v, dict) and v.get("versionCode") is not None
    ]
    store_list = sorted(store_list, key=lambda x: x.get("versionCode", 0))

    mr_list = [
        {
            "iid": mr.get("iid"),
            "title": mr.get("title", ""),
            "state": mr.get("state", ""),
            "pipeline_status": mr.get("pipeline_status"),
            "web_url": mr.get("web_url", ""),
            "author": mr.get("author", ""),
            "version": mr.get("version") or extract_mr_version(mr.get("title", ""), mr.get("source_branch", "")),
        }
        for mr in state.get("gitlab_mrs", [])
        if isinstance(mr, dict) and mr.get("iid") is not None
    ]
    mr_list = sorted(mr_list, key=lambda x: str(x.get("iid", "")))

    logs_list = sorted(list(set(state.get("logs", []))))
    queue_list = sorted(list(set(state.get("queue", []))))

    return {
        "store": store_list,
        "gitlab_mrs": mr_list,
        "logs": logs_list,
        "queue": queue_list,
    }


def collect_status(previous_state: dict | None = None) -> tuple[dict, bool]:
    """Gather current status. On network failures, preserves previous state to prevent false diffs."""
    status = {}
    had_error = False

    try:
        versions = published_versions()
        status["store"] = [
            {"versionName": v.get("versionName"), "versionCode": v.get("versionCode")}
            for v in versions
        ]
    except Exception as e:
        had_error = True
        print(f"Error fetching store versions: {e}", file=sys.stderr)
        if previous_state and "store" in previous_state:
            status["store"] = previous_state["store"]
        else:
            status["store"] = []

    try:
        mrs = gitlab_mr_status()
        status["gitlab_mrs"] = [
            {
                "iid": mr.get("iid"),
                "title": mr.get("title", ""),
                "state": mr.get("state", ""),
                "pipeline_status": mr.get("pipeline_status"),
                "web_url": mr.get("web_url", ""),
                "author": mr.get("author", ""),
                "version": mr.get("version", ""),
            }
            for mr in mrs
        ]
    except Exception as e:
        had_error = True
        print(f"Error fetching GitLab MRs: {e}", file=sys.stderr)
        if previous_state and "gitlab_mrs" in previous_state:
            status["gitlab_mrs"] = previous_state["gitlab_mrs"]
        else:
            status["gitlab_mrs"] = []

    try:
        status["logs"] = build_logs()
    except Exception as e:
        had_error = True
        print(f"Error fetching build logs: {e}", file=sys.stderr)
        if previous_state and "logs" in previous_state:
            status["logs"] = previous_state["logs"]
        else:
            status["logs"] = []

    try:
        status["queue"] = queue_status()
    except Exception as e:
        had_error = True
        print(f"Error fetching queue status: {e}", file=sys.stderr)
        if previous_state and "queue" in previous_state:
            status["queue"] = previous_state["queue"]
        else:
            status["queue"] = []

    return status, had_error


def compute_changes(old: dict, new: dict) -> tuple[list[str], list[str]]:
    """Return (changes_list, top_targets_summary_list) between old and new state."""
    changes = []
    top_targets = []

    # 1. Store changes
    old_store_codes = {v.get("versionCode") for v in old.get("store", [])}
    new_store_versions = [v for v in new.get("store", []) if v.get("versionCode") not in old_store_codes]
    for v in new_store_versions:
        ver_name = v.get('versionName') or f"code {v.get('versionCode')}"
        top_targets.append(f"🎉 Store: {ver_name} published!")
        changes.append(f"🎉 New version live in F-Droid Store: {v.get('versionName')} (versionCode {v.get('versionCode')})")

    # 2. GitLab MR changes
    old_mrs = {mr.get("iid"): mr for mr in old.get("gitlab_mrs", [])}
    new_mrs = {mr.get("iid"): mr for mr in new.get("gitlab_mrs", [])}

    for iid, new_mr in new_mrs.items():
        ver = new_mr.get("version") or extract_mr_version(new_mr.get("title", ""))
        ver_tag = f"[{ver}] " if ver else ""
        mr_url = new_mr.get("web_url") or f"https://gitlab.com/fdroid/fdroiddata/-/merge_requests/{iid}"

        if iid not in old_mrs:
            top_targets.append(f"📋 New MR !{iid} {ver_tag}({new_mr.get('state')})")
            changes.append(
                f"📋 New GitLab MR !{iid} {ver_tag}\n"
                f"   Title: \"{new_mr.get('title')}\"\n"
                f"   State: {new_mr.get('state')}\n"
                f"   URL: {mr_url}"
            )
        else:
            old_mr = old_mrs[iid]
            state_changed = old_mr.get("state") != new_mr.get("state")
            pipeline_changed = (
                old_mr.get("pipeline_status") != new_mr.get("pipeline_status")
                and new_mr.get("pipeline_status") is not None
            )

            if state_changed:
                old_st = old_mr.get("state", "unknown")
                new_st = new_mr.get("state", "unknown")
                state_note = ""
                if new_st == "merged":
                    state_note = " (✅ Merged into fdroiddata — queued for next build)"
                elif new_st == "closed":
                    state_note = " (❌ Closed without merge)"

                top_targets.append(f"🔄 MR !{iid} {ver_tag}: {old_st} ➔ {new_st.upper()}")
                changes.append(
                    f"🔄 GitLab MR !{iid} {ver_tag}\n"
                    f"   Title: \"{new_mr.get('title')}\"\n"
                    f"   State change: {old_st} ➔ {new_st.upper()}{state_note}\n"
                    f"   URL: {mr_url}"
                )

            if pipeline_changed:
                p_old = old_mr.get("pipeline_status") or "none"
                p_new = new_mr.get("pipeline_status")
                top_targets.append(f"⚙️ MR !{iid} {ver_tag}CI: {p_old} ➔ {p_new}")
                changes.append(
                    f"⚙️ GitLab MR !{iid} {ver_tag}CI Pipeline:\n"
                    f"   Pipeline status: {p_old} ➔ {p_new.upper()}\n"
                    f"   Title: \"{new_mr.get('title')}\"\n"
                    f"   URL: {mr_url}"
                )

    # 3. Build logs changes
    old_logs = set(old.get("logs", []))
    new_logs = [l for l in new.get("logs", []) if l not in old_logs]
    for log in new_logs:
        top_targets.append(f"📝 Build Log: {log}")
        changes.append(f"📝 New build log on monitor: {log}")

    # 4. Queue changes
    old_queue = set(old.get("queue", []))
    new_queue = set(new.get("queue", []))
    if old_queue != new_queue:
        if new_queue:
            q_str = ', '.join(new.get('queue', []))
            top_targets.append(f"⏳ In Build Queue: {q_str}")
            changes.append(f"⏳ Build queue updated: {q_str}")
        elif old_queue:
            top_targets.append("✅ Package left build queue")
            changes.append("✅ Package left the build queue (build finished)")

    return changes, top_targets


def format_status(status: dict) -> str:
    lines = [f"F-Droid status for {PACKAGE}:", ""]
    lines.append("Store:")
    for v in status.get("store", []):
        lines.append(f"  • {v['versionName']} (code {v['versionCode']})")
    if not status.get("store"):
        lines.append("  (none)")

    lines.append("\nGitLab MRs:")
    for mr in status.get("gitlab_mrs", []):
        ver_tag = f"[{mr.get('version')}] " if mr.get("version") else ""
        p_stat = f" (CI: {mr['pipeline_status']})" if mr.get("pipeline_status") else ""
        st = mr['state'].upper()
        lines.append(f"  • {ver_tag}!{mr['iid']}: \"{mr['title']}\" [{st}]{p_stat}")
        if mr.get("web_url"):
            lines.append(f"    {mr['web_url']}")
    if not status.get("gitlab_mrs"):
        lines.append("  (none)")

    lines.append("\nBuild logs:")
    lines += [f"  • {n}" for n in status.get("logs", [])] or ["  (none)"]
    lines.append("\nQueue:")
    lines += [f"  • {h}" for h in status.get("queue", [])] or ["  (not in queue)"]
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
    old_raw = None
    if os.path.exists(STATE_FILE):
        try:
            with open(STATE_FILE, encoding="utf-8") as f:
                old_raw = json.load(f)
        except Exception as e:
            print(f"Failed to read state file: {e}", file=sys.stderr)

    old = normalize_status(old_raw)
    status_raw, had_error = collect_status(previous_state=old)
    current = normalize_status(status_raw)

    report_extra = format_status(current)
    print(report_extra)

    if old is None:
        # First run / baseline creation
        with open(STATE_FILE, "w", encoding="utf-8") as f:
            json.dump(current, f, ensure_ascii=False, indent=2)
        print("\nBaseline saved, no notification on first run.")
        return

    changes, top_targets = compute_changes(old, current)
    if changes:
        with open(STATE_FILE, "w", encoding="utf-8") as f:
            json.dump(current, f, ensure_ascii=False, indent=2)

        header_lines = ["🔔 F-Droid: Статус обновления изменился!"]
        if top_targets:
            header_lines.append("")
            header_lines.append("🎯 Затронутое обновление / MR:")
            for t in top_targets:
                header_lines.append(f"  {t}")

        change_summary = "\n\n".join(changes)
        lines = [
            "\n".join(header_lines),
            "",
            "📋 Детали изменений:",
            change_summary,
            "",
            "────────────────────────",
            report_extra
        ]
        send_telegram("\n".join(lines))
        print("\nStatus changed, Telegram notification sent:\n" + "\n".join(top_targets) + "\n\n" + change_summary)
    else:
        # Always re-save normalized state
        with open(STATE_FILE, "w", encoding="utf-8") as f:
            json.dump(current, f, ensure_ascii=False, indent=2)
        print("\nNo changes.")


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
