// Modified by Rafail Andreou, 2026-10-26, for FocusRelay. Original notices retained.
// SPDX-License-Identifier: GPL-2.0-only
import 'package:flutter/material.dart';
import 'rule_models.dart';

const ink = Color(0xFFF1F0EA);
const muted = Color(0xFFADB2B9);
const mint = Color(0xFF9BD2B2);
const canvas = Color(0xFF17191C);
const panel = Color(0xFF23262B);
const line = Color(0xFF383D45);
const amber = Color(0xFFE7C16B);

ThemeData blockingTheme({
  Brightness brightness = Brightness.dark,
  Color? accent,
  bool amoled = false,
}) {
  final dark = brightness == Brightness.dark;
  final bg = dark ? (amoled ? Colors.black : canvas) : const Color(0xFFF5F4F0);
  final fg = dark ? ink : const Color(0xFF24272C);
  final surface = dark ? panel : Colors.white;
  final primary = accent == null
      ? (dark ? mint : const Color(0xFF356D50))
      : ColorScheme.fromSeed(seedColor: accent, brightness: brightness).primary;
  final scheme =
      ColorScheme.fromSeed(seedColor: primary, brightness: brightness).copyWith(
        primary: primary,
        onPrimary: dark ? canvas : Colors.white,
        surface: bg,
        onSurface: fg,
        surfaceContainer: surface,
        surfaceContainerLow: surface,
        surfaceContainerHigh: dark
            ? const Color(0xFF2D3137)
            : const Color(0xFFE9EAEC),
        surfaceContainerHighest: dark ? line : const Color(0xFFDFE2E6),
        secondary: dark ? const Color(0xFFBCB1DD) : const Color(0xFF6B5D8C),
        secondaryContainer: dark
            ? const Color(0xFF33323E)
            : const Color(0xFFEAE5F1),
        onSecondaryContainer: fg,
        onSurfaceVariant: dark ? muted : const Color(0xFF626970),
        outline: dark ? line : const Color(0xFFCED2D8),
      );
  return ThemeData(
    fontFamily: 'Roboto',
    useMaterial3: true,
    brightness: brightness,
    scaffoldBackgroundColor: bg,
    colorScheme: scheme,
    textTheme: const TextTheme(
      headlineLarge: TextStyle(
        fontSize: 32,
        fontWeight: FontWeight.w700,
        height: 1.15,
        letterSpacing: -.6,
      ),
      headlineSmall: TextStyle(fontSize: 25, fontWeight: FontWeight.w700),
      titleLarge: TextStyle(fontSize: 21, fontWeight: FontWeight.w700),
      titleMedium: TextStyle(fontSize: 16, fontWeight: FontWeight.w600),
      bodyLarge: TextStyle(fontSize: 16, height: 1.4),
      bodyMedium: TextStyle(fontSize: 14, height: 1.45),
      bodySmall: TextStyle(fontSize: 12, color: muted, height: 1.4),
    ),
    appBarTheme: AppBarTheme(
      backgroundColor: bg,
      foregroundColor: fg,
      surfaceTintColor: Colors.transparent,
    ),
    // Resolve every switch state explicitly: the default M3 off thumb uses
    // outline, which otherwise matches this design's off track.
    switchTheme: SwitchThemeData(
      thumbColor: WidgetStateProperty.resolveWith((states) =>
          states.contains(WidgetState.selected) ? scheme.onPrimary : fg),
      trackColor: WidgetStateProperty.resolveWith((states) =>
          states.contains(WidgetState.selected)
              ? primary
              : scheme.surfaceContainerHighest),
      trackOutlineColor: WidgetStateProperty.resolveWith((states) =>
          states.contains(WidgetState.selected)
              ? Colors.transparent
              : scheme.onSurfaceVariant),
    ),
    hintColor: scheme.onSurfaceVariant,
    dividerColor: scheme.outline,
    dividerTheme: DividerThemeData(color: scheme.outline, thickness: 1),
    chipTheme: ChipThemeData(side: BorderSide(color: scheme.outline)),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: surface,
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(16),
        borderSide: BorderSide(color: scheme.outline),
      ),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(16),
        borderSide: BorderSide(color: scheme.outline),
      ),
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        minimumSize: const Size(0, 52),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
        textStyle: const TextStyle(
          fontFamily: 'Roboto',
          fontSize: 16,
          fontWeight: FontWeight.w700,
        ),
      ),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(
        minimumSize: const Size(0, 48),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
      ),
    ),
    bottomSheetTheme: BottomSheetThemeData(
      backgroundColor: surface,
      showDragHandle: true,
    ),
    navigationBarTheme: NavigationBarThemeData(
      backgroundColor: bg,
      indicatorColor: primary.withValues(alpha: .16),
      height: 76,
    ),
    dialogTheme: DialogThemeData(
      backgroundColor: surface,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
    ),
    cardTheme: CardThemeData(color: surface, elevation: 0),
    listTileTheme: const ListTileThemeData(minVerticalPadding: 14),
    splashFactory: InkRipple.splashFactory,
    visualDensity: VisualDensity.standard,
  ).copyWith(
    textTheme: ThemeData(brightness: brightness).textTheme
        .merge(
          const TextTheme(
            headlineLarge: TextStyle(
              fontSize: 32,
              fontWeight: FontWeight.w700,
              letterSpacing: -.6,
            ),
            headlineSmall: TextStyle(fontSize: 25, fontWeight: FontWeight.w700),
            titleLarge: TextStyle(fontSize: 21, fontWeight: FontWeight.w700),
            bodyLarge: TextStyle(fontSize: 16, height: 1.4),
            bodyMedium: TextStyle(fontSize: 14, height: 1.45),
          ),
        )
        .apply(fontFamily: 'Roboto', bodyColor: fg, displayColor: fg),
  );
}

/// Shared colours also let preferences apply to the main four tabs and editors.
mixin DesignColors<T extends StatefulWidget> on State<T> {
  ColorScheme get colors => Theme.of(context).colorScheme;
  Color get ink => colors.onSurface;
  Color get muted => colors.onSurfaceVariant;
  Color get mint => colors.primary;
  Color get canvas => Theme.of(context).scaffoldBackgroundColor;
  Color get panel => colors.surfaceContainer;
  Color get line => colors.outline;
}

class Surface extends StatelessWidget {
  const Surface({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.all(18),
  });
  final Widget child;
  final EdgeInsetsGeometry padding;
  @override
  Widget build(BuildContext context) => Material(
    color: Theme.of(context).colorScheme.surfaceContainer,
    shape: RoundedRectangleBorder(
      borderRadius: BorderRadius.circular(18),
      side: BorderSide(
        color: Theme.of(context).colorScheme.outline.withValues(alpha: .55),
      ),
    ),
    clipBehavior: Clip.antiAlias,
    child: Padding(padding: padding, child: child),
  );
}

class AppBadge extends StatelessWidget {
  const AppBadge(this.app, {super.key, this.size = 36});
  final RuleApp app;
  final double size;
  @override
  Widget build(BuildContext context) => Semantics(
    label: app.label,
    image: true,
    child: ClipRRect(
      borderRadius: BorderRadius.circular(10),
      child: app.icon == null
          ? Container(
              width: size,
              height: size,
              color: Theme.of(context).colorScheme.surfaceContainerHighest,
              alignment: Alignment.center,
              child: Text(
                app.label.isEmpty
                    ? '?'
                    : app.label.characters.first.toUpperCase(),
              ),
            )
          : Image.memory(
              app.icon!,
              width: size,
              height: size,
              gaplessPlayback: true,
              errorBuilder: (_, __, ___) => SizedBox(
                width: size,
                height: size,
                child: const Icon(Icons.apps_rounded),
              ),
            ),
    ),
  );
}

String clock(int ms) {
  final d = DateTime.fromMillisecondsSinceEpoch(ms);
  return '${d.hour.toString().padLeft(2, '0')}:${d.minute.toString().padLeft(2, '0')}';
}

String durationLabel(int seconds) => seconds >= 3600
    ? '${seconds ~/ 3600}h ${(seconds % 3600) ~/ 60}m'
    : '${seconds ~/ 60}m';
String dayLabel(Set<int> days) {
  if (days.length == 7) return 'Every day';
  if (days.length == 5 && days.containsAll({1, 2, 3, 4, 5})) return 'Weekdays';
  const names = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
  return (days.toList()..sort()).map((d) => names[d - 1]).join(', ');
}

String ruleSummary(RuleConfig r) => r.kind == 'usage'
    ? '${r.minutes} min daily · ${r.shared ? 'shared' : 'per app'}'
    : r.kind == 'quick'
    ? '${r.minutes} min quick block'
    : '${dayLabel(r.days)} · ${r.times.map((t) => "${t['start']}–${t['end']}").join(', ')}';
IconData ruleIcon(String kind) => kind == 'usage'
    ? Icons.timelapse_rounded
    : kind == 'quick'
    ? Icons.bolt_rounded
    : Icons.calendar_today_outlined;

/// Bounded icon group with a count of every app that is not displayed.
class AppIconGroup extends StatelessWidget {
  const AppIconGroup(this.apps, {super.key, this.size = 28});
  final List<RuleApp> apps;
  final double size;
  @override
  Widget build(BuildContext context) => LayoutBuilder(
    builder: (context, box) {
      if (apps.isEmpty) return const SizedBox.shrink();
      final count = apps.length == 1
          ? 1
          : box.maxWidth >= size * 2 + 54
          ? 2
          : 1;
      final shown = apps.take(count).toList();
      final hidden = apps.length - shown.length;
      return Semantics(
        label: apps.map((a) => a.label).join(', '),
        child: ExcludeSemantics(
          child: Wrap(
            spacing: 6,
            runSpacing: 4,
            crossAxisAlignment: WrapCrossAlignment.center,
            children: [
              for (final app in shown) AppBadge(app, size: size),
              if (hidden > 0)
                Text(
                  '+$hidden',
                  style: TextStyle(
                    color: Theme.of(context).colorScheme.onSurfaceVariant,
                    fontSize: 12,
                  ),
                ),
            ],
          ),
        ),
      );
    },
  );
}

