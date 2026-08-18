# Strategie di Aggiornamento Future

Questo documento descrive i possibili metodi per gestire gli aggiornamenti di StreamForge in futuro.

## 1. Aggiornamento Manuale (Metodo Attuale)
L'utente scarica la nuova versione dell'installer (MSI) e la esegue.
*   **Vantaggi**: Zero configurazione aggiuntiva.
*   **Svantaggi**: L'utente deve controllare manualmente la presenza di nuove versioni.

## 2. Notifica In-App (Consigliato per il prossimo step)
L'app all'avvio controlla un file JSON su un server (es. GitHub Releases) per vedere se esiste una versione più recente.
*   **Flusso**:
    1. L'app legge `https://mio-sito.it/version.json`.
    2. Se `versione_remota > versione_attuale`, mostra un banner: *"Nuova versione disponibile! [Scarica]"*.
    3. Il tasto scarica apre il browser sul link del nuovo `.msi`.
*   **Implementazione**: Richiede l'aggiunta di una chiamata HTTP all'avvio.

## 3. Aggiornamenti Automatici con Conveyor
**Conveyor** è uno strumento di terze parti che sostituisce il plugin Gradle di Compose per il packaging.
*   **Vantaggi**: Supporta aggiornamenti "stile Chrome" (background update). Firma automaticamente i pacchetti.
*   **Costi**: Gratuito solo per progetti Open Source pubblici.
*   **Sito**: [https://www.hydraulic.dev/](https://www.hydraulic.dev/)

## 4. GitHub Actions (Automazione Build)
Possiamo configurare una "Pipeline" che, ogni volta che carichi il codice su GitHub, crea automaticamente gli installer per Windows, Mac e Linux.
*   Questo non è un metodo di aggiornamento per l'utente, ma facilita enormemente la distribuzione per te.

## 5. MSIX (Windows Store Style)
Invece di MSI, si può usare il formato MSIX.
*   Supporta l'aggiornamento automatico nativo gestito da Windows.
*   Richiede un certificato di firma digitale (costoso) o la pubblicazione sul Microsoft Store.

---

### Piano d'Azione Proposto
1. Continuare con **MSI** per la massima compatibilità.
2. Implementare la **Notifica In-App** tra qualche release per avvisare gli utenti.
3. Valutare **Conveyor** se il progetto diventerà molto popolare.
