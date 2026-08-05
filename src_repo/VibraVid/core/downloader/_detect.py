# 09.06.26


import logging
from pathlib import Path
from typing import Optional
from urllib.parse import urlparse

logger = logging.getLogger(__name__)


def detect_stream_type(url: str) -> str:
    """
    Guess the stream type from the URL path.
    Falls back to a HEAD request when the extension is ambiguous.

    Returns: 'mp4' | 'hls' | 'dash' | 'ism'
    """
    clean = url.lower().split('?')[0].rstrip('/')

    if clean.endswith(('.mpd', '.mpp')):
        return 'dash'
    if clean.endswith('.ism') or clean.endswith('.ism/manifest'):
        return 'ism'
    if clean.endswith(('.m3u8', '.m3u')):
        return 'hls'
    if clean.endswith(('.mp4', '.mkv', '.avi', '.mov', '.webm', '.ts', '.m4v')):
        return 'mp4'

    # Ambiguous extension: probe with a HEAD request
    try:
        from VibraVid.utils.http_client import create_client
        with create_client(timeout=10, follow_redirects=True) as client:
            resp = client.head(url)
            ct = (resp.headers.get('content-type') or '').lower()

        if 'mpd' in ct or 'dash' in ct:
            return 'dash'
        if 'mpegurl' in ct or 'm3u8' in ct:
            return 'hls'
        if 'mp4' in ct or 'video' in ct or 'octet-stream' in ct:
            return 'mp4'
        if 'silverlight' in ct or 'ism' in ct:
            return 'ism'
    
    except Exception as exc:
        logger.debug(f"HEAD probe failed for type detection: {exc}")

    # Last resort: assume HLS (most common streaming format)
    logger.warning(f"Could not detect stream type for URL, defaulting to HLS: {url}")
    return 'hls'


def parse_headers(headers_list: Optional[list]) -> dict:
    """
    Convert ['Key: Value', 'Key2:Value2', ...] into a plain dict.
    Both 'Key: Value' and 'Key:Value' are accepted.
    """
    result = {}
    for entry in (headers_list or []):
        if ':' in entry:
            k, v = entry.split(':', 1)
            result[k.strip()] = v.strip()
        else:
            logger.warning(f"Ignoring malformed header entry (expected 'Key:Value'): {entry!r}")
    return result


def parse_keys(key_list: Optional[list]) -> Optional[list]:
    """
    Return a list of 'kid:key' strings or None if empty.
    Each element comes from one --key argument.
    """
    if not key_list:
        return None
    cleaned = [k.strip() for k in key_list if k.strip()]
    return cleaned if cleaned else None


def derive_output_path(url: str, output: Optional[str], extension: str) -> str:
    """
    Build a final output path: derive a stem from the URL when *output* is empty,
    and append the configured *extension* when no suffix is present.
    """
    output = (output or "").strip()
    if not output:
        url_path = urlparse(url).path.rstrip('/')
        stem = Path(url_path).stem or 'download'
        return f"{stem}.{extension}"
    if not Path(output).suffix:
        return f"{output}.{extension}"
    return output