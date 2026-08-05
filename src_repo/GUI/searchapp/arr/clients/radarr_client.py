# 07.05.26

import logging
import requests
from itertools import count
from typing import Any, Dict, List, Optional

logger = logging.getLogger("ARR.RADARR")


class RadarrClient:
    """Native Radarr API v3 client with retry, timeout, and error handling."""

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
                logger.warning(f"Radarr request {method} {path} attempt {attempt}/{self.max_retries} failed: {exc}")

        logger.error(f"Radarr request {method} {path} failed after {self.max_retries} attempts")
        raise last_exc

    def _get(self, path: str, params: Optional[dict] = None) -> requests.Response:
        return self._request("GET", path, params=params)

    def _get_safe(self, path: str, params: Optional[dict] = None) -> List[Dict[str, Any]]:
        """GET that returns an empty list on any HTTP/network error (no retry)."""
        url = f"{self._base}{path}"
        try:
            resp = requests.get(url, params=params, headers=self._headers, timeout=self.timeout)
            if not resp.ok:
                logger.debug(f"Radarr {path} returned {resp.status_code}, treating as empty")
                return []
            return resp.json()
        except Exception as exc:
            logger.debug(f"Radarr safe GET {path} failed: {exc}")
            return []

    def _post(self, path: str, json_data: Optional[dict] = None) -> requests.Response:
        return self._request("POST", path, json=json_data)

    def _put(self, path: str, json_data: Optional[dict] = None) -> requests.Response:
        return self._request("PUT", path, json=json_data)

    # ── status ───────────────────────────────────────────

    def system_status(self) -> Dict[str, Any]:
        """Check Radarr connectivity and API key validity."""
        return self._get("/system/status").json()

    def is_available(self) -> bool:
        """Return True if Radarr is reachable."""
        try:
            self.system_status()
            return True
        except Exception:
            return False

    # ── wanted / missing ─────────────────────────────────

    def wanted_missing(self, page: int = 1, page_size: int = 20) -> Dict[str, Any]:
        """Get missing movies (paginated)."""
        return self._get("/wanted/missing", params={
            "pageSize": page_size,
            "page": page,
        }).json()

    def get_all_missing(self) -> List[Dict[str, Any]]:
        """Iterate all pages and return every missing movie record."""
        all_records: List[Dict[str, Any]] = []
        for page in count(1):
            data = self.wanted_missing(page=page)
            records = data.get("records", [])
            if not records:
                break
            all_records.extend(records)
        return all_records

    # ── movies ───────────────────────────────────────────

    def get_movies(self) -> List[Dict[str, Any]]:
        """Get all movies in Radarr."""
        return self._get("/movie").json()

    def get_movie_by_id(self, movie_id: int) -> Dict[str, Any]:
        """Get a single movie by ID."""
        return self._get(f"/movie/{movie_id}").json()

    def update_movie_path(self, movie_id: int, new_path: str) -> bool:
        """Update the root path of a movie so Radarr expects files there."""
        try:
            movie = self.get_movie_by_id(movie_id)
            if movie.get("path") == new_path:
                return True
            movie["path"] = new_path
            self._put(f"/movie/{movie_id}", json_data=movie)
            logger.info(f"Updated Radarr movie {movie_id} path to '{new_path}'")
            return True
        except Exception as exc:
            logger.error(f"Failed to update movie path: {exc}")
            return False

    def set_movie_unmonitored(self, movie_id: int) -> bool:
        """Mark a movie as unmonitored."""
        try:
            movie_data = self.get_movie_by_id(movie_id)
            movie_data["monitored"] = False
            self._put(f"/movie/{movie_id}", json_data=movie_data)
            return True
        except Exception as exc:
            logger.error(f"Failed to set movie {movie_id} unmonitored: {exc}")
            return False

    # ── queue ────────────────────────────────────────────

    def queue(self) -> Dict[str, Any]:
        return self._get("/queue", params={
            "includeUnknownMovieItems": False,
            "includeMovie": False,
        }).json()

    def is_movie_in_queue(self, movie_id: int) -> bool:
        """Check if a specific movie is already downloading."""
        try:
            records = self.queue().get("records", [])
            return any(r.get("movieId") == movie_id for r in records)
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
            logger.error(f"Failed to fetch Radarr tags: {exc}")
            return {}

    # ── commands ─────────────────────────────────────────

    def command_downloaded_movies_scan(self, path: str) -> Dict[str, Any]:
        """Tell Radarr to scan a folder for newly downloaded movies."""
        return self._post("/command", json_data={
            "name": "DownloadedMoviesScan",
            "path": path,
            "importMode": "Auto",
        }).json()

    def command_rescan_movie(self, movie_id: int) -> Dict[str, Any]:
        return self._post("/command", json_data={
            "name": "RescanMovie",
            "movieId": movie_id,
        }).json()

    # ── manual import ─────────────────────────────────

    def manual_import_lookup(self, folder_path: str, movie_id: Optional[int] = None) -> List[Dict[str, Any]]:
        """Get list of files available for manual import in a folder."""
        params: Dict[str, Any] = {"folder": folder_path, "filterExistingFiles": False}
        if movie_id:
            params["movieId"] = movie_id
        return self._get_safe("/manualimport", params=params)

    def manual_import(self, import_items: List[Dict[str, Any]]) -> Dict[str, Any]:
        """Submit manual import decisions to Radarr."""
        return self._post("/manualimport", json_data=import_items).json()
