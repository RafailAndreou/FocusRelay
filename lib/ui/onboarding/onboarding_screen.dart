// Modified by Rafail Andreou, 2026-10-26, for FocusRelay. Original notices retained.
/*
 *
 *  * Copyright (c) 2024 Mindful (https://github.com/akaMrNagar/Mindful)
 *  * Author : Pawan Nagar (https://github.com/akaMrNagar)
 *  *
 *  * This source code is licensed under the GPL-2.0 license license found in the
 *  * LICENSE file in the root directory of this source tree.
 *
 */

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:mindful/config/navigation/navigation_service.dart';
import 'package:mindful/providers/system/mindful_settings_provider.dart';
import 'package:mindful/providers/system/permissions_provider.dart';
import 'package:mindful/ui/onboarding/permission_page.dart';

class OnboardingScreen extends ConsumerStatefulWidget {
  const OnboardingScreen({super.key, required this.isOnboardingDone});
  final bool isOnboardingDone;
  @override
  ConsumerState<OnboardingScreen> createState() => _OnboardingState();
}

class _OnboardingState extends ConsumerState<OnboardingScreen> {
  late bool _permissions = widget.isOnboardingDone;
  bool _finishing = false;
  Future<void> _finish() async {
    if (_finishing) return;
    setState(() => _finishing = true);
    ref.read(mindfulSettingsProvider.notifier).markOnboardingDone();
    NavigationService.instance.init(
      showChangeLogsToo: !widget.isOnboardingDone,
    );
  }

  @override
  Widget build(BuildContext context) {
    final perms = ref.watch(permissionProvider);
    final ready =
        perms.haveUsageAccessPermission &&
        perms.haveDisplayOverlayPermission &&
        perms.haveAlarmsPermission &&
        perms.haveNotificationPermission;
    return Scaffold(
      appBar: AppBar(
        title: const Text('Set up FocusRelay'),
        automaticallyImplyLeading: false,
        leading: _permissions && !widget.isOnboardingDone
            ? IconButton(
                icon: const Icon(Icons.arrow_back),
                onPressed: () => setState(() => _permissions = false),
              )
            : null,
      ),
      body: SafeArea(
        child: _permissions
            ? const PermissionsPage()
            : ListView(
                padding: const EdgeInsets.all(24),
                children: [
                  const SizedBox(height: 32),
                  Align(
                    alignment: Alignment.centerLeft,
                    child: Icon(
                      Icons.spa_outlined,
                      size: 64,
                      color: Theme.of(context).colorScheme.primary,
                    ),
                  ),
                  const SizedBox(height: 28),
                  Text(
                    'Make room for what matters.',
                    style: Theme.of(context).textTheme.headlineLarge,
                  ),
                  const SizedBox(height: 16),
                  const Text(
                    'Choose the apps you want to pause. Build a routine that fits your day.',
                  ),
                  const SizedBox(height: 32),
                  for (final item in [
                    (
                      Icons.bolt_outlined,
                      'Quick block',
                      'Take a break whenever you need it.',
                    ),
                    (
                      Icons.calendar_today_outlined,
                      'Your own rules',
                      'Schedules and daily allowances, each with its own apps.',
                    ),
                    (
                      Icons.insights_outlined,
                      'Understand your habits',
                      'See your activity and app usage on this phone.',
                    ),
                  ])
                    Card(
                      child: ListTile(
                        leading: Icon(item.$1),
                        title: Text(item.$2),
                        subtitle: Text(item.$3),
                      ),
                    ),
                ],
              ),
      ),
      bottomNavigationBar: SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(24, 12, 24, 20),
          child: FilledButton(
            onPressed: _finishing
                ? null
                : _permissions
                ? (ready ? _finish : null)
                : () => setState(() => _permissions = true),
            child: Text(
              _finishing
                  ? 'Opening…'
                  : _permissions
                  ? 'Finish setup'
                  : 'Continue',
            ),
          ),
        ),
      ),
    );
  }
}

