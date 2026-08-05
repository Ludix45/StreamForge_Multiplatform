# 06.06.25

from django.apps import AppConfig


class SearchappConfig(AppConfig):
    default_auto_field = "django.db.models.BigAutoField"
    name = "searchapp"

    def ready(self) -> None:

        # Initialize the logger for the application
        try:
            from VibraVid.utils.logger import setup_logger

            setup_logger()
        except Exception as exc:
            print(f"[Logger] Failed to initialize: {exc}")

        # Start the auto loop for the watchlist
        try:
            from .watchlist_auto import start_watchlist_auto_loop

            start_watchlist_auto_loop()
        except Exception as exc:
            print(f"[WatchlistAuto] Failed to start: {exc}")

        # Start the auto loop for ARR (Automatic Release Recognition)
        try:
            from .arr_auto import start_arr_auto_loop

            start_arr_auto_loop()
        except Exception as exc:
            print(f"[ARR] Failed to start auto loop: {exc}")

        # Apply the download concurrency limit on startup
        try:
            from .arr.arr_service import _load_arr_config
            from . import views

            cfg = _load_arr_config()
            views.set_max_download_slots(int(cfg.get("max_concurrent_downloads", 1) or 1))
        except Exception as exc:
            print(f"[Downloads] Failed to set max concurrent slots: {exc}")