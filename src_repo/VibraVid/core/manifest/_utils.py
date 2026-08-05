# 09.05.26

from urllib.parse import urlparse
from pathlib import Path


def calc_base_url(url: str) -> str:
    """Strip the filename from a manifest URL, returning base with trailing slash."""
    p = urlparse(url)
    path = p.path.rsplit("/", 1)[0]
    return f"{p.scheme}://{p.netloc}{path}/"


def save_raw_manifest(raw_content: str, directory, filename: str):
    """Save raw manifest text to ``{directory}/{filename}``."""
    path = Path(directory) / filename
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(raw_content or "", encoding="utf-8")
    return path
