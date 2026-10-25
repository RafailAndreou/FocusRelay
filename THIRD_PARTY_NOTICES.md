# Third-party software

The application is based on Mindful, GNU GPL v2. Its original copyright headers and license are retained. The license is also bundled as assets/licenses/Mindful-GPL-2.0.txt and registered in the app's Open-source licenses page.

Flutter/Dart packages retain their own licenses. Flutter generates dependency notices in the bundled license registry, accessible from Settings → About → Open-source licenses. The pinned upstream pubspec.lock and this repository's pubspec.yaml define the application dependencies. Do not remove upstream assets or their notices from source distributions.

Android libraries keep their respective licenses and notices. The app adds androidx.work:work-runtime 2.10.1 and androidx.appcompat:appcompat 1.7.1 (Apache License 2.0), core-library desugaring 2.1.5, and test-only org.json 20240303 and JUnit 4.13.2. The full Gradle dependency graph also includes upstream dependencies; use Gradle's dependencies task in the reconstructed source when auditing a binary distribution. This file does not relicense any dependency.

Wrangler and its dependencies are development/deployment tools. Their exact versions are in relay/package-lock.json and their license files are installed by npm ci. They are not embedded in the Android APK. Review and retain those licenses if redistributing node_modules or tool binaries.

No private signing key, device credential or Cloudflare account credential is part of the application source or the required source distribution.
