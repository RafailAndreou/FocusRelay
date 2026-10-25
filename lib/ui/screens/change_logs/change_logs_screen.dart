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
import 'package:mindful/config/app_constants.dart';
import 'package:mindful/core/services/method_channel_service.dart';
import 'package:mindful/providers/system/mindful_settings_provider.dart';
import 'package:mindful/ui/common/scaffold_shell.dart';
import 'package:mindful/ui/common/default_list_tile.dart';

class ChangeLogsScreen extends ConsumerStatefulWidget {
  const ChangeLogsScreen({super.key});
  @override
  ConsumerState<ChangeLogsScreen> createState() => _ChangeLogsScreenState();
}

class _ChangeLogsScreenState extends ConsumerState<ChangeLogsScreen> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) {
        ref.read(mindfulSettingsProvider.notifier).updateAppVersion();
      }
    });
  }

  @override
  Widget build(BuildContext context) => ScaffoldShell(
    items: [
      NavbarItem(
        icon: Icons.new_releases_outlined,
        filledIcon: Icons.new_releases,
        titleText: 'What’s new',
        sliverBody: ListView(
          children: [
            Text(
              'A calmer, more connected Mindful.',
              style: Theme.of(context).textTheme.headlineLarge,
            ),
            const SizedBox(height: 12),
            const Text('Design Preview 1.5.1'),
            const SizedBox(height: 28),
            for (final item in [
            ('Clearer rule switches', 'The off thumb stays visible. Saving a rule no longer disables every switch.'),
              (
                'One consistent space',
                'Neutral surfaces and unified setup, feature pages, settings and dialogs.',
              ),
              (
                'A simpler Quick Block',
                'Tap the card to choose apps. Set the timer separately. Start when you’re ready.',
              ),
              (
                'Apps at a glance',
                'Real installed-app icons on rules, allowances and blocking activity.',
              ),
              (
                'Your rules, easier to edit',
                'Existing rules open at their app selection, with their timing one tap away.',
              ),
              (
                'A quieter blocker',
                'Clear app and reason, plus an action to return to Android Home.',
              ),
            ])
              Padding(
                padding: const EdgeInsets.only(bottom: 24),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      item.$1,
                      style: Theme.of(context).textTheme.titleLarge,
                    ),
                    const SizedBox(height: 8),
                    Text(item.$2),
                  ],
                ),
              ),
            DefaultListTile(
              titleText: 'Original Mindful release history',
              trailing: const Icon(Icons.open_in_new),
              onPressed: () => MethodChannelService.instance.launchUrl(
                '${AppConstants.githubUrl}/releases',
              ),
            ),
          ],
        ),
      ),
    ],
  );
}

