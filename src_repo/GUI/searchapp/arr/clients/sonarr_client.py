# 07.05.26

import logging
import requests
from itertools import count
from typing import Any, Dict, List, Optional

logger = logging.getLogger("ARR.SONARR")


class SonarrClient:
    """Native Sonarr API v3 client with retry, timeout, and error handling."""

    def __init__(self, url: str, api_key: str, timeout: int = 15, max_retries: int = 3):
        self.url = url.rstrip("/")
        self.api_key = api_key
        self.timeout = timeout
        self.max_retries = max_retries
        self._base = f"{self.url}/api/v3"
        self._headers = {"X-Api-Key": self.api_key}

    # ── helpers ──────────────────────────────────────────

    def _request(self, method: str, path: str, **kwargs) -> requests.Response:
        """Execute an HTTP request with retry logic."""
        url = f"{self._base}{path}"
        kwargs.setdefault("headers", self._headers)
        kwargs.setdefault("timeout", self.timeout)

        last_exc = None
        for attempt in range(1, self.max_retries + 1):
            try:
                resp = requests.request(method, url, **kwargs)
                resp.raise_for_status()
                return resp
            except requests.RequestException as exc:
                last_exc = exc
                logger.warning(f"Sonarr request {method} {path} attempt {attempt}/{self.max_retries} failed: {exc}")

        logger.error(f"Sonarr request {method} {path} failed after {self.max_retries} attempts")
        raise last_exc

    def _get(self, path: str, params: Optional[dict] = None) -> requests.Response:
        return self._request("GET", path, params=params)

    def _get_safe(self, path: str, params: Optional[dict] = None) -> List[Dict[str, Any]]:
        """GET that returns an empty list on any HTTP/network error (no retry).

        Use for optional/informational endpoints (e.g. manualimport lookup)
        where a 4xx/5xx should be treated as "nothing found" rather than a hard error.
        """
        url = f"{self._base}{path}"
        try:
            resp = requests.get(url, params=params, headers=self._headers, timeout=self.timeout)
            if not resp.ok:
                logger.debug(f"Sonarr {path} returned {resp.status_code}, treating as empty")
                return []
            return resp.json()
        except Exception as exc:
            logger.debug(f"Sonarr safe GET {path} failed: {exc}")
            return []

    def _post(self, path: str, json_data: Optional[dict] = None) -> requests.Response:
        return self._request("POST", path, json=json_data)

    def _put(self, path: str, json_data: Optional[dict] = None) -> requests.Response:
        return self._request("PUT", path, json=json_data)

    # ── status ───────────────────────────────────────────

    def system_status(self) -> Dict[str, Any]:
        """Check Sonarr connectivity and API key validity."""
        return self._get("/system/status").json()

    def is_available(self) -> bool:
        """Return True if Sonarr is reachable."""
        try:
            self.system_status()
            return True
        except Exception:
            return False

    # ── wanted / missing ─────────────────────────────────

    def wanted_missing(self, page: int = 1, page_size: int = 20) -> Dict[str, Any]:
        """Get missing episodes (paginated)."""
        return self._get("/wanted/missing", params={
            "includeSeries": True,
            "pageSize": page_size,
            "page": page,
        }).json()

    def get_all_missing(self) -> List[Dict[str, Any]]:
        """Iterate all pages and return every missing episode record."""
        all_records: List[Dict[str, Any]] = []
        for page in count(1):
            data = self.wanted_missing(page=page)
            records = data.get("records", [])
            if not records:
                break
            all_records.extend(records)
        return all_records

    # ── series ───────────────────────────────────────────

    def get_series(self) -> List[Dict[str, Any]]:
        """Get all series in Sonarr."""
        return self._get("/series").json()

    def get_series_by_id(self, series_id: int) -> Dict[str, Any]:
        """Get a single series by ID."""
        return self._get(f"/series/{series_id}").json()

    def update_series_path(self, series_id: int, new_path: str) -> bool:
        """Update the root path of a series so Sonarr expects files there."""
        try:
            series = self.get_series_by_id(series_id)
            if series.get("path") == new_path:
                return True
            series["path"] = new_path
            self._put(f"/series/{series_id}", json_data=series)
            logger.info(f"Updated Sonarr series {series_id} path to '{new_path}'")
            return True
        except Exception as exc:
            logger.error(f"Failed to update series path: {exc}")
            return False

    # ── episodes ─────────────────────────────────────────

    def get_episode(self, episode_id: int) -> Dict[str, Any]:
        return self._get(f"/episode/{episode_id}").json()

    def get_episodes_for_series(self, series_id: int) -> List[Dict[str, Any]]:
        """Get all episodes for a specific series."""
        return self._get("/episode", params={"seriesId": series_id}).json()

    def set_episode_unmonitored(self, episode_ids: List[int]) -> bool:
        """Mark episodes as unmonitored so they disappear from wanted/missing."""
        try:
            self._put("/episode/monitor", json_data={
                "episodeIds": episode_ids,
                "monitored": False,
            })
            return True
        except Exception as exc:
            logger.error(f"Failed to set episodes unmonitored: {exc}")
            return False

    # ── queue ────────────────────────────────────────────

    def queue(self) -> Dict[str, Any]:
        return self._get("/queue", params={
            "includeUnknownSeriesItems": False,
            "includeSeries": False,
            "includeEpisode": False,
        }).json()

    def is_episode_in_queue(self, episode_id: int) -> bool:
        """Check if a specific episode is already downloading."""
        try:
            records = self.queue().get("records", [])
            return any(r.get("episodeId") == episode_id for r in records)
        except Exception:
            return False

    # ── tags ─────────────────────────────────────────────

    def get_tags(self) -> List[Dict[str, Any]]:
        return self._get("/tag").json()

    def get_tags_map(self) -> Dict[int, str]:
        """Return {tag_id: tag_label_lowercase}."""
        try:
            return {t["id"]: t["label"].lower() for t in self.get_tags()}
        except Exception as exc:
            logger.error(f"Failed to fetch Sonarr tags: {exc}")
            return {}

    # ── commands ─────────────────────────────────────────

    def command_downloaded_episodes_scan(self, path: str) -> Dict[str, Any]:
        """Tell Sonarr to scan a folder for newly downloaded episodes."""
        return self._post("/command", json_data={
            "name": "DownloadedEpisodesScan",
            "path": path,
            "importMode": "Auto",
        }).json()

    def command_rescan_series(self, series_id: int) -> Dict[str, Any]:
        return self._post("/command", json_data={
            "name": "RescanSeries",
            "seriesId": series_id,
        }).json()

    def command_series_search(self, series_id: int) -> Dict[str, Any]:
        """Trigger a search for all missing episodes of a series."""
        return self._post("/command", json_data={
            "name": "SeriesSearch",
            "seriesId": series_id,
        }).json()

    # ── manual import ───────────────────────────────────

    def manual_import_lookup(self, folder_path: str, series_id: Optional[int] = None) -> List[Dict[str, Any]]:
        """Get list of files available for manual import in a folder.

        Returns [] (not raises) if the folder is missing, empty, or Sonarr returns an error.
        """
        params: Dict[str, Any] = {"folder": folder_path, "filterExistingFiles": False}
        if series_id:
            params["seriesId"] = series_id
        return self._get_safe("/manualimport", params=params)

    def manual_import(self, import_items: List[Dict[str, Any]]) -> Dict[str, Any]:
        """Submit manual import decisions to Sonarr."""
        return self._post("/manualimport", json_data=import_items).json()
