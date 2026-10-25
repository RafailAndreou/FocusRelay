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

class DefaultHero extends StatelessWidget {
  const DefaultHero({super.key, required this.tag, required this.child});

  final Object tag;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return HeroMode(
      enabled: !MediaQuery.disableAnimationsOf(context),
      child: Hero(
        tag: tag,
        child: Material(color: Colors.transparent, elevation: 0, child: child),
      ),
    );
  }
}

