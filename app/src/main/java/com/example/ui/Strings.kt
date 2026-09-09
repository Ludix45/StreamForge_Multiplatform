package com.example.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Composable

interface StreamForgeStrings {
    val app_name: String
    val settings: String
    val language: String
    val app_language_title: String
    val audio_language_title: String
    val updates: String
    val update_desc: String
    val update_now: String
    val update_success: String
    val update_error: String
    val back: String
    val settings_reset_onboarding: String
    
    val home: String
    val search: String
    val continue_watching: String
    val favorites: String
    
    val trending_movies: String
    val trending_series: String
    val action_movies: String
    val comedy_movies: String
    val no_continue_watching_title: String
    val no_continue_watching_desc: String
    val no_favorites_title: String
    val no_favorites_desc: String
    
    val search_hint: String
    val search_no_results: String
    val search_error: String
    val searching: String
    val search_provider: String
    
    val details_title: String
    val details_seasons: String
    val details_episodes: String
    val details_play: String
    val details_watch_now: String
    val details_add_favorite: String
    val details_remove_favorite: String
    val details_copy_link: String
    val details_season_label: String
    val details_year: String
    val details_extracting: String
    val details_play_movie: String
    val details_resume: String
    val details_seasons_select: String
    
    val player_next: String
    val player_subtitles: String
    val player_audio: String
    val player_zoom: String
    val player_mirror: String
    val player_close: String
    val player_mirror_screen: String
    val player_share_link: String
    val player_subtitles_off: String
    val player_options: String
    
    val onboarding_welcome_title: String
    val onboarding_welcome_desc: String
    val onboarding_search_title: String
    val onboarding_search_desc: String
    val onboarding_fav_title: String
    val onboarding_fav_desc: String
    val onboarding_next: String
    val onboarding_start: String
}

object ItalianStrings : StreamForgeStrings {
    override val app_name = "StreamForge"
    override val settings = "Impostazioni"
    override val language = "Lingua"
    override val app_language_title = "Lingua App (Titoli e Trama)"
    override val audio_language_title = "Lingua Audio Predefinita"
    override val updates = "Aggiornamenti"
    override val update_desc = "Aggiorna manualmente i domini dei siti e le impostazioni API dal repository."
    override val update_now = "Aggiorna Ora"
    override val update_success = "Aggiornamento completato con successo"
    override val update_error = "Errore durante l'aggiornamento"
    override val back = "Indietro"
    override val settings_reset_onboarding = "Riavvia Tutorial"
    
    override val home = "Home"
    override val search = "Cerca"
    override val continue_watching = "Continua a Guardare"
    override val favorites = "I Miei Preferiti"
    
    override val trending_movies = "Film del Momento"
    override val trending_series = "Serie TV del Momento"
    override val action_movies = "Azione"
    override val comedy_movies = "Commedia"
    override val no_continue_watching_title = "Nessun titolo in riproduzione"
    override val no_continue_watching_desc = "Inizia la riproduzione di un film o serie TV dalla scheda Ricerca per ritrovarlo qui!"
    override val no_favorites_title = "Ancora nessun preferito"
    override val no_favorites_desc = "Aggiungi i tuoi titoli preferiti premendo il cuore nella pagina dei dettagli!"
    
    override val search_hint = "Cerca film o serie TV..."
    override val search_no_results = "Nessun risultato trovato per la tua ricerca."
    override val search_error = "Si è verificato un errore durante la ricerca."
    override val searching = "Ricerca in corso..."
    override val search_provider = "Provider"
    
    override val details_title = "Dettagli Titolo"
    override val details_seasons = "Stagioni"
    override val details_episodes = "Lista Episodi"
    override val details_play = "Riproduci"
    override val details_watch_now = "Guarda Ora"
    override val details_add_favorite = "Aggiungi ai preferiti"
    override val details_remove_favorite = "Rimuovi dai preferiti"
    override val details_copy_link = "Copia Link"
    override val details_season_label = "Stagione"
    override val details_year = "Anno di rilascio"
    override val details_extracting = "Estrazione link di streaming in corso..."
    override val details_play_movie = "▶ RIPRODUCI FILM COMPLETO"
    override val details_resume = "PROSEGUI"
    override val details_seasons_select = "Seleziona Stagione"
    
    override val player_next = "PROSSIMO"
    override val player_subtitles = "Sottotitoli"
    override val player_audio = "Lingua Audio"
    override val player_zoom = "Zoom"
    override val player_mirror = "Mirroring"
    override val player_close = "Chiudi Media Player"
    override val player_mirror_screen = "Mirroring Schermo"
    override val player_share_link = "Condividi Link Web"
    override val player_subtitles_off = "Disattivati"
    override val player_options = "Menu Opzioni"
    
    override val onboarding_welcome_title = "Benvenuto su StreamForge"
    override val onboarding_welcome_desc = "La tua nuova esperienza di streaming definitiva. Semplice, veloce e senza pubblicità."
    override val onboarding_search_title = "Tutto a portata di click"
    override val onboarding_search_desc = "Cerca i tuoi film e serie TV preferiti tra diversi provider affidabili."
    override val onboarding_fav_title = "Non perdere il filo"
    override val onboarding_fav_desc = "Salva i tuoi preferiti e riprendi la visione esattamente da dove avevi lasciato."
    override val onboarding_next = "Avanti"
    override val onboarding_start = "Inizia Ora"
}

object EnglishStrings : StreamForgeStrings {
    override val app_name = "StreamForge"
    override val settings = "Settings"
    override val language = "Language"
    override val app_language_title = "App Language (Titles & Plots)"
    override val audio_language_title = "Default Audio Language"
    override val updates = "Updates"
    override val update_desc = "Manually update site domains and API settings from the repository."
    override val update_now = "Update Now"
    override val update_success = "Update completed successfully"
    override val update_error = "Error during update"
    override val back = "Back"
    override val settings_reset_onboarding = "Restart Tutorial"
    
    override val home = "Home"
    override val search = "Search"
    override val continue_watching = "Continue Watching"
    override val favorites = "My Favorites"
    
    override val trending_movies = "Trending Movies"
    override val trending_series = "Trending Series"
    override val action_movies = "Action"
    override val comedy_movies = "Comedy"
    override val no_continue_watching_title = "No titles in playback"
    override val no_continue_watching_desc = "Start playing a movie or TV show from the Search tab to find it here!"
    override val no_favorites_title = "No favorites yet"
    override val no_favorites_desc = "Add your favorite titles by pressing the heart on the details page!"
    
    override val search_hint = "Search movies or TV shows..."
    override val search_no_results = "No results found for your search."
    override val search_error = "An error occurred during search."
    override val searching = "Searching..."
    override val search_provider = "Provider"
    
    override val details_title = "Title Details"
    override val details_seasons = "Seasons"
    override val details_episodes = "Episodes List"
    override val details_play = "Play"
    override val details_watch_now = "Watch Now"
    override val details_add_favorite = "Add to favorites"
    override val details_remove_favorite = "Remove from favorites"
    override val details_copy_link = "Copy Link"
    override val details_season_label = "Season"
    override val details_year = "Release Year"
    override val details_extracting = "Extracting streaming link..."
    override val details_play_movie = "▶ PLAY FULL MOVIE"
    override val details_resume = "RESUME"
    override val details_seasons_select = "Select Season"
    
    override val player_next = "NEXT"
    override val player_subtitles = "Subtitles"
    override val player_audio = "Audio Language"
    override val player_zoom = "Zoom"
    override val player_mirror = "Mirroring"
    override val player_close = "Close Media Player"
    override val player_mirror_screen = "Screen Mirroring"
    override val player_share_link = "Share Web Link"
    override val player_subtitles_off = "Off"
    override val player_options = "Options Menu"
    
    override val onboarding_welcome_title = "Welcome to StreamForge"
    override val onboarding_welcome_desc = "Your new ultimate streaming experience. Simple, fast, and ad-free."
    override val onboarding_search_title = "Everything at your fingertips"
    override val onboarding_search_desc = "Search for your favorite movies and TV shows across reliable providers."
    override val onboarding_fav_title = "Don't lose track"
    override val onboarding_fav_desc = "Save your favorites and resume watching exactly where you left off."
    override val onboarding_next = "Next"
    override val onboarding_start = "Start Now"
}

object SpanishStrings : StreamForgeStrings {
    override val app_name = "StreamForge"
    override val settings = "Ajustes"
    override val language = "Idioma"
    override val app_language_title = "Idioma de la aplicación (Títulos y Tramas)"
    override val audio_language_title = "Idioma de audio predeterminado"
    override val updates = "Actualizaciones"
    override val update_desc = "Actualizar manualmente los dominios del sitio y la configuración de la API desde el repositorio."
    override val update_now = "Actualizar ahora"
    override val update_success = "Actualización completada con éxito"
    override val update_error = "Error durante la actualización"
    override val back = "Atrás"
    override val settings_reset_onboarding = "Reiniciar Tutorial"
    
    override val home = "Inicio"
    override val search = "Buscar"
    override val continue_watching = "Continuar viendo"
    override val favorites = "Mis favoritos"
    
    override val trending_movies = "Películas del momento"
    override val trending_series = "Series del momento"
    override val action_movies = "Acción"
    override val comedy_movies = "Comedia"
    override val no_continue_watching_title = "No hay títulos en reproducción"
    override val no_continue_watching_desc = "¡Empieza a ver una película o serie desde la pestaña Buscar para encontrarla aquí!"
    override val no_favorites_title = "Aún no hay favoritos"
    override val no_favorites_desc = "¡Añade tus títulos favoritos pulsando el corazón en la página de detalles!"
    
    override val search_hint = "Buscar películas o series..."
    override val search_no_results = "No se han encontrado resultados para tu búsqueda."
    override val search_error = "Se ha producido un error durante la búsqueda."
    override val searching = "Buscando..."
    override val search_provider = "Proveedor"
    
    override val details_title = "Detalles del título"
    override val details_seasons = "Temporadas"
    override val details_episodes = "Lista de episodios"
    override val details_play = "Reproducir"
    override val details_watch_now = "Ver ahora"
    override val details_add_favorite = "Añadir a favoritos"
    override val details_remove_favorite = "Quitar de favoritos"
    override val details_copy_link = "Copiar enlace"
    override val details_season_label = "Temporada"
    override val details_year = "Año de lanzamiento"
    override val details_extracting = "Extrayendo enlace de streaming..."
    override val details_play_movie = "▶ REPRODUCIR PELÍCULA COMPLETA"
    override val details_resume = "CONTINUAR"
    override val details_seasons_select = "Seleccionar temporada"
    
    override val player_next = "SIGUIENTE"
    override val player_subtitles = "Subtítulos"
    override val player_audio = "Idioma de audio"
    override val player_zoom = "Zoom"
    override val player_mirror = "Espejo"
    override val player_close = "Cerrar reproductor"
    override val player_mirror_screen = "Pantalla espejo"
    override val player_share_link = "Compartir enlace web"
    override val player_subtitles_off = "Desactivados"
    override val player_options = "Menú de opciones"
    
    override val onboarding_welcome_title = "Bienvenido a StreamForge"
    override val onboarding_welcome_desc = "Tu nueva experiencia definitiva de streaming. Sencilla, rápida y sin publicidad."
    override val onboarding_search_title = "Todo a tu alcance"
    override val onboarding_search_desc = "Busca tus películas e series favoritas en proveedores fiables."
    override val onboarding_fav_title = "No pierdas el hilo"
    override val onboarding_fav_desc = "Guarda tus favoritos y reanuda la visualización exactamente donde la dejaste."
    override val onboarding_next = "Siguiente"
    override val onboarding_start = "Empezar ahora"
}

object FrenchStrings : StreamForgeStrings {
    override val app_name = "StreamForge"
    override val settings = "Paramètres"
    override val language = "Langue"
    override val app_language_title = "Langue de l'application (Titres et intrigues)"
    override val audio_language_title = "Langue audio par défaut"
    override val updates = "Mises à jour"
    override val update_desc = "Mettre à jour manuellement i domaines du site et les paramètres API depuis le dépôt."
    override val update_now = "Mettre à jour maintenant"
    override val update_success = "Mise à jour complétée avec succès"
    override val update_error = "Erreur lors de la mise à jour"
    override val back = "Retour"
    override val settings_reset_onboarding = "Redémarrer le Tutoriel"
    
    override val home = "Accueil"
    override val search = "Rechercher"
    override val continue_watching = "Continuer à regarder"
    override val favorites = "Mes favoris"
    
    override val trending_movies = "Films du moment"
    override val trending_series = "Séries du moment"
    override val action_movies = "Action"
    override val comedy_movies = "Comédie"
    override val no_continue_watching_title = "Aucun titre en cours"
    override val no_continue_watching_desc = "Commencez à regarder un film ou une série depuis l'onglet Recherche pour le retrouver ici !"
    override val no_favorites_title = "Pas ancora de favoris"
    override val no_favorites_desc = "Ajoutez vos titres favoris en appuyant sur le cœur sur la page des détails !"
    
    override val search_hint = "Rechercher des films o séries..."
    override val search_no_results = "Aucun résultat trouvé pour votre recherche."
    override val search_error = "Une erreur est survenue lors de la recherche."
    override val searching = "Recherche en cours..."
    override val search_provider = "Fournisseur"
    
    override val details_title = "Détails du titre"
    override val details_seasons = "Saisons"
    override val details_episodes = "Liste des épisodes"
    override val details_play = "Lire"
    override val details_watch_now = "Regarder maintenant"
    override val details_add_favorite = "Ajouter aux favoris"
    override val details_remove_favorite = "Retirer des favoris"
    override val details_copy_link = "Copier le lien"
    override val details_season_label = "Saison"
    override val details_year = "Année de sortie"
    override val details_extracting = "Extraction du lien de streaming..."
    override val details_play_movie = "▶ LIRE LE FILM COMPLET"
    override val details_resume = "REPRENDRE"
    override val details_seasons_select = "Sélectionner la saison"
    
    override val player_next = "SUIVANT"
    override val player_subtitles = "Sous-titres"
    override val player_audio = "Langue audio"
    override val player_zoom = "Zoom"
    override val player_mirror = "Miroir"
    override val player_close = "Fermer le lecteur"
    override val player_mirror_screen = "Miroir de l'écran"
    override val player_share_link = "Partager le lien web"
    override val player_subtitles_off = "Désactivés"
    override val player_options = "Menu des options"
    
    override val onboarding_welcome_title = "Bienvenue sur StreamForge"
    override val onboarding_welcome_desc = "Votre nouvelle expérience de streaming ultime. Simple, rapide et sans publicité."
    override val onboarding_search_title = "Tout à portée de main"
    override val onboarding_search_desc = "Recherchez vos films et séries préférés parmi des fournisseurs fiables."
    override val onboarding_fav_title = "Ne perdez pas le fil"
    override val onboarding_fav_desc = "Enregistrez vos favoris et reprenez la lecture exactement là où vous vous étiez arrêté."
    override val onboarding_next = "Suivant"
    override val onboarding_start = "Commencer maintenant"
}

val LocalStreamForgeStrings = compositionLocalOf<StreamForgeStrings> { ItalianStrings }

val s: StreamForgeStrings
    @Composable
    @ReadOnlyComposable
    get() = LocalStreamForgeStrings.current
