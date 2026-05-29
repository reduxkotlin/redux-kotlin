/* TaskFlow Screens — nav scrollspy + mode tabs */
(function () {
  function tabs() {
    document.querySelectorAll("[data-modegroup]").forEach(grp => {
      grp.querySelectorAll(".mode-tabs button").forEach(btn => {
        btn.addEventListener("click", () => {
          grp.querySelectorAll(".mode-tabs button").forEach(b => b.classList.toggle("active", b === btn));
          const t = btn.dataset.mode;
          grp.querySelectorAll(".mode-pane").forEach(p => p.classList.toggle("active", p.dataset.mode === t));
        });
      });
    });
  }
  function nav() {
    const links = [...document.querySelectorAll(".nav a")];
    const map = new Map(links.map(a => [a.getAttribute("href").slice(1), a]));
    const obs = new IntersectionObserver((entries) => {
      entries.forEach(e => {
        if (e.isIntersecting) {
          links.forEach(l => l.classList.remove("active"));
          const a = map.get(e.target.id);
          if (a) a.classList.add("active");
        }
      });
    }, { rootMargin: "-8% 0px -82% 0px", threshold: 0 });
    document.querySelectorAll(".section[id]").forEach(s => obs.observe(s));
  }
  function init(){ tabs(); nav(); }
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", init);
  else init();
})();
