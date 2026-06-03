// Deliberately decomposed into many small composables: each per-column / per-card composable is its
// own function so it can be wrapped in key(...) and bind only its own narrow store slice — the
// render-isolation discipline (Rule C) this screen exists to showcase. The per-composable split is
// the point, so the default function-per-file ceiling is suppressed here.
@file:Suppress("TooManyFunctions")

package org.reduxkotlin.sample.taskflow.feature.board

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import org.reduxkotlin.Store
import org.reduxkotlin.compose.multimodel.fieldStateOf
import org.reduxkotlin.compose.rememberStableStore
import org.reduxkotlin.compose.selectorState
import org.reduxkotlin.multimodel.ModelState
import org.reduxkotlin.sample.taskflow.app.nav.OpenCard
import org.reduxkotlin.sample.taskflow.core.AccountId
import org.reduxkotlin.sample.taskflow.core.AccountSummary
import org.reduxkotlin.sample.taskflow.core.ActivityEntry
import org.reduxkotlin.sample.taskflow.core.CardId
import org.reduxkotlin.sample.taskflow.core.ColumnId
import org.reduxkotlin.sample.taskflow.feature.activity.ActivityModel
import org.reduxkotlin.sample.taskflow.feature.boardlist.BoardListModel
import org.reduxkotlin.sample.taskflow.feature.collaborators.CollaboratorsModel
import org.reduxkotlin.sample.taskflow.feature.undo.Redo
import org.reduxkotlin.sample.taskflow.feature.undo.Undo
import org.reduxkotlin.sample.taskflow.feature.undo.UndoModel
import org.reduxkotlin.sample.taskflow.ui.LocalIdGenerator
import org.reduxkotlin.sample.taskflow.ui.adaptive.WindowSizeClass
import org.reduxkotlin.sample.taskflow.ui.adaptive.widthSizeClass
import org.reduxkotlin.sample.taskflow.ui.theme.Dimens

/**
 * The board (Screen 4) — TaskFlow's flagship and the render-isolation showcase (Rule C). A kanban
 * of columns whose cards live in [BoardModel]; the header binds the board name (from
 * [BoardListModel]), a [SyncIndicator] ([SyncModel]), and a [FilterBar] over [FilterModel] /
 * [UndoModel]. A [FabMenu] mints new card / column actions, and a [SyncToast] surfaces the last sync
 * error.
 *
 * Binding discipline (the whole point): **no composable selects the board, its cards, or its columns
 * wholesale.** The column list is bound as a lightweight [ColDesc] list once; each [ColumnView] is
 * wrapped in `key(colId)` and subscribes only to its own `cardIds` (by [ColumnId], never by index)
 * via [deriveVisibleCardIds] in a `selectorState`; each card is wrapped in `key(cardId)` and
 * subscribes only to its own [org.reduxkotlin.sample.taskflow.core.Card] plus its optimistic flag
 * (`cardId in `[SyncModel.inFlight]). All derivation (visible/filtered ids, WIP count, name lookup)
 * lives in `selectorState{}` or reducers — never `.filter`/`.count` in a composable body. So moving
 * one card recomposes only the two affected columns; every other column stays frozen.
 *
 * Components receive finished `@Stable` data plus remembered callbacks — the store never reaches a
 * leaf. Every interaction dispatches an action, minting ids/clock at the dispatch site
 * ([LocalIdGenerator] / [org.reduxkotlin.sample.taskflow.ui.LocalClock], Rule G).
 *
 * Adaptive (spec breakpoints 600 / 840): **Compact** pages single columns in a [HorizontalPager]
 * with paging dots; **Medium / Expanded** lay columns side-by-side in a [Row]; **Expanded** also
 * pins an Activity rail bound to [ActivityModel]. A skeleton shows while `board == null`, and a
 * per-column empty state shows when a column has no visible cards.
 *
 * @param store the active account store holding the board's models.
 * @param modifier the [Modifier] for the screen root.
 */
@Composable
public fun BoardScreen(store: Store<ModelState>, modifier: Modifier = Modifier) {
    val s = rememberStableStore(store).value

    val boardName by s.selectorState { ms ->
        ms.get<BoardModel>().board?.boardId?.let { ms.get<BoardListModel>().boards[it]?.name } ?: ""
    }
    val sync by s.fieldStateOf(SyncModel::class) { it }
    val boardLoaded by s.fieldStateOf(BoardModel::class) { it.board != null }

    val onRefresh: () -> Unit = remember(store) { { store.dispatch(Refresh) } }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            BoardHeader(
                store = store,
                s = s,
                boardName = boardName,
                online = sync.online,
                pendingCount = sync.pendingCount,
                onRefresh = onRefresh,
            )
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (boardLoaded) {
                    BoardBody(store = store, s = s)
                } else {
                    BoardSkeleton(modifier = Modifier.fillMaxSize())
                }
            }
        }

        BoardOverlays(store = store, s = s)
    }
}

/**
 * The pinned header: board name (Headline Medium on `primaryContainer`), the [SyncIndicator], and
 * the [FilterBar]. Each control binds its own narrow slice; nothing here touches the cards.
 */
@Composable
private fun BoardHeader(
    store: Store<ModelState>,
    s: Store<ModelState>,
    boardName: String,
    online: Boolean,
    pendingCount: Int,
    onRefresh: () -> Unit,
) {
    val query by s.fieldStateOf(FilterModel::class) { it.query }
    val filterActive by s.selectorState { ms ->
        val f = ms.get<FilterModel>()
        f.query.isNotBlank() || f.assignee != null || f.labelIds.isNotEmpty()
    }
    val canUndo by s.fieldStateOf(UndoModel::class) { it.past.isNotEmpty() }
    val canRedo by s.fieldStateOf(UndoModel::class) { it.future.isNotEmpty() }

    val onQueryChange: (String) -> Unit = remember(store) { { q -> store.dispatch(SetFilterQuery(q)) } }
    val onUndo: () -> Unit = remember(store) { { store.dispatch(Undo) } }
    val onRedo: () -> Unit = remember(store) { { store.dispatch(Redo) } }

    Surface(color = MaterialTheme.colorScheme.primaryContainer, tonalElevation = 0.dp) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Dimens.space4),
            verticalArrangement = Arrangement.spacedBy(Dimens.space3),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = boardName,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                SyncIndicator(online = online, pendingCount = pendingCount, onRefresh = onRefresh)
            }
            FilterBar(
                query = query,
                onQueryChange = onQueryChange,
                filterActive = filterActive,
                onFilterClick = {},
                canUndo = canUndo,
                canRedo = canRedo,
                onUndo = onUndo,
                onRedo = onRedo,
            )
        }
    }
}

/**
 * The adaptive board body: a lightweight [ColDesc] list + the collaborators map are bound once, then
 * the layout splits on the window size class — paged columns (Compact) vs. a side-by-side [Row]
 * (Medium / Expanded, plus the Activity rail at Expanded).
 */
@Composable
private fun BoardBody(store: Store<ModelState>, s: Store<ModelState>) {
    val columns by s.selectorState { ms ->
        ms.get<BoardModel>().board?.columns?.map { ColDesc(it.id, it.title, it.wipLimit) }?.toPersistentList()
            ?: persistentListOf()
    }
    val collaborators by s.fieldStateOf(CollaboratorsModel::class) { it.byId }

    val onCardClick: (CardId) -> Unit = remember(store) { { id -> store.dispatch(OpenCard(id)) } }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        when (widthSizeClass(maxWidth)) {
            WindowSizeClass.Compact -> CompactColumns(
                s = s,
                columns = columns,
                collaborators = collaborators,
                onCardClick = onCardClick,
            )

            WindowSizeClass.Medium -> WideColumns(
                s = s,
                columns = columns,
                collaborators = collaborators,
                onCardClick = onCardClick,
                showActivityRail = false,
            )

            WindowSizeClass.Expanded -> WideColumns(
                s = s,
                columns = columns,
                collaborators = collaborators,
                onCardClick = onCardClick,
                showActivityRail = true,
            )
        }
    }
}

/** Compact: one column per page in a [HorizontalPager], with paging dots beneath. */
@Composable
private fun CompactColumns(
    s: Store<ModelState>,
    columns: PersistentList<ColDesc>,
    collaborators: PersistentMap<AccountId, AccountSummary>,
    onCardClick: (CardId) -> Unit,
) {
    if (columns.isEmpty()) {
        EmptyBoardHint(modifier = Modifier.fillMaxSize())
        return
    }
    val pagerState = rememberPagerState(pageCount = { columns.size })
    Column(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = Dimens.space4),
            pageSpacing = Dimens.space3,
        ) { page ->
            val colDesc = columns[page]
            key(colDesc.id) {
                ColumnView(
                    s = s,
                    colId = colDesc.id,
                    title = colDesc.title,
                    wipLimit = colDesc.wipLimit,
                    collaborators = collaborators,
                    onCardClick = onCardClick,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        PagingDots(count = columns.size, selected = pagerState.currentPage)
    }
}

/** The row of page indicator dots under the compact pager. */
@Composable
private fun PagingDots(count: Int, selected: Int) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(Dimens.space3),
        horizontalArrangement = Arrangement.spacedBy(Dimens.space2, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            Box(
                modifier = Modifier
                    .size(if (i == selected) 10.dp else 8.dp)
                    .clip(CircleShape)
                    .background(if (i == selected) scheme.primary else scheme.outlineVariant),
            )
        }
    }
}

/**
 * Medium / Expanded: columns side-by-side in a horizontally-scrolling [Row]; the Expanded layout
 * also pins a persistent [ActivityRail] on the trailing edge.
 */
@Composable
private fun WideColumns(
    s: Store<ModelState>,
    columns: PersistentList<ColDesc>,
    collaborators: PersistentMap<AccountId, AccountSummary>,
    onCardClick: (CardId) -> Unit,
    showActivityRail: Boolean,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        if (columns.isEmpty()) {
            EmptyBoardHint(modifier = Modifier.weight(1f).fillMaxHeight())
        } else {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .horizontalScroll(rememberScrollState())
                    .padding(Dimens.space4),
                horizontalArrangement = Arrangement.spacedBy(Dimens.space4),
            ) {
                columns.forEach { colDesc ->
                    key(colDesc.id) {
                        ColumnView(
                            s = s,
                            colId = colDesc.id,
                            title = colDesc.title,
                            wipLimit = colDesc.wipLimit,
                            collaborators = collaborators,
                            onCardClick = onCardClick,
                            modifier = Modifier.width(COLUMN_WIDTH).fillMaxHeight(),
                        )
                    }
                }
            }
        }
        if (showActivityRail) {
            ActivityRail(s = s, modifier = Modifier.width(ACTIVITY_RAIL_WIDTH).fillMaxHeight())
        }
    }
}

/**
 * One column: a sticky [ColumnHeader] (title + live WIP badge) over the column's own visible cards.
 * The header's WIP count and the visible card ids each come from a `selectorState` keyed on this
 * column's [colId] — so this composable recomposes only when *its* slice moves (Rule C). Each card
 * is wrapped in `key(cardId)` and binds only its own card + optimistic flag.
 *
 * @param colId this column's stable id (the enclosing composable is already `key(colId)`).
 */
@Composable
private fun ColumnView(
    s: Store<ModelState>,
    colId: ColumnId,
    title: String,
    wipLimit: Int?,
    collaborators: PersistentMap<AccountId, AccountSummary>,
    onCardClick: (CardId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visibleCardIds by s.selectorState { ms ->
        deriveVisibleCardIds(ms.get<BoardModel>(), ms.get<FilterModel>(), colId)
    }
    val wip by s.selectorState { ms ->
        val c = ms.get<BoardModel>().board?.columnById(colId)
        WipState(c?.cardIds?.size ?: 0, c?.wipLimit ?: wipLimit)
    }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(Dimens.space3)) {
            ColumnHeader(title = title, count = wip.count, wipLimit = wip.limit)
            if (visibleCardIds.isEmpty()) {
                EmptyColumnHint(modifier = Modifier.weight(1f).fillMaxWidth())
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = Dimens.space3),
                    verticalArrangement = Arrangement.spacedBy(Dimens.space3),
                ) {
                    items(visibleCardIds, key = { it.v }) { cardId ->
                        key(cardId) {
                            CardCell(
                                s = s,
                                cardId = cardId,
                                collaborators = collaborators,
                                onCardClick = onCardClick,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One card cell: binds only its own [org.reduxkotlin.sample.taskflow.core.Card] and its optimistic
 * flag, so a change to a sibling card never recomposes this one (Rule C). The assignee is an O(1)
 * map lookup against the pre-bound [collaborators] (a single-key lookup is the allowed exception).
 */
@Composable
private fun CardCell(
    s: Store<ModelState>,
    cardId: CardId,
    collaborators: PersistentMap<AccountId, AccountSummary>,
    onCardClick: (CardId) -> Unit,
) {
    val card by s.fieldStateOf(BoardModel::class) { it.board?.cards?.get(cardId) }
    val optimistic by s.fieldStateOf(SyncModel::class) { cardId in it.inFlight }
    card?.let {
        KanbanCard(
            card = it,
            isOptimistic = optimistic,
            assignee = it.assigneeId?.let { a -> collaborators[a] },
            onClick = { onCardClick(cardId) },
        )
    }
}

/**
 * The FAB menu + sync toast layer, drawn over the board. "Add card" targets the first column
 * (`focusedColumnId`); "Add column" mints a fresh [ColumnId] at the dispatch site. The toast appears
 * only while [SyncModel.lastError] is non-null and the user has not locally dismissed it.
 */
@Composable
private fun BoardOverlays(store: Store<ModelState>, s: Store<ModelState>) {
    var expanded by remember { mutableStateOf(false) }
    val focusedColumnId by s.selectorState { ms ->
        ms.get<BoardModel>().board?.columns?.firstOrNull()?.id
    }
    val idGen = LocalIdGenerator.current

    FabMenu(
        expanded = expanded,
        onToggle = { expanded = !expanded },
        onAddCard = {
            focusedColumnId?.let { store.dispatch(StartCreateCard(it)) }
            expanded = false
        },
        onAddColumn = {
            store.dispatch(AddColumn(idGen.newColumnId(), "New column"))
            expanded = false
        },
    )

    val lastError by s.fieldStateOf(SyncModel::class) { it.lastError }
    var dismissedError by remember { mutableStateOf<String?>(null) }
    val visibleError = lastError?.takeIf { it != dismissedError }
    if (visibleError != null) {
        Box(modifier = Modifier.fillMaxSize().padding(Dimens.space4), contentAlignment = Alignment.BottomStart) {
            SyncToast(
                message = visibleError,
                isError = true,
                onRetry = { store.dispatch(Refresh) },
                onDismiss = { dismissedError = visibleError },
            )
        }
    }
}

/**
 * The Expanded-only activity rail: a list of recent [ActivityEntry]s bound directly from
 * [ActivityModel]. Read-only, so it carries no callbacks.
 */
@Composable
private fun ActivityRail(s: Store<ModelState>, modifier: Modifier = Modifier) {
    val entries by s.fieldStateOf(ActivityModel::class) { it.entries }
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 1.dp) {
        Column(modifier = Modifier.fillMaxSize().padding(Dimens.space4)) {
            Text(
                text = "Activity",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = Dimens.space2),
            )
            if (entries.isEmpty()) {
                Text(
                    text = "No recent activity",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                    items(entries, key = { it.id }) { entry -> ActivityRow(entry) }
                }
            }
        }
    }
}

/** One activity entry row: the summary line in Body Medium. */
@Composable
private fun ActivityRow(entry: ActivityEntry) {
    Text(
        text = entry.summary,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The board skeleton shown while `board == null` (loading): three shimmering placeholder columns. */
@Composable
private fun BoardSkeleton(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(Dimens.space4),
        horizontalArrangement = Arrangement.spacedBy(Dimens.space4),
    ) {
        repeat(SKELETON_COLUMNS) {
            Surface(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(Dimens.space3),
                    verticalArrangement = Arrangement.spacedBy(Dimens.space3),
                ) {
                    repeat(SKELETON_CARDS) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().height(SKELETON_CARD_HEIGHT),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ) {}
                    }
                }
            }
        }
    }
}

/** Empty-board hint (no columns yet) — nudges the user toward the FAB. */
@Composable
private fun EmptyBoardHint(modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(Dimens.space6), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.space2),
        ) {
            Text(
                text = "No columns yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Tap + to add your first column.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Empty-column hint (no visible cards — either truly empty or filtered out). */
@Composable
private fun EmptyColumnHint(modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(Dimens.space4), contentAlignment = Alignment.Center) {
        Text(
            text = "No cards",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private val COLUMN_WIDTH = 300.dp
private val ACTIVITY_RAIL_WIDTH = 280.dp
private val SKELETON_CARD_HEIGHT = 72.dp
private const val SKELETON_COLUMNS = 3
private const val SKELETON_CARDS = 3
