# 09.06.26

import os
import re
import time
import signal
import atexit
import threading
import concurrent.futures
from typing import Any, Dict, List, Optional

from VibraVid.core.ui.tracker import download_tracker
from VibraVid.cli.run import execute_hooks


__all__ = [
    "download_executor",
    "scheduled_downloads",
    "scheduled_downloads_lock",
    "cancelled_scheduled_downloads",
    "set_max_download_slots",
    "_acquire_download_slot",
    "_release_download_slot",
    "_add_scheduled_download",
    "_remove_scheduled_download",
    "_cancel_scheduled_download",
    "_is_scheduled_cancelled",
    "_extract_series_base_title",
    "_same_series",
    "_get_scheduled_downloads",
    "_enrich_active_downloads_with_series",
    "_prune_scheduled_downloads",
    "shutdown_downloads",
    "_submit_download_task",
    "signal_handler",
]


download_executor = concurrent.futures.ThreadPoolExecutor(max_workers=10, thread_name_prefix="DownloadWorker")
scheduled_downloads: Dict[str, Dict[str, Any]] = {}
scheduled_downloads_lock = threading.Lock()
cancelled_scheduled_downloads: set[str] = set()

# ── Download concurrency limiter 
_download_slot_cond = threading.Condition()
_active_downloads = 0
_max_download_slots = 1


def set_max_download_slots(n: int) -> None:
    global _max_download_slots
    _max_download_slots = max(1, n)
    with _download_slot_cond:
        _download_slot_cond.notify_all()


def _acquire_download_slot() -> None:
    global _active_downloads
    with _download_slot_cond:
        while _active_downloads >= _max_download_slots:
            _download_slot_cond.wait()
        _active_downloads += 1


def _release_download_slot() -> None:
    global _active_downloads
    with _download_slot_cond:
        _active_downloads -= 1
        _download_slot_cond.notify()


def _add_scheduled_download(download_id: str, title: str, site: str, media_type: str = "Film", season: str = None, episodes: str = None) -> None:
    with scheduled_downloads_lock:
        scheduled_downloads[download_id] = {
            "id": download_id,
            "title": title,
            "site": site,
            "type": media_type,
            "season": season,
            "episodes": episodes,
            "scheduled_at": time.time(),
        }
        cancelled_scheduled_downloads.discard(download_id)


def _remove_scheduled_download(download_id: str) -> None:
    with scheduled_downloads_lock:
        scheduled_downloads.pop(download_id, None)
        cancelled_scheduled_downloads.discard(download_id)


def _cancel_scheduled_download(download_id: str) -> None:
    with scheduled_downloads_lock:
        cancelled_scheduled_downloads.add(download_id)
        scheduled_downloads.pop(download_id, None)


def _is_scheduled_cancelled(download_id: str) -> bool:
    with scheduled_downloads_lock:
        return download_id in cancelled_scheduled_downloads


def _extract_series_base_title(raw_title: str) -> str:
    """Normalize title to a stable series base name (strip season/episode suffixes)."""
    title = str(raw_title or "").strip()
    if not title:
        return ""
    # Examples: "Show - S1", "Show - S1 E3", "Show - S01 E01-02"
    base = re.split(r"\s-\sS\d+(?:\sE[\d\-\*,]+)?", title, maxsplit=1, flags=re.IGNORECASE)[0]
    return base.strip()


def _same_series(title: str, series_base: str) -> bool:
    if not series_base:
        return False
    return _extract_series_base_title(title).casefold() == series_base.casefold()


def _get_scheduled_downloads(exclude_ids: Optional[set] = None) -> List[Dict[str, Any]]:
    exclude_ids = exclude_ids or set()
    with scheduled_downloads_lock:
        return sorted(
            (item for item in scheduled_downloads.values() if item.get("id") not in exclude_ids),
            key=lambda item: item.get("scheduled_at", 0),
        )


def _enrich_active_downloads_with_series(active_downloads: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    """Attach series_name for active TV downloads so GUI can show the parent series."""
    with scheduled_downloads_lock:
        scheduled_by_id = {k: dict(v) for k, v in scheduled_downloads.items()}

    enriched: List[Dict[str, Any]] = []
    for item in active_downloads:
        row = dict(item)
        media_type = str(row.get("type") or "").lower()

        if media_type in {"serie", "tv", "series", "anime"}:
            series_name = ""
            row_id = row.get("id")

            scheduled_info = scheduled_by_id.get(row_id)
            if scheduled_info:
                series_name = _extract_series_base_title(scheduled_info.get("title", ""))

            if not series_name:
                title = str(row.get("title") or "").strip()
                title_base = _extract_series_base_title(title)
                # Only trust title-derived series name when title contains the Sxx suffix pattern.
                if title_base and title_base != title:
                    series_name = title_base

            if series_name:
                row["series_name"] = series_name

        enriched.append(row)

    return enriched


def _prune_scheduled_downloads(_active_downloads: List[Dict[str, Any]], history: List[Dict[str, Any]]) -> None:
    history_ids = {item.get("id") for item in history if item.get("id")}
    now = time.time()
    max_age_seconds = 6 * 60 * 60

    with scheduled_downloads_lock:
        to_remove = []
        for download_id, item in scheduled_downloads.items():

            # Keep entries visible while not completed; remove only once they
            # reach history (completed/failed/cancelled) or become stale.
            if download_id in history_ids:
                to_remove.append(download_id)
                continue
            if now - float(item.get("scheduled_at", now)) > max_age_seconds:
                to_remove.append(download_id)

        for download_id in to_remove:
            scheduled_downloads.pop(download_id, None)
            cancelled_scheduled_downloads.discard(download_id)


def shutdown_downloads():
    """Shutdown downloads and kill processes on exit."""
    print("Shutting down downloads...")
    with scheduled_downloads_lock:
        scheduled_downloads.clear()
        cancelled_scheduled_downloads.clear()
    download_tracker.shutdown()
    download_executor.shutdown(wait=True)


def _submit_download_task(fn):
    """Submit a task to the download executor, recreating it if it was shutdown."""
    global download_executor
    try:
        return download_executor.submit(fn)
    except RuntimeError:
        # Executor has been shutdown (interpreter shutdown or explicit call). Recreate.
        try:
            download_executor = concurrent.futures.ThreadPoolExecutor(max_workers=10, thread_name_prefix="DownloadWorker")
            return download_executor.submit(fn)
        except Exception as exc:
            print(f"[Error] Could not recreate download executor: {exc}")
            raise


# Ensure downloads are shut down on exit
atexit.register(shutdown_downloads)


# Handle SIGINT and SIGTERM to shutdown properly
def signal_handler(signum, frame):
    shutdown_thread = threading.Thread(target=shutdown_downloads, daemon=True)
    shutdown_thread.start()

    print("Running post-run hooks...")
    execute_hooks('post_run')

    print("Downloads shutdown started, exiting immediately...")
    os._exit(0)


if threading.current_thread() is threading.main_thread():
    signal.signal(signal.SIGINT, signal_handler)
    signal.signal(signal.SIGTERM, signal_handler)