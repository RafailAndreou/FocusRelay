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

class DefaultPageTransitionsBuilder extends PageTransitionsBuilder {
  const DefaultPageTransitionsBuilder();
  @override
  Widget buildTransitions<T>(
    PageRoute<T> route,
    BuildContext context,
    Animation<double> animation,
    Animation<double> secondaryAnimation,
    Widget child,
  ) {
    if (MediaQuery.disableAnimationsOf(context)) return child;
    return FadeTransition(
      opacity: animation.drive(
        CurveTween(curve: const Interval(0, .65, curve: Curves.easeOut)),
      ),
      child: SlideTransition(
        position: animation.drive(
          Tween(
            begin: const Offset(.025, 0),
            end: Offset.zero,
          ).chain(CurveTween(curve: Curves.easeOutCubic)),
        ),
        child: child,
      ),
    );
  }
}

