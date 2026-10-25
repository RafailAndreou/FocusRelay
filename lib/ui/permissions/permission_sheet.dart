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
import 'package:mindful/ui/permissions/permission_granting_steps.dart';

class PermissionSheet extends StatelessWidget {
  const PermissionSheet({
    super.key,
    required this.icon,
    required this.title,
    required this.description,
    required this.onTapGrantPermission,
    this.isAccessibilityPerm = false,
    this.deviceSwitchTileLabel,
  });

  final IconData icon;
  final String title;
  final String description;
  final VoidCallback onTapGrantPermission;
  final bool isAccessibilityPerm;
  final String? deviceSwitchTileLabel;

  @override
  Widget build(BuildContext context) {
    final positiveButtonLabel = isAccessibilityPerm
        ? context.locale.permission_button_agree_and_continue
        : context.locale.permission_button_grant_permission;

    return SafeArea(
      child: SingleChildScrollView(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                Icon(icon, color: Theme.of(context).colorScheme.primary),
                const Spacer(),
                IconButton(
                  tooltip: 'Close',
                  onPressed: () => Navigator.maybePop(context),
                  icon: const Icon(Icons.close),
                ),
              ],
            ),
            16.vBox,
            Text(title, style: Theme.of(context).textTheme.headlineSmall),
            12.vBox,
            Text(description),
            if (deviceSwitchTileLabel != null) ...[
              24.vBox,
              PermissionGrantingSteps(
                labelOfBtnToClick: positiveButtonLabel,
                deviceSwitchTileLabel: deviceSwitchTileLabel!,
                isAccessibilityPerm: isAccessibilityPerm,
              ),
            ],
            24.vBox,
            Text(
              context.locale.permission_sheet_privacy_info,
              style: TextStyle(
                fontSize: 12,
                color: Theme.of(context).colorScheme.onSurfaceVariant,
              ),
            ),
            24.vBox,
            FilledButton(
              onPressed: onTapGrantPermission,
              child: Text(positiveButtonLabel),
            ),
            if (isAccessibilityPerm)
              TextButton(
                onPressed: () => Navigator.maybePop(context),
                child: Text(context.locale.permission_button_not_now),
              ),
          ],
        ),
      ),
    );
  }
}

