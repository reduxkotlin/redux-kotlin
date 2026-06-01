package org.reduxkotlin.sample.taskflow.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.reduxkotlin.sample.taskflow.model.Route
import org.reduxkotlin.sample.taskflow.ui.adaptive.WindowSizeClass

/**
 * One top-level navigation destination. [Route.Board] is reached from [Route.BoardList], so it is
 * not a destination of its own — a board route highlights the Boards tab.
 */
private enum class NavDestination(val label: String, val glyph: String, val route: Route) {
    Boards("Boards", "▦", Route.BoardList),
    Profile("Profile", "☺", Route.Profile),
    Settings("Settings", "⚙", Route.Settings),
}

/** `true` when [route] should highlight this [NavDestination] (Board routes map to Boards). */
private fun NavDestination.matches(route: Route): Boolean = when (this) {
    NavDestination.Boards -> route is Route.BoardList || route is Route.Board
    NavDestination.Profile -> route is Route.Profile
    NavDestination.Settings -> route is Route.Settings
}

/**
 * The adaptive navigation shell. At [WindowSizeClass.Compact] it wraps [content] in a [Scaffold]
 * with a bottom [NavigationBar]; at [WindowSizeClass.Medium] / [WindowSizeClass.Expanded] it places
 * a [NavigationRail] (with an optional [header], e.g. a FAB) beside the [content]. Same three
 * destinations (Boards / Profile / Settings) and the same [NavModel][org.reduxkotlin.sample.taskflow.model.NavModel]
 * route either way; the selected item uses a `secondaryContainer` pill indicator with a Label
 * Medium label. Mirrors the `AdaptiveNav` spec entry (spec-data.js).
 *
 * Pure presentational (Rule C): immutable [sizeClass] + [currentRoute] in; [onNavigate] is
 * remembered by the caller (the screen turns it into a route dispatch). Reads no store.
 *
 * @param sizeClass the current window size class (drives bar vs rail).
 * @param currentRoute the active route; selects the matching destination.
 * @param onNavigate invoked with the tapped destination's [Route].
 * @param modifier the [Modifier] for the shell root.
 * @param header optional rail header content (e.g. a FAB) shown above the rail items (rail only).
 * @param content the screen content hosted inside the shell.
 */
@Composable
public fun AdaptiveNav(
    sizeClass: WindowSizeClass,
    currentRoute: Route,
    onNavigate: (Route) -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    if (sizeClass == WindowSizeClass.Compact) {
        // Edge-to-edge (Rule H): the Scaffold spans the full window so the system bars show the
        // theme colour. `contentWindowInsets = systemBars` tells the Scaffold to pad the content
        // slot for the status bar AND for the portion of the bottom bar that overlaps the
        // gesture/3-button navigation bar — the [NavigationBar] already pads its own buttons via
        // its default `windowInsets`, so this is not double-padding.
        Scaffold(
            modifier = modifier,
            contentWindowInsets = WindowInsets.systemBars,
            bottomBar = { BottomBar(currentRoute = currentRoute, onNavigate = onNavigate) },
        ) { innerPadding ->
            // `consumeWindowInsets` tells nested composables those insets are already handled, so
            // a stray `Modifier.statusBarsPadding()` inside a screen would return zero instead of
            // double-padding for the status bar.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
            ) {
                content()
            }
        }
    } else {
        // Medium / Expanded (Rule H): the [NavigationRail] handles its own start + vertical
        // insets (so the rail extends top-to-bottom of the screen with items inset from the
        // status / nav bars). The content area beside it explicitly pads for the remaining
        // sides — top (status bar), end (a side-cutout, if any) and bottom (a bottom system
        // nav bar, if any) — via `safeDrawing` so cutouts and the IME are also respected.
        Row(modifier = modifier.fillMaxSize()) {
            Rail(currentRoute = currentRoute, onNavigate = onNavigate, header = header)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Top + WindowInsetsSides.End + WindowInsetsSides.Bottom,
                        ),
                    ),
            ) {
                content()
            }
        }
    }
}

/** The compact bottom [NavigationBar] with a secondaryContainer pill indicator. */
@Composable
private fun BottomBar(currentRoute: Route, onNavigate: (Route) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    NavigationBar {
        NavDestination.entries.forEach { dest ->
            NavigationBarItem(
                selected = dest.matches(currentRoute),
                onClick = { onNavigate(dest.route) },
                icon = { Text(dest.glyph, style = MaterialTheme.typography.titleMedium) },
                label = { Text(dest.label, style = MaterialTheme.typography.labelMedium) },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = scheme.secondaryContainer,
                    selectedIconColor = scheme.onSecondaryContainer,
                    selectedTextColor = scheme.onSurface,
                ),
                modifier = Modifier.semantics { contentDescription = dest.label },
            )
        }
    }
}

/** The medium/expanded [NavigationRail] with an optional [header] and a pill indicator. */
@Composable
private fun Rail(currentRoute: Route, onNavigate: (Route) -> Unit, header: @Composable (() -> Unit)?) {
    val scheme = MaterialTheme.colorScheme
    NavigationRail(header = header?.let { { it() } }) {
        NavDestination.entries.forEach { dest ->
            NavigationRailItem(
                selected = dest.matches(currentRoute),
                onClick = { onNavigate(dest.route) },
                icon = { Text(dest.glyph, style = MaterialTheme.typography.titleMedium) },
                label = { Text(dest.label, style = MaterialTheme.typography.labelMedium) },
                colors = NavigationRailItemDefaults.colors(
                    indicatorColor = scheme.secondaryContainer,
                    selectedIconColor = scheme.onSecondaryContainer,
                    selectedTextColor = scheme.onSurface,
                ),
                modifier = Modifier.semantics { contentDescription = dest.label },
            )
        }
    }
}
