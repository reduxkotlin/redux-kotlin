# redux-kotlin-snapshot Semantics + Publish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Populate the stubbed `SemanticsDump` with a real text/role tree, expose it (and a cheaper semantics-golden gate) through the `rk snapshot` CLI + batch report, and make the module consumable from Maven Central under a reconciled version.

**Architecture:** A pure, `@Serializable` semantics data model + its text/JSON serializers (`Semantics.kt`); extraction from the live Compose scene inside the existing Skiko-only backend (`RenderBackend.kt`); a string-equality + line-diff comparator (`SemanticsDiffer.kt`); batch wiring that writes per-shot sidecars and a v2 report (`BatchRunner.kt`, `Report.kt`); new CLI options with distinct declared names (`cli/Cli.kt`); dashboard + docs updates. TDD throughout.

**Tech Stack:** Kotlin 2.3.20, Compose Multiplatform 1.11.1 (Skiko 0.144.6), kotlinx.serialization, Clikt, JUnit-style `kotlin.test`, Gradle. JDK toolchain 17.

## Global Constraints

- **JVM/Skiko headless only.** No emulator/Robolectric. The only Skiko-touching surface is `RenderBackend.kt`.
- **`explicitApi()` is strict.** Every new public declaration needs an explicit `public` modifier + KDoc. After any public-API change run `./gradlew apiDump` and commit `redux-kotlin-snapshot/api/redux-kotlin-snapshot.api` (done in Task 9; `:redux-kotlin-snapshot:test` does not run `apiCheck`, so intermediate tasks stay green).
- **Preserve the CLI contract:** existing flags, `--list` JSON shape, batch manifest, exit codes (0 ok / 1 render-or-verify failure / 2 usage), and `runCli` → `exitProcess(0)`.
- **Determinism:** the semantics dump carries **no bounds/position**. Ordering is by `SemanticsNode.id` (owners) and layout order (children) — never pixels. One JSON form (`toCanonicalJson`) is used for both `--semantics-format json` and goldens.
- **Merged semantics tree only** (matches compose-ui-test default). Merging absorbs a descendant's semantics onto the merge boundary (e.g. a Button absorbs its child Text).
- **Verdict vocabulary is lowercase:** `match` | `mismatch` | `missing-golden` (matches the existing report `result` strings).
- **Report back-compat:** all new fields nullable/defaulted; new `Int` totals default `= 0`. Consumers reading with an older model must use `ignoreUnknownKeys`.
- **Test DB note is irrelevant here** — this is `redux-kotlin`, no database. `./gradlew :redux-kotlin-snapshot:test` is safe.
- **Do not raise the JDK toolchain above 17.**

**Test command shape used throughout:**
`./gradlew :redux-kotlin-snapshot:test --tests "org.reduxkotlin.snapshot.<Class>"` (append `.<method>` for one method).

## File Structure

- **Create** `redux-kotlin-snapshot/src/main/kotlin/org/reduxkotlin/snapshot/Semantics.kt` — `SemanticsDump`, `SemanticsDump.Node`, `toText()`, `toCanonicalJson()`, `EMPTY`. Pure data (no Compose).
- **Create** `redux-kotlin-snapshot/src/main/kotlin/org/reduxkotlin/snapshot/SemanticsDiffer.kt` — `SemanticsDiffer`, `SemanticsDiffResult`. Pure strings.
- **Modify** `.../RenderBackend.kt` — remove the old `SemanticsDump` (moved to `Semantics.kt`); add extraction in `ImageComposeSceneBackend.render`.
- **Modify** `.../Report.kt` — `schemaVersion = 2`, new `ShotReport`/`Totals` fields, `SemanticsVerifyReport`.
- **Modify** `.../BatchRunner.kt` — semantics params, consume `renderResult().semantics`, sidecars, semantics verify, totals.
- **Modify** `.../cli/Cli.kt` — new options; single + batch handling; terse drift-only output.
- **Modify** `.../DashboardGenerator.kt` — reflect semantics verdict + new totals.
- **Modify** `redux-kotlin-snapshot/README.md` — flags table, typical loop, schemas, coordinate.
- **Test files** under `redux-kotlin-snapshot/src/test/kotlin/org/reduxkotlin/snapshot/`: new `SemanticsTest.kt`, `SemanticsExtractionTest.kt`, `SemanticsDifferTest.kt`, `ReportTest.kt`; extend `BatchRunnerTest.kt`, `CliTest.kt`, `DashboardGeneratorTest.kt`.

---

### Task 1: Semantics data model + serialization

Pure data + serializers. No Compose. Replaces the stub `SemanticsDump` (text-only) with `roots: List<Node>` + `texts`.

**Files:**
- Create: `redux-kotlin-snapshot/src/main/kotlin/org/reduxkotlin/snapshot/Semantics.kt`
- Modify: `redux-kotlin-snapshot/src/main/kotlin/org/reduxkotlin/snapshot/RenderBackend.kt` (remove old `SemanticsDump`)
- Test: `redux-kotlin-snapshot/src/test/kotlin/org/reduxkotlin/snapshot/SemanticsTest.kt`

**Interfaces:**
- Produces:
  - `SemanticsDump.Node(role: String?, text: List<String>, contentDescription: List<String>, testTag: String?, enabled: Boolean?, selected: Boolean?, toggle: String?, children: List<Node>)` — `@Serializable`
  - `SemanticsDump(roots: List<SemanticsDump.Node>, texts: List<String>)` with `fun toText(): String`, `fun toCanonicalJson(): String`, `companion object { val EMPTY }`

- [ ] **Step 1: Write the failing test**

Create `SemanticsTest.kt`:

```kotlin
package org.reduxkotlin.snapshot

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class SemanticsTest {
    private fun leaf(text: String) = SemanticsDump.Node(
        role = null, text = listOf(text), contentDescription = emptyList(),
        testTag = null, enabled = null, selected = null, toggle = null, children = emptyList(),
    )

    private val sample = SemanticsDump(
        roots = listOf(
            SemanticsDump.Node(
                role = "button", text = listOf("Save"), contentDescription = emptyList(),
                testTag = "save", enabled = false, selected = null, toggle = null,
                children = listOf(leaf("Icon")),
            ),
        ),
        texts = listOf("Save", "Icon"),
    )

    @Test fun canonical_json_is_deterministic() {
        assertEquals(sample.toCanonicalJson(), sample.toCanonicalJson())
    }

    @Test fun canonical_json_has_no_bounds_key() {
        assertTrue("bounds" !in sample.toCanonicalJson())
    }

    @Test fun text_form_shows_fields_and_indents_children() {
        val t = sample.toText()
        assertTrue("role=button" in t, t)
        assertTrue("Save" in t, t)
        assertTrue("testTag=save" in t, t)
        assertTrue("enabled=false" in t, t)
        assertTrue("  " in t, "child should be indented") // 2-space indent
    }

    @Test fun empty_dump_text_and_json() {
        assertEquals("(no semantics)", SemanticsDump.EMPTY.toText())
        assertEquals("[]", SemanticsDump.EMPTY.toCanonicalJson().trim())
        assertTrue(SemanticsDump.EMPTY.texts.isEmpty())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :redux-kotlin-snapshot:test --tests "org.reduxkotlin.snapshot.SemanticsTest"`
Expected: compile failure (`SemanticsDump.Node` unresolved / `toText`/`toCanonicalJson` missing).

- [ ] **Step 3: Remove the old `SemanticsDump` from `RenderBackend.kt`**

Delete lines 9–23 of `RenderBackend.kt` (the KDoc + the old `public class SemanticsDump(... texts ...)` with its companion). Leave `RenderResult` (it still references `SemanticsDump` — same package). The file top keeps `@file:OptIn(...)` and the `RenderResult`/`RenderBackend`/`ImageComposeSceneBackend` declarations.

- [ ] **Step 4: Create `Semantics.kt`**

```kotlin
package org.reduxkotlin.snapshot

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * A deterministic, bounds-free dump of a rendered scene's semantics, beside the pixels.
 * Lets a consumer (e.g. an AI agent) assert content as text/JSON instead of reading the PNG.
 * Ordering is by semantics-node id (owners) and layout order (children) — never pixel position —
 * so the [toCanonicalJson] form is stable across architectures.
 */
public class SemanticsDump(
    /** One tree per Compose `SemanticsOwner` (a Popup/Dialog gets its own). Empty when nothing captured. */
    public val roots: List<Node>,
    /** All rendered text strings, pre-order, flattened. Kept for simple text assertions. */
    public val texts: List<String>,
) {
    /** One semantics node: its merged role/text/state plus merged children. Carries no bounds. */
    @Serializable
    public class Node(
        /** Accessibility role, lowercased (e.g. `button`), or null. */
        public val role: String?,
        /** Text on this node, in order (a merge boundary absorbs descendants' text). */
        public val text: List<String>,
        /** Content descriptions on this node, in order. */
        public val contentDescription: List<String>,
        /** Test tag, or null. */
        public val testTag: String?,
        /** `false` when the node is marked disabled; otherwise null (tri-state: never `true`). */
        public val enabled: Boolean?,
        /** Selected state, or null if unspecified. */
        public val selected: Boolean?,
        /** Toggleable state name (`On`/`Off`/`Indeterminate`), or null. */
        public val toggle: String?,
        /** Merged child nodes, in layout order. */
        public val children: List<Node>,
    )

    /** Compact indented tree — the default agent/human form. One node per line, 2-space indent per depth. */
    public fun toText(): String {
        if (roots.isEmpty()) return "(no semantics)"
        val sb = StringBuilder()
        roots.forEach { appendNode(it, 0, sb) }
        return sb.toString().trimEnd('\n')
    }

    /** The single JSON form: pretty, stable field order, no bounds. Used for `--semantics-format json` and goldens. */
    public fun toCanonicalJson(): String = CANONICAL.encodeToString(ListSerializer(Node.serializer()), roots)

    private fun appendNode(n: Node, depth: Int, sb: StringBuilder) {
        val indent = "  ".repeat(depth)
        val fields = buildList {
            n.role?.let { add("role=$it") }
            if (n.text.isNotEmpty()) add("text=${n.text}")
            if (n.contentDescription.isNotEmpty()) add("desc=${n.contentDescription}")
            n.testTag?.let { add("testTag=$it") }
            n.enabled?.let { add("enabled=$it") }
            n.selected?.let { add("selected=$it") }
            n.toggle?.let { add("toggle=$it") }
        }
        sb.append(indent).append("node").append(if (fields.isEmpty()) "" else " " + fields.joinToString(" ")).append('\n')
        n.children.forEach { appendNode(it, depth + 1, sb) }
    }

    /** Shared instances. */
    public companion object {
        /** An empty dump (no semantics captured). */
        public val EMPTY: SemanticsDump = SemanticsDump(roots = emptyList(), texts = emptyList())
        private val CANONICAL = Json { prettyPrint = true; encodeDefaults = true; prettyPrintIndent = "  " }
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :redux-kotlin-snapshot:test --tests "org.reduxkotlin.snapshot.SemanticsTest"`
Expected: PASS (4 tests).

- [ ] **Step 6: Commit**

```bash
git add redux-kotlin-snapshot/src/main/kotlin/org/reduxkotlin/snapshot/Semantics.kt \
        redux-kotlin-snapshot/src/main/kotlin/org/reduxkotlin/snapshot/RenderBackend.kt \
        redux-kotlin-snapshot/src/test/kotlin/org/reduxkotlin/snapshot/SemanticsTest.kt
git commit -m "feat(snapshot): semantics data model with text + canonical JSON forms"
```

---

### Task 2: Extract semantics from the rendered scene

Populate the dump inside the Skiko backend, after `scene.render()`, before `scene.close()`. Non-fatal on error.

**Files:**
- Modify: `redux-kotlin-snapshot/src/main/kotlin/org/reduxkotlin/snapshot/RenderBackend.kt`
- Test: `redux-kotlin-snapshot/src/test/kotlin/org/reduxkotlin/snapshot/SemanticsExtractionTest.kt`

**Interfaces:**
- Consumes: `SemanticsDump`, `SemanticsDump.Node` (Task 1), `RenderSpec`, `RenderResult`.
- Produces: `ImageComposeSceneBackend.render(spec)` now returns `RenderResult(png, populatedDump)`.

- [ ] **Step 1: Write the failing test**

Create `SemanticsExtractionTest.kt`:

```kotlin
package org.reduxkotlin.snapshot

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class SemanticsExtractionTest {
    private val richContent: @Composable () -> Unit = {
        Column {
            Text("Title")
            Button(onClick = {}, enabled = false, modifier = Modifier.testTag("save")) { Text("Save") }
            Text("Logo", modifier = Modifier.semantics { contentDescription = "logo image" })
        }
    }

    private fun render() = ImageComposeSceneBackend().render(
        RenderSpec(widthDp = 200, heightDp = 200, density = 2f, content = richContent),
    ).semantics

    @Test fun captures_text_in_traversal_order() {
        val texts = render().texts
        assertTrue("Title" in texts, texts.toString())
        assertTrue("Save" in texts, texts.toString())
    }

    @Test fun button_node_carries_role_testTag_and_disabled() {
        val dump = render()
        val button = allNodes(dump).first { it.testTag == "save" }
        assertEquals("button", button.role)
        assertEquals(false, button.enabled)
        assertTrue("Save" in button.text, button.text.toString())
    }

    @Test fun merged_tree_absorbs_the_buttons_text_child() {
        val dump = render()
        val button = allNodes(dump).first { it.testTag == "save" }
        // The child Text("Save") is merged into the button; it is not a separate child node.
        assertTrue(button.children.none { it.text == listOf("Save") }, "Text child should be absorbed")
    }

    @Test fun content_description_is_captured() {
        assertTrue(allNodes(render()).any { it.contentDescription == listOf("logo image") })
    }

    @Test fun same_input_yields_identical_canonical_json() {
        assertEquals(render().toCanonicalJson(), render().toCanonicalJson())
    }

    @Test fun text_free_scene_yields_empty_texts() {
        // The 'counter' demo scene draws only bars, no semantics text.
        val dump = ImageComposeSceneBackend().render(
            RenderSpec(200, 200, 2f) {},
        ).semantics
        assertTrue(dump.texts.isEmpty())
    }

    private fun allNodes(dump: SemanticsDump): List<SemanticsDump.Node> {
        val out = mutableListOf<SemanticsDump.Node>()
        fun walk(n: SemanticsDump.Node) { out += n; n.children.forEach(::walk) }
        dump.roots.forEach(::walk)
        return out
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :redux-kotlin-snapshot:test --tests "org.reduxkotlin.snapshot.SemanticsExtractionTest"`
Expected: FAIL — dump is `EMPTY` (texts empty, no button node).

- [ ] **Step 3: Add extraction to `RenderBackend.kt`**

Add these imports under the existing ones:

```kotlin
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsPropertyKey
```

Replace the body of `ImageComposeSceneBackend.render` so it captures semantics after render:

```kotlin
    override fun render(spec: RenderSpec): RenderResult {
        val scene = ImageComposeScene(
            width = spec.widthPx,
            height = spec.heightPx,
            density = Density(spec.density),
        ) { spec.content() }
        return try {
            val png = scene.render().encodeToData(EncodedImageFormat.PNG)?.bytes
                ?: error("PNG encode returned null")
            RenderResult(png, extractSemantics(scene))
        } finally {
            scene.close()
        }
    }
```

Add, at the bottom of `ImageComposeSceneBackend` (or as file-private functions in the same file):

```kotlin
    // Reading the experimental semantics tree must never fail a render: pixels are the primary
    // artifact. Any failure degrades to SemanticsDump.EMPTY while keeping the PNG.
    @Suppress("TooGenericExceptionCaught")
    private fun extractSemantics(scene: ImageComposeScene): SemanticsDump = try {
        val roots = scene.semanticsOwners
            .sortedBy { it.rootSemanticsNode.id }
            .map { toNode(it.rootSemanticsNode) }
        if (roots.isEmpty()) {
            SemanticsDump.EMPTY
        } else {
            val texts = mutableListOf<String>()
            roots.forEach { collectTexts(it, texts) }
            SemanticsDump(roots, texts)
        }
    } catch (e: Exception) {
        SemanticsDump.EMPTY
    }

    private fun toNode(n: SemanticsNode): SemanticsDump.Node {
        val cfg = n.config
        return SemanticsDump.Node(
            role = cfg.getOrNull(SemanticsProperties.Role)?.toString()?.lowercase(),
            text = cfg.getOrNull(SemanticsProperties.Text)?.map { it.text } ?: emptyList(),
            contentDescription = cfg.getOrNull(SemanticsProperties.ContentDescription) ?: emptyList(),
            testTag = cfg.getOrNull(SemanticsProperties.TestTag),
            enabled = if (cfg.contains(SemanticsProperties.Disabled)) false else null,
            selected = cfg.getOrNull(SemanticsProperties.Selected),
            toggle = cfg.getOrNull(SemanticsProperties.ToggleableState)?.toString(),
            children = n.children.map { toNode(it) },
        )
    }

    private fun collectTexts(n: SemanticsDump.Node, out: MutableList<String>) {
        out += n.text
        n.children.forEach { collectTexts(it, out) }
    }

    // Public SemanticsConfiguration API is get(key) + contains(key); this pairs them safely.
    private fun <T> SemanticsConfiguration.getOrNull(key: SemanticsPropertyKey<T>): T? =
        if (contains(key)) get(key) else null
```

Note: `scene.semanticsOwners` is `@ExperimentalComposeUiApi`; the file already has `@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)`, so no extra annotation is needed.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :redux-kotlin-snapshot:test --tests "org.reduxkotlin.snapshot.SemanticsExtractionTest"`
Expected: PASS (6 tests). If `same_input_yields_identical_canonical_json` flakes, it indicates nondeterministic ordering — do NOT sort by anything pixel-derived; `id` order is correct.

- [ ] **Step 5: Run the full module suite (guard the render/backend regression set)**

Run: `./gradlew :redux-kotlin-snapshot:test --tests "org.reduxkotlin.snapshot.RenderBackendTest"`
Expected: PASS (unchanged).

- [ ] **Step 6: Commit**

```bash
git add redux-kotlin-snapshot/src/main/kotlin/org/reduxkotlin/snapshot/RenderBackend.kt \
        redux-kotlin-snapshot/src/test/kotlin/org/reduxkotlin/snapshot/SemanticsExtractionTest.kt
git commit -m "feat(snapshot): extract merged semantics tree from the rendered scene"
```

---

### Task 3: Semantics comparator (string equality + line diff)

**Files:**
- Create: `redux-kotlin-snapshot/src/main/kotlin/org/reduxkotlin/snapshot/SemanticsDiffer.kt`
- Test: `redux-kotlin-snapshot/src/test/kotlin/org/reduxkotlin/snapshot/SemanticsDifferTest.kt`

**Interfaces:**
- Produces:
  - `SemanticsDiffResult(result: String /* "match" | "mismatch" */, delta: List<String>)`
  - `SemanticsDiffer(maxDeltaLines: Int = 40)` with `fun compare(golden: String, actual: String): SemanticsDiffResult`

- [ ] **Step 1: Write the failing test**

Create `SemanticsDifferTest.kt`:

```kotlin
package org.reduxkotlin.snapshot

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class SemanticsDifferTest {
    private val differ = SemanticsDiffer()

    @Test fun equal_strings_match_with_no_delta() {
        val r = differ.compare("a\nb\nc", "a\nb\nc")
        assertEquals("match", r.result)
        assertTrue(r.delta.isEmpty())
    }

    @Test fun changed_line_shows_removed_and_added() {
        val r = differ.compare("""  "text": ["Save"]""", """  "text": ["Saved"]""")
        assertEquals("mismatch", r.result)
        assertTrue(r.delta.any { it.startsWith("-") && "Save" in it }, r.delta.toString())
        assertTrue(r.delta.any { it.startsWith("+") && "Saved" in it }, r.delta.toString())
    }

    @Test fun delta_is_capped_with_overflow_marker() {
        val golden = (1..100).joinToString("\n") { "g$it" }
        val actual = (1..100).joinToString("\n") { "a$it" }
        val r = SemanticsDiffer(maxDeltaLines = 10).compare(golden, actual)
        assertEquals("mismatch", r.result)
        assertEquals(11, r.delta.size) // 10 + overflow marker
        assertTrue(r.delta.last().startsWith("…"), r.delta.last())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :redux-kotlin-snapshot:test --tests "org.reduxkotlin.snapshot.SemanticsDifferTest"`
Expected: FAIL — `SemanticsDiffer` unresolved.

- [ ] **Step 3: Create `SemanticsDiffer.kt`**

```kotlin
package org.reduxkotlin.snapshot

/** Result of a semantics comparison: a lowercase [result] and a bounded, readable [delta]. */
public class SemanticsDiffResult(
    /** `match` when the canonical forms are equal, else `mismatch`. */
    public val result: String,
    /** Line-level delta (`-` golden-only, `+` actual-only); empty on match. Capped, may end with an overflow marker. */
    public val delta: List<String>,
)

/**
 * Compares two canonical semantics forms ([SemanticsDump.toCanonicalJson]) by string equality and,
 * on mismatch, emits a bounded line diff. A structured per-node differ is intentionally deferred:
 * the gate is equality, and the line diff is a readable-enough signal. Lines present in both forms
 * are elided; a changed value line surfaces as a `-`/`+` pair.
 */
public class SemanticsDiffer(private val maxDeltaLines: Int = 40) {
    /** Compares [golden] vs [actual] canonical strings. */
    public fun compare(golden: String, actual: String): SemanticsDiffResult {
        if (golden == actual) return SemanticsDiffResult("match", emptyList())
        val g = golden.lines()
        val a = actual.lines()
        val gSet = g.toSet()
        val aSet = a.toSet()
        val lines = g.filter { it !in aSet }.map { "- $it" } + a.filter { it !in gSet }.map { "+ $it" }
        val capped = if (lines.size > maxDeltaLines) {
            lines.take(maxDeltaLines) + "… (${lines.size - maxDeltaLines} more)"
        } else {
            lines
        }
        return SemanticsDiffResult("mismatch", capped)
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :redux-kotlin-snapshot:test --tests "org.reduxkotlin.snapshot.SemanticsDifferTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add redux-kotlin-snapshot/src/main/kotlin/org/reduxkotlin/snapshot/SemanticsDiffer.kt \
        redux-kotlin-snapshot/src/test/kotlin/org/reduxkotlin/snapshot/SemanticsDifferTest.kt
git commit -m "feat(snapshot): semantics comparator with capped line-diff delta"
```

---

### Task 4: Report v2 (report types + totals)

**Files:**
- Modify: `redux-kotlin-snapshot/src/main/kotlin/org/reduxkotlin/snapshot/Report.kt`
- Test: `redux-kotlin-snapshot/src/test/kotlin/org/reduxkotlin/snapshot/ReportTest.kt`

**Interfaces:**
- Produces (all `internal`):
  - `SnapshotReport.schemaVersion == 2`
  - `ShotReport(..., verifySemantics: SemanticsVerifyReport? = null, semanticsSidecar: String? = null, semanticsBytes: Int? = null)`
  - `SemanticsVerifyReport(golden: String, result: String, delta: List<String>? = null)`
  - `Totals(total, ok, failed, mismatched, missingGolden, semanticsMismatched = 0, semanticsMissingGolden = 0, semanticsMatched = 0, renderMsTotal = 0)`

- [ ] **Step 1: Write the failing test**

Create `ReportTest.kt`:

```kotlin
package org.reduxkotlin.snapshot

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

internal class ReportTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun schema_version_is_2() {
        val r = SnapshotReport(runId = "r", outDir = "o", totals = Totals(0, 0, 0, 0, 0), shots = emptyList())
        assertEquals(2, r.schemaVersion)
    }

    @Test fun new_shot_fields_default_to_null() {
        val s = ShotReport(id = "a", scene = "counter", input = "preset=n0", status = "ok")
        assertEquals(null, s.verifySemantics)
        assertEquals(null, s.semanticsSidecar)
        assertEquals(null, s.semanticsBytes)
    }

    @Test fun new_totals_fields_default_to_zero() {
        val t = Totals(0, 0, 0, 0, 0)
        assertEquals(0, t.semanticsMismatched)
        assertEquals(0, t.semanticsMatched)
        assertEquals(0L, t.renderMsTotal)
    }

    @Test fun v1_report_json_parses_under_v2_model() {
        // A v1 file lacks the new fields; defaults fill them in.
        val v1 = """{"schemaVersion":1,"runId":"r","outDir":"o",
            "totals":{"total":1,"ok":1,"failed":0,"mismatched":0,"missingGolden":0},
            "shots":[{"id":"a","scene":"counter","input":"preset=n0","status":"ok"}]}"""
        val r = json.decodeFromString(SnapshotReport.serializer(), v1)
        assertEquals(1, r.shots.size)
        assertEquals(0, r.totals.semanticsMismatched)
    }

    @Test fun semantics_verify_report_round_trips() {
        val sv = SemanticsVerifyReport(golden = "g.semantics.json", result = "mismatch", delta = listOf("- a", "+ b"))
        val encoded = Json.encodeToString(SemanticsVerifyReport.serializer(), sv)
        val decoded = json.decodeFromString(SemanticsVerifyReport.serializer(), encoded)
        assertEquals("mismatch", decoded.result)
        assertEquals(listOf("- a", "+ b"), decoded.delta)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :redux-kotlin-snapshot:test --tests "org.reduxkotlin.snapshot.ReportTest"`
Expected: FAIL — `schemaVersion` is 1, new fields/types missing.

- [ ] **Step 3: Update `Report.kt`**

Set `schemaVersion` default to `2`. Extend `Totals`, `ShotReport`, and add `SemanticsVerifyReport`:

```kotlin
/** Run roll-up counts. */
@Serializable
internal data class Totals(
    val total: Int,
    val ok: Int,
    val failed: Int,
    val mismatched: Int,
    val missingGolden: Int,
    val semanticsMismatched: Int = 0,
    val semanticsMissingGolden: Int = 0,
    val semanticsMatched: Int = 0,
    val renderMsTotal: Long = 0,
)

/** One shot's result. [status] is `ok` or `error`; [verify]/[verifySemantics] present only when verifying. */
@Serializable
internal data class ShotReport(
    val id: String,
    val scene: String,
    val input: String,
    val theme: String? = null,
    val sizePx: List<Int> = emptyList(),
    val out: String? = null,
    val bytes: Int? = null,
    val renderMs: Long? = null,
    val status: String,
    val error: String? = null,
    val verify: VerifyReport? = null,
    val verifySemantics: SemanticsVerifyReport? = null,
    val semanticsSidecar: String? = null,
    val semanticsBytes: Int? = null,
)

/** Semantics golden comparison for one shot. [result] is `match` | `mismatch` | `missing-golden`. */
@Serializable
internal data class SemanticsVerifyReport(
    val golden: String,
    val result: String,
    val delta: List<String>? = null,
)
```

Change the `schemaVersion` field to `val schemaVersion: Int = 2,` in `SnapshotReport`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :redux-kotlin-snapshot:test --tests "org.reduxkotlin.snapshot.ReportTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add redux-kotlin-snapshot/src/main/kotlin/org/reduxkotlin/snapshot/Report.kt \
        redux-kotlin-snapshot/src/test/kotlin/org/reduxkotlin/snapshot/ReportTest.kt
git commit -m "feat(snapshot): report v2 with semantics verdict + measurement fields"
```

---

### Task 5: BatchRunner — consume semantics, write sidecars, verify, aggregate totals

**Files:**
- Modify: `redux-kotlin-snapshot/src/main/kotlin/org/reduxkotlin/snapshot/BatchRunner.kt`
- Test: `redux-kotlin-snapshot/src/test/kotlin/org/reduxkotlin/snapshot/BatchRunnerTest.kt` (extend)

**Interfaces:**
- Consumes: `SemanticsDump` (Task 1/2), `SemanticsDiffer`/`SemanticsDiffResult` (Task 3), `ShotReport`/`Totals`/`SemanticsVerifyReport` (Task 4).
- Produces: `BatchRunner.run(manifest, outDir, verify, goldenDir, runId, semantics: Boolean = false, verifySemantics: Boolean = false, updateSemantics: Boolean = false, semanticsFormat: String = "text")` — writes `<id>.semantics.<ext>` sidecars, semantics goldens under `goldenDir` as `<id>.semantics.json`, and populated totals.

- [ ] **Step 1: Write the failing test (append to `BatchRunnerTest.kt`)**

First read the existing `BatchRunnerTest.kt` to reuse its `demoSnapshots` + tmp-dir setup, then add:

```kotlin
    @Test fun batch_writes_semantics_sidecars_when_requested() {
        val manifest = BatchManifest(shots = listOf(ShotSpec(id = "d", scene = "demo", preset = "default")))
        val outDir = newTmpDir()
        val report = BatchRunner(demoSnapshots).run(
            manifest, outDir, verify = false, goldenDir = null, runId = "r", semantics = true,
        )
        val sidecar = File(outDir, "d.semantics.txt")
        assertTrue(sidecar.isFile, "sidecar missing")
        assertEquals(sidecar.path, report.shots.single().semanticsSidecar)
        assertTrue((report.shots.single().semanticsBytes ?: 0) > 0)
    }

    @Test fun semantics_verify_missing_golden_is_non_failing() {
        val manifest = BatchManifest(shots = listOf(ShotSpec(id = "d", scene = "demo", preset = "default")))
        val goldenDir = newTmpDir() // empty
        val report = BatchRunner(demoSnapshots).run(
            manifest, newTmpDir(), verify = false, goldenDir = goldenDir, runId = "r", verifySemantics = true,
        )
        assertEquals("missing-golden", report.shots.single().verifySemantics?.result)
        assertEquals(0, report.totals.semanticsMismatched)
    }

    @Test fun update_semantics_writes_golden_then_verify_matches() {
        val manifest = BatchManifest(shots = listOf(ShotSpec(id = "d", scene = "demo", preset = "default")))
        val goldenDir = newTmpDir()
        // 1) author baseline
        BatchRunner(demoSnapshots).run(
            manifest, newTmpDir(), verify = false, goldenDir = goldenDir, runId = "r", updateSemantics = true,
        )
        assertTrue(File(goldenDir, "d.semantics.json").isFile)
        // 2) verify against it
        val report = BatchRunner(demoSnapshots).run(
            manifest, newTmpDir(), verify = false, goldenDir = goldenDir, runId = "r2", verifySemantics = true,
        )
        assertEquals("match", report.shots.single().verifySemantics?.result)
        assertEquals(1, report.totals.semanticsMatched)
    }

    @Test fun totals_aggregate_render_ms() {
        val manifest = BatchManifest(shots = listOf(ShotSpec(id = "d", scene = "demo", preset = "default")))
        val report = BatchRunner(demoSnapshots).run(manifest, newTmpDir(), verify = false, goldenDir = null, runId = "r")
        assertTrue(report.totals.renderMsTotal >= 0)
    }
```

If `BatchRunnerTest.kt` has no `newTmpDir()` helper, add:

```kotlin
    private fun newTmpDir(): File = File.createTempFile("rkbatch", "").let { it.delete(); it.mkdirs(); it }
```

and imports: `import kotlin.test.assertEquals`, `import kotlin.test.assertTrue`, `import java.io.File` (if absent).

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :redux-kotlin-snapshot:test --tests "org.reduxkotlin.snapshot.BatchRunnerTest"`
Expected: FAIL — `run(...)` has no `semantics`/`verifySemantics`/`updateSemantics` params.

- [ ] **Step 3: Update `BatchRunner.kt`**

Add a `SemanticsDiffer` to the constructor and extend `run`/`runShot`. New imports: none beyond existing (`java.io.File` already imported).

Constructor:

```kotlin
internal class BatchRunner(
    private val app: SnapshotApp,
    private val backend: RenderBackend = ImageComposeSceneBackend(),
    private val differ: Differ = Differ(),
    private val semanticsDiffer: SemanticsDiffer = SemanticsDiffer(),
) {
```

Replace `run(...)`:

```kotlin
    @Suppress("LongParameterList")
    fun run(
        manifest: BatchManifest,
        outDir: File,
        verify: Boolean,
        goldenDir: File?,
        runId: String,
        semantics: Boolean = false,
        verifySemantics: Boolean = false,
        updateSemantics: Boolean = false,
        semanticsFormat: String = "text",
    ): SnapshotReport {
        outDir.mkdirs()
        val shots = manifest.shots.map {
            runShot(it, manifest.defaults, outDir, verify, goldenDir, semantics, verifySemantics, updateSemantics, semanticsFormat)
        }
        return SnapshotReport(
            runId = runId,
            outDir = outDir.path,
            totals = Totals(
                total = shots.size,
                ok = shots.count {
                    it.status == "ok" && it.verify?.result != "mismatch" && it.verifySemantics?.result != "mismatch"
                },
                failed = shots.count { it.status == "error" },
                mismatched = shots.count { it.verify?.result == "mismatch" },
                missingGolden = shots.count { it.verify?.result == "missing-golden" },
                semanticsMismatched = shots.count { it.verifySemantics?.result == "mismatch" },
                semanticsMissingGolden = shots.count { it.verifySemantics?.result == "missing-golden" },
                semanticsMatched = shots.count { it.verifySemantics?.result == "match" },
                renderMsTotal = shots.sumOf { it.renderMs ?: 0 },
            ),
            shots = shots,
        )
    }
```

Replace `runShot(...)` so it captures the full `RenderResult` and handles semantics:

```kotlin
    @Suppress("TooGenericExceptionCaught", "LongParameterList")
    private fun runShot(
        spec: ShotSpec,
        defaults: ManifestDefaults,
        outDir: File,
        verify: Boolean,
        goldenDir: File?,
        semantics: Boolean,
        verifySemantics: Boolean,
        updateSemantics: Boolean,
        semanticsFormat: String,
    ): ShotReport {
        val inputDesc = if (spec.preset != null) "preset=${spec.preset}" else "json"
        return try {
            val input = when {
                spec.preset != null -> SnapshotInput.Preset(spec.preset)
                spec.stateJson != null -> SnapshotInput.Json(spec.stateJson)
                else -> throw SnapshotException("shot '${spec.id}' needs 'preset' or 'stateJson'")
            }
            val shot = app.resolve(
                spec.scene, input,
                theme = spec.theme ?: defaults.theme,
                width = spec.width ?: defaults.width,
                height = spec.height ?: defaults.height,
                density = spec.density ?: defaults.density,
            )
            val start = System.currentTimeMillis()
            val result = app.renderResult(shot, backend)
            val ms = System.currentTimeMillis() - start
            val png = result.png
            val outFile = File(outDir, spec.out ?: "${spec.id}.png").apply {
                parentFile?.mkdirs()
                writeBytes(png)
            }
            val canonical = result.semantics.toCanonicalJson()

            if (updateSemantics && goldenDir != null) {
                File(goldenDir, "${spec.id}.semantics.json").apply { parentFile?.mkdirs(); writeText(canonical) }
            }
            val sidecar = if (semantics) writeSidecar(spec, result.semantics, canonical, outDir, semanticsFormat) else null
            val semVerify = if (verifySemantics) verifySemanticsShot(spec, canonical, goldenDir) else null

            ShotReport(
                id = spec.id, scene = spec.scene, input = inputDesc, theme = shot.theme,
                sizePx = listOf(
                    (shot.widthDp * shot.density).roundToInt(),
                    (shot.heightDp * shot.density).roundToInt(),
                ),
                out = outFile.path, bytes = png.size, renderMs = ms, status = "ok",
                verify = if (verify) verifyShot(spec, png, goldenDir, outDir) else null,
                verifySemantics = semVerify,
                semanticsSidecar = sidecar?.path,
                semanticsBytes = if (semantics || verifySemantics) canonical.toByteArray().size else null,
            )
        } catch (e: Exception) {
            ShotReport(id = spec.id, scene = spec.scene, input = inputDesc, status = "error", error = e.message)
        }
    }

    private fun writeSidecar(spec: ShotSpec, dump: SemanticsDump, canonical: String, outDir: File, format: String): File {
        val ext = if (format == "json") "semantics.json" else "semantics.txt"
        val body = if (format == "json") canonical else dump.toText()
        return File(outDir, "${spec.id}.$ext").apply { parentFile?.mkdirs(); writeText(body) }
    }

    private fun verifySemanticsShot(spec: ShotSpec, canonical: String, goldenDir: File?): SemanticsVerifyReport {
        val goldenFile = File(goldenDir ?: File("."), "${spec.id}.semantics.json")
        val golden = goldenFile.takeIf { it.isFile }?.readText()
            ?: return SemanticsVerifyReport(goldenFile.path, "missing-golden")
        val r = semanticsDiffer.compare(golden, canonical)
        return SemanticsVerifyReport(goldenFile.path, r.result, r.delta.takeIf { it.isNotEmpty() })
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :redux-kotlin-snapshot:test --tests "org.reduxkotlin.snapshot.BatchRunnerTest"`
Expected: PASS (existing + 4 new).

- [ ] **Step 5: Commit**

```bash
git add redux-kotlin-snapshot/src/main/kotlin/org/reduxkotlin/snapshot/BatchRunner.kt \
        redux-kotlin-snapshot/src/test/kotlin/org/reduxkotlin/snapshot/BatchRunnerTest.kt
git commit -m "feat(snapshot): batch semantics sidecars, golden verify, and totals"
```

---

### Task 6: CLI single-shot semantics options

**Files:**
- Modify: `redux-kotlin-snapshot/src/main/kotlin/org/reduxkotlin/snapshot/cli/Cli.kt`
- Test: `redux-kotlin-snapshot/src/test/kotlin/org/reduxkotlin/snapshot/CliTest.kt` (extend)

**Interfaces:**
- Consumes: `SemanticsDiffer` (Task 3), `RenderResult.semantics`, `SemanticsDump.toText/toCanonicalJson` (Task 1).
- Produces: single-shot flags `--semantics`, `--semantics-format`, `--verify-semantics-file`, `--update-semantics`.

- [ ] **Step 1: Write the failing test (append to `CliTest.kt`)**

```kotlin
    @Test fun single_semantics_text_prints_dump() {
        val r = snapshotCommand(demoSnapshots)
            .test(listOf("--scene", "demo", "--preset", "default", "--semantics"))
        assertEquals(0, r.statusCode, r.output)
        assertTrue("node" in r.output, r.output)
    }

    @Test fun single_semantics_json_prints_json() {
        val r = snapshotCommand(demoSnapshots)
            .test(listOf("--scene", "demo", "--preset", "default", "--semantics", "--semantics-format", "json"))
        assertEquals(0, r.statusCode, r.output)
        assertTrue(r.output.trimStart().startsWith("["), r.output)
    }

    @Test fun update_semantics_then_verify_matches() {
        val golden = File(tmp, "demo.semantics.json")
        val g = snapshotCommand(demoSnapshots).test(
            listOf("--scene", "demo", "--preset", "default", "--update-semantics", "--verify-semantics-file", golden.path),
        )
        assertEquals(0, g.statusCode, g.output)
        assertTrue(golden.isFile)
        val v = snapshotCommand(demoSnapshots).test(
            listOf("--scene", "demo", "--preset", "default", "--verify-semantics-file", golden.path),
        )
        assertEquals(0, v.statusCode, v.output)
        assertTrue("match" in v.output, v.output)
    }

    @Test fun verify_semantics_mismatch_exits_1() {
        val golden = File(tmp, "wrong.semantics.json").apply { writeText("[]") }
        val r = snapshotCommand(demoSnapshots).test(
            listOf("--scene", "demo", "--preset", "default", "--verify-semantics-file", golden.path),
        )
        assertEquals(1, r.statusCode, r.output)
        assertTrue("mismatch" in r.output, r.output)
    }

    @Test fun update_semantics_without_file_exits_2() {
        val r = snapshotCommand(demoSnapshots)
            .test(listOf("--scene", "demo", "--preset", "default", "--update-semantics"))
        assertEquals(2, r.statusCode, r.output)
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :redux-kotlin-snapshot:test --tests "org.reduxkotlin.snapshot.CliTest"`
Expected: FAIL — new options unrecognized (Clikt exits 2 / no such option).

- [ ] **Step 3: Update `Cli.kt` (single-shot path)**

Add imports:

```kotlin
import com.github.ajalt.clikt.parameters.types.choice
import org.reduxkotlin.snapshot.SemanticsDiffer
```

Add options to `SnapshotCommand` (beside the existing ones):

```kotlin
    private val semantics by option("--semantics", help = "Emit the semantics dump (stdout single; sidecar batch)").flag()
    private val semanticsFormat by option("--semantics-format", help = "Dump format")
        .choice("json", "text").default("text")
    private val verifySemanticsFile by option(
        "--verify-semantics-file", help = "Semantics golden to compare against (single shot)",
    ).file()
    private val verifySemantics by option(
        "--verify-semantics", help = "Enable the semantics golden gate (batch; needs --golden-dir)",
    ).flag()
    private val updateSemantics by option(
        "--update-semantics", help = "Write/update semantics golden(s), then exit 0",
    ).flag()
```

Change `runSingle` to capture the full result and handle semantics. Replace the render block and add semantics handling before the final `out?.let { ... }`:

```kotlin
        val result = try {
            val shot = app.resolve(sceneName, input, theme, width, height, null)
            app.renderResult(shot, BACKEND)
        } catch (e: SnapshotException) {
            throw CliktError(e.message ?: "usage error", cause = e, statusCode = 2)
        } catch (e: Exception) {
            throw CliktError("render failed: ${e.message}", cause = e, statusCode = 1)
        }
        val png = result.png
        out?.apply {
            parentFile?.mkdirs()
            writeBytes(png)
        }

        val canonical = result.semantics.toCanonicalJson()
        if (updateSemantics) {
            val golden = verifySemanticsFile
                ?: throw CliktError("--update-semantics needs --verify-semantics-file", statusCode = 2)
            golden.parentFile?.mkdirs()
            golden.writeText(canonical)
            echo("wrote semantics golden ${golden.path}")
            return
        }
        if (semantics) {
            val dump = if (semanticsFormat == "json") canonical else result.semantics.toText()
            out?.let { File(it.path + if (semanticsFormat == "json") ".semantics.json" else ".semantics.txt").writeText(dump) }
            echo(dump)
        }
        verifySemanticsFile?.let { golden ->
            if (!golden.isFile) {
                throw CliktError(
                    "--verify-semantics-file golden not found: ${golden.absolutePath}\n" +
                        "  Generate it first with --update-semantics --verify-semantics-file ${golden.path}.",
                    statusCode = 2,
                )
            }
            val r = SemanticsDiffer().compare(golden.readText(), canonical)
            echo("verify-semantics: ${r.result}")
            if (r.result == "mismatch") {
                r.delta.forEach { echo(it) }
                throw CliktError("semantics golden mismatch", statusCode = 1)
            }
        }
```

Keep the existing pixel `verify?.let { ... }` block and the final `out?.let { echo("wrote ...") }`. (The `--verify` pixel path continues to use `png`.)

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :redux-kotlin-snapshot:test --tests "org.reduxkotlin.snapshot.CliTest"`
Expected: PASS (existing + 5 new).

- [ ] **Step 5: Commit**

```bash
git add redux-kotlin-snapshot/src/main/kotlin/org/reduxkotlin/snapshot/cli/Cli.kt \
        redux-kotlin-snapshot/src/test/kotlin/org/reduxkotlin/snapshot/CliTest.kt
git commit -m "feat(snapshot): single-shot --semantics / --verify-semantics-file / --update-semantics"
```

---

### Task 7: CLI batch semantics + terse drift-only output

**Files:**
- Modify: `redux-kotlin-snapshot/src/main/kotlin/org/reduxkotlin/snapshot/cli/Cli.kt`
- Test: `redux-kotlin-snapshot/src/test/kotlin/org/reduxkotlin/snapshot/CliTest.kt` (extend)

**Interfaces:**
- Consumes: `BatchRunner.run(... semantics, verifySemantics, updateSemantics, semanticsFormat)` (Task 5), the new options (Task 6).
- Produces: batch `--semantics` writes sidecars; `--verify-semantics` requires `--golden-dir` (exit 2 otherwise) and gates exit 1; terse drift-only summary lines.

- [ ] **Step 1: Write the failing test (append to `CliTest.kt`)**

```kotlin
    @Test fun batch_verify_semantics_without_golden_dir_exits_2() {
        val manifest = File(tmp, "vs.json").apply {
            writeText("""{"shots":[{"id":"a","scene":"demo","preset":"default"}]}""")
        }
        val r = snapshotCommand(demoSnapshots)
            .test(listOf("--batch", manifest.path, "--out-dir", File(tmp, "vsout").path, "--verify-semantics"))
        assertEquals(2, r.statusCode, r.output)
    }

    @Test fun batch_semantics_writes_sidecars() {
        val manifest = File(tmp, "sc.json").apply {
            writeText("""{"shots":[{"id":"a","scene":"demo","preset":"default"}]}""")
        }
        val outDir = File(tmp, "scout")
        val r = snapshotCommand(demoSnapshots)
            .test(listOf("--batch", manifest.path, "--out-dir", outDir.path, "--semantics"))
        assertEquals(0, r.statusCode, r.output)
        assertTrue(File(outDir, "a.semantics.txt").isFile)
    }

    @Test fun batch_semantics_mismatch_exits_1_and_lists_drifted() {
        val goldenDir = File(tmp, "sg").apply { mkdirs() }
        File(goldenDir, "a.semantics.json").writeText("[]") // wrong baseline -> drift
        val manifest = File(tmp, "sm.json").apply {
            writeText("""{"shots":[{"id":"a","scene":"demo","preset":"default"}]}""")
        }
        val r = snapshotCommand(demoSnapshots).test(
            listOf(
                "--batch", manifest.path, "--out-dir", File(tmp, "smout").path,
                "--golden-dir", goldenDir.path, "--verify-semantics",
            ),
        )
        assertEquals(1, r.statusCode, r.output)
        assertTrue("a" in r.output && "mismatch" in r.output, r.output)
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :redux-kotlin-snapshot:test --tests "org.reduxkotlin.snapshot.CliTest"`
Expected: FAIL — batch ignores the new flags (no exit-2 guard, no sidecars, no gating).

- [ ] **Step 3: Update `runBatch` in `Cli.kt`**

Add a guard + pass the new params + terse output. Replace `runBatch`:

```kotlin
    private fun runBatch(file: File) {
        requireManifest(file)
        if (verifySemantics && goldenDir == null) {
            throw CliktError("--verify-semantics needs --golden-dir", statusCode = 2)
        }
        if (updateSemantics && goldenDir == null) {
            throw CliktError("--update-semantics (batch) needs --golden-dir", statusCode = 2)
        }
        val manifest = try {
            Json.decodeFromString(BatchManifest.serializer(), file.readText())
        } catch (e: SerializationException) {
            throw CliktError("invalid --batch manifest: ${e.message}", cause = e, statusCode = 2)
        }
        val runId = "run-${System.currentTimeMillis()}"
        val report = BatchRunner(app, BACKEND).run(
            manifest, outDir, verify = goldenDir != null, goldenDir, runId,
            semantics = semantics, verifySemantics = verifySemantics,
            updateSemantics = updateSemantics, semanticsFormat = semanticsFormat,
        )
        val json = Json { prettyPrint = true }.encodeToString(SnapshotReport.serializer(), report)
        File(outDir, "report.json").apply {
            parentFile?.mkdirs()
            writeText(json)
        }
        if (dashboard) {
            val index = DashboardGenerator.generate(report, outDir)
            if (!jsonMode) echo("dashboard: ${index.path}")
        }
        if (jsonMode) {
            echo(json)
        } else {
            val t = report.totals
            echo(
                "batch: ${t.ok}/${t.total} ok, ${t.failed} failed, ${t.mismatched} mismatched, " +
                    "${t.semanticsMismatched} semantics-mismatched -> ${outDir.path}",
            )
            report.shots.filter {
                it.status == "error" || it.verify?.result == "mismatch" || it.verifySemantics?.result == "mismatch"
            }.forEach { s ->
                echo("  drift ${s.id}: pixel=${s.verify?.result ?: "-"} semantics=${s.verifySemantics?.result ?: "-"} " +
                    "png=${s.out ?: "-"} diff=${s.verify?.diffImage ?: "-"} sidecar=${s.semanticsSidecar ?: "-"}")
                s.verifySemantics?.delta?.forEach { echo("    $it") }
            }
        }
        if (report.totals.failed > 0 || report.totals.mismatched > 0 || report.totals.semanticsMismatched > 0) {
            throw CliktError(
                "batch had ${report.totals.failed} failed / ${report.totals.mismatched} mismatched / " +
                    "${report.totals.semanticsMismatched} semantics-mismatched",
                statusCode = 1,
            )
        }
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :redux-kotlin-snapshot:test --tests "org.reduxkotlin.snapshot.CliTest"`
Expected: PASS (existing + 3 new).

- [ ] **Step 5: Commit**

```bash
git add redux-kotlin-snapshot/src/main/kotlin/org/reduxkotlin/snapshot/cli/Cli.kt \
        redux-kotlin-snapshot/src/test/kotlin/org/reduxkotlin/snapshot/CliTest.kt
git commit -m "feat(snapshot): batch semantics gate + terse drift-only output"
```

---

### Task 8: Dashboard reflects the semantics verdict

Prevents a semantics-only mismatch rendering as a green card while the process exits 1.

**Files:**
- Modify: `redux-kotlin-snapshot/src/main/kotlin/org/reduxkotlin/snapshot/DashboardGenerator.kt`
- Test: `redux-kotlin-snapshot/src/test/kotlin/org/reduxkotlin/snapshot/DashboardGeneratorTest.kt` (extend)

**Interfaces:**
- Consumes: `SnapshotReport`/`ShotReport.verifySemantics`/`Totals` (Task 4).
- Produces: `index.html` mentions the semantics verdict for drifted shots + the `semanticsMismatched` total.

- [ ] **Step 1: Read `DashboardGenerator.kt` and its test**

Read both files to learn the current HTML shape (how a shot card and the totals row are built) before editing. The exact string-building style must be matched.

- [ ] **Step 2: Write the failing test (append to `DashboardGeneratorTest.kt`)**

```kotlin
    @Test fun dashboard_shows_semantics_mismatch() {
        val report = SnapshotReport(
            runId = "r", outDir = "o",
            totals = Totals(total = 1, ok = 0, failed = 0, mismatched = 0, missingGolden = 0, semanticsMismatched = 1),
            shots = listOf(
                ShotReport(
                    id = "a", scene = "demo", input = "preset=default", status = "ok",
                    verifySemantics = SemanticsVerifyReport("a.semantics.json", "mismatch", listOf("- x", "+ y")),
                ),
            ),
        )
        val outDir = File.createTempFile("dash", "").let { it.delete(); it.mkdirs(); it }
        val html = DashboardGenerator.generate(report, outDir).readText()
        assertTrue("semantics" in html.lowercase(), "should mention semantics")
        assertTrue("mismatch" in html, "should show the semantics verdict")
    }
```

- [ ] **Step 3: Update `DashboardGenerator.kt`**

Following the file's existing HTML-building style, (a) include `semanticsMismatched` in the totals row, and (b) for each shot with `verifySemantics != null`, render its `result` (and, when `mismatch`, treat the card as failing rather than green). Because the exact template is file-specific, mirror how the pixel `verify.result` is already rendered and add a parallel `verifySemantics.result` line/badge. Do not remove existing pixel rendering.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :redux-kotlin-snapshot:test --tests "org.reduxkotlin.snapshot.DashboardGeneratorTest"`
Expected: PASS (existing + 1 new).

- [ ] **Step 5: Commit**

```bash
git add redux-kotlin-snapshot/src/main/kotlin/org/reduxkotlin/snapshot/DashboardGenerator.kt \
        redux-kotlin-snapshot/src/test/kotlin/org/reduxkotlin/snapshot/DashboardGeneratorTest.kt
git commit -m "feat(snapshot): surface semantics verdict in the dashboard"
```

---

### Task 9: Docs, API dump, version reconcile, publish verification

No new behavior; wraps up the public surface, docs, and the publish path. Ends with the full module suite green.

**Files:**
- Modify: `redux-kotlin-snapshot/README.md`
- Modify: `redux-kotlin-snapshot/api/redux-kotlin-snapshot.api` (regenerated)
- Read: `gradle.properties`, `CHANGELOG.md` (version reconcile note)

- [ ] **Step 1: Regenerate the API dump**

Run: `./gradlew apiDump`
Then confirm the new public types are present:
Run: `grep -E "Semantics(Dump|Differ|DiffResult)" redux-kotlin-snapshot/api/redux-kotlin-snapshot.api`
Expected: entries for `SemanticsDump`, `SemanticsDump$Node`, `SemanticsDiffer`, `SemanticsDiffResult`, and the updated `SemanticsDump` constructor (now `(List;List;)V`).

- [ ] **Step 2: Verify the API check passes**

Run: `./gradlew :redux-kotlin-snapshot:apiCheck`
Expected: PASS (no drift after the dump).

- [ ] **Step 3: Update `README.md`**

Read the current README first, then add/extend, matching its style:
1. **Flags table** rows for: `--semantics`, `--semantics-format json|text`, `--verify-semantics-file <file>` (single), `--verify-semantics` (batch, needs `--golden-dir`), `--update-semantics`.
2. A **"Semantics for AI agents"** section with the typical loop:
   - Author baselines: `rk snapshot --batch shots.json --golden-dir goldens --update-semantics`
   - Gate: `rk snapshot --batch shots.json --out-dir out --golden-dir goldens --verify-semantics` → exit 1 + terse drift lines when something changed; the agent reads `out/<id>.semantics.txt` (or the `.diff.png`) only for drifted shots.
   - Single dump: `rk snapshot --scene demo --preset default --semantics` (text) or `--semantics-format json`.
3. **Dump JSON schema:** document the `Node` object fields (`role, text, contentDescription, testTag, enabled, selected, toggle, children`) and that the top level is a JSON array of roots, no bounds.
4. **Report v2 schema:** note `schemaVersion: 2`, the new `ShotReport` fields (`verifySemantics`, `semanticsSidecar`, `semanticsBytes`) and `Totals` fields (`semanticsMismatched`, `semanticsMissingGolden`, `semanticsMatched`, `renderMsTotal`); note consumers should read with `ignoreUnknownKeys = true`.
5. **Consuming from Maven Central:** the coordinate `org.reduxkotlin:redux-kotlin-snapshot:1.0.0-alpha03` with a Gradle snippet:
   ```kotlin
   repositories { mavenCentral() }
   dependencies { implementation("org.reduxkotlin:redux-kotlin-snapshot:1.0.0-alpha03") }
   ```
   plus a note that scene authoring transitively pulls Compose/clikt/serialization (`api` deps).
6. A **version note:** `1.0.0-SNAPSHOT` is the `master` baseline; released artifacts ride the shared release-tag line with the CLI (next: `1.0.0-alpha03`).

- [ ] **Step 4: Verify a local publish resolves (artifact + POM + jars)**

Run: `./gradlew :redux-kotlin-snapshot:publishToMavenLocal`
Then confirm the coordinate landed with sources + javadoc jars:
Run: `ls ~/.m2/repository/org/reduxkotlin/redux-kotlin-snapshot/1.0.0-SNAPSHOT/`
Expected: a `.pom`, the main `.jar`, `-sources.jar`, and `-javadoc.jar` (the local publish uses the `1.0.0-SNAPSHOT` baseline; the released `alpha03` coordinate is produced by CI on the maintainer tag push — a follow-up action, not run here).

- [ ] **Step 5: Run the full module test suite**

Run: `./gradlew :redux-kotlin-snapshot:test`
Expected: PASS (all classes).

- [ ] **Step 6: Commit**

```bash
git add redux-kotlin-snapshot/README.md redux-kotlin-snapshot/api/redux-kotlin-snapshot.api
git commit -m "docs(snapshot): document semantics flags, schemas, and Maven Central coordinate; apiDump"
```

---

## Release follow-up (maintainer, out of band)

Cutting `org.reduxkotlin:redux-kotlin-snapshot:1.0.0-alpha03` to Maven Central is a signed CI publish triggered by a maintainer tag push (`-Pversion=1.0.0-alpha03`, `signingInMemoryKey` present). Not performed by this plan. Confirm the snapshot module is in the release publish set when the tag is cut.

## Self-Review (completed by plan author)

**Spec coverage:**
- §A extraction → Tasks 1–2. §B canonical + line diff → Tasks 1 (canonical) + 3 (diff). §C CLI → Tasks 6–7. §D report v2 + terse output + measurement → Tasks 4, 5, 7. §E publish/version → Task 9. Docs → Task 9. Testing → every task's TDD steps + explicit multi-owner/empty/back-compat/merged-absorption/determinism cases.
- Multi-owner: modeled as `roots: List<Node>` (Task 1) and ordered by `id` (Task 2). A dedicated Popup/Dialog owner-ordering test is **not** asserted because headless `ImageComposeScene` owner population for popups is unverified; the `roots` list + `id` sort are correct for the single-owner case and cost nothing if a second owner appears. If, during Task 2, a Popup fixture is found to populate a second owner, add the ordering test there.

**Placeholder scan:** No TBD/TODO. Task 8 step 3 and Task 9 step 3 describe edits against file-specific templates (dashboard HTML / README prose) rather than pasting full replacements, because the exact current content must be read first; each names the precise fields/rows to add.

**Type consistency:** `SemanticsDump(roots, texts)`, `SemanticsDump.Node(...)`, `toCanonicalJson()`/`toText()`, `SemanticsDiffer.compare(golden, actual): SemanticsDiffResult(result, delta)`, `SemanticsVerifyReport(golden, result, delta)`, and `BatchRunner.run(..., semantics, verifySemantics, updateSemantics, semanticsFormat)` are used identically across tasks.
