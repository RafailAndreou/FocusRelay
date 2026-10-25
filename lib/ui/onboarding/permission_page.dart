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
import 'package:mindful/core/extensions/ext_build_context.dart';
import 'package:mindful/core/extensions/ext_num.dart';
import 'package:mindful/core/services/method_channel_service.dart';
import 'package:mindful/ui/permissions/alarm_permission_tile.dart';
import 'package:mindful/ui/permissions/battery_permission_tile.dart';
import 'package:mindful/ui/permissions/display_overlay_permission_tile.dart';
import 'package:mindful/ui/permissions/notification_permission_tile.dart';
import 'package:mindful/ui/permissions/usage_access_permission_tile.dart';

class PermissionsPage extends StatelessWidget {
  const PermissionsPage({super.key});

  @override
  Widget build(BuildContext context) {
    // 24 is min sdk
    final sdkVersion = MethodChannelService.instance.deviceInfo.sdkVersion;

    return ListView(
      padding: const EdgeInsets.fromLTRB(24, 12, 24, 24),
      children: [
        Text(
          'Ready to focus?',
          style: Theme.of(context).textTheme.headlineLarge,
        ),
        12.vBox,
        Text(
          context.locale.onboarding_page_permissions_info,
          style: TextStyle(
            color: Theme.of(context).colorScheme.onSurfaceVariant,
          ),
        ),
        28.vBox,
        const UsageAccessPermissionTile(),
        const DisplayOverlayPermissionTile(),
        const NotificationPermissionTile(),
        if (sdkVersion >= 31) const AlarmPermissionTile(),
        20.vBox,
        const Text(
          'Keep FocusRelay available',
          style: TextStyle(fontWeight: FontWeight.w600),
        ),
        8.vBox,
        const BatteryPermissionTile(),
      ],
    );
  }
}

