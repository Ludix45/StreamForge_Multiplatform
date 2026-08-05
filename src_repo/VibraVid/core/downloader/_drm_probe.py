# 22.02.25

import logging
from typing import Optional

from rich.console import Console

from VibraVid.core.decryptor._mp4_inspector import parse_binary
from VibraVid.core.drm.system import KNOWN_DRM_SYSTEMS
from VibraVid.utils.os import os_manager

console = Console()
logger = logging.getLogger(__name__)
PROBE_BYTES = 4 * 1024 * 1024  # 4 MB


class DRMProbe:
    def __init__(self, known_systems: Optional[dict] = None) -> None:
        self._systems: dict[str, str] = (known_systems if known_systems is not None else KNOWN_DRM_SYSTEMS)

    def probe(self, url: str, headers: dict, client) -> tuple:
        """
        Returns ``(encrypted: bool, scheme: str | None, drm_names: list[str])``.
        Never raises — all errors are caught and logged at DEBUG level.
        """
        try:
            raw = self._fetch_bytes(url, headers, client)
            if not raw:
                return False, None, []

            info = self._parse_bytes(raw)
            if not info.encrypted:
                logger.debug("DRMProbe: no encryption markers found in first 4 MB.")
                return False, None, []

            drm_names = self._resolve_drm_names(info.pssh_boxes)
            self._report(info.scheme, info.kid, drm_names)
            return True, info.scheme, drm_names

        except Exception as exc:
            logger.debug(f"DRMProbe failed (non-fatal): {exc}")
            return False, None, []

    def _fetch_bytes(self, url: str, headers: dict, client) -> Optional[bytes]:
        probe_headers = {**headers, "Range": f"bytes=0-{PROBE_BYTES - 1}"}
        resp = client.get(url, headers=probe_headers, timeout=15)

        if resp.status_code not in (200, 206):
            logger.debug(f"DRMProbe: unexpected status {resp.status_code} — skipping.")
            return None

        raw = resp.content[:PROBE_BYTES]
        return raw if raw else None

    @staticmethod
    def _parse_bytes(raw: bytes):
        """Write *raw* to a temp file, run ``parse_binary``, then delete."""
        with os_manager.temp_binary_file(raw, suffix=".mp4probe") as tmp_path:
            return parse_binary(tmp_path)

    def _resolve_drm_names(self, pssh_boxes: list) -> list:
        """Given a list of PSSH boxes, return a list of known DRM system names (if any) and log any unknown system IDs."""
        known_names: list[str] = []
        unknown_ids: list[str] = []
        seen_known: set[str] = set()
        seen_unknown: set[str] = set()

        for box in pssh_boxes:
            sid = box.get("system_id", "").replace(" ", "").lower()
            if not sid:
                continue

            name = self._systems.get(sid)
            if name:
                if name not in seen_known:
                    seen_known.add(name)
                    known_names.append(name)
                continue

            if sid not in seen_unknown:
                seen_unknown.add(sid)
                unknown_ids.append(sid)

        if unknown_ids:
            logger.debug(f"DRMProbe: unknown system_id(s): {', '.join(unknown_ids)}")

        if known_names and unknown_ids:
            return [*known_names, f"Unknown SID x{len(unknown_ids)}"]
        
        if known_names:
            return known_names
        
        if unknown_ids:
            return [f"Unknown ({sid[:8]}...)" for sid in unknown_ids]
        return ["Unknown"]

    @staticmethod
    def _report(scheme: Optional[str], kid: Optional[str], drm_names: list) -> None:
        label = ", ".join(drm_names) if drm_names else "unknown DRM"
        logger.info(f"DRMProbe: encryption detected — scheme={scheme or 'unknown'}, kid={kid or 'n/a'}, DRM=[{label}]")
        console.print(f"[cyan]Probe: [magenta]Encrypted [white]| [cyan]scheme: [magenta]{scheme or 'unknown'} [white]| [cyan]DRM: [magenta]{label}")