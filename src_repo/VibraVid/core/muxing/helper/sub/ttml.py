# 16.04.24

import os
import re
import logging
import xml.etree.ElementTree as et
from typing import Optional, List
from pathlib import Path

from rich.console import Console
import ttconv.imsc.reader as imsc_reader
import ttconv.srt.writer as srt_writer
import ttconv.vtt.writer as vtt_writer
from ttconv.srt.config import SRTWriterConfiguration
from ttconv.vtt.config import VTTWriterConfiguration

from .sanitize import sanitize_srt_file, sanitize_vtt_file


# suppress ttconv logging (Merging ISD paragraphs/regions)
logging.getLogger("ttconv").setLevel(logging.WARNING)

console = Console()
logger = logging.getLogger(__name__)
_XML10_INVALID = re.compile('[\x00-\x08\x0B\x0C\x0E-\x1F￾￿]')


def _get_declared_xml_encoding(block: bytes) -> Optional[str]:
    """Extract encoding from XML declaration if present."""
    try:
        head = block[:256].decode('ascii', errors='ignore')
        match = re.search(r'<\?xml[^>]*encoding=["\']([^"\']+)["\']', head, re.IGNORECASE)
        if match:
            return match.group(1).strip()
    except Exception:
        pass
    return None


def _sanitize_xml(s: str) -> str:
    """Remove characters that are invalid in XML 1.0."""
    return _XML10_INVALID.sub('', s)


def _decode_ttml_block(block: bytes) -> str:
    """Decode TTML block with declared encoding first, then safe fallbacks."""
    candidates: List[str] = []
    declared = _get_declared_xml_encoding(block)
    if declared:
        candidates.append(declared)

    candidates.extend([
        'utf-8-sig',
        'utf-8',
        'utf-16',
        'utf-16-le',
        'utf-16-be',
        'cp1252',
        'latin-1',
    ])

    tried = set()
    for encoding in candidates:
        if encoding in tried:
            continue
        tried.add(encoding)
        try:
            decoded = block.decode(encoding)

            # A TTML block must start with '<' after stripping any BOM.
            # Skip encodings that mangle the start (e.g. UTF-16 turning '<tt' into CJK).
            stripped_start = decoded.lstrip('﻿￾')
            if stripped_start and stripped_start[0] != '<':
                continue

            logger.debug(f"Decoded TTML block with encoding: {encoding}")
            return decoded
        except Exception:
            logger.debug(f"Failed to decode TTML block with encoding: {encoding}")
            continue

    raise UnicodeDecodeError('utf-8', block, 0, min(len(block), 1), 'could not decode TTML block with supported encodings')


def convert_ttml_to_format(ttml_path: str, output_path: Optional[str] = None, target_format: str = 'srt') -> bool:
    """
    Convert TTML file or .m4s fragment containing TTML to SRT or VTT format.

    Args:
        ttml_path (str): Path to the TTML or .m4s file.
        output_path (Optional[str]): Path where to save the converted file. If None, uses same name as ttml_path but with target extension.
        target_format (str): The target format ('srt' or 'vtt').

    Returns:
        bool: True if conversion was successful, False otherwise.
    """
    if not os.path.exists(ttml_path):
        console.print(f"[red]File {ttml_path} does not exist")
        return False

    target_format = target_format.lower()
    if target_format not in ['srt', 'vtt']:
        console.print(f"[red]Unsupported target format for TTML conversion: {target_format}")
        return False

    if output_path is None:
        output_path = str(Path(ttml_path).with_suffix(f'.{target_format}'))

    try:
        with open(ttml_path, 'rb') as f:
            data = f.read()

        # Extract TTML blocks from plain XML or fragmented MP4 payloads.
        # Supports both XML declaration-prefixed documents and raw <tt> blocks.
        raw_blocks = re.findall(
            br'(?:<\?xml[^>]*\?>\s*)?<tt\b.*?</tt>',
            data,
            re.DOTALL,
        )

        # Discard binary false-positives: real TTML XML is valid UTF-8; binary
        # MP4 box data that accidentally contains <tt...>...</tt> bytes is not.
        ttml_blocks = []
        for blk in raw_blocks:
            try:
                blk.decode('utf-8')
                ttml_blocks.append(blk)
            except UnicodeDecodeError:
                logger.debug(f"Discarding non-UTF-8 block that matched TTML pattern in {os.path.basename(ttml_path)}")
                pass

        if not ttml_blocks:
            # Try to see if it's a plain TTML without the XML declaration or just one block
            try:
                text_content = data.decode('utf-8', errors='ignore')
                if '<tt' in text_content and '</tt>' in text_content:
                    match = re.search(r'<tt.*?</tt>', text_content, re.DOTALL)
                    if match:
                        ttml_blocks = [match.group(0).encode('utf-8')]
            except Exception:
                pass

        if not ttml_blocks:
            console.print(f"[red]No valid TTML blocks found in {ttml_path}")
            return False

        all_captions: List[str] = []

        for index, block in enumerate(ttml_blocks, start=1):
            try:
                # Decode and parse TTML
                ttml_str = _sanitize_xml(_decode_ttml_block(block))
                root = et.fromstring(ttml_str)
                tree = et.ElementTree(root)

                # Convert TTML to internal model
                model = imsc_reader.to_model(tree)

                if model is not None:
                    if target_format == 'srt':
                        srt_config = SRTWriterConfiguration()
                        content = srt_writer.from_model(model, srt_config)
                    else:  # vtt
                        vtt_config = VTTWriterConfiguration()
                        content = vtt_writer.from_model(model, vtt_config)

                    if content.strip():
                        all_captions.append(content.strip())

            except Exception as e:
                console.print(f"[yellow]Warning: Failed to process TTML block {index}/{len(ttml_blocks)}: {e}")
                continue

        if not all_captions:
            console.print(f"[red]No valid TTML blocks processed from {ttml_path}")
            return False

        # Combine output
        delimiter = "\n\n" if target_format == 'srt' else "\n"
        output_content = delimiter.join(all_captions)

        # Add VTT header if needed
        if target_format == 'vtt' and not output_content.startswith("WEBVTT"):
            output_content = "WEBVTT\n\n" + output_content

        with open(output_path, 'w', encoding='utf-8') as f:
            f.write(output_content)

        # Sanitize based on format
        if target_format == 'srt':
            sanitize_srt_file(output_path)
        elif target_format == 'vtt':
            sanitize_vtt_file(output_path)

        logger.info(f"convert_ttml_to_format: Successfully converted {os.path.basename(ttml_path)} to {target_format.upper()} with {len(ttml_blocks)} TTML blocks processed")
        return True

    except Exception as e:
        console.print(f"[red]Error during TTML to {target_format.upper()} conversion: {e}")
        return False


def extract_srt_from_m4s(m4s_file_path: str, output_srt_path: Optional[str] = None) -> str:
    """Compatibility wrapper for the user requested function name."""
    if convert_ttml_to_format(m4s_file_path, output_srt_path):
        if output_srt_path is None:
            output_srt_path = str(Path(m4s_file_path).with_suffix('.srt'))
        with open(output_srt_path, 'r', encoding='utf-8') as f:
            return f.read()
    else:
        raise ValueError("Failed to extract SRT from m4s")