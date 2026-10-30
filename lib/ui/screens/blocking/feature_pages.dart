// Modified by Rafail Andreou, 2026-10-26, for FocusRelay. Original notices retained.
// SPDX-License-Identifier: GPL-2.0-only
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:mindful/providers/system/mindful_settings_provider.dart';
import 'blocking_home.dart';
import 'package:mindful/ui/common/scaffold_shell.dart';
import 'package:mindful/ui/screens/home/statistics/tab_statistics.dart';
import 'package:mindful/ui/screens/home/bedtime/tab_bedtime.dart';
import 'package:mindful/ui/screens/home/notifications/tab_notifications.dart';
import 'package:mindful/ui/screens/home/notifications/new_notification_schedule_fab.dart';

class FeaturePage extends StatelessWidget {
  const FeaturePage({super.key, required this.feature});
  final String feature;
  @override
  Widget build(BuildContext context) => ScaffoldShell(
    items: [
      switch (feature) {
        'bedtime' => const NavbarItem(
          icon: Icons.bedtime_outlined,
          filledIcon: Icons.bedtime,
          titleText: 'Bedtime',
          sliverBody: TabBedtime(),
        ),
        'notifications' => const NavbarItem(
          icon: Icons.notifications_outlined,
          filledIcon: Icons.notifications,
          titleText: 'Notification schedules',
          sliverBody: TabNotifications(),
          fab: NewNotificationScheduleFab(),
        ),
        _ => const NavbarItem(
          icon: Icons.insights_outlined,
          filledIcon: Icons.insights,
          titleText: 'Usage & app limits',
          sliverBody: TabStatistics(),
        ),
      },
    ],
  );
}

class PreferredHome extends ConsumerWidget {
  const PreferredHome({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final preference = ref.watch(
      mindfulSettingsProvider.select((v) => v.defaultHomeTab),
    );
    return BlockingHome(initialTab: [0, 3, 1, 4][preference.index]);
  }
}
