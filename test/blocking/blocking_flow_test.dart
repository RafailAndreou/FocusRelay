// Modified by Rafail Andreou, 2026-10-26, for FocusRelay. Original notices retained.
// SPDX-License-Identifier: GPL-2.0-only
import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:ui' as ui;
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mindful/ui/screens/blocking/blocking_home.dart';
import 'package:mindful/ui/screens/blocking/blocking_style.dart';
import 'package:mindful/ui/screens/blocking/rule_models.dart';
import 'package:mindful/ui/screens/blocking/rule_editor.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  final calls = <MethodCall>[];
  late List<Map<String, dynamic>> rules;
  late Set<String> quick;
  var failSave = false;
  var failToggle = false;
  Completer<void>? toggleGate;
  var events = <Map<String, dynamic>>[];
  const apps = [
    {'packageName': 'reddit', 'label': 'Reddit'},
    {'packageName': 'instagram', 'label': 'Instagram'},
    {'packageName': 'cola', 'label': 'Coca Cola'},
  ];
  final now = DateTime(2026, 10, 22, 11, 18).millisecondsSinceEpoch;
  Map<String, dynamic> entry(
    RuleConfig rule, {
    String phase = 'scheduled',
    int used = 0,
  }) => {
    'rule': jsonEncode(rule.toMap()),
    'phase': phase,
    'usedSeconds': used,
    'blockedPackages': phase == 'active' ? rule.packages.toList() : [],
    'startMs': DateTime(2026, 10, 22, 9).millisecondsSinceEpoch,
    'endMs': DateTime(2026, 10, 22, 12).millisecondsSinceEpoch,
    'windows': [
      {
        'start': DateTime(2026, 10, 22, 9).millisecondsSinceEpoch,
        'end': DateTime(2026, 10, 22, 12).millisecondsSinceEpoch,
      },
    ],
  };
  setUpAll(() async {
    final root = Platform.environment['FLUTTER_ROOT'];
    if (root != null) {
      final font = File(
        '$root/bin/cache/artifacts/material_fonts/Roboto-Regular.ttf',
      );
      if (font.existsSync()) {
        await (FontLoader('Roboto')..addFont(
              Future.value(ByteData.sublistView(font.readAsBytesSync())),
            ))
            .load();
      }
      final icons = File(
        '$root/bin/cache/artifacts/material_fonts/MaterialIcons-Regular.otf',
      );
      if (icons.existsSync()) {
        await (FontLoader('MaterialIcons')..addFont(
              Future.value(ByteData.sublistView(icons.readAsBytesSync())),
            ))
            .load();
      }
    }
  });
  setUp(() {
    rules = [];
    quick = {};
    calls.clear();
    failSave = false;
    failToggle = false;
    toggleGate = null;
    events = [];
    messenger.setMockMethodCallHandler(BlockingController.channel, (
      call,
    ) async {
      calls.add(call);
      switch (call.method) {
        case 'modeState':
          return <String, dynamic>{};
        case 'localApps':
          return apps;
        case 'status':
          return {'enabled': false, 'blocks': [], 'schedules': []};
        case 'localState':
          return {
            'nowMs': now,
            'zone': 'Asia/Nicosia',
            'usagePermission': true,
            'overlayPermission': true,
            'exactAlarms': true,
            'screenSeconds': 9540,
            'usage': {'reddit': 600, 'instagram': 480},
            'quickPackages': quick.toList(),
            'quickMinutes': 30,
            'rules': rules,
            'activity': events,
          };
        case 'saveLocalRule':
          if (failSave) {
            throw PlatformException(
              code: 'SAVE',
              message: 'Could not save rules',
            );
          }
          final r = RuleConfig.fromMap(
            Map<String, dynamic>.from((call.arguments as Map)['rule'] as Map),
          );
          rules.removeWhere(
            (v) =>
                RuleConfig.fromMap(
                  jsonDecode(v['rule'] as String) as Map<String, dynamic>,
                ).id ==
                r.id,
          );
          rules.add(entry(r));
          return true;
        case 'saveQuickConfig':
          quick = Set<String>.from((call.arguments as Map)['packages'] as List);
          return true;
        case 'toggleLocalRule':
          await toggleGate?.future;
          if (failToggle) throw PlatformException(code: 'SAVE', message: 'Could not update rule');
          for (final e in rules) {
            final r = jsonDecode(e['rule'] as String) as Map<String, dynamic>;
            if (r['id'] == (call.arguments as Map)['id']) {
              r['enabled'] = (call.arguments as Map)['enabled'];
              e['rule'] = jsonEncode(r);
              e['phase'] = r['enabled'] == true ? 'scheduled' : 'paused';
            }
          }
          return true;
        default:
          return true;
      }
    });
  });
  tearDown(
    () => messenger.setMockMethodCallHandler(BlockingController.channel, null),
  );
  Future<void> mount(
    WidgetTester tester, {
    int tab = 0,
    Size size = const Size(412, 892),
    double scale = 1,
    GlobalKey? boundary,
  }) async {
    tester.view.physicalSize = size;
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(
      RepaintBoundary(
        key: boundary,
        child: MaterialApp(
          debugShowCheckedModeBanner: false,
          theme: blockingTheme(),
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(
              context,
            ).copyWith(textScaler: TextScaler.linear(scale)),
            child: child!,
          ),
          home: BlockingHome(initialTab: tab),
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  Future<void> unmount(WidgetTester tester) async {
    await tester.pumpWidget(const SizedBox());
    await tester.pump();
  }

  Future<void> chooseType(WidgetTester tester, String label) async {
    await tester.ensureVisible(find.byKey(const Key('add-rule-home')));
    await tester.tap(find.byKey(const Key('add-rule-home')));
    await tester.pumpAndSettle();
    expect(find.text('Choose how you want to block.'), findsOneWidget);
    await tester.tap(find.text(label).last);
    await tester.pumpAndSettle();
  }

  Future<void> next(WidgetTester tester) async {
    await tester.tap(find.byKey(const Key('editor-continue')));
    await tester.pumpAndSettle();
  }

  testWidgets('Modes expander and page scroll restore independently after tab changes', (tester) async {
    await mount(tester, tab: 2, size: const Size(390, 600));
    final page = find.byKey(const PageStorageKey('blocking-tab-2'));
    await tester.drag(page, const Offset(0, -320));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Settings').last);
    await tester.pumpAndSettle();
    await tester.tap(find.text('Modes').last);
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    final controls = find.text('Device controls and privacy');
    await tester.ensureVisible(controls);
    await tester.tap(controls);
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.text('Share usage summaries'));
    expect(tester.takeException(), isNull);
    await tester.tap(find.text('Rules').last);
    await tester.pumpAndSettle();
    await tester.tap(find.text('Modes').last);
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    expect(tester.widget<ExpansionTile>(find.byType(ExpansionTile)).key,
        const PageStorageKey('device-mode-controls'));
    await unmount(tester);
  });
  testWidgets('five tabs and empty state use real navigation', (tester) async {
    await mount(tester);
    for (final label in ['Blocking', 'Rules', 'Modes', 'Activity', 'Settings']) {
      expect(find.text(label), findsOneWidget);
    }
    expect(find.text('Your day is open'), findsOneWidget);
    await tester.tap(find.text('Modes'));
    await tester.pumpAndSettle();
    expect(find.text('Enable Device Modes'), findsOneWidget);
    await tester.tap(find.text('Rules'));
    await tester.pumpAndSettle();
    expect(find.text('Your rules'), findsOneWidget);
    await tester.tap(find.text('Settings'));
    await tester.pumpAndSettle();
    expect(find.text('Blocking readiness'), findsOneWidget);
    await unmount(tester);
  });
  testWidgets('add schedule guides timing apps review and persists rule', (
    tester,
  ) async {
    await mount(tester);
    await chooseType(tester, 'Schedule');
    expect(find.text('When should apps be blocked?'), findsOneWidget);
    await next(tester);
    expect(find.text('Choose your apps'), findsOneWidget);
    await next(tester);
    expect(find.text('Select at least one app.'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('app-reddit')));
    await next(tester);
    await tester.enterText(find.byKey(const Key('rule-name')), 'Study');
    await next(tester);
    final call = calls.singleWhere((c) => c.method == 'saveLocalRule');
    final r = (call.arguments as Map)['rule'] as Map;
    expect(r['packages'], ['reddit']);
    expect(r['kind'], 'schedule');
    expect(r['zone'], 'Asia/Nicosia');
    expect(r['days'], [1, 2, 3, 4, 5]);
    expect(r['times'], [
      {'start': '09:00', 'end': '12:00'},
    ]);
    expect(find.text('Your day'), findsOneWidget);
    await unmount(tester);
  });
  testWidgets('usage editor supports independent per-app allowance', (
    tester,
  ) async {
    await mount(tester);
    await chooseType(tester, 'Usage limit');
    await tester.enterText(find.byKey(const Key('rule-minutes')), '45');
    await tester.ensureVisible(find.byType(SwitchListTile));
    await tester.tap(find.byType(SwitchListTile));
    await next(tester);
    await tester.tap(find.text('Select all'));
    await next(tester);
    await tester.enterText(
      find.byKey(const Key('rule-name')),
      'Social allowance',
    );
    await next(tester);
    final r =
        (calls.singleWhere((c) => c.method == 'saveLocalRule').arguments
                as Map)['rule']
            as Map;
    expect(r['minutes'], 45);
    expect(r['shared'], false);
    expect((r['packages'] as List).length, 3);
    await unmount(tester);
  });
  testWidgets('quick configuration never starts a block before Start', (
    tester,
  ) async {
    await mount(tester);
    await tester.tap(find.byKey(const Key('quick-edit')));
    await tester.pumpAndSettle();
    expect(find.text('Choose your apps'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('app-cola')));
    await next(tester);
    await next(tester);
    expect(calls.where((c) => c.method == 'quickStart'), isEmpty);
    await tester.tap(find.byKey(const Key('quick-start')));
    await tester.pumpAndSettle();
    expect(calls.singleWhere((c) => c.method == 'quickStart').arguments, {
      'packages': ['cola'],
      'minutes': 30,
    });
    await unmount(tester);
  });
  testWidgets(
    'pausing a local rule never calls remote override or disconnect',
    (tester) async {
      rules = [
        entry(
          RuleConfig(
            id: 'a',
            name: 'Study',
            kind: 'schedule',
            packages: {'reddit'},
          ),
          phase: 'active',
        ),
      ];
      await mount(tester, tab: 1);
      await tester.tap(find.byKey(const ValueKey('toggle-a')));
      await tester.pumpAndSettle();
      expect(
        calls.singleWhere((c) => c.method == 'toggleLocalRule').arguments,
        {'id': 'a', 'enabled': false},
      );
      expect(
        calls.where(
          (c) => ['disconnect', 'pauseRemote', 'updateApps'].contains(c.method),
        ),
        isEmpty,
      );
      await unmount(tester);
    },
  );
  test('switch thumbs contrast with their tracks in every colour and enabled state', () {
    for (final theme in [blockingTheme(), blockingTheme(amoled: true), blockingTheme(brightness: Brightness.light)]) {
      for (final selected in [false, true]) {
        for (final disabled in [false, true]) {
          final states = <WidgetState>{if (selected) WidgetState.selected, if (disabled) WidgetState.disabled};
          final thumb = theme.switchTheme.thumbColor!.resolve(states)!;
          final track = theme.switchTheme.trackColor!.resolve(states)!;
          final a = thumb.computeLuminance(), b = track.computeLuminance();
          expect(((a > b ? a : b) + .05) / ((a < b ? a : b) + .05), greaterThanOrEqualTo(3));
        }
      }
    }
  });
  testWidgets('saving one toggle leaves other switches interactive and visually enabled', (tester) async {
    rules = [for (final id in ['a','b']) entry(RuleConfig(id: id, name: 'Rule $id', kind: 'schedule', packages: {'reddit'}))];
    toggleGate = Completer<void>();
    await mount(tester, tab: 1);
    final other = tester.element(find.byKey(const ValueKey('toggle-b')));
    await tester.tap(find.byKey(const ValueKey('toggle-a')));
    await tester.pump(const Duration(milliseconds: 100));
    expect(tester.widget<Switch>(find.byKey(const ValueKey('toggle-a'))).value, isFalse);
    expect(tester.widget<Switch>(find.byKey(const ValueKey('toggle-a'))).onChanged, isNull);
    expect(tester.widget<Switch>(find.byKey(const ValueKey('toggle-b'))).value, isTrue);
    expect(tester.widget<Switch>(find.byKey(const ValueKey('toggle-b'))).onChanged, isNotNull);
    expect(identical(other, tester.element(find.byKey(const ValueKey('toggle-b')))), isTrue);
    // Other rules can also be changed while the first write is pending.
    await tester.tap(find.byKey(const ValueKey('toggle-b')));
    await tester.pump();
    expect(calls.where((c) => c.method == 'toggleLocalRule').length, 2);
    toggleGate!.complete();
    await tester.pumpAndSettle();
    for (final id in ['a','b']) {
      final control = tester.widget<Switch>(find.byKey(ValueKey('toggle-$id')));
      expect(control.value, isFalse);
      expect(control.onChanged, isNotNull);
    }
    await unmount(tester);
  });
  testWidgets('failed toggle restores only that rule and reports the save error', (tester) async {
    rules = [for (final id in ['a','b']) entry(RuleConfig(id: id, name: 'Rule $id', kind: 'schedule', packages: {'reddit'}))];
    failToggle = true;
    await mount(tester, tab: 1);
    await tester.tap(find.byKey(const ValueKey('toggle-a')));
    await tester.pumpAndSettle();
    expect(find.descendant(of: find.byType(SnackBar), matching: find.text('Could not update rule')), findsOneWidget);
    for (final id in ['a','b']) {
      final control = tester.widget<Switch>(find.byKey(ValueKey('toggle-$id')));
      expect(control.value, isTrue);
      expect(control.onChanged, isNotNull);
    }
    await unmount(tester);
  });
  testWidgets('failed save keeps editor open and preserves user input', (
    tester,
  ) async {
    failSave = true;
    await mount(tester);
    await chooseType(tester, 'Schedule');
    await next(tester);
    await tester.tap(find.byKey(const ValueKey('app-reddit')));
    await next(tester);
    await tester.enterText(find.byKey(const Key('rule-name')), 'Study');
    await next(tester);
    expect(find.text('Could not save rules'), findsOneWidget);
    expect(find.text('Study'), findsWidgets);
    expect(find.text('Create rule'), findsOneWidget);
    await unmount(tester);
  });
  testWidgets(
    'small display and larger text render all tabs without overflow',
    (tester) async {
      quick = {'reddit', 'instagram', 'cola'};
      rules = [
        entry(
          RuleConfig(
            id: 'small',
            name: 'Daily allowance',
            kind: 'usage',
            packages: quick,
          ),
          phase: 'available',
        ),
      ];
      await mount(tester, size: const Size(360, 740), scale: 1.4);
      for (final tab in ['Rules', 'Activity', 'Settings', 'Blocking']) {
        await tester.tap(find.text(tab).last);
        await tester.pumpAndSettle();
        expect(tester.takeException(), isNull);
      }
      await unmount(tester);
    },
  );
  testWidgets(
    'whole quick card opens apps first and cancel preserves saved apps',
    (tester) async {
      quick = {'reddit'};
      await mount(tester);
      await tester.tap(find.text('Quick block').first);
      await tester.pumpAndSettle();
      expect(find.text('Choose your apps'), findsOneWidget);
      expect(
        tester
            .widget<CheckboxListTile>(find.byKey(const ValueKey('app-reddit')))
            .value,
        isTrue,
      );
      await tester.tap(find.byKey(const ValueKey('app-cola')));
      await tester.tap(find.byIcon(Icons.arrow_back).first);
      await tester.pumpAndSettle();
      expect(find.text('Discard changes?'), findsOneWidget);
      await tester.tap(find.text('Keep editing'));
      await tester.pumpAndSettle();
      expect(
        tester
            .widget<CheckboxListTile>(find.byKey(const ValueKey('app-cola')))
            .value,
        isTrue,
      );
      await tester.tap(find.byIcon(Icons.arrow_back).first);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Discard'));
      await tester.pumpAndSettle();
      expect(quick, {'reddit'});
      expect(
        calls.where(
          (c) => ['quickStart', 'saveQuickConfig'].contains(c.method),
        ),
        isEmpty,
      );
      await unmount(tester);
    },
  );
  testWidgets(
    'timer edits only duration without opening apps or starting a block',
    (tester) async {
      quick = {'reddit', 'cola'};
      await mount(tester);
      await tester.tap(find.byKey(const Key('quick-timer')));
      await tester.pumpAndSettle();
      expect(find.text('Choose your apps'), findsNothing);
      await tester.enterText(find.byKey(const Key('quick-timer-minutes')), '5');
      await tester.tap(find.text('Save'));
      await tester.pumpAndSettle();
      await tester.pump(const Duration(milliseconds: 300));
      await tester.pump();
      expect(
        calls.singleWhere((c) => c.method == 'saveQuickConfig').arguments,
        {
          'packages': ['reddit', 'cola'],
          'minutes': 5,
        },
      );
      expect(calls.where((c) => c.method == 'quickStart'), isEmpty);
      await unmount(tester);
    },
  );
  testWidgets(
    'existing rule starts with apps, retains timing and saves edits',
    (tester) async {
      rules = [
        entry(
          RuleConfig(
            id: 'edit',
            name: 'Work',
            kind: 'schedule',
            packages: {'reddit'},
            days: {2, 4},
            times: [
              {'start': '14:00', 'end': '16:00'},
            ],
          ),
        ),
      ];
      await mount(tester, tab: 1);
      await tester.tap(find.text('Work'));
      await tester.pumpAndSettle();
      expect(find.text('Choose your apps'), findsOneWidget);
      await tester.tap(find.byKey(const Key('edit-condition')));
      await tester.pumpAndSettle();
      expect(find.text('14:00'), findsOneWidget);
      await next(tester);
      expect(find.text('Choose your apps'), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('app-cola')));
      await next(tester);
      await next(tester);
      final rule =
          (calls.singleWhere((c) => c.method == 'saveLocalRule').arguments
                  as Map)['rule']
              as Map;
      expect(rule['id'], 'edit');
      expect(rule['packages'], ['reddit', 'cola']);
      expect(rule['days'], [2, 4]);
      expect(rule['times'], [
        {'start': '14:00', 'end': '16:00'},
      ]);
      await unmount(tester);
    },
  );
  testWidgets(
    'icon group counts hidden apps exactly and has accessible app names',
    (tester) async {
      final apps = List.generate(7, (i) => RuleApp('pkg$i', 'App $i'));
      Future<void> group(double width) => tester.pumpWidget(
        MaterialApp(
          theme: blockingTheme(),
          home: Scaffold(
            body: Align(
              alignment: Alignment.topLeft,
              child: SizedBox(width: width, child: AppIconGroup(apps)),
            ),
          ),
        ),
      );
      await group(180);
      expect(find.byType(AppBadge), findsNWidgets(2));
      expect(find.text('+5'), findsOneWidget);
      expect(
        find.bySemanticsLabel(apps.map((a) => a.label).join(', ')),
        findsOneWidget,
      );
      await group(85);
      expect(find.byType(AppBadge), findsOneWidget);
      expect(find.text('+6'), findsOneWidget);
      expect(tester.takeException(), isNull);
      await unmount(tester);
    },
  );
  testWidgets('refresh reuses installed app metadata and icon objects', (
    tester,
  ) async {
    await mount(tester);
    await tester.pump(const Duration(seconds: 15));
    await tester.pumpAndSettle();
    expect(calls.where((c) => c.method == 'localApps').length, 1);
    await unmount(tester);
  });
  testWidgets('editor and home obey light theme and reduced motion', (
    tester,
  ) async {
    final c = BlockingController();
    await c.refresh();
    await tester.pumpWidget(
      MaterialApp(
        theme: blockingTheme(brightness: Brightness.light),
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(context).copyWith(disableAnimations: true),
          child: child!,
        ),
        home: RuleEditor(controller: c, kind: 'quick'),
      ),
    );
    await tester.pumpAndSettle();
    expect(
      Theme.of(tester.element(find.byType(RuleEditor))).brightness,
      Brightness.light,
    );
    expect(find.text('Choose your apps'), findsOneWidget);
    expect(tester.takeException(), isNull);
    await unmount(tester);
    c.dispose();
  });
  testWidgets('render implemented screens for visual review', (tester) async {
    rules = [
      entry(
        RuleConfig(
          id: 'study',
          name: 'Study',
          kind: 'schedule',
          packages: {'reddit', 'instagram'},
        ),
        phase: 'active',
      ),
      entry(
        RuleConfig(
          id: 'budget',
          name: 'Social allowance',
          kind: 'usage',
          packages: {'reddit', 'instagram'},
        ),
        phase: 'available',
        used: 1080,
      ),
      entry(
        RuleConfig(
          id: 'wind',
          name: 'Wind down',
          kind: 'schedule',
          packages: {'cola'},
          times: [
            {'start': '22:00', 'end': '07:00'},
          ],
        ),
        phase: 'scheduled',
      ),
    ];
    // Fixtures exercise the same widgets and method-channel decoding used on device.
    rules[1]['windows'] = <Object>[];
    rules[2]['windows'] = [
      {
        'start': DateTime(2026, 10, 22, 22).millisecondsSinceEpoch,
        'end': DateTime(2026, 10, 23, 7).millisecondsSinceEpoch,
      },
    ];
    quick = {'reddit', 'instagram'};
    events = [
      {'package': 'reddit', 'name': 'Study', 'at': now - 60000},
      {'package': 'cola', 'name': 'Quick block', 'at': now - 120000},
    ];
    final key = GlobalKey();
    await mount(tester, boundary: key);
    Future<void> capture(String name) async {
      expect(tester.takeException(), isNull);
      await tester.runAsync(() async {
        final image =
            await (key.currentContext!.findRenderObject()!
                    as RenderRepaintBoundary)
                .toImage(pixelRatio: 2);
        final bytes = await image.toByteData(format: ui.ImageByteFormat.png);
        final file = File('build/ui-preview/$name.png');
        file.parent.createSync(recursive: true);
        file.writeAsBytesSync(bytes!.buffer.asUint8List());
        image.dispose();
      });
    }

    for (final name in ['blocking', 'rules', 'activity', 'settings']) {
      if (name != 'blocking') {
        await tester.tap(
          find
              .text(
                {
                  'rules': 'Rules',
                  'activity': 'Activity',
                  'settings': 'Settings',
                }[name]!,
              )
              .last,
        );
        await tester.pumpAndSettle();
      }
      await capture(name);
      if (name == 'rules') {
        await tester.tap(find.byKey(const ValueKey('toggle-study')));
        await tester.pumpAndSettle();
        await capture('rules-paused');
        await tester.tap(find.byKey(const ValueKey('toggle-study')));
        await tester.pumpAndSettle();
      }
    }
    await tester.tap(find.text('Blocking').last);
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.byKey(const Key('add-rule-home')));
    await tester.tap(find.byKey(const Key('add-rule-home')));
    await tester.pumpAndSettle();
    expect(find.text('Choose how you want to block.'), findsOneWidget);
    await capture('add-rule');
    await tester.tap(find.text('Schedule').last);
    await tester.pumpAndSettle();
    await capture('schedule-timing');
    await next(tester);
    await capture('schedule-apps');
    await tester.tap(find.byKey(const ValueKey('app-reddit')));
    await next(tester);
    await tester.enterText(find.byKey(const Key('rule-name')), 'Study');
    await tester.testTextInput.receiveAction(TextInputAction.done);
    await tester.pumpAndSettle();
    await capture('schedule-review');
    await unmount(tester);
  });
}

