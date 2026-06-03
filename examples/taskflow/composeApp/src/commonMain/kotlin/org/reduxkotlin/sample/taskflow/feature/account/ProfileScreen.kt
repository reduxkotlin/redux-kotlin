package org.reduxkotlin.sample.taskflow.feature.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import org.reduxkotlin.Store
import org.reduxkotlin.compose.multimodel.fieldStateOf
import org.reduxkotlin.compose.rememberStableStore
import org.reduxkotlin.multimodel.ModelState
import org.reduxkotlin.sample.taskflow.core.AccountSummary
import org.reduxkotlin.sample.taskflow.feature.boardlist.BoardListModel
import org.reduxkotlin.sample.taskflow.feature.collaborators.CollaboratorsModel
import org.reduxkotlin.sample.taskflow.ui.Avatar
import org.reduxkotlin.sample.taskflow.ui.theme.Dimens

/**
 * The profile / user screen (Screen 6): this account's identity, an editable detail card, and a
 * destructive "Log out" affordance. Identity (display name / email / avatar) is read from this
 * account's [CollaboratorsModel] (which includes self) — never duplicated into [SessionModel],
 * which holds only the account id + the session-only bio.
 *
 * Binding discipline (Rule C): every value is bound through the smallest possible store slice —
 * `selfId` and `bio` from [SessionModel], the self [AccountSummary] from [CollaboratorsModel.byId]
 * (an O(1) lookup), and the board count from [BoardListModel.order]. Child composables receive
 * finished data plus remembered callbacks; the store never reaches a presentational child. Only the
 * transient editor text lives in local `remember`, re-seeded whenever the bound identity changes.
 *
 * Cross-store propagation (Rule D, §D): an [EditProfile] commit is dispatched to **both** the
 * [accountStore] (updating this account's [SessionModel] bio + [CollaboratorsModel] self entry, so
 * cards/assignees resolve fresh identity) **and** the [rootStore] (updating the root `AccountsModel`,
 * so the account switcher and app-bar avatar never go stale). [LogoutAccount] disposes only this
 * account's store from the registry, dispatched to the [rootStore].
 *
 * @param accountStore the active account store holding [SessionModel] / [CollaboratorsModel] /
 *   [BoardListModel].
 * @param rootStore the root app store holding `AccountsModel` (the switcher / app-bar identity source).
 * @param modifier the [Modifier] for the screen root.
 */
@Composable
public fun ProfileScreen(accountStore: Store<ModelState>, rootStore: Store<ModelState>, modifier: Modifier = Modifier) {
    val a = rememberStableStore(accountStore).value
    val selfId by a.fieldStateOf(SessionModel::class) { it.accountId }
    val self by a.fieldStateOf(CollaboratorsModel::class) { it.byId[selfId] }
    val bio by a.fieldStateOf(SessionModel::class) { it.bio }
    val boardCount by a.fieldStateOf(BoardListModel::class) { it.order.size }

    val identity = self ?: return

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ProfileHeader(self = identity, seedId = selfId.v)
        ProfileBody(
            self = identity,
            seedId = selfId.v,
            bio = bio,
            boardCount = boardCount,
            onSave = { displayName, email, avatarUrl, newBio ->
                val edit = EditProfile(displayName, email, avatarUrl, newBio)
                // §D: account store updates Session + Collaborators; root updates AccountsModel
                // so the switcher / app-bar avatar / card assignees never go stale.
                accountStore.dispatch(edit)
                rootStore.dispatch(edit)
            },
            onLogout = { rootStore.dispatch(LogoutAccount(selfId)) },
        )
    }
}

/**
 * The cover band + identity header: a `primaryContainer` band with a 56-dp squircle [Avatar] that
 * breaks the band (a 3-dp surface border per the redline), then the display name (Headline Small)
 * and email. The avatar overlaps the band's bottom edge via a negative offset.
 */
@Composable
private fun ProfileHeader(self: AccountSummary, seedId: String) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.primaryContainer)
            .height(COVER_HEIGHT),
    )
    Column(modifier = Modifier.padding(horizontal = Dimens.space4)) {
        Box(
            modifier = Modifier
                .offset(y = -(AVATAR_SIZE / 2))
                .size(AVATAR_SIZE)
                .clip(MaterialTheme.shapes.large)
                .border(AVATAR_BORDER, SolidColor(scheme.surface), MaterialTheme.shapes.large),
            contentAlignment = Alignment.Center,
        ) {
            Avatar(
                name = self.displayName,
                avatarUrl = self.avatarUrl,
                seedId = seedId,
                size = AVATAR_SIZE,
            )
        }
        Text(
            text = self.displayName,
            style = MaterialTheme.typography.headlineSmall,
            color = scheme.onSurface,
        )
        Text(
            text = self.email,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
        )
    }
}

/**
 * The editable detail card + stats + log-out button. The editor text lives in local `remember`,
 * re-seeded from the bound identity / bio whenever [self] changes (account switch / external edit);
 * Save reports the four edited fields up to the caller, which fans the [EditProfile] out to both
 * stores (§D). The stats row shows a read-only board count. Log out routes through a confirm dialog.
 */
@Composable
private fun ProfileBody(
    self: AccountSummary,
    seedId: String,
    bio: String?,
    boardCount: Int,
    onSave: (displayName: String, email: String, avatarUrl: String, bio: String?) -> Unit,
    onLogout: () -> Unit,
) {
    var displayName by remember(seedId) { mutableStateOf(self.displayName) }
    var email by remember(seedId) { mutableStateOf(self.email) }
    var avatarUrl by remember(seedId) { mutableStateOf(self.avatarUrl) }
    var bioText by remember(seedId) { mutableStateOf(bio.orEmpty()) }
    var confirming by remember { mutableStateOf(false) }

    // Re-seed editor text when the bound identity changes (account switch / external EditProfile).
    LaunchedEffect(self, bio) {
        displayName = self.displayName
        email = self.email
        avatarUrl = self.avatarUrl
        bioText = bio.orEmpty()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.space4)
            .padding(bottom = Dimens.space6),
        verticalArrangement = Arrangement.spacedBy(Dimens.space4),
    ) {
        EditableDetailCard(
            displayName = displayName,
            email = email,
            avatarUrl = avatarUrl,
            bio = bioText,
            boardCount = boardCount,
            onDisplayName = { displayName = it },
            onEmail = { email = it },
            onAvatarUrl = { avatarUrl = it },
            onBio = { bioText = it },
        )
        Button(
            onClick = { onSave(displayName, email, avatarUrl, bioText.ifBlank { null }) },
            enabled = displayName.isNotBlank() && email.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save profile", style = MaterialTheme.typography.labelLarge)
        }
        OutlinedButton(
            onClick = { confirming = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.space12),
        ) {
            Text(
                text = "Log out",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

    if (confirming) {
        LogoutConfirmDialog(
            onConfirm = {
                confirming = false
                onLogout()
            },
            onDismiss = { confirming = false },
        )
    }
}

/**
 * The `surfaceContainerLowest` Large-16 detail card: editable Display name / Email / Avatar URL /
 * Bio fields split by `outlineVariant` hairlines, plus a read-only Boards stat row. Each field
 * reports its keystrokes up via a remembered callback; the card holds no store reference.
 */
@Composable
private fun EditableDetailCard(
    displayName: String,
    email: String,
    avatarUrl: String,
    bio: String,
    boardCount: Int,
    onDisplayName: (String) -> Unit,
    onEmail: (String) -> Unit,
    onAvatarUrl: (String) -> Unit,
    onBio: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = Dimens.space1,
    ) {
        Column(
            modifier = Modifier.padding(Dimens.space4),
            verticalArrangement = Arrangement.spacedBy(Dimens.space3),
        ) {
            EditableField(label = "Display name", value = displayName, singleLine = true, onValueChange = onDisplayName)
            DetailHairline()
            EditableField(label = "Email", value = email, singleLine = true, onValueChange = onEmail)
            DetailHairline()
            EditableField(label = "Avatar URL", value = avatarUrl, singleLine = true, onValueChange = onAvatarUrl)
            DetailHairline()
            EditableField(label = "Bio", value = bio, singleLine = false, onValueChange = onBio)
            DetailHairline()
            StatRow(label = "Boards", value = boardCount.toString())
        }
    }
}

/** One labelled, editable row (an [OutlinedTextField]); the ✎ glyph marks it as editable per the redline. */
@Composable
private fun EditableField(label: String, value: String, singleLine: Boolean, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("$label ✎") },
        singleLine = singleLine,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A read-only stat row: a Body-Medium [label] on the leading edge and a Title-Small [value] trailing. */
@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.space2),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** The `outlineVariant` hairline that splits detail-card rows. */
@Composable
private fun DetailHairline() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** The destructive log-out confirm dialog: [onConfirm] disposes this account's store; [onDismiss] cancels. */
@Composable
private fun LogoutConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log out?") },
        text = { Text("This signs out of this account. Other accounts stay signed in.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Log out", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private val COVER_HEIGHT = 64.dp
private val AVATAR_SIZE = 56.dp
private val AVATAR_BORDER = 3.dp
