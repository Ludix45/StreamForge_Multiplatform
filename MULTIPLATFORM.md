# StreamForge: base Kotlin Multiplatform

## Cosa è stato aggiunto

Il progetto Android esistente nel modulo `app` non è stato spostato, rinominato o privato di funzioni.
Sono stati aggiunti due moduli indipendenti:

- `shared`: contratti Kotlin Multiplatform (`commonMain`) per cataloghi autorizzati e sorgenti di riproduzione;
- `desktopApp`: finestra Compose Desktop pronta per Windows, Linux e macOS.

La struttura segue l'impostazione essenziale di NuvioDesktop (un modulo condiviso e un entry point desktop), senza copiare il suo codice o le sue dipendenze native.

Il target `desktopApp` compila direttamente le classi esistenti `Scraper`, `HttpClient` e i modelli dal modulo Android. Piccoli adapter JVM per `Uri`, `Log`, `Base64` e `DomainManager` consentono quindi di mantenere invariata la logica dei provider e di eseguirla anche su desktop. La UI desktop include ricerca, selezione di film/serie, stagioni, episodi e passaggio dell'URL risultante al player `mpv`.

## Eseguire su Windows

Installa una JDK 17 o superiore e [mpv](https://mpv.io/) rendendolo disponibile nel `PATH`, poi esegui:

```powershell
./gradlew.bat :desktopApp:run
```

Per generare il pacchetto Windows MSI dal sistema Windows:

```powershell
./gradlew.bat :desktopApp:packageMsi
```

Da Linux e macOS, Compose Desktop genera rispettivamente il pacchetto DEB e DMG con i task di packaging equivalenti. La firma/notarizzazione macOS e la firma Windows richiedono certificati di distribuzione separati.

## Riproduzione e provider

`DesktopMpvPlayer` avvia il player locale `mpv` per URL HTTPS risolti dai provider. Questo mantiene il decoder nativo e portabile tra i tre sistemi. `AuthorizedCatalogProvider` in `shared` resta disponibile per integrare ulteriori cataloghi o account tramite API ufficiali.

Non sono stati portati gli estrattori che ricavano token o URL di riproduzione da pagine terze né qualsiasi flusso di aggiramento DRM. Queste integrazioni devono essere sostituite da API dei fornitori con le rispettive autorizzazioni.
