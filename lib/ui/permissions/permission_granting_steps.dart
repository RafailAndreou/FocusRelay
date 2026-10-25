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

class PermissionGrantingSteps extends StatelessWidget {
  const PermissionGrantingSteps({
    super.key,
    required this.deviceSwitchTileLabel,
    required this.labelOfBtnToClick,
    this.isAccessibilityPerm = false,
  });
  final String deviceSwitchTileLabel, labelOfBtnToClick;
  final bool isAccessibilityPerm;
  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      for (final step in [
        (1, 'Tap “$labelOfBtnToClick” to open Android settings.'),
        (2, 'Choose FocusRelay in the app list.'),
        (3, 'Enable “$deviceSwitchTileLabel”, then return here.'),
      ])
        Padding(
          padding: const EdgeInsets.only(bottom: 12),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              CircleAvatar(
                radius: 12,
                backgroundColor: Theme.of(
                  context,
                ).colorScheme.surfaceContainerHigh,
                child: Text('${step.$1}', style: const TextStyle(fontSize: 12)),
              ),
              const SizedBox(width: 12),
              Expanded(child: Text(step.$2)),
            ],
          ),
        ),
    ],
  );
}

