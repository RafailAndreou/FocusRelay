// Modified by Rafail Andreou, 2026-10-26, for FocusRelay. Original notices retained.
// SPDX-License-Identifier: GPL-2.0-only
import 'package:flutter/material.dart';
import 'blocking_style.dart';
import 'rule_models.dart';

Future<void> openRuleOptions(
  BuildContext context,
  BlockingController controller,
) async {
  final kind = await showModalBottomSheet<String>(
    context: context,
    useSafeArea: true,
    builder: (context) => Padding(
      padding: EdgeInsets.fromLTRB(24, 0, 24, 24),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  'Add a rule',
                  style: TextStyle(fontSize: 26, fontWeight: FontWeight.w700),
                ),
              ),
              IconButton(
                onPressed: () => Navigator.pop(context),
                icon: Icon(Icons.close),
              ),
            ],
          ),
          Text('Choose how you want to block.', style: TextStyle(color: muted)),
          SizedBox(height: 24),
          for (final option in [
            (
              'schedule',
              Icons.calendar_today_outlined,
              'Schedule',
              'Block apps during chosen hours.',
            ),
            (
              'usage',
              Icons.timelapse_rounded,
              'Usage limit',
              'Set a daily allowance for your apps.',
            ),
          ])
            Padding(
              padding: EdgeInsets.only(bottom: 12),
              child: Surface(
                padding: EdgeInsets.zero,
                child: ListTile(
                  contentPadding: EdgeInsets.symmetric(
                    horizontal: 18,
                    vertical: 10,
                  ),
                  leading: Icon(option.$2, color: mint),
                  title: Text(option.$3),
                  subtitle: Text(option.$4),
                  trailing: Icon(Icons.chevron_right),
                  onTap: () => Navigator.pop(context, option.$1),
                ),
              ),
            ),
          SizedBox(height: 8),
          Text(
            'Each rule has its own apps and settings.',
            style: TextStyle(color: muted),
          ),
        ],
      ),
    ),
  );
  if (kind != null && context.mounted) {
    await openRuleEditor(context, controller, kind);
  }
}

Future<void> openRuleEditor(
  BuildContext context,
  BlockingController controller,
  String kind, {
  RuleConfig? rule,
}) => Navigator.of(context).push<void>(
  MaterialPageRoute(
    builder: (_) => Theme(
      data: Theme.of(context),
      child: RuleEditor(controller: controller, kind: kind, existing: rule),
    ),
  ),
);

class RuleEditor extends StatefulWidget {
  const RuleEditor({
    super.key,
    required this.controller,
    required this.kind,
    this.existing,
  });
  final BlockingController controller;
  final String kind;
  final RuleConfig? existing;
  @override
  State<RuleEditor> createState() => _RuleEditorState();
}

class _RuleEditorState extends State<RuleEditor> with DesignColors<RuleEditor> {
  late final TextEditingController _name, _minutes;
  final _search = TextEditingController();
  late Set<String> _selected;
  late Set<int> _days;
  late List<Map<String, String>> _times;
  late bool _shared;
  int _step = 0;
  String? _error;
  bool _saving = false, _allowPop = false;
  late String _initial;
  bool get _editing => widget.existing != null || _quick;
  String get _snapshot =>
      '${_name.text}|${_minutes.text}|${_selected.toList()..sort()}|${_days.toList()..sort()}|$_times|$_shared';
  bool get _dirty => _snapshot != _initial;
  Future<void> _back() async {
    if (_saving) return;
    if ((_editing && _step != 1) || (!_editing && _step > 0)) {
      setState(() {
        _step = _editing ? 1 : _step - 1;
        _error = null;
      });
      return;
    }
    if (_dirty) {
      final discard = await showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
          title: Text('Discard changes?'),
          content: Text('Your saved configuration will stay as it was.'),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: Text('Keep editing'),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(context, true),
              child: Text('Discard'),
            ),
          ],
        ),
      );
      if (discard != true || !mounted) return;
    }
    setState(() => _allowPop = true);
    if (mounted) Navigator.pop(context);
  }

  bool get _schedule => widget.kind == 'schedule';
  bool get _quick => widget.kind == 'quick';
  @override
  void initState() {
    super.initState();
    final r = widget.existing;
    _name = TextEditingController(text: r?.name ?? '');
    _minutes = TextEditingController(
      text: '${r?.minutes ?? (_quick ? widget.controller.quickMinutes : 30)}',
    );
    _selected = {
      ...?r?.packages,
      if (_quick && r == null) ...widget.controller.quickPackages,
    };
    _days = {
      ...?r?.days,
      if (r == null) ...{1, 2, 3, 4, 5},
    };
    _times =
        (r?.times ??
                [
                  {'start': '09:00', 'end': '12:00'},
                ])
            .map((t) => Map<String, String>.from(t))
            .toList();
    _shared = r?.shared ?? true;
    _step = _editing ? 1 : 0;
    _initial = _snapshot;
  }

  @override
  void dispose() {
    _name.dispose();
    _minutes.dispose();
    _search.dispose();
    super.dispose();
  }

  int? get _duration => int.tryParse(_minutes.text);
  String get _title => _quick
      ? 'Quick block'
      : '${widget.existing == null ? 'New' : 'Edit'} ${_schedule ? 'schedule' : 'usage limit'}';
  Future<void> _pickTime(int index, String field) async {
    final parts = _times[index][field]!.split(':').map(int.parse).toList();
    final time = await showTimePicker(
      context: context,
      initialTime: TimeOfDay(hour: parts[0], minute: parts[1]),
      builder: (context, child) => MediaQuery(
        data: MediaQuery.of(context).copyWith(alwaysUse24HourFormat: true),
        child: child!,
      ),
    );
    if (time != null && mounted) {
      setState(
        () => _times[index][field] =
            '${time.hour.toString().padLeft(2, '0')}:${time.minute.toString().padLeft(2, '0')}',
      );
    }
  }

  void _next() {
    String? error;
    if (_step == 0) {
      if (_schedule && _days.isEmpty) error = 'Choose at least one day.';
      if (_schedule && _times.any((t) => t['start'] == t['end'])) {
        error = 'Start and end times must differ.';
      }
      if (!_schedule &&
          (_duration == null || _duration! < 1 || _duration! > 1440)) {
        error = 'Choose 1–1,440 minutes.';
      }
    }
    if (_step == 1 && _selected.isEmpty) error = 'Select at least one app.';
    setState(() {
      _error = error;
      if (error == null) _step = _editing && _step == 0 ? 1 : _step + 1;
    });
  }

  Future<void> _save() async {
    if (!_quick && _name.text.trim().isEmpty) {
      setState(() => _error = 'Give your rule a name.');
      return;
    }
    setState(() {
      _saving = true;
      _error = null;
    });
    final c = widget.controller;
    bool saved;
    if (_quick) {
      saved = await c.action('saveQuickConfig', {
        'packages': _selected.toList(),
        'minutes': _duration,
      });
    } else {
      final r = RuleConfig(
        id:
            widget.existing?.id ??
            'local-${DateTime.now().microsecondsSinceEpoch}',
        name: _name.text.trim(),
        kind: widget.kind,
        packages: _selected,
        days: _days,
        times: _times,
        minutes: _duration ?? 30,
        shared: _shared,
        zone: widget.existing?.zone ?? c.zone,
        enabled: widget.existing?.enabled ?? true,
      );
      saved = await c.action('saveLocalRule', {'rule': r.toMap()});
    }
    if (!mounted) return;
    if (saved) {
      setState(() => _allowPop = true);
      Navigator.pop(context);
    } else {
      setState(() {
        _saving = false;
        _error = c.error ?? 'Could not save changes.';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final steps = _editing ? ['Apps', 'Review'] : ['Timing', 'Apps', 'Review'];
    return PopScope(
      canPop: _allowPop,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) _back();
      },
      child: Scaffold(
        appBar: AppBar(
          title: Text(_title),
          leading: IconButton(
            icon: Icon(Icons.arrow_back),
            onPressed: _saving ? null : _back,
          ),
        ),
        body: SafeArea(
          child: Column(
            children: [
              Padding(
                padding: EdgeInsets.fromLTRB(24, 8, 24, 20),
                child: Row(
                  children: [
                    for (var i = 0; i < steps.length; i++)
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Container(
                              height: 3,
                              margin: EdgeInsets.only(right: 8),
                              color: i <= (_editing ? _step - 1 : _step)
                                  ? mint
                                  : line,
                            ),
                            SizedBox(height: 8),
                            Text(
                              '${i + 1}  ${steps[i]}',
                              style: TextStyle(
                                color: i == (_editing ? _step - 1 : _step)
                                    ? mint
                                    : muted,
                                fontSize: 12,
                              ),
                            ),
                          ],
                        ),
                      ),
                  ],
                ),
              ),
              Expanded(
                child: _step == 1
                    ? _appPicker()
                    : ListView(
                        padding: EdgeInsets.fromLTRB(24, 4, 24, 24),
                        children: _step == 0 ? _timing() : _review(),
                      ),
              ),
              Container(
                padding: EdgeInsets.fromLTRB(24, 12, 24, 18),
                decoration: BoxDecoration(
                  border: Border(top: BorderSide(color: line)),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    if (_error != null)
                      Padding(
                        padding: EdgeInsets.only(bottom: 12),
                        child: Text(
                          _error!,
                          style: TextStyle(color: Color(0xFFFFB4AB)),
                        ),
                      ),
                    FilledButton(
                      key: Key('editor-continue'),
                      onPressed: _saving ? null : (_step == 2 ? _save : _next),
                      child: Text(
                        _saving
                            ? 'Saving…'
                            : _editing && _step == 0
                            ? 'Done'
                            : _step < 2
                            ? 'Continue'
                            : _quick
                            ? 'Save quick block'
                            : widget.existing == null
                            ? 'Create rule'
                            : 'Save changes',
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  List<Widget> _timing() => [
    Text(
      _schedule
          ? 'When should apps be blocked?'
          : _quick
          ? 'Make a little room.'
          : 'How much time feels right?',
      style: Theme.of(context).textTheme.headlineSmall,
    ),
    SizedBox(height: 12),
    Text(
      _schedule
          ? 'Choose your hours and repeat days. Other rules can run alongside this one.'
          : _quick
          ? 'Set the timer for your next block. Running blocks keep their own end time.'
          : 'The allowance resets at midnight. Usage earlier today counts too.',
      style: TextStyle(color: muted),
    ),
    SizedBox(height: 28),
    if (_schedule) ...[
      for (var i = 0; i < _times.length; i++)
        Padding(
          padding: EdgeInsets.only(bottom: 12),
          child: Surface(
            child: Column(
              children: [
                Row(
                  children: [
                    Expanded(child: _timeButton(i, 'start', 'From')),
                    Padding(
                      padding: EdgeInsets.symmetric(horizontal: 12),
                      child: Text('—'),
                    ),
                    Expanded(child: _timeButton(i, 'end', 'Until')),
                    if (_times.length > 1)
                      IconButton(
                        tooltip: 'Remove time window',
                        onPressed: () => setState(() => _times.removeAt(i)),
                        icon: Icon(Icons.close, size: 20),
                      ),
                  ],
                ),
                if (_times[i]['end']!.compareTo(_times[i]['start']!) < 0)
                  Padding(
                    padding: EdgeInsets.only(top: 12),
                    child: Text(
                      'Ends the following day',
                      style: TextStyle(color: amber),
                    ),
                  ),
              ],
            ),
          ),
        ),
      if (_times.length < 12)
        Align(
          alignment: Alignment.centerLeft,
          child: TextButton.icon(
            onPressed: () =>
                setState(() => _times.add({'start': '18:00', 'end': '19:00'})),
            icon: Icon(Icons.add),
            label: Text('Add time window'),
          ),
        ),
      SizedBox(height: 24),
      Text(
        'Repeat on',
        style: TextStyle(fontSize: 18, fontWeight: FontWeight.w600),
      ),
      SizedBox(height: 12),
      Row(
        children: [
          for (var d = 1; d <= 7; d++)
            Expanded(
              child: Padding(
                padding: EdgeInsets.symmetric(horizontal: 3),
                child: Semantics(
                  button: true,
                  selected: _days.contains(d),
                  label: [
                    'Monday',
                    'Tuesday',
                    'Wednesday',
                    'Thursday',
                    'Friday',
                    'Saturday',
                    'Sunday',
                  ][d - 1],
                  child: InkWell(
                    key: ValueKey('weekday-$d'),
                    borderRadius: BorderRadius.circular(24),
                    onTap: () => setState(() {
                      if (_days.contains(d)) {
                        _days.remove(d);
                      } else {
                        _days.add(d);
                      }
                    }),
                    child: Container(
                      height: 48,
                      alignment: Alignment.center,
                      decoration: BoxDecoration(
                        color: _days.contains(d) ? mint : panel,
                        border: Border.all(color: line),
                        borderRadius: BorderRadius.circular(24),
                      ),
                      child: ExcludeSemantics(
                        child: Text(
                          ['M', 'T', 'W', 'T', 'F', 'S', 'S'][d - 1],
                          style: TextStyle(
                            color: _days.contains(d) ? canvas : ink,
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            ),
        ],
      ),
      SizedBox(height: 16),
      Text(
        'Times use ${widget.existing?.zone ?? widget.controller.zone}.',
        style: TextStyle(color: muted),
      ),
    ] else ...[
      TextField(
        key: Key('rule-minutes'),
        controller: _minutes,
        keyboardType: TextInputType.number,
        decoration: InputDecoration(
          labelText: _quick ? 'Block duration' : 'Daily allowance',
          suffixText: 'minutes',
        ),
      ),
      SizedBox(height: 12),
      Wrap(
        spacing: 8,
        children: [
          for (final n in [15, 30, 60, 120])
            ActionChip(
              label: Text('$n min'),
              onPressed: () => setState(() => _minutes.text = '$n'),
            ),
        ],
      ),
      if (!_quick) ...[
        SizedBox(height: 24),
        Surface(
          padding: EdgeInsets.all(8),
          child: SwitchListTile(
            title: Text('Share the allowance'),
            subtitle: Text(
              _shared
                  ? 'Selected apps share one daily budget.'
                  : 'Each selected app gets its own daily budget.',
            ),
            value: _shared,
            onChanged: (v) => setState(() => _shared = v),
          ),
        ),
      ],
    ],
  ];
  Widget _timeButton(int index, String field, String title) => InkWell(
    onTap: () => _pickTime(index, field),
    child: Padding(
      padding: EdgeInsets.symmetric(vertical: 6),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(title, style: TextStyle(color: muted)),
          SizedBox(height: 6),
          Text(
            _times[index][field]!,
            style: TextStyle(fontSize: 26, fontWeight: FontWeight.w600),
          ),
        ],
      ),
    ),
  );
  Widget _appPicker() {
    final apps =
        [
              ...widget.controller.apps,
              for (final pkg in _selected)
                if (!widget.controller.apps.any((a) => a.package == pkg))
                  widget.controller.app(pkg),
            ]
            .where(
              (a) => '${a.label} ${a.package}'.toLowerCase().contains(
                _search.text.toLowerCase(),
              ),
            )
            .toList();
    return Column(
      children: [
        Padding(
          padding: EdgeInsets.symmetric(horizontal: 24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'Choose your apps',
                style: Theme.of(context).textTheme.headlineSmall,
              ),
              if (_editing)
                Padding(
                  padding: EdgeInsets.symmetric(vertical: 12),
                  child: OutlinedButton.icon(
                    key: Key('edit-condition'),
                    onPressed: () => setState(() => _step = 0),
                    icon: Icon(ruleIcon(widget.kind)),
                    label: Text(
                      _schedule
                          ? 'Schedule · ${dayLabel(_days)}'
                          : '${_quick ? 'Timer' : 'Daily allowance'} · ${_minutes.text} min',
                    ),
                  ),
                ),
              SizedBox(height: 12),
              TextField(
                controller: _search,
                onChanged: (_) => setState(() {}),
                decoration: InputDecoration(
                  hintText: 'Search apps',
                  prefixIcon: Icon(Icons.search),
                ),
              ),
              Row(
                children: [
                  Expanded(
                    child: Text(
                      '${_selected.length} selected',
                      style: TextStyle(color: muted),
                    ),
                  ),
                  TextButton(
                    onPressed: () => setState(
                      () => _selected.addAll(apps.map((a) => a.package)),
                    ),
                    child: Text('Select all'),
                  ),
                  TextButton(
                    onPressed: () => setState(_selected.clear),
                    child: Text('Clear'),
                  ),
                ],
              ),
            ],
          ),
        ),
        if (apps.isEmpty)
          Padding(
            padding: EdgeInsets.all(24),
            child: Text('No matching apps.'),
          ),
        Expanded(
          child: ListView.builder(
            itemCount: apps.length,
            itemBuilder: (context, i) {
              final a = apps[i];
              return CheckboxListTile(
                key: ValueKey('app-${a.package}'),
                secondary: AppBadge(a),
                title: Text(a.label),
                contentPadding: EdgeInsets.symmetric(horizontal: 24),
                value: _selected.contains(a.package),
                onChanged: (v) => setState(() {
                  if (v == true) {
                    _selected.add(a.package);
                  } else {
                    _selected.remove(a.package);
                  }
                }),
              );
            },
          ),
        ),
        Padding(
          padding: EdgeInsets.symmetric(horizontal: 24, vertical: 8),
          child: Text(
            'Essential phone controls are excluded.',
            style: TextStyle(color: muted, fontSize: 12),
          ),
        ),
      ],
    );
  }

  List<Widget> _review() => [
    Text(
      _quick ? 'Ready whenever you are.' : 'Make it yours.',
      style: Theme.of(context).textTheme.headlineSmall,
    ),
    SizedBox(height: 24),
    if (!_quick)
      TextField(
        key: Key('rule-name'),
        controller: _name,
        maxLength: 80,
        decoration: InputDecoration(
          labelText: 'Rule name',
          hintText: 'e.g. Study',
        ),
      ),
    SizedBox(height: 20),
    Surface(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(ruleIcon(widget.kind), color: mint),
              SizedBox(width: 12),
              Text(
                _schedule
                    ? 'Schedule'
                    : _quick
                    ? 'Quick block'
                    : 'Usage limit',
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.w600),
              ),
            ],
          ),
          SizedBox(height: 16),
          Text(
            _schedule
                ? '${dayLabel(_days)}\n${_times.map((t) => "${t['start']}–${t['end']}").join('\n')}'
                : '${_duration ?? 30} minutes${_quick
                      ? ''
                      : _shared
                      ? ' shared each day'
                      : ' per app each day'}',
          ),
          Divider(height: 32),
          Text(
            '${_selected.length} apps selected',
            style: TextStyle(fontWeight: FontWeight.w600),
          ),
          SizedBox(height: 8),
          Text(
            _selected.map(widget.controller.label).join(', '),
            style: TextStyle(color: muted),
          ),
        ],
      ),
    ),
    SizedBox(height: 20),
    Text(
      _quick
          ? 'Save this setup, then tap Start on Blocking. This does not start a block yet.'
          : 'Other rules stay independent. An app stays blocked while any applicable rule blocks it.',
      style: TextStyle(color: muted),
    ),
  ];
}

