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
import 'package:flutter_animate/flutter_animate.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:mindful/config/navigation/app_routes.dart';
import 'package:mindful/core/extensions/ext_build_context.dart';
import 'package:mindful/core/extensions/ext_num.dart';
import 'package:mindful/core/extensions/ext_widget.dart';
import 'package:mindful/providers/focus/focus_mode_provider.dart';
import 'package:mindful/ui/common/sliver_active_session_alert.dart';
import 'package:mindful/ui/common/sliver_tabs_bottom_padding.dart';
import 'package:mindful/ui/common/styled_text.dart';
import 'package:mindful/ui/screens/focus/focus_mode/focus_distracting_apps_list.dart';
import 'package:mindful/ui/screens/focus/focus_mode/focus_configurations.dart';

class TabFocus extends StatelessWidget {
  const TabFocus({super.key});

  @override
  Widget build(BuildContext context) {
    return CustomScrollView(
      physics: const BouncingScrollPhysics(),
      slivers: [
        /// Information
        StyledText(context.locale.focus_tab_info).sliver,

        8.vSliverBox,

        const SliverActiveSessionAlert(),

        const FocusConfigurations(),

        16.vSliverBox,
        Consumer(
          builder: (context, ref, _) => FilledButton.icon(
            onPressed: () => _startFocusSession(context, ref),
            icon: const Icon(Icons.play_arrow_rounded),
            label: const Text('Start session'),
          ),
        ).sliver,

        40.vSliverBox,
        const FocusDistractingAppsList(),

        const SliverTabsBottomPadding(),
      ],
    );
  }

  void _startFocusSession(BuildContext context, WidgetRef ref) async {
    final focusMode = ref.read(focusModeProvider);

    /// If another focus session is already active
    if (focusMode.activeSession.value != null) {
      context.showSnackAlert(
        context.locale.focus_session_already_active_snack_alert,
      );
      return;
    }

    // If no distracting apps selected
    if (focusMode.focusProfile.distractingApps.isEmpty) {
      context.showSnackAlert(
        context.locale.focus_session_minimum_apps_snack_alert,
      );
      return;
    }

    await ref.read(focusModeProvider.notifier).startNewSession();

    await Future.delayed(300.ms);
    if (context.mounted) {
      Navigator.of(context).pushNamed(AppRoutes.activeSessionPath);
    }
  }
}

