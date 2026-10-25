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

class DefaultEffects {
  static List<Effect> get transitionIn => [
    FadeEffect(duration: 180.ms, curve: Curves.easeOut),
    MoveEffect(
      duration: 180.ms,
      curve: Curves.easeOut,
      begin: const Offset(0, 8),
      end: Offset.zero,
    ),
  ];
}

