# 16.04.24

import os
import re
import logging


logger = logging.getLogger(__name__)


def _clean_srt_tag(m: re.Match) -> str:
    """Return the tag unchanged if it is an allowed bare SRT tag, else remove it."""
    tag = m.group(2).lower()
    attrs = (m.group(3) or '').strip()
    if tag in {'i', 'b', 'u', 's'} and not attrs:
        return m.group(0)
    return ''


def sanitize_srt_file(subtitle_path: str) -> str:
    """Sanitize SRT subtitle files by removing invalid HTML tags. SRT only allows <i>, <b>, <u>, <s> without attributes."""
    try:
        with open(subtitle_path, 'r', encoding='utf-8', errors='replace') as f:
            content = f.read()

        logger.info(f"Sanitizing SRT: {os.path.basename(subtitle_path)}")
        sanitized_content = re.compile(r'<(/?)([a-zA-Z][a-zA-Z0-9]*)(\s[^>]*)?>').sub(_clean_srt_tag, content)

        if sanitized_content != content:
            with open(subtitle_path, 'w', encoding='utf-8') as f:
                f.write(sanitized_content)
            logger.info(f"SRT sanitized: {os.path.basename(subtitle_path)}")

        return subtitle_path
    except Exception as e:
        logger.error(f"Could not sanitize SRT file {os.path.basename(subtitle_path)}: {str(e)}")
        return subtitle_path


def sanitize_vtt_file(subtitle_path: str) -> str:
    """Sanitize VTT subtitle files by replacing unmatched '<' symbols with '-'."""
    try:
        with open(subtitle_path, 'r', encoding='utf-8', errors='replace') as f:
            content = f.read()

        # Replace unmatched '<' symbols (not followed by closing '>') with '- '
        logger.info(f"Sanitizing VTT: {os.path.basename(subtitle_path)}")
        sanitized_content = re.sub(r'<(?![^>]*>)', '- ', content)

        if sanitized_content != content:
            with open(subtitle_path, 'w', encoding='utf-8') as f:
                f.write(sanitized_content)
            logger.info(f"VTT sanitized: {os.path.basename(subtitle_path)}")

        return subtitle_path
    except Exception as e:
        logger.error(f"Could not sanitize VTT file {os.path.basename(subtitle_path)}: {str(e)}")
        return subtitle_path