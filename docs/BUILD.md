# Build FocusRelay

## GitHub Actions (no Android development environment required)

Fork this public source repository into your GitHub account. Open Actions → Build FocusRelay → Run workflow. The workflow checks out the pinned upstream, applies the overlay, runs relay, Flutter and Android checks and produces an unsigned release APK and full application-source archive. Download artifacts only from a successful run of the source revision you intend to use.

The CI APK is unsigned and must be signed privately before installation. No release key or password is uploaded to the runner. Use a published maintainer-signed release for normal installation. Keep the complete-source artifact alongside any redistributed APK, from the same run.

## Local reconstruction

Install Git, Python 3, Flutter 3.35.7, JDK 17 and the Android SDK/NDK requested by Gradle (NDK 27.0.12077973). Node.js 24 is used for relay tooling. From this repository's root:

```sh
git clone https://github.com/akaMrNagar/Mindful.git upstream
git -C upstream checkout b6eb68d500463c54e1127b6cc7ad540f02ae0c8e
python3 scripts/apply_overlay.py upstream
cd upstream
flutter pub get
dart run build_runner build --delete-conflicting-outputs
flutter test test/remote test/blocking
flutter build apk --debug --target-platform android-arm64 --build-number=160 --build-name=1.6.0
cd android
./gradlew :app:testDebugUnitTest -Ptarget-platform=android-arm64
```

On Windows use gradlew.bat for the final command. GitHub Actions sets MINDFUL_PREVIEW_SUFFIX=.design. To match its debug package locally, set that environment variable before the build (PowerShell: `$env:MINDFUL_PREVIEW_SUFFIX='.design'`; bash: `export MINDFUL_PREVIEW_SUFFIX=.design`).

The installed label is FocusRelay. Existing package/namespace identifiers are deliberately retained; a label rename does not require new permissions or pairing. Updating an installed Design Preview also requires the exact same signing certificate, package ID and a higher version code. A random CI debug key cannot replace your personally signed app.

## Release signing

Generate and retain your own Android release keystore using the official [Flutter signing instructions](https://docs.flutter.dev/deployment/android#sign-the-app). Keep it private and backed up outside git. Set KEYSTORE_FILE to its absolute path, STORE_PASSWORD, KEY_ALIAS and KEY_PASSWORD in your local build environment, then run flutter build apk --release with the version flags above. Without a suffix the release application ID is com.mindful.android.remote. Set MINDFUL_PREVIEW_SUFFIX=.design to retain the Design Preview identity com.mindful.android.remote.design; the public workflow uses this identity so a privately signed release can update Design Preview. FOCUSRELAY_UNSIGNED_RELEASE=true explicitly disables release signing for CI; omit it when signing through Gradle locally.

Never publish the private keystore or passwords. Preserve a stable signing identity for every subsequent update. There is no signing secret in the public workflow.

## Redistributing source with APKs

Run `python3 scripts/package_source.py upstream` from the overlay root after applying it and before generating binaries. It makes build/focusrelay-complete-source.tar.gz with the full application tree, relay, GPL license, notices and build instructions, excluding build caches and private files. The CI workflow runs this too. Distribute the archive with the matching APK; retain dependency notices. This avoids making recipients reconstruct missing source from an overlay alone.
