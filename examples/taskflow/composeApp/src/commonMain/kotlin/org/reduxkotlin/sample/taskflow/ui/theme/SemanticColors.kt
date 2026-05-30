package org.reduxkotlin.sample.taskflow.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * A container + on-color pair, used for the app's label chips and other
 * tonal-surface tokens that are not part of the 29 standard M3 color roles.
 */
data class ColorPair(val container: Color, val on: Color)

/**
 * App-semantic colors that fall outside the standard M3 roles: the six seeded
 * label colors and the WIP / sync state colors. Sourced verbatim from the hi-fi
 * spec (spec-assets/spec-data.js -> `semantic`). One immutable instance per
 * light / dark mode is provided via [LocalSemanticColors] by `TaskFlowTheme`.
 *
 * @property backend label chip color for the `backend` tag.
 * @property frontend label chip color for the `frontend` tag.
 * @property p1 label chip color for the `p1` (priority) tag.
 * @property docs label chip color for the `docs` tag.
 * @property infra label chip color for the `infra` tag.
 * @property design label chip color for the `design` tag.
 * @property wipOk WIP badge color when the column is under its limit.
 * @property wipAtLimit WIP badge color when the column count equals its limit.
 * @property wipOver WIP badge color when the column count exceeds its limit.
 * @property online presence-dot color for an online/positive state.
 * @property saving color for an optimistic in-flight (saving) state.
 * @property syncError color for a failed sync op that triggers revert + retry.
 */
data class SemanticColors(
    val backend: ColorPair,
    val frontend: ColorPair,
    val p1: ColorPair,
    val docs: ColorPair,
    val infra: ColorPair,
    val design: ColorPair,
    val wipOk: ColorPair,
    val wipAtLimit: ColorPair,
    val wipOver: ColorPair,
    val online: Color,
    val saving: Color,
    val syncError: Color,
) {
    /**
     * Looks up the container/on color pair for a seeded label by its [name]
     * (e.g. "backend", "p1"). Returns `null` for unseeded/custom labels — the
     * caller should fall back to the label's own `color: Long` via [labelColor].
     */
    fun labelColors(name: String): ColorPair? = when (name.lowercase()) {
        "backend" -> backend
        "frontend" -> frontend
        "p1" -> p1
        "docs" -> docs
        "infra" -> infra
        "design" -> design
        else -> null
    }
}

/**
 * Maps a `Label.color: Long` (0xAARRGGBB from the model) to a Compose [Color]
 * for direct display when there is no seeded [SemanticColors.labelColors] entry.
 * Compose's `Color(Long)` reads the low 32 bits as packed ARGB, so the model's
 * 0xFFRRGGBB values map straight through.
 */
fun labelColor(argb: Long): Color = Color(argb)

// The label and state colors below are identical in light and dark mode per the
// spec (the chips are tonal surfaces tuned to read on both backgrounds). They
// are kept as two values so future per-mode tuning is a one-line change.

/** Light-mode [SemanticColors], from the hi-fi `semantic` tokens. */
val LightSemanticColors: SemanticColors = SemanticColors(
    backend = ColorPair(Color(0xFFDCE6FF), Color(0xFF13366B)),
    frontend = ColorPair(Color(0xFFD7ECDD), Color(0xFF11553A)),
    p1 = ColorPair(Color(0xFFFFDBE3), Color(0xFF8C0C3A)),
    docs = ColorPair(Color(0xFFFDE9CF), Color(0xFF7A4A00)),
    infra = ColorPair(Color(0xFFD6EEF0), Color(0xFF0D4F56)),
    design = ColorPair(Color(0xFFECE0FF), Color(0xFF3C2480)),
    wipOk = ColorPair(Color(0xFFECE6F0), Color(0xFF49454E)),
    wipAtLimit = ColorPair(Color(0xFFFFDBE3), Color(0xFF8C0C3A)),
    wipOver = ColorPair(Color(0xFFF9DEDC), Color(0xFF410E0B)),
    online = Color(0xFF1E8A5B),
    saving = Color(0xFF5D5D74),
    syncError = Color(0xFFB3261E),
)

/** Dark-mode [SemanticColors]. Currently mirrors light per the spec. */
val DarkSemanticColors: SemanticColors = LightSemanticColors

/**
 * CompositionLocal carrying the active [SemanticColors]. Provided by
 * `TaskFlowTheme`; reading it outside the theme throws, which surfaces the
 * missing provider early rather than silently using a default.
 */
val LocalSemanticColors = staticCompositionLocalOf<SemanticColors> {
    error("LocalSemanticColors not provided — wrap content in TaskFlowTheme")
}
