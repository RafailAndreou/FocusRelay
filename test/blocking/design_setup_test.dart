// Modified by Rafail Andreou, 2026-10-26, for FocusRelay. Original notices retained.
// SPDX-License-Identifier: GPL-2.0-only
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mindful/core/services/method_channel_service.dart';
import 'package:mindful/l10n/generated/app_localizations.dart';
import 'package:mindful/models/permissions_model.dart';
import 'package:mindful/providers/system/permissions_provider.dart';
import 'package:mindful/ui/onboarding/onboarding_screen.dart';
import 'package:mindful/ui/screens/blocking/blocking_style.dart';

class SetupPermissions extends PermissionNotifier {
  @override
  Future<PermissionsModel> fetchPermissionsStatus() async {
    state = const PermissionsModel(
      haveUsageAccessPermission: false,
      haveDisplayOverlayPermission: false,
      haveNotificationPermission: false,
      haveAlarmsPermission: false,
    );
    return state;
  }

  @override
  void askUsageAccessPermission() {
    state = state.copyWith(haveUsageAccessPermission: true);
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  testWidgets(
    'setup starts at permissions after revocation, explains grants and waits for essentials',
    (tester) async {
      final messenger =
          TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
      const channel = MethodChannel('com.mindful.android.methodchannel.fg');
      messenger.setMockMethodCallHandler(
        channel,
        (call) async =>
            call.method == 'getDeviceInfo' ? {'sdkVersion': 34} : false,
      );
      addTearDown(() => messenger.setMockMethodCallHandler(channel, null));
      await MethodChannelService.instance.init();
      final permissions = SetupPermissions();
      await tester.pumpWidget(
        ProviderScope(
          overrides: [permissionProvider.overrideWith((ref) => permissions)],
          child: MaterialApp(
            theme: blockingTheme(),
            supportedLocales: AppLocalizations.supportedLocales,
            localizationsDelegates: const [
              AppLocalizations.delegate,
              GlobalMaterialLocalizations.delegate,
              GlobalWidgetsLocalizations.delegate,
              GlobalCupertinoLocalizations.delegate,
            ],
            home: const OnboardingScreen(isOnboardingDone: true),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Ready to focus?'), findsOneWidget);
      final finish = find.ancestor(
        of: find.text('Finish setup'),
        matching: find.byType(FilledButton),
      );
      expect(tester.widget<FilledButton>(finish).onPressed, isNull);
      final locale = AppLocalizations.of(
        tester.element(find.byType(OnboardingScreen)),
      )!;
      await tester.tap(find.text(locale.permission_usage_title));
      await tester.pumpAndSettle();
      expect(find.text(locale.permission_usage_info), findsOneWidget);
      await tester.ensureVisible(
        find.text(locale.permission_button_grant_permission),
      );
      await tester.tap(find.text(locale.permission_button_grant_permission));
      await tester.pumpAndSettle();
      expect(permissions.state.haveUsageAccessPermission, isTrue);
      expect(tester.widget<FilledButton>(finish).onPressed, isNull);
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox());
    },
  );
}

