# Ottimizzazione Build iOS e GitHub Actions

L'obiettivo è risolvere il blocco durante la fase di linking iOS su GitHub Actions, riducendo il consumo di memoria e implementando una cache per le dipendenze native.

## User Review Required

> [!IMPORTANT]
> Le modifiche limiteranno il parallelismo durante il build su GitHub per garantire stabilità. Questo potrebbe rendere il build leggermente più lungo in termini di esecuzione sequenziale, ma eviterà i blocchi di 30+ minuti causati dalla saturazione della RAM.

## Proposed Changes

### GitHub Workflow

#### [MODIFY] [ios-check.yml](file:///C:/Users/matla/Desktop/StreamForge-Multiplatform_1.2.1/.github/workflows/ios-check.yml)
- Aggiunta dello step `actions/cache` per la directory `~/.konan` (dipendenze Kotlin/Native).
- Modifica del comando di build per includere limitazioni di memoria e worker via command line.

### Project Configuration

#### [MODIFY] [gradle.properties](file:///C:/Users/matla/Desktop/StreamForge-Multiplatform_1.2.1/gradle.properties)
- Ottimizzazione dei parametri JVM per Kotlin/Native.
- Abilitazione di `kotlin.native.cacheKind` per migliorare i tempi di compilazione incrementale.

---

## Verification Plan

### Manual Verification
1. Verificare che il push del codice scateni il workflow su GitHub.
2. Controllare nei log di GitHub Actions che lo step "Cache Konan" venga eseguito correttamente.
3. Verificare che il build `assembleSharedReleaseXCFramework` termini con successo entro tempi ragionevoli (previsto: 15-20 minuti dopo il primo avvio con cache).
