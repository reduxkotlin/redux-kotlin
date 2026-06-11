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
        api("$g:redux-kotlin-routing-codegen:$v")
        api("$g:redux-kotlin-bundle:$v")
        api("$g:redux-kotlin-bundle-compose:$v")
        api("$g:redux-kotlin-compose-saveable:$v")
    }
}
