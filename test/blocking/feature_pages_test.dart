// Modified by Rafail Andreou, 2026-10-26, for FocusRelay. Original notices retained.
// SPDX-License-Identifier: GPL-2.0-only
// Smoke and render the migrated feature pages with real Dart providers and an isolated database.
import 'dart:io';
import 'dart:convert';
import 'dart:ffi' show DynamicLibrary;
import 'package:sqlite3/open.dart';
import 'dart:ui' as ui;
import 'package:drift/native.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mindful/config/navigation/app_routes.dart';
import 'package:mindful/core/database/app_database.dart';
import 'package:mindful/core/services/drift_db_service.dart';
import 'package:mindful/core/services/method_channel_service.dart';
import 'package:mindful/l10n/generated/app_localizations.dart';
import 'package:mindful/ui/screens/blocking/blocking_style.dart';
import 'package:mindful/ui/screens/app_dashboard/app_dashboard_screen.dart';
import 'package:mindful/ui/onboarding/onboarding_screen.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  if (Platform.isLinux) {
    open.overrideFor(
      OperatingSystem.linux,
      () => DynamicLibrary.open('libsqlite3.so.0'),
    );
  }
  testWidgets('render and inspect migrated feature pages', (tester) async {
    tester.view.physicalSize = const Size(412, 892);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    const channel = MethodChannel('com.mindful.android.methodchannel.fg');
    messenger.setMockMethodCallHandler(channel, (call) async {
      switch (call.method) {
        case 'getDeviceInfo':
          return {'sdkVersion': 34, 'mindfulVersion': '1.5.0'};
        case 'getDeviceAppsInfo':
          return [
            {
              'appName': 'Example app',
              'packageName': 'sample',
              'appIcon': '',
              'isImpSysApp': false,
            },
          ];
        case 'getAppsUsageForInterval':
          return [
            {
              'packageName': 'sample',
              'screenTime': 1800,
              'mobileData': 1200,
              'wifiData': 2400,
            },
          ];
        case 'getAppsLaunchCount':
          return <String, int>{};
        case 'getShortsScreenTimeMs':
          return 0;
        case 'getNativeCrashLogs':
          return '[]';
        default:
          return true;
      }
    });
    await MethodChannelService.instance.init();
    final db = AppDatabase(NativeDatabase.memory());
    DriftDbService.instance.driftDb = db;
    final font = File(
      '${Platform.environment['FLUTTER_ROOT']}/bin/cache/artifacts/material_fonts/Roboto-Regular.ttf',
    );
    if (font.existsSync()) {
      await (FontLoader('Roboto')..addFont(
            Future.value(ByteData.sublistView(font.readAsBytesSync())),
          ))
          .load();
    }
    final materialIcons = File(
      '${Platform.environment['FLUTTER_ROOT']}/bin/cache/artifacts/material_fonts/MaterialIcons-Regular.otf',
    );
    if (materialIcons.existsSync()) {
      await (FontLoader('MaterialIcons')..addFont(
            Future.value(ByteData.sublistView(materialIcons.readAsBytesSync())),
          ))
          .load();
    }
    final configFile = File('.dart_tool/package_config.json').absolute;
    final packages =
        (jsonDecode(configFile.readAsStringSync()) as Map)['packages'] as List;
    final fluent = packages.cast<Map>().singleWhere(
      (p) => p['name'] == 'fluentui_system_icons',
    );
    final rootUri = fluent['rootUri'] as String;
    final fluentRoot = configFile.uri.resolve(
      rootUri.endsWith('/') ? rootUri : '$rootUri/',
    );
    for (final family in [
      'FluentSystemIcons-Regular',
      'FluentSystemIcons-Filled',
    ]) {
      final data = File.fromUri(
        fluentRoot.resolve('lib/fonts/$family.ttf'),
      ).readAsBytesSync();
      await (FontLoader(
        'packages/fluentui_system_icons/$family',
      )..addFont(Future.value(ByteData.sublistView(data)))).load();
    }
    final container = ProviderContainer();
    for (final route in [
      AppRoutes.statisticsPath,
      AppRoutes.bedtimePath,
      AppRoutes.focusModePath,
      AppRoutes.restrictionGroupsPath,
      AppRoutes.websitesBlockingPath,
      AppRoutes.shortsBlockingPath,
      AppRoutes.notificationSettingsPath,
      AppRoutes.notificationsPath,
      AppRoutes.parentalControlsPath,
      AppRoutes.settingsPath,
      'sample-app',
      'setup',
    ]) {
      debugPrint('Feature audit: $route');
      final key = GlobalKey();
      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: RepaintBoundary(
            key: key,
            child: MaterialApp(
              theme: blockingTheme(),
              debugShowCheckedModeBanner: false,
              supportedLocales: AppLocalizations.supportedLocales,
              localizationsDelegates: const [
                AppLocalizations.delegate,
                GlobalMaterialLocalizations.delegate,
                GlobalWidgetsLocalizations.delegate,
                GlobalCupertinoLocalizations.delegate,
              ],
              home: route == 'sample-app'
                  ? const AppDashboardScreen(packageName: 'sample')
                  : route == 'setup'
                  ? const OnboardingScreen(isOnboardingDone: true)
                  : Builder(builder: AppRoutes.routes[route]!),
            ),
          ),
        ),
      );
      // Let actual providers populate from the isolated database, and let intro animations complete.
      await tester.runAsync(() async {
        await Future<void>.delayed(const Duration(milliseconds: 150));
      });
      for (var i = 0; i < 8; i++) {
        await tester.pump(const Duration(milliseconds: 300));
      }
      expect(tester.takeException(), isNull, reason: route);
      await tester.runAsync(() async {
        final shot =
            await (key.currentContext!.findRenderObject()!
                    as RenderRepaintBoundary)
                .toImage(pixelRatio: 1.5);
        final bytes = await shot.toByteData(format: ui.ImageByteFormat.png);
        final file = File(
          'build/ui-preview/feature-${route.replaceAll('/', '')}.png',
        );
        file.parent.createSync(recursive: true);
        file.writeAsBytesSync(bytes!.buffer.asUint8List());
        shot.dispose();
      });
      await tester.pumpWidget(const SizedBox());
      await tester.pump(const Duration(seconds: 2));
    }
    await tester.runAsync(() async {
      await Future<void>.delayed(const Duration(milliseconds: 200));
    });
    container.dispose();
    await tester.runAsync(db.close);
  });
}
