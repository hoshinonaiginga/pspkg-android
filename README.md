# PS PKG Extractor (Android)

Port Android di [ps4-pkg-tools](https://github.com/xXJSONDeruloXx/ps4-pkg-tools): estrae e decritta i file `.pkg` PS4 (sce_sys, file system PFS, struttura completa) direttamente sul telefono.

Il core di estrazione è il codice C++ derivato da shadPS4 (`core/src`), compilato con l'NDK come `libpspkg.so` e richiamato via JNI. Nessuna dipendenza AndroidX.

## Download

Ogni push su `main` builda l'APK con GitHub Actions e lo pubblica nella release **latest** → `PS-PKG-Extractor.apk`.
Solo **arm64-v8a** (praticamente tutti i telefoni dal 2017 in poi).

## Uso

1. Al primo avvio concedi **Accesso a tutti i file** (serve per leggere i PKG sul posto, senza copiarli: possono pesare decine di GB).
2. **Scegli PKG** per un singolo file, oppure **Cartella (batch)** per estrarre tutti i `.pkg` in una cartella (anche nelle sottocartelle).
3. Destinazione: di default `<cartella del PKG>/<TITLE ID>`, oppure una cartella a scelta.
4. **Estrai**. L'estrazione gira in un servizio in primo piano con notifica di avanzamento, quindi continua anche a schermo spento.

La fase iniziale ("Decrittazione PFS e lettura indice") non ha percentuale; poi parte il conteggio file per file.

## Struttura

```
core/                 sorgenti C++ upstream (shadPS4 / ps4-pkg-tools) + Crypto++ come submodule
app/src/main/cpp/     CMake per NDK + bridge JNI (zlib = quella di sistema dell'NDK)
app/src/main/java/    UI (MainActivity), browser file, servizio di estrazione
```

Build locale: `git clone --recursive …` poi `./gradlew assembleRelease` (serve Android SDK + NDK 27).

> La chiave di firma `app/release.keystore` è nel repo per comodità (permette di aggiornare l'app sopra la versione precedente). Se il repo diventa pubblico e ti interessa, spostala nei secrets di GitHub.

## Licenza

GPL-2.0-or-later, come il progetto originale (vedi `core/LICENSE` e `core/LICENSES/`).
