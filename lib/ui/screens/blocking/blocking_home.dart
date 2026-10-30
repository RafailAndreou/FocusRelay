// Modified by Rafail Andreou, 2026-10-26, for FocusRelay. Original notices retained.
// SPDX-License-Identifier: GPL-2.0-only
import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:mindful/config/navigation/app_routes.dart';
import 'blocking_style.dart';
import 'device_modes.dart';
import 'rule_editor.dart';
import 'rule_models.dart';

class BlockingHome extends StatefulWidget {
  const BlockingHome({super.key, this.controller, this.initialTab = 0});
  final BlockingController? controller;
  final int initialTab;
  @override
  State<BlockingHome> createState() => _BlockingHomeState();
}

class _BlockingHomeState extends State<BlockingHome>
    with WidgetsBindingObserver, DesignColors<BlockingHome> {
  late final BlockingController c;
  late int _tab;
  int _filter = 0;
  Timer? _timer;
  @override
  void initState() {
    super.initState();
    c = widget.controller ?? BlockingController();
    _tab = widget.initialTab.clamp(0, 4);
    c.addListener(_changed);
    WidgetsBinding.instance.addObserver(this);
    c.refresh();
    _startTimer();
  }

  void _changed() {
    if (mounted) setState(() {});
  }

  void _startTimer() {
    _timer?.cancel();
    _timer = Timer.periodic(Duration(seconds: 5), (_) => c.refresh());
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      c.refresh();
      _startTimer();
    } else {
      _timer?.cancel();
    }
  }

  @override
  void dispose() {
    _timer?.cancel();
    WidgetsBinding.instance.removeObserver(this);
    c.removeListener(_changed);
    if (widget.controller == null) c.dispose();
    super.dispose();
  }

  Future<void> _act(String name, [Map<String, dynamic>? args]) async {
    final ok = await c.action(name, args);
    if (!ok && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(c.error ?? 'Could not save changes.')),
      );
    }
  }

  Future<void> _open(String route, [Map<String, dynamic>? args]) async {
    await Navigator.of(context).pushNamed(route, arguments: args);
    await c.refresh();
  }

  @override
  Widget build(BuildContext context) => Theme(
    data: Theme.of(context),
    child: Builder(
      builder: (context) => AnnotatedRegion<SystemUiOverlayStyle>(
        value: SystemUiOverlayStyle.light.copyWith(
          statusBarColor: Colors.transparent,
          systemNavigationBarColor: canvas,
          systemNavigationBarIconBrightness: Brightness.light,
        ),
        child: Scaffold(
          body: SafeArea(
            bottom: false,
            child: c.loading
                ? Center(child: CircularProgressIndicator())
                : RefreshIndicator(
                    onRefresh: c.refresh,
                    child: ListView(
                      key: PageStorageKey('blocking-tab-$_tab'),
                      padding: EdgeInsets.fromLTRB(24, 20, 24, 28),
                      children: [
                        Row(
                          children: [
                            Icon(Icons.spa_outlined, color: mint, size: 22),
                            SizedBox(width: 8),
                            Text(
                              'FocusRelay',
                              style: TextStyle(
                                fontSize: 19,
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                          ],
                        ),
                        SizedBox(height: 12),
                        Text(
                          [
                            'Your day',
                            'Your rules',
                            'Modes',
                            'Activity',
                            'Settings',
                          ][_tab],
                          style: Theme.of(context).textTheme.headlineLarge,
                        ),
                        SizedBox(height: 22),
                        if (c.error != null) ...[
                          _message(
                            c.error!,
                            Icons.error_outline,
                            action: 'Retry',
                            onTap: c.refresh,
                          ),
                          SizedBox(height: 16),
                        ],
                        if (_tab != 4 &&
                            (!c.usagePermission || !c.overlayPermission)) ...[
                          _message(
                            'Finish permissions to enable blocking.',
                            Icons.shield_outlined,
                            action: 'Set up',
                            onTap: () => setState(() => _tab = 4),
                          ),
                          SizedBox(height: 16),
                        ],
                        ...switch (_tab) {
                          0 => _blocking(context),
                          1 => _rules(context),
                          2 => [DeviceModesPanel()],
                          3 => _activity(context),
                          _ => _settings(context),
                        },
                      ],
                    ),
                  ),
          ),
          bottomNavigationBar: DecoratedBox(
            decoration: BoxDecoration(
              border: Border(top: BorderSide(color: line)),
            ),
            child: NavigationBar(
              animationDuration: MediaQuery.disableAnimationsOf(context)
                  ? Duration.zero
                  : Duration(milliseconds: 180),
              selectedIndex: _tab,
              onDestinationSelected: (i) => setState(() => _tab = i),
              destinations: [
                NavigationDestination(
                  icon: Icon(Icons.lock_outline),
                  selectedIcon: Icon(Icons.lock),
                  label: 'Blocking',
                ),
                NavigationDestination(
                  icon: Icon(Icons.format_list_bulleted),
                  label: 'Rules',
                ),
                NavigationDestination(icon: Icon(Icons.tune), label: 'Modes'),
                NavigationDestination(
                  icon: Icon(Icons.bar_chart),
                  label: 'Activity',
                ),
                NavigationDestination(
                  icon: Icon(Icons.settings_outlined),
                  selectedIcon: Icon(Icons.settings),
                  label: 'Settings',
                ),
              ],
            ),
          ),
        ),
      ),
    ),
  );

  List<Widget> _blocking(BuildContext context) {
    final quick = c.quickBlocks;
    final timeline = <({RuleEntry entry, int start, int end})>[];
    for (final e in c.rules.where((e) => !e.paused && e.rule.kind != 'usage')) {
      for (final w in e.windows) {
        timeline.add((entry: e, start: w['start']!, end: w['end']!));
      }
    }
    timeline.sort((a, b) => a.start.compareTo(b.start));
    final now = c.now.millisecondsSinceEpoch;
    return [
      Material(
        color: panel,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(20),
          side: BorderSide(color: line),
        ),
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          key: Key('quick-card'),
          onTap: c.busy ? null : () => openRuleEditor(context, c, 'quick'),
          child: Padding(
            padding: EdgeInsets.all(18),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Icon(Icons.bolt_rounded, color: mint),
                    SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        'Quick block',
                        style: TextStyle(
                          fontSize: 18,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ),
                    IconButton(
                      key: Key('quick-edit'),
                      tooltip: 'Configure quick block',
                      onPressed: c.busy
                          ? null
                          : () => openRuleEditor(context, c, 'quick'),
                      icon: Icon(Icons.edit_outlined, size: 20),
                    ),
                  ],
                ),
                Wrap(
                  spacing: 16,
                  runSpacing: 8,
                  crossAxisAlignment: WrapCrossAlignment.center,
                  children: [
                    if (c.quickPackages.isNotEmpty)
                      SizedBox(
                        width: 116,
                        child: AppIconGroup(c.selectedApps(c.quickPackages)),
                      ),
                    TextButton.icon(
                      key: Key('quick-timer'),
                      onPressed: c.busy ? null : () => _quickTimer(context),
                      icon: Icon(Icons.timer_outlined, size: 18),
                      label: Text('Timer · ${c.quickMinutes} min'),
                    ),
                  ],
                ),
                if (c.quickPackages.isEmpty)
                  Text('Choose apps', style: TextStyle(color: muted)),
                SizedBox(height: 16),
                SizedBox(
                  width: double.infinity,
                  child: FilledButton.icon(
                    key: Key('quick-start'),
                    onPressed: c.busy
                        ? null
                        : () {
                            if (c.quickPackages.isEmpty) {
                              openRuleEditor(context, c, 'quick');
                            } else {
                              _act('quickStart', {
                                'packages': c.quickPackages.toList(),
                                'minutes': c.quickMinutes,
                              });
                            }
                          },
                    icon: Icon(Icons.play_arrow_rounded),
                    label: Text(
                      c.busy
                          ? 'Working…'
                          : c.quickPackages.isEmpty
                          ? 'Choose apps'
                          : 'Start block',
                    ),
                  ),
                ),
                for (final e in quick)
                  Padding(
                    padding: EdgeInsets.only(top: 12),
                    child: Row(
                      children: [
                        Expanded(
                          child: Text(
                            'Blocking ${e.rule.packages.length} apps until ${clock(e.rule.endAtMs)}',
                            style: TextStyle(color: mint),
                          ),
                        ),
                        TextButton(
                          onPressed: c.busy
                              ? null
                              : () =>
                                    _act('deleteLocalRule', {'id': e.rule.id}),
                          child: Text('Stop'),
                        ),
                      ],
                    ),
                  ),
              ],
            ),
          ),
        ),
      ),
      SizedBox(height: 28),
      Row(
        children: [
          Expanded(
            child: Text(
              'Today',
              style: TextStyle(fontSize: 21, fontWeight: FontWeight.w700),
            ),
          ),
          OutlinedButton.icon(
            key: Key('add-rule-home'),
            onPressed: () => openRuleOptions(context, c),
            icon: Icon(Icons.add, size: 18),
            label: Text('Add rule'),
          ),
        ],
      ),
      Text(
        MaterialLocalizations.of(context).formatMediumDate(c.now),
        style: TextStyle(color: muted, fontSize: 12),
      ),
      SizedBox(height: 18),
      if (timeline.isEmpty)
        _empty(
          Icons.calendar_today_outlined,
          'Your day is open',
          'Add a schedule to see your blocking times here.',
        ),
      for (var i = 0; i < timeline.length; i++) ...[
        if (timeline[i].start > now && (i == 0 || timeline[i - 1].start <= now))
          _nowRow(),
        _timelineRow(
          context,
          timeline[i].entry,
          timeline[i].start,
          timeline[i].end,
        ),
      ],
      if (timeline.isNotEmpty && timeline.last.start <= now) _nowRow(),
      SizedBox(height: 12),
      Divider(),
      SizedBox(height: 16),
      if (c.rules.any((e) => e.rule.kind == 'usage' && !e.paused)) ...[
        Text(
          'Daily allowances',
          style: TextStyle(fontSize: 19, fontWeight: FontWeight.w700),
        ),
        SizedBox(height: 16),
        for (final e in c.rules.where(
          (e) => e.rule.kind == 'usage' && !e.paused,
        ))
          _budget(context, e),
      ],
      if (c.remote['enabled'] == true) ...[
        _remoteSummary(),
        SizedBox(height: 12),
      ],
      Row(
        children: [
          Expanded(
            child: Text(
              '${c.rules.where((e) => e.blocking).length} ${c.rules.where((e) => e.blocking).length == 1 ? 'rule' : 'rules'} blocking now',
              style: TextStyle(color: muted),
            ),
          ),
          TextButton.icon(
            key: Key('add-rule-home-footer'),
            onPressed: () => openRuleOptions(context, c),
            icon: Icon(Icons.add),
            label: Text('Add rule'),
          ),
        ],
      ),
    ];
  }

  Future<void> _quickTimer(BuildContext context) async {
    final input = TextEditingController(text: '${c.quickMinutes}');
    final minutes = await showDialog<int>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setDialogState) {
          final valid = int.tryParse(input.text);
          return AlertDialog(
            title: Text('Quick block timer'),
            content: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextField(
                  key: Key('quick-timer-minutes'),
                  controller: input,
                  autofocus: true,
                  keyboardType: TextInputType.number,
                  onChanged: (_) => setDialogState(() {}),
                  decoration: InputDecoration(
                    labelText: 'Minutes',
                    helperText: '1–1,440 minutes',
                  ),
                ),
                SizedBox(height: 12),
                Text(
                  'Applies the next time you start a block.',
                  style: TextStyle(color: muted),
                ),
              ],
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(context),
                child: Text('Cancel'),
              ),
              FilledButton(
                onPressed: valid != null && valid >= 1 && valid <= 1440
                    ? () {
                        final value = int.tryParse(input.text);
                        if (value != null && value >= 1 && value <= 1440) {
                          Navigator.pop(context, value);
                        }
                      }
                    : null,
                child: Text('Save'),
              ),
            ],
          );
        },
      ),
    );
    // Wait until the closing route no longer builds its TextField.
    await Future<void>.delayed(Duration(milliseconds: 240));
    input.dispose();
    if (minutes != null && mounted) {
      await _act('saveQuickConfig', {
        'packages': c.quickPackages.toList(),
        'minutes': minutes,
      });
    }
  }

  Widget _nowRow() => Padding(
    padding: EdgeInsets.only(bottom: 18),
    child: Row(
      children: [
        SizedBox(
          width: 58,
          child: Text(
            clock(c.now.millisecondsSinceEpoch),
            style: TextStyle(color: mint, fontSize: 12),
          ),
        ),
        Icon(Icons.circle_outlined, size: 13, color: mint),
        SizedBox(width: 12),
        Expanded(child: Divider(color: mint)),
        SizedBox(width: 8),
        Text('Now', style: TextStyle(color: mint, fontSize: 12)),
      ],
    ),
  );
  Widget _timelineRow(BuildContext context, RuleEntry e, int start, int end) {
    final now = c.now.millisecondsSinceEpoch;
    final active = start <= now && end > now;
    final past = end <= now;
    final color = active
        ? mint
        : past
        ? muted
        : Color(0xFFB5A4E0);
    return InkWell(
      onTap: () => openRuleEditor(context, c, e.rule.kind, rule: e.rule),
      child: Padding(
        padding: EdgeInsets.only(bottom: 20),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            SizedBox(
              width: 58,
              child: Text(
                clock(start),
                style: TextStyle(color: active ? mint : muted, fontSize: 12),
              ),
            ),
            Column(
              children: [
                Icon(Icons.circle, size: 12, color: color),
                Container(
                  width: 2,
                  height: 96,
                  color: color.withValues(alpha: .4),
                ),
              ],
            ),
            SizedBox(width: 16),
            Expanded(
              child: Padding(
                padding: EdgeInsets.only(bottom: 8),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Wrap(
                      spacing: 10,
                      runSpacing: 6,
                      crossAxisAlignment: WrapCrossAlignment.center,
                      children: [
                        Text(
                          e.rule.name,
                          style: TextStyle(
                            fontSize: 18,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                        _pill(
                          active
                              ? 'Blocking now'
                              : past
                              ? 'Finished'
                              : 'Upcoming',
                          color,
                        ),
                      ],
                    ),
                    SizedBox(height: 7),
                    Text(
                      '${clock(start)}–${clock(end)} · ${e.rule.packages.length} apps',
                      style: TextStyle(color: muted),
                    ),
                    SizedBox(height: 8),
                    AppIconGroup(c.selectedApps(e.rule.packages), size: 22),
                    if (active)
                      Padding(
                        padding: EdgeInsets.only(top: 4),
                        child: Text(
                          'Ends in ${((end - now) / 60000).ceil()} min',
                          style: TextStyle(color: mint),
                        ),
                      ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  List<Widget> _rules(BuildContext context) {
    final entries = c.rules
        .where((e) => _filter == 0 || (_filter == 1 ? !e.paused : e.paused))
        .toList();
    return [
      Row(
        children: [
          Expanded(
            child: Text(
              'Each rule has its own apps and settings.',
              style: TextStyle(color: muted),
            ),
          ),
          OutlinedButton.icon(
            key: Key('add-rule-list'),
            onPressed: () => openRuleOptions(context, c),
            icon: Icon(Icons.add, size: 18),
            label: Text('Add'),
          ),
        ],
      ),
      SizedBox(height: 18),
      Wrap(
        spacing: 8,
        children: [
          for (var i = 0; i < 3; i++)
            ChoiceChip(
              label: Text(
                '${['All', 'Enabled', 'Paused'][i]} ${i == 0 ? c.rules.length : c.rules.where((e) => i == 1 ? !e.paused : e.paused).length}',
              ),
              selected: _filter == i,
              onSelected: (_) => setState(() => _filter = i),
            ),
        ],
      ),
      SizedBox(height: 16),
      if (entries.isEmpty)
        _empty(
          Icons.tune,
          'No rules here yet',
          'Create a schedule or a daily usage allowance.',
        ),
      for (final e in entries) _ruleRow(context, e),
      if (c.remote['enabled'] == true) ...[
        SizedBox(height: 24),
        _remoteSummary(),
        for (final raw in c.remote['schedules'] as List? ?? [])
          if (raw['deletedAtMs'] == null)
            ListTile(
              contentPadding: EdgeInsets.zero,
              leading: Icon(Icons.cloud_outlined, color: muted),
              title: Text(raw['name']?.toString() ?? 'Remote schedule'),
              subtitle: Text(
                '${raw['state'] ?? 'Saved'} · Managed through remote control',
              ),
              trailing: Icon(Icons.chevron_right),
              onTap: () => _open(AppRoutes.remoteControlPath),
            ),
      ],
      SizedBox(height: 28),
      Text(
        'Create a rule',
        style: TextStyle(fontSize: 19, fontWeight: FontWeight.w700),
      ),
      SizedBox(height: 12),
      _link(
        Icons.calendar_today_outlined,
        'Schedule',
        'Block during chosen hours',
        () => openRuleEditor(context, c, 'schedule'),
      ),
      _link(
        Icons.timelapse_rounded,
        'Usage limit',
        'Set a daily allowance',
        () => openRuleEditor(context, c, 'usage'),
      ),
    ];
  }

  Widget _ruleRow(BuildContext context, RuleEntry e) => Column(
    key: ValueKey('rule-row-${e.rule.id}'),
    children: [
      Padding(
        padding: EdgeInsets.symmetric(vertical: 16),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            CircleAvatar(
              backgroundColor: line,
              foregroundColor: e.rule.kind == 'usage' ? amber : mint,
              child: Icon(ruleIcon(e.rule.kind)),
            ),
            SizedBox(width: 14),
            Expanded(
              child: InkWell(
                onTap: () =>
                    openRuleEditor(context, c, e.rule.kind, rule: e.rule),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      e.rule.name,
                      style: TextStyle(
                        fontSize: 18,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    SizedBox(height: 6),
                    Text(ruleSummary(e.rule), style: TextStyle(color: muted)),
                    SizedBox(height: 6),
                    AppIconGroup(c.selectedApps(e.rule.packages)),
                    SizedBox(height: 8),
                    if (e.rule.kind == 'usage' && !e.paused) ...[
                      _progress(e),
                      SizedBox(height: 8),
                    ],
                    _pill(
                      e.paused
                          ? 'Paused'
                          : e.blocking
                          ? 'Blocking now'
                          : e.phase == 'available'
                          ? 'Allowance available'
                          : e.phase == 'expired'
                          ? 'Finished'
                          : 'Scheduled',
                      e.blocking ? mint : muted,
                    ),
                  ],
                ),
              ),
            ),
            Column(
              children: [
                Switch(
                  key: ValueKey('toggle-${e.rule.id}'),
                  value: c.pendingRuleToggles[e.rule.id] ?? e.rule.enabled,
                  onChanged:
                      c.busy || c.pendingRuleToggles.containsKey(e.rule.id)
                      ? null
                      : (v) => _act('toggleLocalRule', {
                          'id': e.rule.id,
                          'enabled': v,
                        }),
                ),
                PopupMenuButton<String>(
                  tooltip: 'Rule options',
                  onSelected: (v) {
                    if (v == 'edit') {
                      openRuleEditor(context, c, e.rule.kind, rule: e.rule);
                    } else {
                      _delete(context, e);
                    }
                  },
                  itemBuilder: (_) => [
                    PopupMenuItem(value: 'edit', child: Text('Edit')),
                    PopupMenuItem(value: 'delete', child: Text('Delete')),
                  ],
                ),
              ],
            ),
          ],
        ),
      ),
      Divider(height: 1),
    ],
  );
  Future<void> _delete(BuildContext context, RuleEntry e) async {
    final yes = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Delete ${e.rule.name}?'),
        content: Text(
          'Other rules and remote blocks will keep their own restrictions.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: Text('Delete'),
          ),
        ],
      ),
    );
    if (yes == true) await _act('deleteLocalRule', {'id': e.rule.id});
  }

  Widget _budget(BuildContext context, RuleEntry e) => Padding(
    padding: EdgeInsets.only(bottom: 12),
    child: Surface(
      padding: EdgeInsets.zero,
      child: InkWell(
        borderRadius: BorderRadius.circular(18),
        onTap: () => openRuleEditor(context, c, 'usage', rule: e.rule),
        child: Padding(
          padding: EdgeInsets.all(18),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text(
                      e.rule.name,
                      style: TextStyle(
                        fontWeight: FontWeight.w600,
                        fontSize: 16,
                      ),
                    ),
                  ),
                  SizedBox(
                    width: 116,
                    child: AppIconGroup(c.selectedApps(e.rule.packages)),
                  ),
                ],
              ),
              SizedBox(height: 8),
              Text(
                '${e.usedSeconds ~/ 60} / ${e.rule.minutes} min${e.rule.shared ? ' shared' : ' · most-used app'}',
                style: TextStyle(color: muted),
              ),
              SizedBox(height: 12),
              _progress(e),
              SizedBox(height: 8),
              Text(
                e.blocking
                    ? '${e.blocked.length} apps blocked until midnight'
                    : '${((e.rule.minutes * 60 - e.usedSeconds).clamp(0, 86400) / 60).ceil()} min remaining',
                style: TextStyle(
                  color: e.blocking ? amber : muted,
                  fontSize: 12,
                ),
              ),
              if (!e.rule.shared) ...[
                SizedBox(height: 8),
                for (final pkg in e.rule.packages)
                  Text(
                    '${c.label(pkg)} · ${(c.usage[pkg] ?? 0) ~/ 60} / ${e.rule.minutes} min',
                    style: TextStyle(fontSize: 12, color: muted),
                  ),
              ],
            ],
          ),
        ),
      ),
    ),
  );
  Widget _progress(RuleEntry e) => ClipRRect(
    borderRadius: BorderRadius.circular(5),
    child: LinearProgressIndicator(
      minHeight: 6,
      value: (e.usedSeconds / (e.rule.minutes * 60)).clamp(0, 1),
      backgroundColor: line,
      color: e.blocking ? amber : mint,
    ),
  );
  List<Widget> _activity(BuildContext context) => [
    Text('Today on your device', style: TextStyle(color: muted)),
    SizedBox(height: 20),
    Wrap(
      spacing: 12,
      runSpacing: 12,
      children: [
        Surface(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Screen time', style: TextStyle(color: muted)),
              SizedBox(height: 8),
              Text(
                c.usagePermission
                    ? durationLabel(c.screenSeconds)
                    : 'Unavailable',
                style: TextStyle(fontSize: 27, fontWeight: FontWeight.w700),
              ),
            ],
          ),
        ),
        Surface(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Blocking events', style: TextStyle(color: muted)),
              SizedBox(height: 8),
              Text(
                '${c.activity.length}',
                style: TextStyle(fontSize: 27, fontWeight: FontWeight.w700),
              ),
            ],
          ),
        ),
      ],
    ),
    SizedBox(height: 26),
    if (c.rules.any((e) => e.rule.kind == 'usage')) ...[
      Text(
        'Usage allowances',
        style: TextStyle(fontSize: 20, fontWeight: FontWeight.w700),
      ),
      SizedBox(height: 14),
      for (final e in c.rules.where((e) => e.rule.kind == 'usage'))
        _budget(context, e),
    ],
    SizedBox(height: 12),
    Text(
      'Recent blocking',
      style: TextStyle(fontSize: 20, fontWeight: FontWeight.w700),
    ),
    SizedBox(height: 14),
    if (c.activity.isEmpty)
      _empty(
        Icons.history,
        'Nothing recorded yet',
        'Blocking events will appear here as they happen.',
      ),
    for (final a in c.activity)
      ListTile(
        contentPadding: EdgeInsets.zero,
        leading: Text(
          clock((a['at'] as num).toInt()),
          style: TextStyle(color: muted, fontSize: 12),
        ),
        title: Row(
          children: [
            AppBadge(c.app(a['package'] as String), size: 32),
            SizedBox(width: 10),
            Expanded(child: Text(c.label(a['package'] as String))),
          ],
        ),
        subtitle: Text(a['name'] as String),
        isThreeLine: false,
      ),
    SizedBox(height: 18),
    Text(
      'Events are stored on this phone. The latest 200 are retained; a repeated overlay within 5 seconds counts once.',
      style: TextStyle(color: muted, fontSize: 12),
    ),
    SizedBox(height: 16),
    _link(
      Icons.insights_outlined,
      'Detailed usage statistics',
      'Explore days, apps and usage history',
      () => _open(AppRoutes.statisticsPath),
    ),
  ];
  List<Widget> _settings(BuildContext context) => [
    Text(
      'Blocking readiness',
      style: TextStyle(fontSize: 19, fontWeight: FontWeight.w700),
    ),
    SizedBox(height: 14),
    _permission(
      'Usage access',
      'Detect apps and measure daily allowances',
      c.usagePermission,
      'usage',
    ),
    _permission(
      'Display over other apps',
      'Show the blocking screen',
      c.overlayPermission,
      'overlay',
    ),
    _permission(
      'Precise schedules',
      'Allow exact alarms for schedule boundaries',
      c.exactAlarms,
      'alarms',
    ),
    _link(
      Icons.battery_saver_outlined,
      'Battery settings',
      'Allow FocusRelay to keep running in the background',
      () => _act('localPermission', {'kind': 'battery'}),
    ),
    SizedBox(height: 28),
    Text(
      'Connections',
      style: TextStyle(fontSize: 19, fontWeight: FontWeight.w700),
    ),
    SizedBox(height: 14),
    _link(
      Icons.cloud_outlined,
      'Remote control',
      c.remote['enabled'] == true
          ? 'Enabled · pairing, app access and remote schedules'
          : 'Connect ChatGPT or Claude',
      () => _open(AppRoutes.remoteControlPath),
    ),
    SizedBox(height: 28),
    Text(
      'Tools & preferences',
      style: TextStyle(fontSize: 19, fontWeight: FontWeight.w700),
    ),
    SizedBox(height: 14),
    for (final feature in [
      (
        Icons.apps_outlined,
        'App restrictions',
        'Timers, active periods and app details',
        AppRoutes.statisticsPath,
      ),
      (
        Icons.workspaces_outline,
        'App groups',
        'Existing shared timers and active periods',
        AppRoutes.restrictionGroupsPath,
      ),
      (
        Icons.bedtime_outlined,
        'Bedtime',
        'Wind-down schedule and selected apps',
        AppRoutes.bedtimePath,
      ),
      (
        Icons.center_focus_strong,
        'Focus sessions',
        'Profiles, sessions and history',
        AppRoutes.focusModePath,
      ),
      (
        Icons.language,
        'Websites',
        'Blocked sites and website settings',
        AppRoutes.websitesBlockingPath,
      ),
      (
        Icons.video_collection_outlined,
        'Short videos',
        'Shorts limits and supported apps',
        AppRoutes.shortsBlockingPath,
      ),
      (
        Icons.notifications_outlined,
        'Notifications',
        'Batching, schedules and history',
        AppRoutes.notificationSettingsPath,
      ),
      (
        Icons.shield_outlined,
        'Protection',
        'Parental controls and tamper protection',
        AppRoutes.parentalControlsPath,
      ),
    ])
      _link(feature.$1, feature.$2, feature.$3, () => _open(feature.$4)),
    _link(
      Icons.tune,
      'Advanced settings',
      'Preferences, database, permissions and about',
      () => _open(AppRoutes.settingsPath),
    ),
    SizedBox(height: 24),
    Text(
      'Local rules work independently of remote control. If several rules apply, the app stays blocked until all applicable restrictions end.',
      style: TextStyle(color: muted),
    ),
  ];
  Widget _permission(String title, String subtitle, bool ready, String kind) =>
      ListTile(
        contentPadding: EdgeInsets.zero,
        leading: Icon(
          ready ? Icons.check_circle_outline : Icons.warning_amber_rounded,
          color: ready ? mint : amber,
        ),
        title: Text(title),
        subtitle: Text(subtitle),
        trailing: ready
            ? Text('Ready', style: TextStyle(color: mint))
            : Icon(Icons.chevron_right),
        onTap: () => _act('localPermission', {'kind': kind}),
      );
  Widget _remoteSummary() => _link(
    Icons.cloud_outlined,
    'Remote control',
    '${(c.remote['blocks'] as List? ?? []).length} apps with active remote blocks',
    () => _open(AppRoutes.remoteControlPath),
  );
  Widget _message(
    String text,
    IconData icon, {
    required String action,
    required VoidCallback onTap,
  }) => Surface(
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(icon, color: amber),
        SizedBox(height: 8),
        Text(text),
        TextButton(onPressed: onTap, child: Text(action)),
      ],
    ),
  );
  Widget _empty(IconData icon, String title, String subtitle) => Padding(
    padding: EdgeInsets.symmetric(vertical: 24),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(icon, color: muted),
        SizedBox(height: 12),
        Text(
          title,
          style: TextStyle(fontSize: 17, fontWeight: FontWeight.w600),
        ),
        SizedBox(height: 6),
        Text(subtitle, style: TextStyle(color: muted)),
      ],
    ),
  );
  Widget _pill(String text, Color color) => Container(
    padding: EdgeInsets.symmetric(horizontal: 9, vertical: 4),
    decoration: BoxDecoration(
      color: color.withValues(alpha: .13),
      borderRadius: BorderRadius.circular(20),
    ),
    child: Text(text, style: TextStyle(color: color, fontSize: 11)),
  );
  Widget _link(
    IconData icon,
    String title,
    String subtitle,
    VoidCallback onTap,
  ) => Padding(
    padding: EdgeInsets.only(bottom: 8),
    child: Surface(
      padding: EdgeInsets.zero,
      child: ListTile(
        leading: Icon(icon, color: mint),
        title: Text(title),
        subtitle: Text(subtitle),
        trailing: Icon(Icons.chevron_right),
        onTap: onTap,
        contentPadding: EdgeInsets.symmetric(horizontal: 16, vertical: 6),
      ),
    ),
  );
}
