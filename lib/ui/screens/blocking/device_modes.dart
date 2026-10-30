// Copyright 2026 Rafail Andreou. SPDX-License-Identifier: GPL-2.0-only
import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// Local consent stays on the phone; mutations use the authenticated relay.
class DeviceModesPanel extends StatefulWidget {
  const DeviceModesPanel({super.key});
  @override
  State<DeviceModesPanel> createState() => _DeviceModesPanelState();
}

class _DeviceModesPanelState extends State<DeviceModesPanel>
    with WidgetsBindingObserver {
  static const channel = MethodChannel('com.mindful.android.remote');
  Map<String, dynamic> state = {};
  String? error;
  bool busy = false;
  Timer? timer;
  List<Map<String, dynamic>> rows(dynamic value) => (value as List? ?? [])
      .map((e) => Map<String, dynamic>.from(e as Map))
      .toList();
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    refresh();
    timer = Timer.periodic(const Duration(seconds: 5), (_) => refresh());
  }

  @override
  void dispose() {
    timer?.cancel();
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState value) {
    if (value == AppLifecycleState.resumed) refresh();
  }

  Future<void> refresh() async {
    if (WidgetsBinding.instance.lifecycleState != null &&
        WidgetsBinding.instance.lifecycleState != AppLifecycleState.resumed) {
      return;
    }
    try {
      final value = await channel.invokeMapMethod<String, dynamic>('modeState');
      if (mounted) {
        setState(() {
          state = value ?? {};
        });
      }
    } on PlatformException catch (e) {
      if (mounted) {
        setState(() {
          error = e.message;
        });
      }
    } on MissingPluginException {
      if (mounted) {
        setState(() {
          error = 'Device Modes require the Android app.';
        });
      }
    }
  }

  Future<void> invoke(String method, [Map<String, dynamic>? args]) async {
    setState(() {
      busy = true;
      error = null;
    });
    try {
      await channel.invokeMethod(method, args);
      await refresh();
      if (mounted && method == 'modeCommand') {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text(
              'Command accepted. Waiting for phone synchronization.',
            ),
          ),
        );
      }
    } on PlatformException catch (e) {
      if (mounted) {
        setState(() {
          error = e.message ?? 'Could not apply command.';
        });
      }
    } finally {
      if (mounted) {
        setState(() {
          busy = false;
        });
      }
    }
  }

  Future<void> command(String operation, Map<String, dynamic> args) =>
      invoke('modeCommand', {'operation': operation, 'arguments': args});
  Future<void> setup(String key, bool value) => invoke('modeSetup', {
    'enabled': key == 'enabled' ? value : state['enabled'] == true,
    'actionsConsent': key == 'actionsConsent'
        ? value
        : state['actionsConsent'] == true,
    'usageSharing': key == 'usageSharing'
        ? value
        : state['usageSharing'] == true,
    'exclusions': state['exclusions'] ?? [],
  });
  String label(String package) =>
      rows(state['apps'])
          .where((a) => a['packageName'] == package)
          .map((a) => a['name'] ?? a['label'] ?? package)
          .firstOrNull
          ?.toString() ??
      package;
  String appNames(dynamic packages) =>
      (packages as List? ?? []).map((p) => label('$p')).join(', ');
  String time(dynamic millis) => millis is num
      ? MaterialLocalizations.of(context).formatTimeOfDay(
          TimeOfDay.fromDateTime(
            DateTime.fromMillisecondsSinceEpoch(millis.toInt()),
          ),
        )
      : '—';
  Future<void> edit([Map<String, dynamic>? template]) async {
    final result = await Navigator.of(context).push<Map<String, dynamic>>(
      MaterialPageRoute(
        builder: (_) => DeviceModeEditor(
          template: template,
          apps: rows(state['apps']),
          timeZone: state['timeZone'] as String? ?? 'UTC',
          eligible: Set<String>.from(state['eligiblePackages'] as List? ?? []),
        ),
      ),
    );
    if (result != null && mounted) {
      await command(
        template == null ? 'create_device_mode' : 'update_device_mode',
        result,
      );
    }
  }

  Future<void> exception(Map<String, dynamic> instance) async {
    String? selected;
    final minutes = TextEditingController(text: '10');
    final result = await showDialog<Map<String, dynamic>>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, update) => AlertDialog(
          title: const Text('Temporary app access'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                DropdownButtonFormField<String>(
                  initialValue: selected,
                  isExpanded: true,
                  hint: const Text('Choose an app'),
                  items: (state['eligiblePackages'] as List? ?? [])
                      .map(
                        (p) => DropdownMenuItem(
                          value: '$p',
                          child: Text(
                            label('$p'),
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                      )
                      .toList(),
                  onChanged: (v) => update(() {
                    selected = v;
                  }),
                ),
                TextField(
                  controller: minutes,
                  keyboardType: TextInputType.number,
                  decoration: const InputDecoration(
                    labelText: 'Minutes (1–60)',
                  ),
                  onChanged: (_) => update(() {}),
                ),
                const Text(
                  'Other modes and local rules can still block this app.',
                ),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('Cancel'),
            ),
            FilledButton(
              onPressed:
                  selected != null &&
                      (int.tryParse(minutes.text) ?? 0) >= 1 &&
                      (int.tryParse(minutes.text) ?? 61) <= 60
                  ? () => Navigator.pop(context, {
                      'instanceId': instance['id'],
                      'packageName': selected,
                      'durationMinutes': int.parse(minutes.text),
                    })
                  : null,
              child: const Text('Allow'),
            ),
          ],
        ),
      ),
    );
    // The closing route still owns the text field during its animation.
    await Future<void>.delayed(const Duration(milliseconds: 250));
    minutes.dispose();
    if (result != null && mounted) {
      await command('grant_temporary_app_access', result);
    }
  }

  @override
  Widget build(BuildContext context) {
    final templates = rows((state['deviceModes'] as Map?)?['templates']);
    final instances = rows(
      state['instances'],
    ).where((i) => ['active', 'paused', 'scheduled'].contains(i['phase']));
    final permissions = Map<String, dynamic>.from(
      state['permissions'] as Map? ?? {},
    );
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const Text(
          'Reusable app rules and optional device settings. Essential apps always remain accessible.',
        ),
        if (error != null)
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 12),
            child: Text(
              error!,
              style: TextStyle(color: Theme.of(context).colorScheme.error),
            ),
          ),
        if (state['connected'] != true)
          const Padding(
            padding: EdgeInsets.symmetric(vertical: 16),
            child: Text('Connect remote control in Settings first.'),
          ),
        SwitchListTile(
          contentPadding: EdgeInsets.zero,
          title: const Text('Enable Device Modes'),
          subtitle: const Text(
            'Only apps you opted into remote control can be restricted.',
          ),
          value: state['enabled'] == true,
          onChanged: busy || state['connected'] != true
              ? null
              : (v) => setup('enabled', v),
        ),
        OutlinedButton.icon(
          onPressed: busy ? null : () => invoke('emergencyEndModes'),
          icon: const Icon(Icons.lock_open),
          label: const Text('Emergency: end all device modes'),
        ),
        const Text(
          'Works offline. Disables Device Modes and cancels their saved future activations. Other local rules are unchanged.',
        ),
        const SizedBox(height: 16),
        for (final i in instances)
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    '${i['name']} · ${i['phase']}',
                    style: Theme.of(context).textTheme.titleLarge,
                  ),
                  Text(
                    'Ends ${time(i['endAtMs'])} · ${(((i['endAtMs'] as num).toInt() - DateTime.now().millisecondsSinceEpoch) / 60000).ceil().clamp(0, 100000)} min remaining',
                  ),
                  Text(
                    i['mode'] == 'allow_list'
                        ? 'Allowed: ${appNames(i['allowedPackages'])}'
                        : 'Blocked: ${appNames(i['blockedPackages'])}',
                  ),
                  for (final x in rows(
                    state['exceptions'],
                  ).where((x) => x['instanceId'] == i['id']))
                    ListTile(
                      contentPadding: EdgeInsets.zero,
                      title: Text(
                        '${label(x['packageName'])} until ${time(x['untilMs'])}',
                      ),
                      trailing: IconButton(
                        tooltip: 'Revoke temporary access',
                        icon: const Icon(Icons.close),
                        onPressed: busy
                            ? null
                            : () => command('revoke_temporary_app_access', {
                                'id': x['id'],
                              }),
                      ),
                    ),
                  Wrap(
                    spacing: 8,
                    children: [
                      FilledButton(
                        onPressed: busy
                            ? null
                            : () => invoke('endModeLocally', {
                                'instanceId': i['id'],
                              }),
                        child: const Text('End Mode'),
                      ),
                      TextButton(
                        onPressed: busy
                            ? null
                            : () => command(
                                i['phase'] == 'paused'
                                    ? 'resume_device_mode'
                                    : 'pause_device_mode',
                                {'instanceId': i['id']},
                              ),
                        child: Text(
                          i['phase'] == 'paused' ? 'Resume' : 'Pause',
                        ),
                      ),
                      if (i['phase'] == 'active')
                        TextButton(
                          onPressed: busy ? null : () => exception(i),
                          child: const Text('Temporary access'),
                        ),
                      if (i['alarmAtMs'] != null && i['phase'] == 'active')
                        TextButton(
                          onPressed: busy
                              ? null
                              : () => invoke('confirmModeAlarm', {
                                  'instanceId': i['id'],
                                }),
                          child: Text(
                            'Set alarm ${time(i['alarmAtMs'])} in Clock',
                          ),
                        ),
                    ],
                  ),
                ],
              ),
            ),
          ),
        if (state['nextTransitionMs'] != null)
          Text('Next transition: ${time(state['nextTransitionMs'])}'),
        const SizedBox(height: 20),
        Row(
          children: [
            Expanded(
              child: Text(
                'Your modes',
                style: Theme.of(context).textTheme.titleLarge,
              ),
            ),
            TextButton.icon(
              onPressed: busy || state['enabled'] != true ? null : () => edit(),
              icon: const Icon(Icons.add),
              label: const Text('Add'),
            ),
          ],
        ),
        if (templates.isEmpty)
          const Text(
            'Create Sleep, Thesis, Deep Work, or your own mode. Templates are separate from active sessions.',
          ),
        for (final t in templates)
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      const Icon(Icons.tune),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          '${t['name']}',
                          style: Theme.of(context).textTheme.titleLarge,
                        ),
                      ),
                    ],
                  ),
                  if (t['description'] != '') Text('${t['description'] ?? ''}'),
                  Text(
                    t['mode'] == 'allow_list'
                        ? 'Only allow: ${appNames(t['allowedPackages'])}'
                        : 'Block: ${appNames(t['blockedPackages'])}',
                  ),
                  Text(
                    t['timing'] == null
                        ? '${t['durationMinutes'] ?? 'Choose'} minutes'
                        : 'Scheduled · ${t['timing']['type']}',
                  ),
                  for (final key in [
                    'dnd',
                    'brightness',
                    'mediaVolume',
                    'ringtoneVolume',
                    'notificationPolicy',
                  ])
                    if (t[key] != null) Text('$key: ${t[key]}'),
                  Wrap(
                    spacing: 8,
                    children: [
                      FilledButton(
                        onPressed: busy || state['enabled'] != true
                            ? null
                            : () => command('activate_device_mode', {
                                'id': t['id'],
                              }),
                        child: const Text('Activate'),
                      ),
                      TextButton(
                        onPressed: busy ? null : () => edit(t),
                        child: const Text('Edit'),
                      ),
                      IconButton(
                        tooltip: 'Delete template',
                        onPressed: busy
                            ? null
                            : () => command('delete_device_mode', {
                                'id': t['id'],
                              }),
                        icon: const Icon(Icons.delete_outline),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
        ExpansionTile(
          // Keep the boolean expansion state separate from the ListView scroll offset.
          key: const PageStorageKey('device-mode-controls'),
          title: const Text('Device controls and privacy'),
          children: [
            SwitchListTile(
              title: const Text('Allow device settings actions'),
              subtitle: const Text(
                'Optional DND, manual brightness and volume. Previous values are journaled before changes.',
              ),
              value: state['actionsConsent'] == true,
              onChanged: busy ? null : (v) => setup('actionsConsent', v),
            ),
            for (final name in ['dnd', 'brightness'])
              ListTile(
                title: Text(
                  name == 'dnd'
                      ? 'Do Not Disturb access'
                      : 'Modify brightness access',
                ),
                subtitle: Text(
                  '${permissions[name == 'dnd' ? 'notificationPolicy' : 'writeSettings'] ?? false}',
                ),
                trailing: TextButton(
                  onPressed: () => invoke('modePermission', {'name': name}),
                  child: const Text('Open settings'),
                ),
              ),
            SwitchListTile(
              title: const Text('Share usage summaries'),
              subtitle: const Text(
                'Only opted-in apps. No message, notification or browsing content.',
              ),
              value: state['usageSharing'] == true,
              onChanged: busy ? null : (v) => setup('usageSharing', v),
            ),
            for (final app in rows(state['apps']))
              CheckboxListTile(
                title: Text(
                  '${app['name'] ?? app['label'] ?? app['packageName']}',
                ),
                subtitle: const Text('Always exclude from Device Modes'),
                value: (state['exclusions'] as List? ?? []).contains(
                  app['packageName'],
                ),
                onChanged: busy
                    ? null
                    : (v) {
                        final excluded = Set<String>.from(
                          state['exclusions'] as List? ?? [],
                        );
                        v == true
                            ? excluded.add(app['packageName'] as String)
                            : excluded.remove(app['packageName']);
                        invoke('modeSetup', {
                          'enabled': state['enabled'] == true,
                          'actionsConsent': state['actionsConsent'] == true,
                          'usageSharing': state['usageSharing'] == true,
                          'exclusions': excluded.toList(),
                        });
                      },
              ),
          ],
        ),
        for (final item in state['errors'] as List? ?? [])
          Text(
            '$item',
            style: TextStyle(color: Theme.of(context).colorScheme.error),
          ),
      ],
    );
  }
}

class DeviceModeEditor extends StatefulWidget {
  const DeviceModeEditor({
    super.key,
    this.template,
    required this.apps,
    required this.eligible,
    this.timeZone = 'UTC',
  });
  final Map<String, dynamic>? template;
  final List<Map<String, dynamic>> apps;
  final Set<String> eligible;
  final String timeZone;
  @override
  State<DeviceModeEditor> createState() => _DeviceModeEditorState();
}

class _DeviceModeEditorState extends State<DeviceModeEditor> {
  late final TextEditingController name, minutes, description;
  late String mode;
  late Set<String> selected;
  late Map<String, dynamic> actions;
  bool scheduled = false;
  Map<String, dynamic>? timing;
  @override
  void initState() {
    super.initState();
    final t = widget.template ?? {};
    name = TextEditingController(text: t['name'] ?? '');
    description = TextEditingController(text: t['description'] ?? '');
    minutes = TextEditingController(text: '${t['durationMinutes'] ?? 60}');
    mode = t['mode'] ?? 'allow_list';
    selected = Set<String>.from(
      t[mode == 'allow_list' ? 'allowedPackages' : 'blockedPackages']
              as List? ??
          [],
    );
    actions = {
      for (final k in ['dnd', 'brightness', 'mediaVolume', 'ringtoneVolume'])
        if (t[k] != null) k: t[k],
    };
    timing = t['timing'] == null
        ? null
        : Map<String, dynamic>.from(t['timing']);
    if (timing?['type'] == 'once') {
      timing = {
        'type': 'once',
        'startAt': DateTime.fromMillisecondsSinceEpoch(
          timing!['startAtMs'] as int,
        ).toUtc().toIso8601String(),
        'endAt': DateTime.fromMillisecondsSinceEpoch(
          timing!['endAtMs'] as int,
        ).toUtc().toIso8601String(),
      };
    }
    scheduled = timing != null && t['durationMinutes'] == null;
  }

  @override
  void dispose() {
    name.dispose();
    minutes.dispose();
    description.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final valid =
        name.text.trim().isNotEmpty &&
        name.text.length <= 80 &&
        selected.isNotEmpty &&
        (scheduled
            ? timing != null &&
                  (timing!['type'] != 'weekly' ||
                      (timing!['daysOfWeek'] as List).isNotEmpty &&
                          timing!['startTime'] != timing!['endTime'])
            : (int.tryParse(minutes.text) ?? 0) >= 1 &&
                  (int.tryParse(minutes.text) ?? 10081) <= 10080);
    return Scaffold(
      appBar: AppBar(
        title: const Text('Device mode'),
        actions: [
          TextButton(
            onPressed: valid
                ? () => Navigator.pop(context, <String, dynamic>{
                    if (widget.template != null) 'id': widget.template!['id'],
                    'name': name.text.trim(),
                    'mode': mode,
                    if (description.text.trim().isNotEmpty)
                      'description': description.text.trim(),
                    if (widget.template?['icon'] != null)
                      'icon': widget.template!['icon'],
                    if (widget.template?['excludedPackages'] != null)
                      'excludedPackages': widget.template!['excludedPackages'],
                    mode == 'allow_list' ? 'allowedPackages' : 'packages':
                        selected.toList(),
                    if (!scheduled) 'durationMinutes': int.parse(minutes.text),
                    if (scheduled) 'timing': timing,
                    ...actions,
                  })
                : null,
            child: const Text('Save'),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(24),
        children: [
          TextField(
            controller: name,
            maxLength: 80,
            decoration: const InputDecoration(labelText: 'Name'),
            onChanged: (_) => setState(() {}),
          ),
          TextField(
            controller: description,
            maxLength: 500,
            decoration: const InputDecoration(
              labelText: 'Description (optional)',
            ),
          ),
          DropdownButtonFormField<String>(
            initialValue: mode,
            items: const [
              DropdownMenuItem(
                value: 'allow_list',
                child: Text('Only allow selected apps'),
              ),
              DropdownMenuItem(
                value: 'block_list',
                child: Text('Block selected apps'),
              ),
            ],
            onChanged: (v) => setState(() {
              mode = v!;
              selected.clear();
            }),
          ),
          const Padding(
            padding: EdgeInsets.symmetric(vertical: 12),
            child: Text(
              'Essential apps and exclusions stay accessible. Allow-list restrictions apply only to eligible, opted-in apps. Other rules can still restrict selected apps.',
            ),
          ),
          for (final app in widget.apps.where(
            (a) =>
                mode == 'allow_list' ||
                widget.eligible.contains(a['packageName']),
          ))
            CheckboxListTile(
              contentPadding: EdgeInsets.zero,
              title: Text(
                '${app['name'] ?? app['label'] ?? app['packageName']}',
              ),
              value: selected.contains(app['packageName']),
              onChanged: (v) => setState(() {
                v == true
                    ? selected.add(app['packageName'])
                    : selected.remove(app['packageName']);
              }),
            ),
          SwitchListTile(
            title: const Text('Scheduled activation'),
            subtitle: const Text(
              'Weekly schedules have a 30-day activation lease.',
            ),
            value: scheduled,
            onChanged: (v) => setState(() {
              scheduled = v;
              timing ??= {
                'type': 'weekly',
                'timeZone': widget.timeZone,
                'daysOfWeek': [1, 2, 3, 4, 5],
                'startTime': '09:00',
                'endTime': '12:00',
              };
            }),
          ),
          if (scheduled && timing?['type'] == 'weekly') ...[
            Text('Time zone: ${timing!['timeZone']}'),
            Wrap(
              spacing: 4,
              children: [
                for (var d = 1; d <= 7; d++)
                  FilterChip(
                    label: Text(
                      ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'][d - 1],
                    ),
                    selected: (timing!['daysOfWeek'] as List).contains(d),
                    onSelected: (v) => setState(() {
                      final days = List<int>.from(timing!['daysOfWeek']);
                      v ? days.add(d) : days.remove(d);
                      days.sort();
                      timing!['daysOfWeek'] = days;
                    }),
                  ),
              ],
            ),
            for (final key in ['startTime', 'endTime'])
              TextButton(
                child: Text(
                  '${key == 'startTime' ? 'Start' : 'End'}: ${timing![key]}',
                ),
                onPressed: () async {
                  final parts = (timing![key] as String).split(':');
                  final picked = await showTimePicker(
                    context: context,
                    initialTime: TimeOfDay(
                      hour: int.parse(parts[0]),
                      minute: int.parse(parts[1]),
                    ),
                  );
                  if (picked != null && mounted) {
                    setState(() {
                      timing![key] =
                          '${picked.hour.toString().padLeft(2, '0')}:${picked.minute.toString().padLeft(2, '0')}';
                    });
                  }
                },
              ),
          ],
          if (!scheduled)
            TextField(
              controller: minutes,
              keyboardType: TextInputType.number,
              decoration: const InputDecoration(
                labelText: 'Duration in minutes (1–10,080)',
              ),
              onChanged: (_) => setState(() {}),
            ),
          const SizedBox(height: 16),
          const Text(
            'Optional device actions require local consent and Android permissions. Brightness requires manual brightness mode. Alarms are confirmed separately in Clock.',
          ),
          SwitchListTile(
            title: const Text('Do Not Disturb (alarms only)'),
            value: actions['dnd'] == true,
            onChanged: (v) => setState(() {
              v ? actions['dnd'] = true : actions.remove('dnd');
            }),
          ),
          for (final entry in {
            'brightness': 'Brightness',
            'mediaVolume': 'Media volume',
            'ringtoneVolume': 'Ringtone volume',
          }.entries) ...[
            SwitchListTile(
              title: Text(entry.value),
              value: actions.containsKey(entry.key),
              onChanged: (v) => setState(() {
                v ? actions[entry.key] = 50 : actions.remove(entry.key);
              }),
            ),
            if (actions.containsKey(entry.key))
              Slider(
                label: '${actions[entry.key]}%',
                value: (actions[entry.key] as num).toDouble(),
                min: entry.key == 'brightness'
                    ? 5
                    : entry.key == 'ringtoneVolume'
                    ? 1
                    : 0,
                max: 100,
                divisions: 95,
                onChanged: (v) => setState(() {
                  actions[entry.key] = v.round();
                }),
              ),
          ],
        ],
      ),
    );
  }
}
