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

import 'package:fluentui_system_icons/fluentui_system_icons.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:mindful/config/navigation/app_routes.dart';
import 'package:mindful/core/extensions/ext_build_context.dart';
import 'package:mindful/core/services/auth_service.dart';
import 'package:mindful/config/navigation/navigation_service.dart';
import 'package:mindful/core/services/method_channel_service.dart';
import 'package:mindful/providers/system/mindful_settings_provider.dart';
import 'package:mindful/providers/system/parental_controls_provider.dart';
import 'package:mindful/providers/system/permissions_provider.dart';

class SplashScreen extends ConsumerStatefulWidget {
  const SplashScreen({super.key});

  @override
  ConsumerState<SplashScreen> createState() => _SplashScreenState();
}

class _SplashScreenState extends ConsumerState<SplashScreen> {
  bool _haveAllEssentialPermissions = false;
  bool _isOnboardingDone = false;
  bool _isAccessProtected = false;
  bool _isAppUpdated = false;

  @override
  void initState() {
    super.initState();
    _checkOnboardingAndPerms();
  }

  void _checkOnboardingAndPerms() async {
    final perms = await ref
        .read(permissionProvider.notifier)
        .fetchPermissionsStatus();

    final settings = await ref.read(mindfulSettingsProvider.notifier).init();
    _isOnboardingDone = settings.isOnboardingDone;
    _isAppUpdated =
        settings.appVersion !=
        MethodChannelService.instance.deviceInfo.mindfulVersion;

    _isAccessProtected =
        (await ref.read(parentalControlsProvider.notifier).init())
            .protectedAccess;
    _haveAllEssentialPermissions =
        perms.haveUsageAccessPermission &&
        perms.haveDisplayOverlayPermission &&
        perms.haveAlarmsPermission &&
        perms.haveNotificationPermission;

    if (mounted) setState(() {});
    _isAccessProtected ? _authenticate() : _goToNextScreen(true);
  }

  void _goToNextScreen(bool shouldDelay) async {
    if (shouldDelay) await Future.delayed(250.ms);
    if (!mounted) return;

    if (_haveAllEssentialPermissions && _isOnboardingDone) {
      NavigationService.instance.init(showChangeLogsToo: _isAppUpdated);
    } else {
      Navigator.of(context).pushReplacementNamed(
        AppRoutes.onboardingPath,
        arguments: {"isOnboardingDone": _isOnboardingDone},
      );
    }
  }

  void _authenticate() async {
    final isAuthenticated = await AuthService.instance.authenticate();

    /// Return if not mounted
    if (!mounted) return;

    /// If removed locks
    if (isAuthenticated == null) {
      context.showSnackAlert(
        context.locale.protected_access_removed_lock_snack_alert,
        icon: FluentIcons.fingerprint_20_filled,
      );
      return;
    }

    /// If aborted the auth
    if (!isAuthenticated) {
      context.showSnackAlert(
        context.locale.protected_access_failed_lock_snack_alert,
        icon: FluentIcons.fingerprint_20_filled,
      );

      return;
    }

    _goToNextScreen(false);
  }

  @override
  Widget build(BuildContext context) => PopScope(
    onPopInvokedWithResult: (didPop, _) {
      if (!didPop) SystemNavigator.pop();
    },
    child: Scaffold(
      body: SafeArea(
        child: Center(
          child: Padding(
            padding: const EdgeInsets.all(32),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(
                  Icons.spa_outlined,
                  size: 52,
                  color: Theme.of(context).colorScheme.primary,
                ),
                const SizedBox(height: 24),
                Text(
                  'FocusRelay',
                  style: Theme.of(context).textTheme.headlineLarge,
                ),
                const SizedBox(height: 8),
                Text(
                  'A little space for your day.',
                  style: TextStyle(
                    color: Theme.of(context).colorScheme.onSurfaceVariant,
                  ),
                ),
                const SizedBox(height: 40),
                if (_isAccessProtected)
                  FilledButton.icon(
                    icon: const Icon(Icons.fingerprint),
                    label: Text(context.locale.unlock_button_label),
                    onPressed: _authenticate,
                  )
                else
                  const SizedBox(
                    width: 24,
                    height: 24,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  ),
              ],
            ),
          ),
        ),
      ),
    ),
  );
}

