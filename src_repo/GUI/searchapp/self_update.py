# In-app self-update logic.

import os
import sys
import json
import shutil
import logging
import threading
import subprocess

from django.conf import settings

logger = logging.getLogger(__name__)

DOCKER_SOCKET = "/var/run/docker.sock"


def _is_docker() -> bool:
    if os.path.exists("/.dockerenv"):
        return True
    try:
        with open("/proc/1/cgroup", "rt") as fh:
            data = fh.read()
        return "docker" in data or "containerd" in data or "kubepods" in data
    except Exception:
        return False


def detect_mode() -> str:
    """Return one of: 'installer', 'docker', 'source'."""
    try:
        from VibraVid.setup import get_is_binary_installation
        if get_is_binary_installation():
            return "installer"
    except Exception:
        pass

    if _is_docker():
        return "docker"

    return "source"


def _self_container_id() -> str | None:
    """Best-effort discovery of the current container's id."""
    cid = os.environ.get("HOSTNAME")
    if cid:
        return cid
    try:
        with open("/proc/self/cgroup", "rt") as fh:
            for line in fh:
                parts = line.strip().split("/")
                for part in reversed(parts):
                    if len(part) >= 12 and all(c in "0123456789abcdef" for c in part):
                        return part
    except Exception:
        pass
    return None


def _remap_under(workdir: str, path: str, mount: str) -> str:
    """Map a host path under workdir to its location under the helper mount.

    Works for both POSIX (/opt/vibravid/...) and Windows (C:\\Users\\...) host
    paths: we strip the workdir prefix, normalise separators to POSIX and
    re-root under ``mount`` (e.g. /project/docker-compose.yml).
    """
    w = workdir.rstrip("\\/")
    if path.startswith(w):
        rel = path[len(w):]
    else:
        rel = "/" + os.path.basename(path.replace("\\", "/"))
    rel = rel.replace("\\", "/").lstrip("/")
    return f"{mount}/{rel}" if rel else mount


def _update_docker() -> dict:
    if not os.path.exists(DOCKER_SOCKET):
        return {
            "success": False,
            "needs_manual": True,
            "message": (
                "Docker socket non montato. Aggiungi "
                "'- /var/run/docker.sock:/var/run/docker.sock' ai volumes del "
                "container e ricrealo, oppure aggiorna a mano con "
                "'docker compose pull && docker compose up -d'."
            ),
        }

    docker = shutil.which("docker")
    if not docker:
        return {"success": False, "needs_manual": True,
                "message": "CLI 'docker' non disponibile nel container."}

    cid = _self_container_id()
    if not cid:
        return {"success": False,
                "message": "Impossibile determinare il container corrente."}

    # Read compose labels + image ref of the running container.
    try:
        raw = subprocess.check_output(
            [docker, "inspect", "--format", "{{json .Config.Labels}}\n{{.Config.Image}}", cid],
            text=True, timeout=20, stderr=subprocess.STDOUT,
        )
    except subprocess.CalledProcessError as exc:
        msg = (exc.output or "").strip()
        if "permission denied" in msg.lower():
            return {"success": False, "needs_manual": True, "message": "Permesso negato sul socket Docker: l'utente del container non è nel gruppo del socket."}
        return {"success": False, "message": f"docker inspect fallito: {msg}"}
    except Exception as exc:
        return {"success": False, "message": f"docker inspect fallito: {exc}"}

    labels_line, _, image_ref = raw.partition("\n")
    image_ref = image_ref.strip()
    try:
        labels = json.loads(labels_line) or {}
    except Exception:
        labels = {}

    project = labels.get("com.docker.compose.project")
    workdir = labels.get("com.docker.compose.project.working_dir")
    config_files = labels.get("com.docker.compose.project.config_files")
    if not project or not workdir or not config_files:
        return {
            "success": False,
            "needs_manual": True,
            "message": ("Il container non è stato avviato con docker compose; aggiorna a mano con 'docker compose pull && docker compose up -d'."),
        }
    if not image_ref:
        return {"success": False,
                "message": "Impossibile determinare l'immagine del container."}

    project_mount = "/project"
    targets = [
        _remap_under(workdir, cf.strip(), project_mount)
        for cf in config_files.split(",") if cf.strip()
    ]
    if not targets:
        return {"success": False, "message": "Nessun file compose individuato."}

    cfg_flags = " ".join(f"-f '{t}'" for t in targets)
    compose = (f"docker compose -p '{project}' "f"--project-directory '{project_mount}' {cfg_flags}")
    script = f"sleep 2; {compose} pull && {compose} up -d --remove-orphans"

    helper = [
        docker, "run", "-d", "--rm",
        "-v", f"{DOCKER_SOCKET}:{DOCKER_SOCKET}",
        "--mount", f"type=bind,source={workdir},target={project_mount}",
        "-w", project_mount,
        "--entrypoint", "sh",
        image_ref,
        "-c", script,
    ]
    try:
        subprocess.run(helper, check=True, timeout=30, capture_output=True, text=True)
    except subprocess.CalledProcessError as exc:
        return {"success": False, "message": f"Avvio updater fallito: {(exc.stderr or exc.stdout or '').strip()}"}
    except Exception as exc:
        return {"success": False, "message": f"Avvio updater fallito: {exc}"}

    logger.info("Docker self-update helper launched (image=%s)", image_ref)
    return {"success": True, "message": "Aggiornamento avviato: pull dell'immagine e ricreazione del container in corso."}


def _update_installer() -> dict:
    try:
        from VibraVid.upload.update import auto_update
    except Exception as exc:
        return {"success": False, "message": f"auto_update non disponibile: {exc}"}

    # auto_update() downloads the new executable and calls sys.exit() to
    # relaunch; run it detached so we can still answer the HTTP request.
    threading.Thread(target=auto_update, daemon=True).start()
    return {"success": True, "message": "Download dell'aggiornamento avviato; l'app si riavvierà."}


def _repo_root() -> str | None:
    path = os.path.abspath(str(getattr(settings, "BASE_DIR", os.getcwd())))
    for _ in range(6):
        if os.path.isdir(os.path.join(path, ".git")):
            return path
        parent = os.path.dirname(path)
        if parent == path:
            break
        path = parent
    return None


def _delayed_reexec() -> None:
    import time
    time.sleep(2)
    os.execv(sys.executable, [sys.executable] + sys.argv)


def _update_source() -> dict:
    git = shutil.which("git")
    repo = _repo_root()
    if not git or not repo:
        return {"success": False, "needs_manual": True, "message": "Installazione da sorgente senza git: aggiorna a mano."}

    # No remote -> nothing to pull (common for source downloads).
    try:
        remotes = subprocess.check_output(
            [git, "-C", repo, "remote"], text=True, timeout=15).strip()
    except Exception as exc:
        return {"success": False, "message": f"git remote fallito: {exc}"}
    if not remotes:
        return {
            "success": False,
            "needs_manual": True,
            "message": ("Nessun remote git configurato. Aggiungilo con 'git remote add origin <url>' oppure aggiorna a mano."),
        }

    try:
        out = subprocess.run(
            [git, "-C", repo, "pull", "--ff-only"],
            check=True, timeout=180, capture_output=True, text=True)
    except subprocess.CalledProcessError as exc:
        return {"success": False,
                "message": f"git pull fallito: {(exc.stderr or exc.stdout or '').strip()}"}
    except Exception as exc:
        return {"success": False, "message": f"git pull fallito: {exc}"}

    logger.info("Source update: %s", (out.stdout or "").strip())
    threading.Thread(target=_delayed_reexec, daemon=True).start()
    return {"success": True, "message": "Codice aggiornato; riavvio del processo in corso."}


def perform_update() -> dict:
    mode = detect_mode()
    logger.info("Self-update requested (mode=%s)", mode)
    if mode == "installer":
        return _update_installer()
    if mode == "docker":
        return _update_docker()
    return _update_source()