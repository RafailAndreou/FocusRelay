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
import 'package:mindful/core/enums/item_position.dart';
import 'package:mindful/ui/common/default_list_tile.dart';

class DefaultDropdownTile<T> extends StatelessWidget {
  const DefaultDropdownTile({
    super.key,
    required this.value,
    required this.items,
    required this.onSelected,
    required this.titleText,
    this.enabled = true,
    this.infoText,
    this.width,
    this.position,
    this.leadingIcon,
    this.dialogIcon,
    this.trailingBuilder,
  });
  final T value;
  final List<DefaultDropdownItem<T>> items;
  final ValueChanged<T> onSelected;
  final String titleText;
  final bool enabled;
  final String? infoText;
  final double? width;
  final ItemPosition? position;
  final IconData? leadingIcon, dialogIcon;
  final Widget Function(T?)? trailingBuilder;
  @override
  Widget build(BuildContext context) {
    final selected = items.where((item) => item.value == value).firstOrNull;
    return DefaultListTile(
      enabled: enabled,
      position: position,
      leadingIcon: leadingIcon,
      titleText: titleText,
      subtitleText: selected?.label,
      trailing:
          trailingBuilder?.call(selected?.value) ??
          const Icon(Icons.expand_more_rounded),
      onPressed: () async {
        final result = await showModalBottomSheet<T>(
          context: context,
          isScrollControlled: true,
          useSafeArea: true,
          builder: (context) => DraggableScrollableSheet(
            expand: false,
            initialChildSize: .65,
            minChildSize: .3,
            maxChildSize: .9,
            builder: (context, scroll) => Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Padding(
                  padding: const EdgeInsets.fromLTRB(24, 0, 12, 16),
                  child: Row(
                    children: [
                      if (dialogIcon != null) ...[
                        Icon(dialogIcon),
                        const SizedBox(width: 12),
                      ],
                      Expanded(
                        child: Text(
                          titleText,
                          style: Theme.of(context).textTheme.titleLarge,
                        ),
                      ),
                      IconButton(
                        tooltip: 'Close',
                        onPressed: () => Navigator.pop(context),
                        icon: const Icon(Icons.close),
                      ),
                    ],
                  ),
                ),
                if (infoText != null)
                  Padding(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 24,
                      vertical: 8,
                    ),
                    child: Text(infoText!),
                  ),
                Expanded(
                  child: ListView(
                    controller: scroll,
                    padding: const EdgeInsets.fromLTRB(20, 0, 20, 24),
                    children: [
                      for (final item in items)
                        Semantics(
                          selected: item.value == value,
                          child: DefaultListTile(
                            leadingIcon: item.value == value
                                ? Icons.radio_button_checked
                                : Icons.radio_button_unchecked,
                            titleText: item.label,
                            trailing: trailingBuilder?.call(item.value),
                            onPressed: () => Navigator.pop(context, item.value),
                          ),
                        ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        );
        if (result != null) onSelected(result);
      },
    );
  }
}

class DefaultDropdownItem<T> {
  DefaultDropdownItem({required this.label, required this.value});
  final String label;
  final T value;
}

