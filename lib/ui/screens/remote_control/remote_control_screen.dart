// Modified by Rafail Andreou, 2026-10-26, for FocusRelay. Original notices retained.
// SPDX-License-Identifier: GPL-2.0-only
import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:mindful/core/services/method_channel_service.dart';

class RemoteControlScreen extends StatefulWidget {
  const RemoteControlScreen({super.key});
  @override
  State<RemoteControlScreen> createState() => _RemoteControlScreenState();
}

class _RemoteControlScreenState extends State<RemoteControlScreen> {
  static const _channel = MethodChannel('com.mindful.android.remote');
  final _url = TextEditingController();
  final _token = TextEditingController();
  final _selected = <String>{};
  Map<String, dynamic> _status = {};
  List<Map> _apps = [];
  Timer? _timer;
  bool _refreshing = false, _appsLoading = true;
  String? _appsError;
  bool _loading = true, _busy = false, _initialized = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _refresh();
    _loadApps();
    _timer = Timer.periodic(const Duration(seconds: 3), (_) => _refresh());
  }

  Future<void> _refresh() async {
    if (_refreshing) {
      return;
    }
    _refreshing = true;
    try {
      final value = await _channel.invokeMapMethod<String, dynamic>('status');
      if (!mounted || value == null) {
        return;
      }
      setState(() {
        _status = value;
        if (!_initialized) {
          _url.text = value['url'] as String? ?? '';
          _selected.addAll(List<String>.from(value['allowed'] as List? ?? []));
          _initialized = true;
        }
        _loading = false;
      });
    } on PlatformException catch (e) {
      if (mounted) {
        setState(() {
          _error = e.message;
          _loading = false;
        });
      }
    } finally {
      _refreshing = false;
    }
  }

  Future<void> _loadApps() async {
    setState(() {
      _appsLoading = true;
      _appsError = null;
    });
    try {
      final apps = await _channel.invokeListMethod<dynamic>('apps');
      if (mounted) {
        setState(() => _apps = (apps ?? []).cast<Map>());
      }
    } on PlatformException catch (e) {
      if (mounted) {
        setState(() => _appsError = e.message ?? 'Could not load apps');
      }
    } finally {
      if (mounted) {
        setState(() => _appsLoading = false);
      }
    }
  }

  Future<void> _action(String name, [Map<String, dynamic>? arguments]) async {
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      if (name == 'connect') {
        await MethodChannelService.instance.getAndAskNotificationPermission(
          askPermissionToo: true,
        );
      }
      await _channel.invokeMethod(
        name,
        arguments ??
            (name == 'connect'
                ? {
                    'url': _url.text.trim(),
                    'token': _token.text.trim(),
                    'packages': _selected.toList(),
                  }
                : name == 'updateApps'
                ? {'packages': _selected.toList()}
                : null),
      );
      _token.clear();
      await _refresh();
    } on PlatformException catch (e) {
      if (mounted) {
        setState(() => _error = e.message ?? 'Connection setup failed');
      }
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
  }

  Widget _appSelection(bool enabled) {
    final saved = Set<String>.from(_status['allowed'] as List? ?? []);
    final editable = !_busy && !_appsLoading && _initialized;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text('Allow remote control for'),
        const Text(
          'Select all apps or choose individually. Essential phone controls are excluded. Newly installed apps need to be selected here.',
        ),
        Wrap(
          spacing: 8,
          children: [
            TextButton(
              onPressed: editable && _apps.isNotEmpty
                  ? () => setState(() {
                      _selected
                        ..clear()
                        ..addAll(
                          _apps.map((app) => app['packageName'] as String),
                        );
                    })
                  : null,
              child: const Text('Select all apps'),
            ),
            TextButton(
              onPressed: editable && _selected.isNotEmpty
                  ? () => setState(_selected.clear)
                  : null,
              child: const Text('Clear selection'),
            ),
            TextButton(
              onPressed: _busy || _appsLoading ? null : _loadApps,
              child: const Text('Refresh apps'),
            ),
          ],
        ),
        Text('${_selected.length} apps selected'),
        if (_appsLoading) const LinearProgressIndicator(),
        if (_appsError != null) Text(_appsError!),
        if (!_appsLoading && _apps.isEmpty && _appsError == null)
          const Text('No eligible apps found.'),
        if (_apps.isNotEmpty)
          SizedBox(
            height: 280,
            child: ListView.builder(
              key: const PageStorageKey('remote-app-list'),
              itemCount: _apps.length,
              itemBuilder: (context, index) {
                final app = _apps[index];
                final packageName = app['packageName'] as String;
                return CheckboxListTile(
                  contentPadding: EdgeInsets.zero,
                  title: Text(app['label'].toString()),
                  subtitle: Text(packageName),
                  value: _selected.contains(packageName),
                  onChanged: editable
                      ? (selected) => setState(() {
                          if (selected == true) {
                            _selected.add(packageName);
                          } else {
                            _selected.remove(packageName);
                          }
                        })
                      : null,
                );
              },
            ),
          ),
        if (enabled)
          FilledButton(
            onPressed:
                editable && _selected.isNotEmpty && !setEquals(saved, _selected)
                ? () => _action('updateApps')
                : null,
            child: Text(_busy ? 'Saving…' : 'Save app access'),
          ),
      ],
    );
  }

  String _date(dynamic value) => value is num
      ? DateTime.fromMillisecondsSinceEpoch(value.toInt()).toLocal().toString()
      : 'None';

  Widget _planningSection(String title, String targetType, List<Map> items) {
    final visible = items.where((item) => item['deletedAtMs'] == null).toList();
    return ExpansionTile(
      title: Text(title),
      subtitle: Text('${visible.length} saved'),
      children: [
        if (visible.isEmpty)
          Padding(
            padding: const EdgeInsets.all(12),
            child: Text(
              targetType == 'preset'
                  ? 'Ask ChatGPT to create a Study, Deep Work, Sleep or Driving preset using your allowed apps.'
                  : 'Ask ChatGPT to create a one-time or weekly block.',
            ),
          ),
        for (final item in visible)
          Builder(
            builder: (context) {
              final until = (item['pausedUntilMs'] as num?)?.toInt() ?? 0;
              final paused =
                  until == -1 || until > DateTime.now().millisecondsSinceEpoch;
              final timing = item['timing'] as Map?;
              final window = item['nextWindow'] as Map?;
              final phase = item['disabled'] == true
                  ? 'unavailable'
                  : item['state'] == 'expired'
                  ? 'expired'
                  : paused
                  ? 'paused'
                  : item['state']?.toString() ?? 'ready';
              const dayNames = [
                'Mon',
                'Tue',
                'Wed',
                'Thu',
                'Fri',
                'Sat',
                'Sun',
              ];
              final days = (timing?['daysOfWeek'] as List? ?? [])
                  .whereType<int>()
                  .where((day) => day >= 1 && day <= 7)
                  .map((day) => dayNames[day - 1])
                  .join(', ');
              final detail = timing?['type'] == 'weekly'
                  ? '$days · ${timing?['startTime']}–${timing?['endTime']} · ${timing?['timeZone']}'
                  : timing?['type'] == 'once'
                  ? '${_date(timing?['startAtMs'])} → ${_date(timing?['endAtMs'])}'
                  : item['durationMinutes'] == null
                  ? 'Choose a duration in ChatGPT'
                  : '${item['durationMinutes']} minutes';
              return ListTile(
                title: Text(item['name'].toString()),
                subtitle: Text(
                  '$phase · $detail${window == null ? '' : '\nNext window: ${_date(window['startAtMs'])}'}${paused && until > 0 ? '\nResumes: ${_date(until)}' : ''}',
                ),
                trailing: TextButton(
                  onPressed:
                      _busy || item['disabled'] == true || phase == 'expired'
                      ? null
                      : () => _action(paused ? 'resumeRemote' : 'pauseRemote', {
                          'targetType': targetType,
                          'id': item['id'],
                        }),
                  child: Text(paused ? 'Resume' : 'Pause'),
                ),
              );
            },
          ),
        const Padding(
          padding: EdgeInsets.all(12),
          child: Text(
            'Pause/resume here requires internet. Received schedules still start and end locally while offline. Other local restrictions remain active.',
          ),
        ),
      ],
    );
  }

  @override
  void dispose() {
    _timer?.cancel();
    _url.dispose();
    _token.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final enabled = _status['enabled'] == true;
    final apps = _apps;
    final blocks = (_status['blocks'] as List? ?? []).cast<Map>();
    final theme = Theme.of(context);
    return Scaffold(
      appBar: AppBar(title: const Text('Remote control')),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.all(20),
              children: [
                Icon(
                  enabled ? Icons.cloud_done_outlined : Icons.cloud_outlined,
                  size: 48,
                ),
                const SizedBox(height: 16),
                Text(
                  enabled
                      ? 'Remote control is enabled'
                      : 'Your focus, connected',
                  style: theme.textTheme.headlineSmall,
                ),
                const SizedBox(height: 8),
                Text(
                  enabled
                      ? (_status['message'] as String? ?? 'Waiting for relay')
                      : 'Connect your personal relay to let ChatGPT or Claude block the apps you choose. Only selected app names, blocking rules, and connection status leave this phone.',
                ),
                const SizedBox(height: 12),
                if (enabled) ...[
                  if (_status['schedulerMode'] == 'inexact')
                    const Text(
                      'Alarms & reminders access is off. Android may delay scheduled transitions.',
                    ),
                  Card(
                    child: ExpansionTile(
                      title: const Text('Connection details'),
                      subtitle: const Text('Sync status and diagnostics'),
                      childrenPadding: const EdgeInsets.all(16),
                      expandedCrossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('Relay: ${_status['url']}'),
                        Text('Received revision: ${_status['revision']}'),
                        Text(
                          'Local scheduler: ${_status['schedulerMode'] ?? 'Waiting'}',
                        ),
                        Text(
                          'Next transition: ${_date(_status['nextTransitionMs'])}',
                        ),
                        for (final error
                            in (_status['scheduleErrors'] as List? ?? []))
                          Text(
                            error.toString(),
                            style: TextStyle(color: theme.colorScheme.error),
                          ),
                        if (_status['enforcement'] != null)
                          Text('Blocking check: ${_status['enforcement']}'),
                        if (_status['overlayResult'] != null)
                          Text(
                            'Last blocking attempt: ${_status['overlayResult']}',
                          ),
                        if ((_status['lastSync'] as num? ?? 0) > 0)
                          Text(
                            'Last contact: ${DateTime.fromMillisecondsSinceEpoch((_status['lastSync'] as num).toInt()).toLocal()}',
                          ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 16),
                  Text('Remote blocks', style: theme.textTheme.titleMedium),
                  if (blocks.isEmpty) const Text('No active remote blocks.'),
                  for (final block in blocks)
                    ListTile(
                      contentPadding: EdgeInsets.zero,
                      title: Text(
                        apps
                                .where(
                                  (a) =>
                                      a['packageName'] == block['packageName'],
                                )
                                .map((a) => a['label'])
                                .firstOrNull
                                ?.toString() ??
                            block['packageName'].toString(),
                      ),
                      subtitle: Text(
                        'Until ${DateTime.fromMillisecondsSinceEpoch((block['untilMs'] as num).toInt()).toLocal()}',
                      ),
                    ),
                  const SizedBox(height: 16),
                  _planningSection(
                    'Scheduled blocks',
                    'schedule',
                    (_status['schedules'] as List? ?? []).cast<Map>(),
                  ),
                  _planningSection(
                    'Focus presets',
                    'preset',
                    (_status['presets'] as List? ?? []).cast<Map>(),
                  ),
                  ExpansionTile(
                    title: const Text('App access'),
                    subtitle: Text('${_selected.length} apps selected'),
                    children: [_appSelection(true)],
                  ),
                  const SizedBox(height: 16),
                  OutlinedButton.icon(
                    onPressed: _busy
                        ? null
                        : () async {
                            final confirmed = await showDialog<bool>(
                              context: context,
                              builder: (context) => AlertDialog(
                                title: const Text('Disconnect remote control?'),
                                content: const Text(
                                  'This clears remote blocks on this phone and removes the saved device token. Other local limits stay active. The relay keeps its rules; reconnecting may restore unexpired blocks.',
                                ),
                                actions: [
                                  TextButton(
                                    onPressed: () =>
                                        Navigator.pop(context, false),
                                    child: const Text('Cancel'),
                                  ),
                                  FilledButton(
                                    onPressed: () =>
                                        Navigator.pop(context, true),
                                    child: const Text('Disconnect'),
                                  ),
                                ],
                              ),
                            );
                            if (confirmed == true) await _action('disconnect');
                          },
                    icon: const Icon(Icons.link_off),
                    label: const Text('Disconnect and clear remote blocks'),
                  ),
                ] else ...[
                  TextField(
                    controller: _url,
                    keyboardType: TextInputType.url,
                    autocorrect: false,
                    decoration: const InputDecoration(
                      labelText: 'Relay URL',
                      hintText: 'https://mindful-remote.example.workers.dev',
                    ),
                  ),
                  const SizedBox(height: 12),
                  TextField(
                    controller: _token,
                    obscureText: true,
                    autocorrect: false,
                    enableSuggestions: false,
                    decoration: const InputDecoration(
                      labelText: 'Device token',
                    ),
                  ),
                  const SizedBox(height: 20),
                  _appSelection(false),
                  const SizedBox(height: 16),
                  FilledButton.icon(
                    onPressed: _busy || _appsLoading || _selected.isEmpty
                        ? null
                        : () => _action('connect'),
                    icon: const Icon(Icons.link),
                    label: Text(
                      _busy ? 'Connecting…' : 'Enable remote control',
                    ),
                  ),
                ],
                if (_error != null)
                  Padding(
                    padding: const EdgeInsets.symmetric(vertical: 12),
                    child: Text(
                      _error!,
                      style: TextStyle(color: theme.colorScheme.error),
                    ),
                  ),
                const Divider(height: 32),
                Text('Phone permissions', style: theme.textTheme.titleMedium),
                TextButton(
                  onPressed: _busy ? null : () => _action('exactAlarms'),
                  child: const Text('Allow precise scheduled blocks'),
                ),
                TextButton(
                  onPressed: () => MethodChannelService.instance
                      .getAndAskUsageAccessPermission(askPermissionToo: true),
                  child: const Text('Grant Usage Access'),
                ),
                TextButton(
                  onPressed: () => MethodChannelService.instance
                      .getAndAskDisplayOverlayPermission(
                        askPermissionToo: true,
                      ),
                  child: const Text('Allow display over other apps'),
                ),
                TextButton(
                  onPressed: () => MethodChannelService.instance
                      .getAndAskIgnoreBatteryOptimizationPermission(
                        askPermissionToo: true,
                      ),
                  child: const Text('Allow unrestricted battery use'),
                ),
                const SizedBox(height: 8),
                const Text(
                  'This personal preview checks for commands about every 15 seconds while Android allows it to run. An ongoing notification shows the connection. Battery saving, force-stop, or lost internet can delay commands. Received blocks expire locally without internet. Reopen FocusRelay after a force-stop.',
                ),
              ],
            ),
    );
  }
}

