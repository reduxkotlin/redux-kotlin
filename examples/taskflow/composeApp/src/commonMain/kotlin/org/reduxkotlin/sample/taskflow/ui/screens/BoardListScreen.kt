package org.reduxkotlin.sample.taskflow.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.reduxkotlin.Store
import org.reduxkotlin.compose.multimodel.fieldStateOf
import org.reduxkotlin.compose.rememberStableStore
import org.reduxkotlin.multimodel.ModelState
import org.reduxkotlin.sample.taskflow.action.CreateBoard
import org.reduxkotlin.sample.taskflow.action.LoadBoardListRequested
import org.reduxkotlin.sample.taskflow.action.Navigate
import org.reduxkotlin.sample.taskflow.model.BoardId
import org.reduxkotlin.sample.taskflow.model.BoardListModel
import org.reduxkotlin.sample.taskflow.model.Route
import org.reduxkotlin.sample.taskflow.ui.LocalClock
import org.reduxkotlin.sample.taskflow.ui.LocalIdGenerator
import org.reduxkotlin.sample.taskflow.ui.adaptive.WindowSizeClass
import org.reduxkotlin.sample.taskflow.ui.adaptive.rememberWindowSize
import org.reduxkotlin.sample.taskflow.ui.components.BoardSummaryCard
import org.reduxkotlin.sample.taskflow.ui.theme.Dimens

/**
 * The board list (Screen 3): the account home. Lists every board in [BoardListModel.order] as a
 * [BoardSummaryCard] (looked up in [BoardListModel.boards]); tapping one dispatches
 * `Navigate(Route.Board(id))` to open it. A dashed "New board" tile opens a name dialog that
 * dispatches [CreateBoard]. When no boards exist yet the screen shows a Display-style "No boards
 * yet" hero. Card counts come straight from each [org.reduxkotlin.sample.taskflow.model.BoardSummary]
 * (the DB-aggregate cache) — never recomputed here.
 *
 * Binding discipline (Rule C): the whole [BoardListModel] is read once via a single [fieldStateOf]
 * over the stable [accountStore]; `order` drives the grid and `boards` resolves each summary, with
 * no list derivation in the composable body. Each [BoardSummaryCard] gets finished immutable data
 * plus a remembered callback — the store never reaches a child. The grid column count is purely a
 * function of window width (1 / 2 / 3 columns at Compact / Medium / Expanded). On enter the screen
 * fires [LoadBoardListRequested] exactly once via `LaunchedEffect(Unit)`. New ids and the clock for
 * [CreateBoard] are minted at the dispatch site from `LocalIdGenerator` / `LocalClock` (Rule G).
 *
 * @param accountStore the active account store holding [BoardListModel].
 * @param modifier the [Modifier] for the screen root.
 */
@Composable
public fun BoardListScreen(accountStore: Store<ModelState>, modifier: Modifier = Modifier) {
    val store = rememberStableStore(accountStore).value
    val boardList by store.fieldStateOf(BoardListModel::class) { it }

    LaunchedEffect(Unit) { accountStore.dispatch(LoadBoardListRequested) }

    var showCreateDialog by remember { mutableStateOf(false) }

    if (boardList.order.isEmpty()) {
        EmptyBoardsHero(
            modifier = modifier,
            onNewBoard = { showCreateDialog = true },
        )
    } else {
        BoardGrid(
            modifier = modifier,
            boardList = boardList,
            onOpenBoard = remember(accountStore) {
                { id: BoardId -> accountStore.dispatch(Navigate(Route.Board(id))) }
            },
            onNewBoard = { showCreateDialog = true },
        )
    }

    if (showCreateDialog) {
        val idGen = LocalIdGenerator.current
        val clock = LocalClock.current
        NewBoardDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                accountStore.dispatch(CreateBoard(idGen.newBoardId(), name, clock()))
                showCreateDialog = false
            },
        )
    }
}

/**
 * The adaptive grid of [BoardSummaryCard]s followed by the dashed "New board" tile. Column count
 * tracks the window size class (1 / 2 / 3). Each card opens its board via [onOpenBoard].
 *
 * @param boardList the bound [BoardListModel]; `order` drives the cells, `boards` resolves each summary.
 * @param onOpenBoard invoked with a [BoardId] when a card is tapped.
 * @param onNewBoard invoked when the create tile is tapped.
 * @param modifier the [Modifier] for the grid root.
 */
@Composable
private fun BoardGrid(
    boardList: BoardListModel,
    onOpenBoard: (BoardId) -> Unit,
    onNewBoard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val columns = when (rememberWindowSize(maxWidth)) {
            WindowSizeClass.Compact -> 1
            WindowSizeClass.Medium -> 2
            WindowSizeClass.Expanded -> 3
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(Dimens.space4),
            horizontalArrangement = Arrangement.spacedBy(Dimens.space4),
            verticalArrangement = Arrangement.spacedBy(Dimens.space3),
        ) {
            item(span = { GridItemSpanFull }) { BoardListTitle() }
            items(boardList.order, key = { it.v }) { id ->
                val summary = boardList.boards[id]
                if (summary != null) {
                    BoardSummaryCard(
                        summary = summary,
                        onClick = { onOpenBoard(id) },
                    )
                }
            }
            item { NewBoardTile(onClick = onNewBoard) }
        }
    }
}

/** Full-row grid span for the screen title that sits above the cards. */
private val GridItemSpanFull
    get() = androidx.compose.foundation.lazy.grid.GridItemSpan(currentLineSpan = Int.MAX_VALUE)

/** The "boards" screen title — Headline Medium, onPrimaryContainer tint (board-list redline 1). */
@Composable
private fun BoardListTitle() {
    Text(
        text = "Boards",
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.padding(bottom = Dimens.space1),
    )
}

/**
 * The dashed-outline "New board" create tile (board-list redline 3): `outlineVariant` border,
 * `primary` label, [onClick] opens the name dialog.
 *
 * @param onClick invoked when the tile is tapped.
 */
@Composable
private fun NewBoardTile(onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.space16),
        shape = MaterialTheme.shapes.large,
        color = scheme.surface,
        border = BorderStroke(2.dp, scheme.outlineVariant),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "+ New board",
                style = MaterialTheme.typography.titleSmall,
                color = scheme.primary,
            )
        }
    }
}

/**
 * The Display-style empty state shown when the account has no boards yet. The "New board" tile
 * remains available so the user can create the first board.
 *
 * @param onNewBoard invoked when the create tile is tapped.
 * @param modifier the [Modifier] for the empty-state root.
 */
@Composable
private fun EmptyBoardsHero(onNewBoard: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier.fillMaxSize().padding(Dimens.space6),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.space4),
        ) {
            Text(
                text = "No boards yet",
                style = MaterialTheme.typography.displaySmall,
                color = scheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Create your first board to get started.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            NewBoardTile(onClick = onNewBoard)
        }
    }
}

/**
 * The "New board" name dialog. The board name lives in transient local `remember` (editor text only,
 * Rule C); [onCreate] reports the trimmed name (only when non-blank) so the caller dispatches
 * [CreateBoard] with a freshly minted id and clock.
 *
 * @param onDismiss invoked when the dialog is dismissed or cancelled.
 * @param onCreate invoked with the entered board name when the user confirms.
 */
@Composable
private fun NewBoardDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    val trimmed = name.trim()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "New board", style = MaterialTheme.typography.titleLarge) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(text = "Board name") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(trimmed) },
                enabled = trimmed.isNotEmpty(),
            ) {
                Text(text = "Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = "Cancel") }
        },
    )
}
