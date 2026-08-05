# 22.02.25

import os
import logging
from typing import Any, Optional

from rich.console import Console

from VibraVid.core.decryptor import Decryptor, KeysManager
from VibraVid.core.ui.tracker import download_tracker


console = Console()
logger = logging.getLogger(__name__)


class PostDownloadDecryptor:

    @staticmethod
    def has_keys(key: Any) -> bool:
        """Return ``True`` when *key* contains at least one usable pair."""
        if key is None:
            return False
        if isinstance(key, KeysManager):
            return bool(key.get_keys_list())
        if isinstance(key, str):
            return bool(key.strip())
        if isinstance(key, (list, tuple)):
            return bool(key)
        return False

    def run(self, path: str, key: Any, download_id: Optional[str] = None) -> None:
        """Decrypt *path* in-place.  Logs and prints errors but never raises."""
        logger.info(f"PostDownloadDecryptor: probing {os.path.basename(path)} …")
        if download_id:
            download_tracker.update_status(download_id, "Decrypting ...")

        dec_path = path + ".dec"
        try:
            decryptor = Decryptor()
            mode, kid, *_rest = decryptor.detect_encryption(path)

            if mode is None:
                logger.info("PostDownloadDecryptor: file is not encrypted — skipping.")
                console.print("[dim]Keys provided but file is not encrypted — skipping decryption.")
                return

            logger.info(f"PostDownloadDecryptor: encryption found (mode={mode}, kid={kid}) — starting decryption.")

            if kid:
                self._warn_if_kid_missing(kid, key)

            ok = decryptor.decrypt(
                encrypted_path=path,
                keys=key,
                output_path=dec_path,
                stream_type="video",
                progress_cb=None,
            )

            if ok and os.path.exists(dec_path) and os.path.getsize(dec_path) > 0:
                try:
                    os.replace(dec_path, path)
                    logger.info(f"PostDownloadDecryptor: success → {os.path.basename(path)}")
                except Exception as exc:
                    logger.error(f"PostDownloadDecryptor: rename failed — {exc}")
                    console.print(f"[red]Decryption rename failed: {exc}")
                    self._remove(dec_path)

            else:
                logger.error(f"PostDownloadDecryptor: decryption failed for {os.path.basename(path)}")
                console.print(f"[red]Decryption failed for {os.path.basename(path)}")
                self._remove(dec_path)

        except Exception as exc:
            logger.error(f"PostDownloadDecryptor: unexpected error — {exc}")
            console.print(f"[red]Decryption error: {exc}")
            self._remove(dec_path)

    @staticmethod
    def _remove(path: str) -> None:
        try:
            if os.path.exists(path):
                os.remove(path)
        except Exception:
            pass

    @staticmethod
    def _warn_if_kid_missing(kid: str, key: Any) -> None:
        """Emit a warning when the probed KID is not among the provided keys."""
        kid_norm = kid.replace("-", "").lower()
        found    = False

        if isinstance(key, KeysManager):
            found = any(
                k.kid.replace("-", "").lower() == kid_norm
                for k in (key.get_keys_list() or [])
                if hasattr(k, "kid")
            )

        elif isinstance(key, str):
            # Accepts "kid:key" or "kid:key|kid:key|..." formats
            found = any(
                pair.split(":")[0].replace("-", "").lower() == kid_norm
                for pair in key.strip().split("|")
                if ":" in pair
            )

        elif isinstance(key, (list, tuple)):
            for pair in key:
                raw = pair if isinstance(pair, str) else str(pair)
                if raw.split(":")[0].replace("-", "").lower() == kid_norm:
                    found = True
                    break

        if not found:
            logger.warning(f"PostDownloadDecryptor: KID [{kid}] from probe not found among provided keys — decryption may fail.")
            console.print(f"[yellow]Warning:[/yellow] KID [cyan]{kid}[/cyan] missing — decryption may fail.")