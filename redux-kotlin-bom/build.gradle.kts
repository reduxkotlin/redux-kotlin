plugins {
    `java-platform`
    id("convention.publishing-platform")
}

description = "Bill of Materials aligning all redux-kotlin module versions"

dependencies {
    constraints {
        val v = project.version.toString()
        val g = project.group.toString()
        api("$g:redux-kotlin:$v")
        api("$g:redux-kotlin-threadsafe:$v")
        api("$g:redux-kotlin-concurrent:$v")
        api("$g:redux-kotlin-thunk:$v")
        api("$g:redux-kotlin-granular:$v")
        api("$g:redux-kotlin-registry:$v")
        api("$g:redux-kotlin-multimodel:$v")
        api("$g:redux-kotlin-multimodel-granular:$v")
        api("$g:redux-kotlin-compose:$v")
        api("$g:redux-kotlin-compose-multimodel:$v")
        api("$g:redux-kotlin-routing:$v")
        // redux-kotlin-routing-codegen is intentionally absent: the KSP
        // processor is not published yet (in-repo only) — re-add when it ships.
        api("$g:redux-kotlin-bundle:$v")
        api("$g:redux-kotlin-bundle-compose:$v")
        api("$g:redux-kotlin-compose-saveable:$v")
        // DevTools family — experimental: aligned by the BOM but exempt from
        // semver until the devtools surface stabilizes (see docs/devtools.md).
        api("$g:redux-kotlin-devtools-core:$v")
        api("$g:redux-kotlin-devtools-bridge:$v")
        api("$g:redux-kotlin-devtools-remote:$v")
        api("$g:redux-kotlin-devtools-inapp:$v")
        api("$g:redux-kotlin-devtools-inapp-noop:$v")
        api("$g:redux-kotlin-devtools-ui:$v")
        // redux-kotlin-snapshot — experimental headless renderer (JVM/desktop);
        // aligned by the BOM but exempt from semver until its surface stabilizes.
        api("$g:redux-kotlin-snapshot:$v")
    }
}
