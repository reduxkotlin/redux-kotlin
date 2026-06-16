package org.reduxkotlin.snapshot

import java.io.File

/**
 * Generates a self-contained static `index.html` from a [SnapshotReport] — a pure function of the
 * report (+ the rendered images). No server: inline CSS, relative image refs. Reachable goldens are
 * copied next to the page so the output directory is portable as a single artifact.
 */
internal object DashboardGenerator {
    fun generate(report: SnapshotReport, outDir: File): File {
        val goldensDir = File(outDir, "goldens")
        report.shots.forEach { s ->
            val g = s.verify?.golden?.let(::File)
            if (g != null && g.isFile) {
                goldensDir.mkdirs()
                g.copyTo(File(goldensDir, "${s.id}.png"), overwrite = true)
            }
        }
        val ordered = report.shots.sortedBy(::rank)
        return File(outDir, "index.html").apply { writeText(page(report, outDir, ordered)) }
    }

    private fun rank(s: ShotReport): Int = when {
        s.status == "error" -> 0
        s.verify?.result == "mismatch" -> 1
        s.verify?.result == "missing-golden" -> 2
        else -> 3
    }

    private fun e(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun page(report: SnapshotReport, outDir: File, shots: List<ShotReport>): String {
        val t = report.totals
        val cards = shots.joinToString("\n") { card(it, outDir) }
        return """<!doctype html>
<html lang="en"><head><meta charset="utf-8"><title>rk-snapshot — ${e(report.runId)}</title>
<style>
  :root { color-scheme: dark; }
  body { margin: 0; font: 14px/1.4 -apple-system,Segoe UI,Roboto,sans-serif; background:#0d1117; color:#e6edf3; }
  header { padding:16px 24px; border-bottom:1px solid #30363d; display:flex; gap:24px; align-items:baseline; flex-wrap:wrap; }
  header h1 { font-size:16px; margin:0; }
  .totals span { margin-right:14px; }
  .ok{color:#3fb950} .fail{color:#f85149} .warn{color:#d29922}
  main { padding:24px; display:grid; grid-template-columns:repeat(auto-fill,minmax(320px,1fr)); gap:20px; }
  .card { border:1px solid #30363d; border-radius:10px; overflow:hidden; background:#161b22; }
  .card.bad { border-color:#f85149; }
  .card h2 { font-size:14px; margin:0; padding:10px 12px; display:flex; justify-content:space-between; gap:8px; align-items:center; background:#1c2128; }
  .pill { font-size:11px; padding:2px 8px; border-radius:999px; }
  .pill.ok{background:#172e1b;color:#3fb950} .pill.bad{background:#3a1416;color:#f85149} .pill.warn{background:#332701;color:#d29922}
  .imgs { display:flex; gap:8px; padding:12px; background:#0d1117; }
  .imgs figure { margin:0; flex:1; text-align:center; }
  .imgs figcaption { font-size:11px; color:#8b949e; margin-bottom:4px; }
  .imgs img { max-width:100%; border:1px solid #30363d; border-radius:4px; image-rendering:pixelated; }
  .meta { padding:10px 12px; font-size:12px; color:#8b949e; }
  .meta code { color:#e6edf3; background:#0d1117; padding:1px 4px; border-radius:3px; }
</style></head>
<body>
<header>
  <h1>rk-snapshot · <code>${e(report.runId)}</code></h1>
  <div class="totals">
    <span>${t.total} total</span>
    <span class="ok">${t.ok} ok</span>
    <span class="fail">${t.failed} failed</span>
    <span class="fail">${t.mismatched} mismatched</span>
    <span class="warn">${t.missingGolden} missing-golden</span>
  </div>
</header>
<main>
$cards
</main>
</body></html>
"""
    }

    private fun pillFor(s: ShotReport): String = when {
        s.status == "error" -> """<span class="pill bad">error</span>"""

        s.verify?.result == "mismatch" ->
            """<span class="pill bad">mismatch ${"%.2f".format(s.verify.diffPercent)}%</span>"""

        s.verify?.result == "missing-golden" -> """<span class="pill warn">no golden</span>"""

        s.verify?.result == "match" -> """<span class="pill ok">match</span>"""

        else -> """<span class="pill ok">ok</span>"""
    }

    private fun card(s: ShotReport, outDir: File): String {
        val bad = s.status == "error" || s.verify?.result == "mismatch"
        val pill = pillFor(s)
        val actualRel = s.out?.let { runCatching { File(it).relativeTo(outDir).path }.getOrNull() ?: File(it).name }
        val goldenExists = File(File(outDir, "goldens"), "${s.id}.png").isFile
        val imgs = buildString {
            append("""<div class="imgs">""")
            if (goldenExists) {
                append(
                    """<figure><figcaption>golden</figcaption><img src="goldens/${e(s.id)}.png"></figure>""",
                )
            }
            if (actualRel != null) {
                append(
                    """<figure><figcaption>actual</figcaption><img src="${e(actualRel)}"></figure>""",
                )
            }
            append("</div>")
        }
        val meta = buildString {
            append("""<div class="meta">""")
            append("input <code>${e(s.input)}</code>")
            s.theme?.let { append(" · theme <code>${e(it)}</code>") }
            if (s.sizePx.size == 2) append(" · ${s.sizePx[0]}×${s.sizePx[1]}px")
            s.renderMs?.let { append(" · ${it}ms") }
            s.error?.let { append("""<br><span class="fail">${e(it)}</span>""") }
            append("</div>")
        }
        return """<div class="card${if (bad) " bad" else ""}">
  <h2><span>${e(s.id)} <small style="color:#8b949e">${e(s.scene)}</small></span>$pill</h2>
  ${if (s.status == "ok") imgs else ""}
  $meta
</div>"""
    }
}
