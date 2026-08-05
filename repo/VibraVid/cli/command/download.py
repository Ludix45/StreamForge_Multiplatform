# 10.12.25

import logging
from typing import Optional

from rich.console import Console

from VibraVid.utils import config_manager
from VibraVid.core.drm.system import DRMType
from VibraVid.core.downloader._detect import (detect_stream_type, parse_headers, parse_keys, derive_output_path)

logger = logging.getLogger(__name__)
console = Console()


def handle_direct_download(args) -> bool:
    """
    Execute a direct URL download when --down is passed.
    Returns True if handled (caller should return immediately), False otherwise.
    """
    url: Optional[str] = getattr(args, 'down', None)
    if not url:
        return False

    url = url.strip()
    headers   = parse_headers(getattr(args, 'headers', None))
    keys      = parse_keys(getattr(args, 'key', None))
    output    = (getattr(args, 'output', None) or '').strip() or None
    lic_url   = (getattr(args, 'license_url', None) or '').strip() or None
    lic_hdr   = parse_headers(getattr(args, 'license_headers', None))
    drm_pref  = (getattr(args, 'drm', None) or 'auto').strip().lower()

    # Map DRM string to DRMType constant (or None if not recognized)
    drm_choice = None
    if drm_pref in ('widevine', 'wv', DRMType.WIDEVINE):
        drm_choice = DRMType.WIDEVINE
    elif drm_pref in ('playready', 'pr', DRMType.PLAYREADY):
        drm_choice = DRMType.PLAYREADY

    # Build output path
    EXTENSION_OUTPUT = config_manager.config.get("PROCESS", "extension")
    output = derive_output_path(url, output, EXTENSION_OUTPUT)

    # Normalise key arg: single string → one-element list kept as list for
    # the segment-based downloaders; MP4 doesn't use keys so it's ignored.
    key_arg = keys

    url_type = detect_stream_type(url)
    console.print(
        f"\n[cyan]  Direct download[/cyan] — detected [bold]{url_type.upper()}[/bold]\n" +
        (f"\n  [dim]drm :[/dim] {drm_pref}" if lic_url or keys else "")
    )

    # Lazy import to avoid circular dependency
    from VibraVid.core.downloader import MP4_Downloader, HLS_Downloader, DASH_Downloader, ISM_Downloader

    try:
        if url_type == 'mp4':
            path, cancelled, error = MP4_Downloader(
                url=url,
                path=output,
                headers_=headers or None,
            )

            if error:
                logger.error(f"MP4 download error: {error}")
                console.print(f"[red]Download error: {error}")
                return True

        elif url_type == 'hls':
            dl = HLS_Downloader(
                m3u8_url=url,
                headers=headers or None,
                license_url=lic_url,
                license_headers=lic_hdr or None,
                output_path=output,
                drm_preference=drm_choice or DRMType.WIDEVINE,
                key=key_arg,
            )
            path, cancelled, error = dl.start()

            if error:
                logger.error(f"HLS download error: {error}")
                console.print(f"[red]Download error: {error}")
                return True

        elif url_type == 'dash':
            effective_drm = drm_choice or DRMType.WIDEVINE
            dl = DASH_Downloader(
                mpd_url=url,
                mpd_headers=headers or None,
                license_url=lic_url,
                license_headers=lic_hdr or None,
                output_path=output,
                drm_preference=effective_drm,
                key=key_arg,
            )
            path, cancelled, error = dl.start()

            if error:
                logger.error(f"DASH download error: {error}")
                console.print(f"[red]Download error: {error}")
                return True

        elif url_type == 'ism':
            effective_drm = drm_choice or DRMType.PLAYREADY
            dl = ISM_Downloader(
                ism_url=url,
                headers=headers or None,
                license_url=lic_url,
                license_headers=lic_hdr or None,
                output_path=output,
                drm_preference=effective_drm,
                key=key_arg,
            )
            path, cancelled, error = dl.start()
            
            if error:
                logger.error(f"ISM download error: {error}")
                console.print(f"[red]Download error: {error}")
                return True

        else:
            console.print(f"[red]Unknown stream type: {url_type}")
            return True

    except Exception as exc:
        logger.exception(f"Direct download failed: {exc}")
        console.print(f"[red]Download error: {exc}")
        return True

    if cancelled:
        console.print("[yellow]Download cancelled.")
    elif path:
        console.print(f"\n[green]Saved → {path}")
    else:
        console.print("[red]Download failed.")

    return True
