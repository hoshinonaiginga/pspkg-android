# PS PKG Extractor (Android)

Extract and decrypt PS4 `.pkg` files (sce_sys, PFS file system, full directory tree) right on your Android phone.

## Credits

**This project exists thanks to [ps4-pkg-tools](https://github.com/xXJSONDeruloXx/ps4-pkg-tools) by [xXJSONDeruloXx](https://github.com/xXJSONDeruloXx).**
All of the PKG extraction and decryption logic comes from that work, which carved the PKG core out of the shadPS4 emulator into a clean, standalone library. This app is "just" the Android port: it builds that core with the NDK and puts a mobile UI on top. Without it, this would have taken days more work.

Thanks also to:
- **[shadPS4](https://github.com/shadps4-emu/shadPS4)**, the PS4 emulator team behind the original PKG/PFS reading and decryption code.
- **[Crypto++](https://github.com/weidai11/cryptopp)** (Wei Dai and contributors) for the cryptography, and **zlib** for decompression.

If this app is useful to you, please leave a ⭐ on [ps4-pkg-tools](https://github.com/xXJSONDeruloXx/ps4-pkg-tools) too.

## Download

Grab the APK from the latest [release](../../releases) (`PS-PKG-Extractor-<version>.apk`).

- Requires **Android 8.0+** on **arm64** (basically every phone since 2017).
- It is not on the Play Store: Google doesn't grant the "All files access" permission to this kind of app.
- To get updates automatically, add this repository's URL to [Obtainium](https://github.com/ImranR98/Obtainium).

## Usage

1. On first launch, grant **All files access**. PKGs are read in place, without copying them, since they can be tens of GB.
2. Tap **Pick PKG** for a single file, or **Folder (batch)** to extract every `.pkg` in a folder, subfolders included.
3. Destination: by default `<PKG folder>/<TITLE ID>`, or any folder you choose.
4. Tap **Extract**. Extraction runs in a foreground service with a progress notification, so it keeps going with the screen off.

The first phase ("Decrypting PFS and reading index") has no percentage. The file counter starts once the actual file extraction begins.

The UI is in English, with an Italian translation that is used automatically when the system language is Italian. Translations for other languages are welcome: just add a `values-<lang>/strings.xml`.

## Disclaimer

- This app **does not include any games, firmware or console keys**, and does not link to any. Use it only with PKGs you have the right to use: your own dumps, homebrew, backups.
- Unofficial project, not affiliated with Sony Interactive Entertainment. "PlayStation" and "PS4" are trademarks of their respective owners and are used here only to describe the file format.
- The software is provided "as is", without warranty of any kind (see the license).

## Project layout

```
core/                 C++ sources from ps4-pkg-tools (shadPS4 core) + Crypto++ as a submodule
app/src/main/cpp/     NDK CMake build + JNI bridge (zlib = the NDK's system zlib)
app/src/main/java/    UI (MainActivity), file browser, extraction service
```

### Building

```
git clone --recursive https://github.com/hoshinonaiginga/pspkg-android
./gradlew assembleRelease
```

You need the Android SDK and NDK 27. Without the signing variables (`PSPKG_KEYSTORE`, `PSPKG_KEYSTORE_PASSWORD`), the release APK is signed with your local debug key.

Official releases: publish a GitHub Release with a `vX.Y.Z` tag, and GitHub Actions builds the signed APK and attaches it automatically.

## License

**GPL-2.0-or-later**, like ps4-pkg-tools and shadPS4 (see `LICENSE`, `core/LICENSE` and `core/LICENSES/`).
You may use, modify and redistribute this software, but modified versions must stay open source under the same license and keep the credits.
