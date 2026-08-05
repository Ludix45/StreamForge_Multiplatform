# 16.12.25

from typing import Any, Optional

from .dash import DASH_Downloader
from .hls import HLS_Downloader
from .ism import ISM_Downloader
from .mp4 import MP4_Downloader
from ._generic import Generic_Downloader
from ._detect import detect_stream_type


__all__ = [
    "DASH_Downloader",
    "HLS_Downloader",
    "ISM_Downloader",
    "MP4_Downloader",
    "Generic_Downloader",
    "detect_stream_type",
    "download",
]


def download(
    url: str, output_path: Optional[str] = None, *, headers: Optional[dict] = None,
    key: Any = None, license_url: Optional[str] = None, license_headers: Optional[dict] = None, license_certificate: Optional[str] = None, drm_preference: Optional[str] = None,
    cookies: Optional[dict] = None,
    max_segments: Optional[int] = None, max_time: Any = None,
    manifest_content: Optional[str] = None, manifest_refresh_fn: Any = None,
    other_tracks: Optional[list] = None,
    **extra: Any,
) -> tuple:
    """
    Unified one-shot download entry point. Auto-detects the protocol from *url* and returns ``(output_path, need_stop, error)``.
    """
    try:
        from VibraVid.utils import setup_logger
        setup_logger()
    except Exception:
        pass

    stype = detect_stream_type(url)

    if stype == "mp4":
        from VibraVid.utils import config_manager
        from ._detect import derive_output_path

        ext = config_manager.config.get("PROCESS", "extension")
        path = output_path or derive_output_path(url, None, ext)
        mp4_extra = {
            k: extra[k]
            for k in ("referer", "label", "max_percentage", "download_id", "site_name")
            if k in extra
        }
        return MP4_Downloader(url=url, path=path, headers_=headers, key=key, **mp4_extra)

    common = dict(
        output_path=output_path,
        license_url=license_url,
        license_headers=license_headers,
        license_certificate=license_certificate,
        key=key,
        cookies=cookies,
        max_segments=max_segments,
        max_time=max_time,
        other_tracks=other_tracks,
        manifest_refresh_fn=manifest_refresh_fn,
    )
    if drm_preference is not None:
        common["drm_preference"] = drm_preference

    if stype == "hls":
        dl = HLS_Downloader(m3u8_url=url, m3u8_content=manifest_content, headers=headers, **common, **extra)
    elif stype == "ism":
        dl = ISM_Downloader(ism_url=url, ism_content=manifest_content, headers=headers, **common, **extra)
    elif stype == "dash":
        dl = DASH_Downloader(mpd_url=url, mpd_content=manifest_content, mpd_headers=headers, **common, **extra)
    else:
        raise ValueError(f"Unsupported stream type detected: {stype!r}")

    return dl.start()