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
import 'package:mindful/config/app_constants.dart';
import 'package:mindful/config/navigation/app_routes.dart';
import 'package:mindful/core/services/method_channel_service.dart';
import 'package:mindful/ui/common/default_list_tile.dart';
import 'package:mindful/ui/common/sliver_tabs_bottom_padding.dart';

class TabAbout extends StatelessWidget {
  const TabAbout({super.key});
  @override
  Widget build(BuildContext context) => CustomScrollView(
    slivers: [
      SliverList.list(
        children: [
          Icon(
            Icons.spa_outlined,
            size: 48,
            color: Theme.of(context).colorScheme.primary,
          ),
          const SizedBox(height: 20),
          Text(
            'FocusRelay',
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.headlineSmall,
          ),
          const SizedBox(height: 8),
          Text(
            MethodChannelService.instance.deviceInfo.mindfulVersion,
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: 28),
          const Text(
            'Built on Mindful',
            style: TextStyle(fontSize: 20, fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: 12),
          const Text(
            'Original Mindful by Pawan Nagar and contributors. This modified edition adds local rules, remote control and a redesigned interface. Distributed under GPL-2.0; original copyright and license notices are retained.',
          ),
          const SizedBox(height: 20),
          DefaultListTile(
            titleText: 'What’s new',
            leadingIcon: Icons.new_releases_outlined,
            onPressed: () =>
                Navigator.pushNamed(context, AppRoutes.changeLogsPath),
          ),
          DefaultListTile(
            titleText: 'Open-source licenses',
            leadingIcon: Icons.description_outlined,
            onPressed: () => showLicensePage(
              context: context,
              applicationName: 'FocusRelay',
              applicationVersion:
                  MethodChannelService.instance.deviceInfo.mindfulVersion,
              applicationLegalese:
                  'Mindful © 2024 Pawan Nagar and contributors. GPL-2.0. Modified edition by Rafail Andreou.',
            ),
          ),
          for (final item in [
            (
              'Modified source',
              'https://github.com/RafailAndreou/FocusRelay',
            ),
            ('Original source & contributors', AppConstants.githubUrl),
            ('Support original development', AppConstants.bmcUrl),
            ('Report an upstream issue', AppConstants.githubIssueDirectUrl),
            (
              'Suggest an upstream idea',
              AppConstants.githubSuggestionDirectUrl,
            ),
            ('Contact original developer', AppConstants.supportEmailUrl),
            ('FocusRelay privacy policy', 'https://github.com/RafailAndreou/FocusRelay/blob/main/PRIVACY.md'),
            ('Report a FocusRelay issue', 'https://github.com/RafailAndreou/FocusRelay/issues'),
            ('Original privacy policy', AppConstants.privacyPolicyUrl),
            ('Instagram', AppConstants.instagramUrl),
            ('Telegram', AppConstants.telegramUrl),
          ])
            DefaultListTile(
              titleText: item.$1,
              trailing: const Icon(Icons.open_in_new, size: 18),
              onPressed: () => MethodChannelService.instance.launchUrl(item.$2),
            ),
          const SizedBox(height: 16),
          const Text(
            'Remote control shares selected app names, rules and connection status with your configured relay. Local activity stays on this phone.',
          ),
        ],
      ),
      const SliverTabsBottomPadding(),
    ],
  );
}

