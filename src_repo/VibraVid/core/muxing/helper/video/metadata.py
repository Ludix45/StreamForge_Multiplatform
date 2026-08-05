# 16.04.24

import json
import logging
import subprocess

from VibraVid.setup import get_ffprobe_path
from VibraVid.core.utils.codec import get_short_codec


logger = logging.getLogger(__name__)


def get_media_metadata(file_path: str) -> dict:
    """
    Extract quality (resolution), languages, and codecs from a media file using ffprobe.

    Returns:
        dict: {'quality': str, 'language': str, 'video_codec': str, 'audio_codec': str}
    """
    cmd = [get_ffprobe_path(), '-v', 'error', '-show_streams', '-print_format', 'json', file_path]

    try:
        result = subprocess.run(cmd, capture_output=True, text=True, check=False)
        if result.returncode != 0:
            logger.error(f"ffprobe error while extracting metadata for file {file_path}: {result.stderr.strip()}")
            return {"quality": "", "language": "", "video_codec": "", "audio_codec": ""}

        info = json.loads(result.stdout)
        streams = info.get('streams', [])
        quality_val = ""
        vcodec_val = ""

        for s in streams:
            if s.get('codec_type') == 'video':
                height = s.get('height')
                if height:
                    if height >= 2160:
                        quality_val = "2160p"
                    elif height >= 1440:
                        quality_val = "1440p"
                    elif height >= 1080:
                        quality_val = "1080p"
                    elif height >= 720:
                        quality_val = "720p"
                    elif height >= 480:
                        quality_val = "480p"
                    else:
                        quality_val = f"{height}p"

                raw_vcodec = s.get('codec_name', '')
                vcodec_val = get_short_codec("video", raw_vcodec)
                break

        languages_found = []
        acodecs_found = []
        for s in streams:
            if s.get('codec_type') == 'audio':
                lang = s.get('tags', {}).get('language')
                if lang:
                    lang = lang.upper()
                    if lang not in languages_found:
                        languages_found.append(lang)

                raw_acodec = s.get('codec_name', '')
                short_acodec = get_short_codec("audio", raw_acodec)
                if short_acodec and short_acodec not in acodecs_found:
                    acodecs_found.append(short_acodec)

        return {
            "quality": quality_val,
            "language": "-".join(languages_found) if languages_found else "",
            "video_codec": vcodec_val,
            "audio_codec": "-".join(acodecs_found) if acodecs_found else ""
        }

    except Exception:
        return {"quality": "", "language": "", "video_codec": "", "audio_codec": ""}