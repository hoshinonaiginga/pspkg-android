# PS PKG Extractor (Android)

Estrae e decritta i file `.pkg` PS4 (sce_sys, file system PFS, struttura completa) direttamente sul telefono Android.

## Crediti

**Questo progetto esiste grazie a [ps4-pkg-tools](https://github.com/xXJSONDeruloXx/ps4-pkg-tools) di [xXJSONDeruloXx](https://github.com/xXJSONDeruloXx).**
Tutta la logica di estrazione e decrittazione dei PKG viene dal suo lavoro: ha isolato il core PKG dell'emulatore shadPS4 in una libreria pulita e autonoma. Questa app è "solo" il port Android: compila quel core con l'NDK e ci costruisce sopra un'interfaccia mobile. Senza il suo progetto sarebbero serviti giorni di lavoro in più.

Grazie anche a:
- **[shadPS4](https://github.com/shadps4-emu/shadPS4)**: il team dell'emulatore PS4 da cui deriva il codice originale di lettura e decrittazione dei PKG e del PFS.
- **[Crypto++](https://github.com/weidai11/cryptopp)** (Wei Dai e contributori), per la crittografia, e **zlib**, per la decompressione.

Se l'app ti è utile, lascia una ⭐ anche a [ps4-pkg-tools](https://github.com/xXJSONDeruloXx/ps4-pkg-tools).

## Download

Scarica l'APK dall'ultima [release](../../releases) (file `PS-PKG-Extractor-<versione>.apk`).

- Richiede **Android 8.0+** su **arm64** (praticamente tutti i telefoni dal 2017 in poi).
- Non è sul Play Store, perché Google non concede il permesso "accesso a tutti i file" a questo tipo di app.
- Per ricevere gli aggiornamenti in automatico puoi usare [Obtainium](https://github.com/ImranR98/Obtainium) con l'URL di questo repository.

## Uso

1. Al primo avvio concedi **Accesso a tutti i file**. Serve per leggere i PKG dove sono, senza copiarli, visto che possono pesare decine di GB.
2. **Scegli PKG** per un singolo file, oppure **Cartella (batch)** per estrarre tutti i `.pkg` di una cartella, sottocartelle comprese.
3. Destinazione: di default `<cartella del PKG>/<TITLE ID>`, oppure una cartella a tua scelta.
4. **Estrai**. L'estrazione gira in un servizio in primo piano con la notifica di avanzamento, quindi continua anche a schermo spento.

La fase iniziale ("Decrittazione PFS e lettura indice") non mostra una percentuale; il conteggio parte quando inizia l'estrazione dei file.

## Avvertenze

- L'app **non contiene giochi, firmware o chiavi di console** e non fornisce link per scaricarli. Usala con i PKG di cui hai diritto: i tuoi dump, i tuoi homebrew, i tuoi backup.
- Progetto non ufficiale, senza alcun legame con Sony Interactive Entertainment. "PlayStation" e "PS4" sono marchi dei rispettivi proprietari e qui sono usati solo per indicare il formato dei file.
- Il software è fornito "così com'è", senza garanzie (vedi la licenza).

## Struttura

```
core/                 sorgenti C++ di ps4-pkg-tools (core shadPS4) + Crypto++ come submodule
app/src/main/cpp/     CMake per NDK + bridge JNI (zlib = quella di sistema dell'NDK)
app/src/main/java/    UI (MainActivity), browser file, servizio di estrazione
```

### Build

```
git clone --recursive https://github.com/hoshinonaiginga/pspkg-android
./gradlew assembleRelease
```

Serve Android SDK + NDK 27. Senza le variabili di firma (`PSPKG_KEYSTORE`, `PSPKG_KEYSTORE_PASSWORD`) l'APK release viene firmato con la chiave debug locale.

Le release ufficiali vengono buildate da GitHub Actions quando viene pubblicato un tag `vX.Y.Z`.

## Licenza

**GPL-2.0-or-later**, come ps4-pkg-tools e shadPS4 (vedi `LICENSE`, `core/LICENSE` e `core/LICENSES/`).
Puoi usare, modificare e ridistribuire questo software, ma le versioni modificate devono restare open source con la stessa licenza e mantenere i crediti.
