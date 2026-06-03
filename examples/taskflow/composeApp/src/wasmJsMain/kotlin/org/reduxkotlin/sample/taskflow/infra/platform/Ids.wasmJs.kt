package org.reduxkotlin.sample.taskflow.infra.platform

/**
 * Mints a UUID via the browser `crypto.randomUUID()` when available, with a timestamp+random
 * fallback for older runtimes lacking the secure-context crypto API. The whole selection runs in a
 * single `js()` expression because Kotlin/Wasm requires `js(code)` to be a top-level expression.
 */
actual fun newUuid(): String = jsRandomUuid()

private fun jsRandomUuid(): String = js(
    "((typeof crypto !== 'undefined' && crypto.randomUUID) ? crypto.randomUUID() : " +
        "('fallback-' + Date.now().toString() + '-' + Math.floor(Math.random() * 1e9).toString()))",
)
