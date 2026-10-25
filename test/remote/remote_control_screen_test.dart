// Modified by Rafail Andreou, 2026-10-26, for FocusRelay. Original notices retained.
import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mindful/ui/screens/remote_control/remote_control_screen.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('com.mindful.android.remote');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  const apps = [
    {'packageName': 'com.instagram.android', 'label': 'Instagram'},
    {'packageName': 'com.example.cola', 'label': 'Coca Cola'},
  ];
  Map<String, dynamic> status({bool enabled = false}) => {
    'enabled': enabled,
    'url': enabled ? 'https://example.workers.dev' : '',
    'message': 'Connected',
    'revision': 2,
    'overlayResult': 'Blocking overlay failed: SecurityException',
    'lastSync': 0,
    'allowed': enabled ? ['com.instagram.android'] : <String>[],
    'blocks': enabled
        ? [
            {'packageName': 'com.instagram.android', 'untilMs': 1900000000000},
          ]
        : <Object>[],
  };
  void prepare(WidgetTester tester) {
    tester.view.physicalSize = const Size(1080, 1920);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    addTearDown(() => messenger.setMockMethodCallHandler(channel, null));
  }

  FilledButton button(WidgetTester tester, String label) =>
      tester.widget<FilledButton>(
        find.ancestor(
          of: find.text(label),
          matching: find.byWidgetPredicate((widget) => widget is FilledButton),
        ),
      );

  testWidgets(
    'pairing supports select all and clear while requiring a selection',
    (tester) async {
      prepare(tester);
      messenger.setMockMethodCallHandler(
        channel,
        (call) async => call.method == 'apps' ? apps : status(),
      );
      await tester.pumpWidget(const MaterialApp(home: RemoteControlScreen()));
      await tester.pump();
      expect(button(tester, 'Enable remote control').onPressed, isNull);
      await tester.tap(find.text('Select all apps'));
      await tester.pump();
      expect(find.text('2 apps selected'), findsOneWidget);
      expect(
        tester
            .widgetList<CheckboxListTile>(find.byType(CheckboxListTile))
            .every((tile) => tile.value == true),
        isTrue,
      );
      expect(button(tester, 'Enable remote control').onPressed, isNotNull);
      await tester.tap(find.text('Clear selection'));
      await tester.pump();
      expect(button(tester, 'Enable remote control').onPressed, isNull);
      await tester.tap(find.byType(CheckboxListTile).first);
      await tester.pump();
      expect(button(tester, 'Enable remote control').onPressed, isNotNull);
      await tester.pumpWidget(const SizedBox());
    },
  );

  testWidgets('connected status displays while app discovery is pending', (
    tester,
  ) async {
    prepare(tester);
    final discovery = Completer<List<Map<String, String>>>();
    messenger.setMockMethodCallHandler(
      channel,
      (call) async =>
          call.method == 'apps' ? discovery.future : status(enabled: true),
    );
    await tester.pumpWidget(const MaterialApp(home: RemoteControlScreen()));
    await tester.pump();
    expect(find.text('Remote control is enabled'), findsOneWidget);
    await tester.tap(find.text('Connection details'));
    await tester.pumpAndSettle();
    expect(find.text('Received revision: 2'), findsOneWidget);
    expect(find.text('Disconnect and clear remote blocks'), findsOneWidget);
    expect(
      find.text(
        'Last blocking attempt: Blocking overlay failed: SecurityException',
      ),
      findsOneWidget,
    );
    await tester.tap(find.text('Connection details'));
    await tester.pumpAndSettle();
    discovery.complete(apps);
    await tester.pumpAndSettle();
    expect(find.text('Instagram'), findsOneWidget);
    await tester.pumpWidget(const SizedBox());
  });

  testWidgets(
    'status polling preserves app selection and never rediscovers apps',
    (tester) async {
      prepare(tester);
      var discoveries = 0;
      var statuses = 0;
      messenger.setMockMethodCallHandler(channel, (call) async {
        if (call.method == 'apps') {
          discoveries++;
          return apps;
        }
        statuses++;
        return status(enabled: true);
      });
      await tester.pumpWidget(const MaterialApp(home: RemoteControlScreen()));
      await tester.pump();
      await tester.tap(find.text('App access'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Select all apps'));
      await tester.pump();
      await tester.pump(const Duration(seconds: 3));
      await tester.pump();
      expect(statuses, greaterThanOrEqualTo(2));
      expect(discoveries, 1);
      expect(button(tester, 'Save app access').onPressed, isNotNull);
      expect(
        tester
            .widgetList<CheckboxListTile>(find.byType(CheckboxListTile))
            .every((tile) => tile.value == true),
        isTrue,
      );
      await tester.pumpWidget(const SizedBox());
    },
  );

  testWidgets(
    'connected device saves all apps without credentials or disconnecting',
    (tester) async {
      prepare(tester);
      final current = status(enabled: true);
      final calls = <MethodCall>[];
      messenger.setMockMethodCallHandler(channel, (call) async {
        calls.add(call);
        if (call.method == 'apps') {
          return apps;
        }
        if (call.method == 'updateApps') {
          current['allowed'] = List<String>.from(
            (call.arguments as Map)['packages'] as List,
          );
          return true;
        }
        return current;
      });
      await tester.pumpWidget(const MaterialApp(home: RemoteControlScreen()));
      await tester.pump();
      await tester.tap(find.text('App access'));
      await tester.pumpAndSettle();
      expect(button(tester, 'Save app access').onPressed, isNull);
      await tester.tap(find.text('Select all apps'));
      await tester.pump();
      await tester.ensureVisible(find.text('Save app access'));
      await tester.tap(find.text('Save app access'));
      await tester.pumpAndSettle();
      final saved = calls.singleWhere((call) => call.method == 'updateApps');
      expect(saved.arguments, {
        'packages': ['com.instagram.android', 'com.example.cola'],
      });
      expect(
        calls.where(
          (call) => call.method == 'connect' || call.method == 'disconnect',
        ),
        isEmpty,
      );
      expect(button(tester, 'Save app access').onPressed, isNull);
      expect(find.text('Remote control is enabled'), findsOneWidget);
      await tester.pumpWidget(const SizedBox());
    },
  );
  testWidgets(
    'schedule status and next transition are visible and pause uses no control secret',
    (tester) async {
      prepare(tester);
      final current = status(enabled: true);
      current['schedulerMode'] = 'exact';
      current['nextTransitionMs'] = 1900000000000;
      current['schedules'] = [
        {
          'id': 'evening',
          'name': 'Evening block',
          'state': 'scheduled',
          'pausedUntilMs': 0,
          'timing': {
            'type': 'weekly',
            'timeZone': 'Asia/Nicosia',
            'daysOfWeek': [1, 2, 3, 4, 5],
            'startTime': '20:00',
            'endTime': '22:00',
          },
        },
      ];
      final calls = <MethodCall>[];
      messenger.setMockMethodCallHandler(channel, (call) async {
        calls.add(call);
        if (call.method == 'apps') {
          return apps;
        }
        if (call.method == 'pauseRemote') {
          ((current['schedules'] as List).first as Map)['pausedUntilMs'] = -1;
          return true;
        }
        return current;
      });
      await tester.pumpWidget(const MaterialApp(home: RemoteControlScreen()));
      await tester.pump();
      await tester.tap(find.text('Connection details'));
      await tester.pumpAndSettle();
      expect(find.text('Local scheduler: exact'), findsOneWidget);
      expect(find.textContaining('Next transition:'), findsOneWidget);
      await tester.tap(find.text('Scheduled blocks'));
      await tester.pumpAndSettle();
      expect(find.text('Evening block'), findsOneWidget);
      await tester.ensureVisible(find.text('Pause'));
      await tester.tap(find.text('Pause'));
      await tester.pumpAndSettle();
      expect(
        calls.singleWhere((call) => call.method == 'pauseRemote').arguments,
        {'targetType': 'schedule', 'id': 'evening'},
      );
      expect(find.text('Resume'), findsOneWidget);
      await tester.pumpWidget(const SizedBox());
    },
  );

  testWidgets('inexact alarm mode shows an honest scheduling limitation', (
    tester,
  ) async {
    prepare(tester);
    final current = status(enabled: true)..['schedulerMode'] = 'inexact';
    messenger.setMockMethodCallHandler(
      channel,
      (call) async => call.method == 'apps' ? apps : current,
    );
    await tester.pumpWidget(const MaterialApp(home: RemoteControlScreen()));
    await tester.pump();
    expect(
      find.text(
        'Alarms & reminders access is off. Android may delay scheduled transitions.',
      ),
      findsOneWidget,
    );
    expect(find.text('Allow precise scheduled blocks'), findsOneWidget);
    await tester.pumpWidget(const SizedBox());
  });
}

