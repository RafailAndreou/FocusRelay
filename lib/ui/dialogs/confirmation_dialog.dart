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

Future<bool> showConfirmationDialog({
  required BuildContext context,
  required Object heroTag,
  required String title,
  required String info,
  required IconData icon,
  required String positiveLabel,
  bool isBarrierDismissible = true,
  String? negativeLabel,
}) async =>
    await showDialog<bool>(
      context: context,
      barrierDismissible: isBarrierDismissible,
      builder: (context) => AlertDialog(
        scrollable: true,
        icon: Icon(icon),
        title: Text(title),
        content: Text(info),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: Text(negativeLabel ?? context.locale.dialog_button_cancel),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: Text(positiveLabel),
          ),
        ],
      ),
    ) ??
    false;

