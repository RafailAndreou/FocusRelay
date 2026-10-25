// Modified by Rafail Andreou, 2026-10-26, for FocusRelay. Original notices retained.
// SPDX-License-Identifier: GPL-2.0-only
import 'dart:async';
import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

class RuleApp {
  const RuleApp(this.package, this.label, [this.icon]);
  final String package, label;
  final Uint8List? icon;
}

class RuleConfig {
  RuleConfig({
    required this.id,
    required this.name,
    required this.kind,
    required this.packages,
    this.enabled = true,
    this.minutes = 30,
    this.shared = true,
    this.zone = 'UTC',
    Set<int>? days,
    List<Map<String, String>>? times,
    this.startAtMs = 0,
    this.endAtMs = 0,
  }) : days = days ?? {1, 2, 3, 4, 5},
       times =
           times ??
           [
             {'start': '09:00', 'end': '12:00'},
           ];
  final String id, name, kind, zone;
  final Set<String> packages;
  final bool enabled, shared;
  final int minutes, startAtMs, endAtMs;
  final Set<int> days;
  final List<Map<String, String>> times;
  factory RuleConfig.fromMap(Map<String, dynamic> m) => RuleConfig(
    id: m['id'] as String,
    name: m['name'] as String,
    kind: m['kind'] as String,
    packages: Set<String>.from(m['packages'] as List),
    enabled: m['enabled'] != false,
    minutes: (m['minutes'] as num?)?.toInt() ?? 30,
    shared: m['shared'] != false,
    zone: m['zone'] as String? ?? 'UTC',
    days: Set<int>.from(m['days'] as List? ?? []),
    times: (m['times'] as List? ?? [])
        .map((v) => Map<String, String>.from(v as Map))
        .toList(),
    startAtMs: (m['startAtMs'] as num?)?.toInt() ?? 0,
    endAtMs: (m['endAtMs'] as num?)?.toInt() ?? 0,
  );
  Map<String, dynamic> toMap() => {
    'id': id,
    'name': name,
    'kind': kind,
    'packages': packages.toList(),
    'enabled': enabled,
    'minutes': minutes,
    'shared': shared,
    'zone': zone,
    'days': days.toList(),
    'times': times,
    'startAtMs': startAtMs,
    'endAtMs': endAtMs,
  };
}

class RuleEntry {
  const RuleEntry(
    this.rule,
    this.phase,
    this.usedSeconds,
    this.blocked,
    this.startMs,
    this.endMs, [
    this.windows = const [],
  ]);
  final RuleConfig rule;
  final String phase;
  final int usedSeconds;
  final Set<String> blocked;
  final int? startMs, endMs;
  final List<Map<String, int>> windows;
  bool get blocking => phase == 'active';
  bool get paused => !rule.enabled;
}

class BlockingController extends ChangeNotifier {
  static const channel = MethodChannel('com.mindful.android.remote');
  List<RuleApp> apps = [];
  List<RuleEntry> entries = [];
  List<Map<String, dynamic>> activity = [];
  Map<String, dynamic> remote = {};
  Map<String, int> usage = {};
  Set<String> quickPackages = {};
  int quickMinutes = 30, screenSeconds = 0;
  String zone = 'UTC';
  bool loading = true,
      busy = false,
      usagePermission = true,
      overlayPermission = true,
      exactAlarms = true;
  String? error;
  // Toggles do not put unrelated controls into their disabled visual state.
  final Map<String, bool> pendingRuleToggles = {};
  Completer<void>? _refreshDone;
  bool _refreshing = false, _disposed = false, _appsLoaded = false;
  DateTime now = DateTime.now();
  List<RuleEntry> get rules => entries
      .where((e) => e.rule.kind != 'quick' || e.phase == 'active')
      .toList();
  List<RuleEntry> get quickBlocks =>
      entries.where((e) => e.rule.kind == 'quick' && e.blocking).toList();
  RuleApp app(String package) =>
      apps.where((a) => a.package == package).firstOrNull ??
      RuleApp(package, package.split('.').last);
  List<RuleApp> selectedApps(Iterable<String> packages) =>
      packages.map(app).toList();
  String label(String package) =>
      apps.where((a) => a.package == package).firstOrNull?.label ?? package;
  Future<void> refresh() async {
    if (_disposed) return;
    if (_refreshing) {
      await _refreshDone?.future;
      return;
    }
    _refreshing = true;
    final refreshDone = _refreshDone = Completer<void>();
    try {
      final m = await channel.invokeMapMethod<String, dynamic>('localState');
      if (_disposed || m == null) return;
      now = DateTime.fromMillisecondsSinceEpoch(
        (m['nowMs'] as num?)?.toInt() ?? DateTime.now().millisecondsSinceEpoch,
      );
      zone = m['zone'] as String? ?? 'UTC';
      usagePermission = m['usagePermission'] == true;
      overlayPermission = m['overlayPermission'] == true;
      exactAlarms = m['exactAlarms'] == true;
      screenSeconds = (m['screenSeconds'] as num?)?.toInt() ?? 0;
      quickMinutes = (m['quickMinutes'] as num?)?.toInt() ?? 30;
      quickPackages = Set<String>.from(m['quickPackages'] as List? ?? []);
      usage = (m['usage'] as Map? ?? {}).map(
        (k, v) => MapEntry(k.toString(), (v as num).toInt()),
      );
      entries = (m['rules'] as List? ?? []).map((item) {
        final v = item as Map;
        return RuleEntry(
          RuleConfig.fromMap(
            jsonDecode(v['rule'] as String) as Map<String, dynamic>,
          ),
          v['phase'] as String,
          (v['usedSeconds'] as num).toInt(),
          Set<String>.from(v['blockedPackages'] as List? ?? []),
          (v['startMs'] as num?)?.toInt(),
          (v['endMs'] as num?)?.toInt(),
          (v['windows'] as List? ?? [])
              .map(
                (w) => (w as Map).map(
                  (k, v) => MapEntry(k.toString(), (v as num).toInt()),
                ),
              )
              .toList(),
        );
      }).toList();
      activity = (m['activity'] as List? ?? [])
          .map((v) => Map<String, dynamic>.from(v as Map))
          .toList();
      error = null;
      if (!_appsLoaded) {
        final list = await channel.invokeListMethod<dynamic>('localApps') ?? [];
        if (_disposed) return;
        apps = list
            .map(
              (v) => RuleApp(
                v['packageName'] as String,
                v['label'] as String,
                v['icon'] as Uint8List?,
              ),
            )
            .toList();
        _appsLoaded = true;
      }
      remote = await channel.invokeMapMethod<String, dynamic>('status') ?? {};
    } on PlatformException catch (e) {
      error = e.message ?? 'Could not load blocking rules';
    } catch (e) {
      error = 'Could not load blocking rules. Try again.';
    } finally {
      _refreshing = false;
      refreshDone.complete();
      loading = false;
      if (!_disposed) notifyListeners();
    }
  }

  Future<bool> action(String method, [Map<String, dynamic>? args]) async {
    if (method == 'toggleLocalRule') {
      return _toggleRule(args!);
    }
    if (busy) return false;
    busy = true;
    error = null;
    notifyListeners();
    try {
      await channel.invokeMethod<dynamic>(method, args);
      await refresh();
      return true;
    } on PlatformException catch (e) {
      error = e.message ?? 'Could not save changes';
      return false;
    } catch (e) {
      error = 'Could not save changes. Try again.';
      return false;
    } finally {
      busy = false;
      if (!_disposed) notifyListeners();
    }
  }

  Future<bool> _toggleRule(Map<String, dynamic> args) async {
    final id = args['id'] as String;
    if (busy || pendingRuleToggles.containsKey(id)) return false;
    pendingRuleToggles[id] = args['enabled'] as bool;
    error = null;
    notifyListeners();
    try {
      await channel.invokeMethod<dynamic>('toggleLocalRule', args);
      // A poll started before this write may contain the old value. Wait for
      // it, then fetch a fresh snapshot before dropping the pending value.
      await _refreshDone?.future;
      await refresh();
      return true;
    } on PlatformException catch (e) {
      error = e.message ?? 'Could not update rule';
      return false;
    } catch (e) {
      error = 'Could not update rule. Try again.';
      return false;
    } finally {
      pendingRuleToggles.remove(id);
      if (!_disposed) notifyListeners();
    }
  }

  @override
  void dispose() {
    _disposed = true;
    super.dispose();
  }
}

