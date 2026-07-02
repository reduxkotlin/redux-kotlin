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
        s.verify?.result == "mismatch" || s.verifySemantics?.result == "mismatch" -> 1
        s.verify?.result == "missing-golden" -> 2
        else -> 3
    }

    private fun e(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun page(report: SnapshotReport, outDir: File, shots: List<ShotReport>): String {
        val t = report.totals
        val cards = shots.joinToString("\n") { card(it, outDir) }
        return """<!doctype html>
<html lang="en"><head><meta charset="utf-8"><title>rk snapshot — ${e(report.runId)}</title>
<style>$STYLE</style></head>
<body>
<header>
  <h1>rk snapshot · <code>${e(report.runId)}</code></h1>
  <div class="totals">
    <span>${t.total} total</span>
    <span class="ok">${t.ok} ok</span>
    <span class="fail">${t.failed} failed</span>
    <span class="fail">${t.mismatched} mismatched</span>
    <span class="warn">${t.missingGolden} missing-golden</span>
    <span class="fail">${t.semanticsMismatched} semantics-mismatched</span>
  </div>
</header>
<main>
$cards
</main>
$LIGHTBOX
</body></html>
"""
    }

    private val STYLE = """
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
  /* smooth downscale for thumbnails (pixelated would nearest-neighbour a 2x screenshot -> jagged) */
  .imgs img { max-width:100%; border:1px solid #30363d; border-radius:4px; image-rendering:auto; cursor:zoom-in; }
  .meta { padding:10px 12px; font-size:12px; color:#8b949e; }
  .meta code { color:#e6edf3; background:#0d1117; padding:1px 4px; border-radius:3px; }
  /* click-to-zoom lightbox */
  .lb { display:none; position:fixed; inset:0; z-index:50; background:rgba(0,0,0,.88); flex-direction:column; }
  .lb.open { display:flex; }
  .lbbar { display:flex; gap:16px; align-items:center; padding:10px 16px; background:#161b22; color:#e6edf3; font-size:13px; }
  .lbbar .sp { flex:1; }
  .lbbar button { background:#21262d; color:#e6edf3; border:1px solid #30363d; border-radius:6px; padding:4px 10px; cursor:pointer; }
  .lbwrap { flex:1; overflow:auto; display:flex; align-items:flex-start; justify-content:center; padding:16px; }
  .lbwrap img.fit { max-width:100%; max-height:calc(100vh - 56px); object-fit:contain; image-rendering:auto; cursor:zoom-in; }
  .lbwrap img.actual { image-rendering:pixelated; cursor:zoom-out; } /* 1:1 native pixels for diff inspection */
""".trim()

    private val LIGHTBOX = """
<div id="lb" class="lb" onclick="if(event.target.id==='lb')lbClose()">
  <div class="lbbar">
    <span id="lbcap"></span><span class="sp"></span>
    <button onclick="lbToggle()">fit / 1:1</button>
    <button onclick="lbClose()">&#10005; close</button>
  </div>
  <div class="lbwrap"><img id="lbimg" class="fit" onclick="lbToggle()"></div>
</div>
<script>
  function lbOpen(src, cap) {
    var i = document.getElementById('lbimg');
    i.src = src; i.className = 'fit';
    document.getElementById('lbcap').textContent = cap;
    document.getElementById('lb').classList.add('open');
  }
  function lbClose() { document.getElementById('lb').classList.remove('open'); }
  function lbToggle() {
    var i = document.getElementById('lbimg');
    i.className = (i.className === 'fit') ? 'actual' : 'fit';
  }
  document.addEventListener('keydown', function (e) { if (e.key === 'Escape') lbClose(); });
</script>
""".trim()

    private fun pillFor(s: ShotReport): String = when {
        s.status == "error" -> """<span class="pill bad">error</span>"""

        s.verify?.result == "mismatch" ->
            """<span class="pill bad">mismatch ${"%.2f".format(s.verify.diffPercent)}%</span>"""

        s.verify?.result == "missing-golden" -> """<span class="pill warn">no golden</span>"""

        s.verify?.result == "match" -> """<span class="pill ok">match</span>"""

        else -> """<span class="pill ok">ok</span>"""
    }

    private fun semanticsPillFor(s: ShotReport): String? = when (s.verifySemantics?.result) {
        "mismatch" -> """<span class="pill bad">semantics mismatch</span>"""
        "missing-golden" -> """<span class="pill warn">no semantics golden</span>"""
        "match" -> """<span class="pill ok">semantics match</span>"""
        else -> null
    }

    private fun rel(path: String, outDir: File): String =
        runCatching { File(path).relativeTo(outDir).path }.getOrNull() ?: File(path).name

    /** Escapes a value for use inside a single-quoted JS string literal. */
    private fun js(s: String): String = s.replace("\\", "\\\\").replace("'", "\\'")

    private fun figure(caption: String, src: String, label: String): String {
        val onClick = "lbOpen('${e(js(src))}','${e(js(label))}')"
        return """<figure><figcaption>$caption</figcaption>""" +
            """<img src="${e(src)}" onclick="$onClick"></figure>"""
    }

    private fun imgsFor(s: ShotReport, outDir: File): String = buildString {
        append("""<div class="imgs">""")
        if (File(File(outDir, "goldens"), "${s.id}.png").isFile) {
            append(figure("golden", "goldens/${s.id}.png", "golden · ${s.id}"))
        }
        s.out?.let { append(figure("actual", rel(it, outDir), "actual · ${s.id}")) }
        s.verify?.diffImage?.let { append(figure("diff", rel(it, outDir), "diff · ${s.id}")) }
        append("</div>")
    }

    private fun card(s: ShotReport, outDir: File): String {
        val bad = s.status == "error" || s.verify?.result == "mismatch" || s.verifySemantics?.result == "mismatch"
        val pill = pillFor(s) + (semanticsPillFor(s)?.let { " $it" } ?: "")
        val imgs = imgsFor(s, outDir)
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
