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

class SliverTabsBottomPadding extends StatelessWidget {
  const SliverTabsBottomPadding({super.key});
  @override
  Widget build(BuildContext context) =>
      const SliverToBoxAdapter(child: SizedBox(height: 112));
}

