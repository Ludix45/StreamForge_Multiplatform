# 16.04.24

from typing import List, Optional


_CODEC_TO_EXT: dict[str, str] = {
    'aac':        'm4a',
    'libmp3lame': 'mp3',
    'flac':       'flac',
    'libopus':    'opus',
    'libvorbis':  'ogg',
    'alac':       'm4a',
}

_AUDIO_CODEC_EXT = {
    "flac": "flac",
    "alac": "m4a",
    "aac": "m4a",
    "mp4a": "m4a",
    "aac_latm": "m4a",
    "ac3": "ac3",
    "ac-3": "ac3",
    "eac3": "eac3",
    "ec-3": "eac3",
    "opus": "opus",
    "vorbis": "ogg",
    "mp3": "mp3",
    "mp3float": "mp3",
    "dts": "dts",
    "dca": "dts",
    "pcm_s16le": "wav",
    "pcm_s24le": "wav",
}


def audio_ext_for_codec(codec: str) -> Optional[str]:
    """Mappa un codec audio (es. 'flac') all'estensione container nativa ('flac')."""
    if not codec:
        return None
    return _AUDIO_CODEC_EXT.get(codec.strip().lower())


def _detect_output_ext(ffmpeg_params: List[str], fallback_ext: str) -> str:
    """
    Derive the output file extension from the -c:a codec in ffmpeg_params.
    Falls back to fallback_ext if -c:a is absent or unrecognised.
    """
    try:
        idx = ffmpeg_params.index('-c:a') + 1
        codec = ffmpeg_params[idx]
        return _CODEC_TO_EXT.get(codec, fallback_ext)
    except (ValueError, IndexError):
        return fallback_ext