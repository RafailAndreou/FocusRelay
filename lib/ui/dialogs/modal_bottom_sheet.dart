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
import 'package:mindful/core/extensions/ext_num.dart';
import 'package:mindful/ui/common/content_section_header.dart';
import 'package:mindful/ui/common/sliver_tabs_bottom_padding.dart';

/// Opens modal bottom sheet with the passed sliver body
///
/// [initialSize] should be between 0-1
Future<void> showDefaultBottomSheet({
  required BuildContext context,
  required Widget sliverBody,
  EdgeInsets padding = const EdgeInsets.symmetric(horizontal: 24),
  Widget? header,
  String? headerTitle,
  double initialSize = 0.5,
}) async => showModalBottomSheet(
  context: context,
  isScrollControlled: true,
  useSafeArea: true,
  showDragHandle: true,
  sheetAnimationStyle: AnimationStyle(
    duration: MediaQuery.disableAnimationsOf(context)
        ? Duration.zero
        : const Duration(milliseconds: 200),
    curve: Curves.easeOut,
    reverseDuration: MediaQuery.disableAnimationsOf(context)
        ? Duration.zero
        : const Duration(milliseconds: 160),
    reverseCurve: Curves.easeIn,
  ),
  builder: (sheetContext) => DraggableScrollableSheet(
    expand: false,
    initialChildSize: initialSize,
    builder: (context, scrollController) => Padding(
      padding: padding,
      child: Column(
        children: [
          /// Header
          headerTitle != null
              ? ContentSectionHeader(
                  title: headerTitle,
                  padding: const EdgeInsets.only(bottom: 12),
                )
              : header ?? 0.vBox,

          /// Body
          Expanded(
            child: CustomScrollView(
              controller: scrollController,
              slivers: [sliverBody, const SliverTabsBottomPadding()],
            ),
          ),
        ],
      ),
    ),
  ),
);

