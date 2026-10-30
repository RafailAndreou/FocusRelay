// Copyright 2026 Rafail Andreou. SPDX-License-Identifier: GPL-2.0-only
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mindful/ui/screens/blocking/device_modes.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('com.mindful.android.remote');
  final calls = <MethodCall>[];
  late Map<String, dynamic> state;
  setUp(() {
    calls.clear();
    state = {
      'connected': true,
      'enabled': false,
      'actionsConsent': false,
      'usageSharing': false,
      'apps': [
        {'packageName': 'com.example.chat', 'label': 'Chat'},
        {'packageName': 'com.example.video', 'label': 'Video'},
      ],
      'eligiblePackages': ['com.example.video'],
      'deviceModes': {'templates': []},
      'instances': [],
      'exceptions': [],
      'permissions': {},
    };
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
          calls.add(call);
          if (call.method == 'modeState') return state;
          if (call.method == 'modeSetup') {
            state.addAll(Map<String, dynamic>.from(call.arguments as Map));
          }
          if (call.method == 'emergencyEndModes') state['enabled'] = false;
          return true;
        });
  });
  Future<void> panel(WidgetTester tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(body: SingleChildScrollView(child: DeviceModesPanel())),
      ),
    );
    await tester.pumpAndSettle();
  }

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null);
  });
  testWidgets(
    'local consent is separate from device actions and usage; emergency stop is local',
    (tester) async {
      await panel(tester);
      expect(
        tester.widget<SwitchListTile>(find.byType(SwitchListTile).first).value,
        false,
      );
      await tester.tap(find.text('Enable Device Modes'));
      await tester.pumpAndSettle();
      final setup =
          calls.lastWhere((c) => c.method == 'modeSetup').arguments as Map;
      expect(setup['enabled'], true);
      expect(setup['actionsConsent'], false);
      expect(setup['usageSharing'], false);
      await tester.tap(find.text('Emergency: end all device modes'));
      await tester.pumpAndSettle();
      expect(calls.any((c) => c.method == 'emergencyEndModes'), true);
      expect(calls.where((c) => c.method == 'modeCommand'), isEmpty);
      await tester.pumpWidget(const SizedBox());
    },
  );
  testWidgets('observed modes expose local end, exceptions and Clock handoff', (
    tester,
  ) async {
    state['enabled'] = true;
    state['instances'] = [
      {
        'id': 'i',
        'name': 'Thesis',
        'mode': 'allow_list',
        'phase': 'active',
        'allowedPackages': ['com.example.chat'],
        'blockedPackages': ['com.example.video'],
        'endAtMs': DateTime.now()
            .add(const Duration(minutes: 30))
            .millisecondsSinceEpoch,
        'alarmAtMs': DateTime.now()
            .add(const Duration(minutes: 30))
            .millisecondsSinceEpoch,
      },
    ];
    state['exceptions'] = [
      {
        'id': 'e',
        'instanceId': 'i',
        'packageName': 'com.example.video',
        'untilMs': DateTime.now()
            .add(const Duration(minutes: 10))
            .millisecondsSinceEpoch,
      },
    ];
    await panel(tester);
    expect(find.text('Thesis · active'), findsOneWidget);
    expect(find.text('Allowed: Chat'), findsOneWidget);
    await tester.tap(find.byTooltip('Revoke temporary access'));
    await tester.pumpAndSettle();
    final c =
        calls.lastWhere((c) => c.method == 'modeCommand').arguments as Map;
    expect(c['operation'], 'revoke_temporary_app_access');
    expect(c['arguments'], {'id': 'e'});
    await tester.tap(find.text('End Mode'));
    await tester.pumpAndSettle();
    expect(calls.lastWhere((c) => c.method == 'endModeLocally').arguments, {
      'instanceId': 'i',
    });
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox());
  });
  testWidgets(
    'editor requires a nonempty allow-list and excludes protected block targets',
    (tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: DeviceModeEditor(
            apps: [
              {'packageName': 'com.example.chat', 'label': 'Chat'},
              {'packageName': 'com.example.video', 'label': 'Video'},
            ],
            eligible: {'com.example.video'},
          ),
        ),
      );
      await tester.enterText(find.byType(TextField).first, 'Thesis');
      await tester.pump();
      expect(
        tester
            .widget<TextButton>(find.widgetWithText(TextButton, 'Save'))
            .onPressed,
        isNull,
      );
      await tester.tap(find.widgetWithText(CheckboxListTile, 'Chat'));
      await tester.pump();
      expect(
        tester
            .widget<TextButton>(find.widgetWithText(TextButton, 'Save'))
            .onPressed,
        isNotNull,
      );
      await tester.tap(find.byType(DropdownButtonFormField<String>));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Block selected apps').last);
      await tester.pumpAndSettle();
      expect(find.widgetWithText(CheckboxListTile, 'Chat'), findsNothing);
      expect(find.widgetWithText(CheckboxListTile, 'Video'), findsOneWidget);
      await tester.pumpWidget(const SizedBox());
    },
  );
}
