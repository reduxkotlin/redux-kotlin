// Decomposed into many small composables: the three modes (view / edit / create) and their sections
// are each their own function so every binding is the smallest possible store slice — the
// render-isolation discipline (Rule C) this sample exists to showcase. The per-composable split is the
// point, so the default function-per-file ceiling is suppressed here.
@file:Suppress("TooManyFunctions")

package org.reduxkotlin.sample.taskflow.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.reduxkotlin.Store
import org.reduxkotlin.compose.multimodel.fieldStateOf
import org.reduxkotlin.compose.rememberStableStore
import org.reduxkotlin.compose.selectorState
import org.reduxkotlin.multimodel.ModelState
import org.reduxkotlin.sample.taskflow.action.AddCard
import org.reduxkotlin.sample.taskflow.action.CancelCreateCard
import org.reduxkotlin.sample.taskflow.action.CardMoveRequested
import org.reduxkotlin.sample.taskflow.action.CloseCard
import org.reduxkotlin.sample.taskflow.action.EditCard
import org.reduxkotlin.sample.taskflow.model.AccountSummary
import org.reduxkotlin.sample.taskflow.model.BoardModel
import org.reduxkotlin.sample.taskflow.model.Card
import org.reduxkotlin.sample.taskflow.model.CardId
import org.reduxkotlin.sample.taskflow.model.CollaboratorsModel
import org.reduxkotlin.sample.taskflow.model.ColumnId
import org.reduxkotlin.sample.taskflow.model.NavModel
import org.reduxkotlin.sample.taskflow.model.SyncModel
import org.reduxkotlin.sample.taskflow.ui.LocalClock
import org.reduxkotlin.sample.taskflow.ui.LocalIdGenerator
import org.reduxkotlin.sample.taskflow.ui.adaptive.WindowSizeClass
import org.reduxkotlin.sample.taskflow.ui.adaptive.widthSizeClass
import org.reduxkotlin.sample.taskflow.ui.components.AttachmentChip
import org.reduxkotlin.sample.taskflow.ui.components.Avatar
import org.reduxkotlin.sample.taskflow.ui.components.LabelChip
import org.reduxkotlin.sample.taskflow.ui.components.MarkdownEditor
import org.reduxkotlin.sample.taskflow.ui.components.MarkdownView
import org.reduxkotlin.sample.taskflow.ui.components.MoveToGroup
import org.reduxkotlin.sample.taskflow.ui.theme.Dimens

/**
 * The card-detail overlay (Screen 5): one surface, three modes — **view**, **edit**, **create** —
 * with no extra route. Create mode is driven by [NavModel.composing] (a target [ColumnId]); view/edit
 * by [NavModel.openCardId]. The App decides when to mount this overlay, but it also guards internally:
 * when both nav fields are `null` it renders nothing.
 *
 * Adaptive (spec): a 330-dp **side sheet** on Medium / Expanded (board stays behind it) and a
 * **full-screen** surface on Compact, switched purely by the window size class.
 *
 * Binding discipline (Rule C): the overlay reads only `openCardId` / `composing` from [NavModel];
 * the view/edit body is wrapped in `key(openCardId)` and binds *just* that one
 * [org.reduxkotlin.sample.taskflow.model.Card] via `fieldStateOf(BoardModel::class){ …cards[id] }`,
 * never the whole board. The card's column position (for [MoveToGroup]'s edge state) is derived in a
 * value-equal [CardColumnNav] `selectorState`, and the assignee is an O(1) lookup against the bound
 * [CollaboratorsModel.byId]. Transient editor text is the only thing in local `remember` — every
 * commit dispatches an action, minting ids/clock at the dispatch site (Rule G).
 *
 * @param store the active account store holding [NavModel] / [BoardModel] / [CollaboratorsModel].
 * @param modifier the [Modifier] for the overlay root.
 */
@Composable
public fun CardDetailScreen(store: Store<ModelState>, modifier: Modifier = Modifier) {
    val s = rememberStableStore(store).value
    val openCardId by s.fieldStateOf(NavModel::class) { it.openCardId }
    val composing by s.fieldStateOf(NavModel::class) { it.composing }

    if (openCardId == null && composing == null) return

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val compact = widthSizeClass(maxWidth) == WindowSizeClass.Compact
        CardDetailContainer(compact = compact) {
            val column = composing
            val cardId = openCardId
            when {
                column != null -> CreateMode(store = store, columnId = column)
                cardId != null -> key(cardId) { ViewEditMode(store = store, s = s, openCardId = cardId) }
            }
        }
    }
}

/**
 * The mode-agnostic chrome: a full-screen [Surface] on Compact, or a trailing 330-dp side sheet on
 * Medium / Expanded (with a left divider via tonal elevation). The board stays interactive behind the
 * sheet because this overlay only fills the sheet's bounds, not the whole window.
 */
@Composable
private fun CardDetailContainer(compact: Boolean, content: @Composable () -> Unit) {
    // imePadding() so the soft keyboard never occludes the title field / MarkdownEditor
    // (effective on iOS and Android).
    if (compact) {
        Surface(
            modifier = Modifier.fillMaxSize().imePadding(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            content()
        }
    } else {
        Box(modifier = Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.TopEnd) {
            Surface(
                modifier = Modifier.width(SIDE_SHEET_WIDTH).fillMaxHeight(),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                tonalElevation = Dimens.space1,
            ) {
                content()
            }
        }
    }
}

/**
 * Create mode: a blank title field + a [MarkdownEditor] whose text lives in local `remember` (Rule C
 * — keystrokes never touch the store). Save is enabled only when the title is non-blank; it mints a
 * fresh card id + op id + clock at the dispatch site, dispatches [AddCard] into the target [columnId],
 * then [CancelCreateCard] to dismiss. Cancel dispatches [CancelCreateCard].
 */
@Composable
private fun CreateMode(store: Store<ModelState>, columnId: ColumnId) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val idGen = LocalIdGenerator.current
    val clock = LocalClock.current

    Column(
        modifier = Modifier.fillMaxSize().padding(Dimens.space4).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Dimens.space4),
    ) {
        ModeHeader(
            overline = "NEW CARD",
            trailingLabel = "Cancel",
            isCreate = true,
            onTrailing = { store.dispatch(CancelCreateCard) },
        )
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Card title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        MarkdownEditor(value = description, onValueChange = { description = it }, modifier = Modifier.fillMaxWidth())
        Button(
            onClick = {
                val now = clock()
                store.dispatch(AddCard(columnId, idGen.newCardId(), title, description, idGen.newOpId(), now))
                store.dispatch(CancelCreateCard)
            },
            enabled = title.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save", style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * View / edit mode for one card. Binds *only* this card (by [openCardId]) and a local edit flag; the
 * card body renders read-only by default and swaps to an inline [MarkdownEditor] when editing. When
 * the card is absent (just closed / removed) it renders nothing.
 */
@Composable
private fun ViewEditMode(store: Store<ModelState>, s: Store<ModelState>, openCardId: CardId) {
    val card by s.fieldStateOf(BoardModel::class) { it.board?.cards?.get(openCardId) }
    var editing by remember { mutableStateOf(false) }
    val current = card ?: return

    if (editing) {
        EditCardBody(
            store = store,
            card = current,
            onDone = { editing = false },
        )
    } else {
        ViewCardBody(
            store = store,
            s = s,
            card = current,
            onEdit = { editing = true },
        )
    }
}

/**
 * The read-only card view: a mode header (overline + ✎ Edit / ✕ Close), Headline-Small title, the
 * label chips, the rendered [MarkdownView] description, the attachment chips, the assignee avatar, and
 * a [MoveToGroup] whose edge state comes from the card's current column position (a value-equal
 * [CardColumnNav] `selectorState`) and whose in-flight state is the card's optimistic flag.
 */
@Composable
private fun ViewCardBody(store: Store<ModelState>, s: Store<ModelState>, card: Card, onEdit: () -> Unit) {
    val nav by s.selectorState { ms -> cardColumnNav(ms.get<BoardModel>(), card.id) }
    val inFlight by s.fieldStateOf(SyncModel::class) { card.id in it.inFlight }
    val byId by s.fieldStateOf(CollaboratorsModel::class) { it.byId }
    val assignee = card.assigneeId?.let { byId[it] }

    Column(
        modifier = Modifier.fillMaxSize().padding(Dimens.space4).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Dimens.space4),
    ) {
        ModeHeader(overline = nav.overline, trailingLabel = "✎ Edit", isCreate = false, onTrailing = onEdit)
        Text(
            text = card.title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        CardLabels(card = card)
        MarkdownView(markdown = card.description, modifier = Modifier.fillMaxWidth())
        CardAttachments(card = card)
        AssigneeAndMoveRow(store = store, card = card, assignee = assignee, nav = nav, inFlight = inFlight)
        TextButton(onClick = { store.dispatch(CloseCard) }, modifier = Modifier.align(Alignment.End)) {
            Text("Close", style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** The card's label chips, wrapped so a label change recomposes only this row. Renders nothing when empty. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardLabels(card: Card) {
    if (card.labels.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
        card.labels.forEach { label -> key(label.id.v) { LabelChip(label = label) } }
    }
}

/** The card's attachment chips (image / link), stacked. Renders nothing when there are no attachments. */
@Composable
private fun CardAttachments(card: Card) {
    if (card.attachments.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
        card.attachments.forEachIndexed { index, attachment ->
            key(index) { AttachmentChip(attachment = attachment, modifier = Modifier.fillMaxWidth()) }
        }
    }
}

/**
 * The bottom action row: the resolved assignee avatar + name on the leading edge and the optimistic
 * [MoveToGroup] on the trailing edge. Each ◂/▸ tap mints a fresh op id and dispatches
 * [CardMoveRequested] toward the adjacent column from [nav]; edge columns disable the button.
 */
@Composable
private fun AssigneeAndMoveRow(
    store: Store<ModelState>,
    card: Card,
    assignee: AccountSummary?,
    nav: CardColumnNav,
    inFlight: Boolean,
) {
    val idGen = LocalIdGenerator.current
    val move: (ColumnId?) -> Unit = { to ->
        val from = nav.from
        if (from != null && to != null) {
            store.dispatch(CardMoveRequested(card.id, from, to, 0, idGen.newOpId()))
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (assignee != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
            ) {
                Avatar(
                    name = assignee.displayName,
                    avatarUrl = assignee.avatarUrl,
                    seedId = assignee.id.v,
                    size = AVATAR_SIZE,
                )
                Text(
                    text = assignee.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Box(modifier = Modifier)
        }
        MoveToGroup(
            canPrev = nav.prev != null,
            canNext = nav.next != null,
            inFlight = inFlight,
            onPrev = { move(nav.prev) },
            onNext = { move(nav.next) },
        )
    }
}

/**
 * The inline edit body: a title field + a [MarkdownEditor] seeded from the bound [card] into local
 * `remember` (keystrokes stay local, Rule C). Save dispatches [EditCard] with a fresh op id + clock
 * and leaves edit mode; Cancel just leaves edit mode without dispatching.
 */
@Composable
private fun EditCardBody(store: Store<ModelState>, card: Card, onDone: () -> Unit) {
    var title by remember(card.id) { mutableStateOf(card.title) }
    var description by remember(card.id) { mutableStateOf(card.description) }
    val idGen = LocalIdGenerator.current
    val clock = LocalClock.current

    Column(
        modifier = Modifier.fillMaxSize().padding(Dimens.space4).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Dimens.space4),
    ) {
        ModeHeader(
            overline = "EDIT CARD",
            trailingLabel = "Cancel",
            isCreate = true,
            onTrailing = onDone,
        )
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Card title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        MarkdownEditor(value = description, onValueChange = { description = it }, modifier = Modifier.fillMaxWidth())
        Button(
            onClick = {
                store.dispatch(EditCard(card.id, title, description, idGen.newOpId(), clock()))
                onDone()
            },
            enabled = title.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save", style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * The mode header: a Label-Small [overline] over a trailing action chip/button. View mode shows an
 * `✎ Edit` assist chip on a `primaryContainer` bar; create / edit modes show a `Cancel` text button
 * on a `primary` bar. The trailing callback is remembered by the caller.
 */
@Composable
private fun ModeHeader(overline: String, trailingLabel: String, isCreate: Boolean, onTrailing: () -> Unit) {
    val barColor = if (isCreate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer
    val onBar = if (isCreate) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
    Surface(color = barColor, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.space3, vertical = Dimens.space2),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = overline, style = MaterialTheme.typography.labelSmall, color = onBar)
            if (isCreate) {
                TextButton(onClick = onTrailing) {
                    Text(trailingLabel, style = MaterialTheme.typography.labelLarge, color = onBar)
                }
            } else {
                AssistChip(
                    onClick = onTrailing,
                    label = { Text(trailingLabel, style = MaterialTheme.typography.labelLarge) },
                )
            }
        }
    }
}

private val SIDE_SHEET_WIDTH = 330.dp
private val AVATAR_SIZE = 30.dp
