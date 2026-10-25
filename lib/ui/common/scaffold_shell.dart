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
import 'package:mindful/config/navigation/app_routes.dart';
import 'package:mindful/ui/controllers/tab_controller_provider.dart';

@immutable
class NavbarItem {
  const NavbarItem({
    required this.icon,
    required this.filledIcon,
    required this.sliverBody,
    this.titleText,
    this.titleBuilder,
    this.fab,
    this.appBarBg,
    this.actions,
  }) : assert(titleText != null || titleBuilder != null);
  final IconData icon, filledIcon;
  final String? titleText;
  final Widget Function(double)? titleBuilder;
  final Widget? fab, appBarBg;
  final Widget sliverBody;
  final List<Widget>? actions;
}

/// The same compact page chrome is used for every feature and nested editor.
/// Feature bodies keep their existing providers, validation and protection gates.
class ScaffoldShell extends StatefulWidget {
  const ScaffoldShell({
    super.key,
    this.initialTab,
    this.canGoBack = true,
    this.appBarExpandedHeight = 200,
    this.bodyPadding = const EdgeInsets.symmetric(horizontal: 20),
    required this.items,
  });
  final int? initialTab;
  final bool canGoBack;
  final double appBarExpandedHeight;
  final EdgeInsets bodyPadding;
  final List<NavbarItem> items;
  @override
  State<ScaffoldShell> createState() => _ScaffoldShellState();
}

class _ScaffoldShellState extends State<ScaffoldShell>
    with SingleTickerProviderStateMixin {
  late final TabController _tabs;
  @override
  void initState() {
    super.initState();
    _tabs = TabController(
      length: widget.items.length,
      initialIndex: (widget.initialTab ?? 0).clamp(0, widget.items.length - 1),
      vsync: this,
    );
    _tabs.addListener(_changed);
  }

  void _changed() {
    if (mounted) setState(() {});
  }

  @override
  void dispose() {
    _tabs.removeListener(_changed);
    _tabs.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final item = widget.items[_tabs.index];
    final multi = widget.items.length > 1;
    return Scaffold(
      appBar: AppBar(
        automaticallyImplyLeading: false,
        leading: widget.canGoBack
            ? IconButton(
                tooltip: MaterialLocalizations.of(context).backButtonTooltip,
                icon: const Icon(Icons.arrow_back_rounded),
                onPressed: () {
                  final nav = Navigator.of(context);
                  if (nav.canPop()) {
                    nav.pop();
                  } else {
                    nav.pushReplacementNamed(AppRoutes.homePath);
                  }
                },
              )
            : null,
        title: item.titleText != null
            ? Text(
                item.titleText!,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              )
            : item.titleBuilder?.call(0),
        actions: item.actions,
        bottom: multi
            ? TabBar(
                controller: _tabs,
                isScrollable: true,
                tabAlignment: TabAlignment.start,
                dividerColor: Theme.of(
                  context,
                ).colorScheme.outline.withValues(alpha: .4),
                tabs: [
                  for (final tab in widget.items)
                    Tab(text: tab.titleText ?? ''),
                ],
              )
            : null,
      ),
      floatingActionButton: item.fab,
      body: SafeArea(
        top: false,
        child: TabControllerProvider(
          controller: _tabs,
          child: TabBarView(
            controller: _tabs,
            physics: const NeverScrollableScrollPhysics(),
            children: [
              for (var i = 0; i < widget.items.length; i++)
                Padding(
                  padding: widget.bodyPadding.copyWith(top: 16),
                  child: KeyedSubtree(
                    key: PageStorageKey(
                      'feature-$i-${widget.items[i].titleText}',
                    ),
                    child: widget.items[i].sliverBody,
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}

class AppBarTitle extends StatelessWidget {
  const AppBarTitle({super.key, required this.titleText});
  final String titleText;
  @override
  Widget build(BuildContext context) => Text(
    titleText,
    maxLines: 1,
    overflow: TextOverflow.ellipsis,
    style: Theme.of(context).textTheme.titleLarge,
  );
}

