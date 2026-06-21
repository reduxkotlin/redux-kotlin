// Force a single Karma browser for every Kotlin/JS module.
//
// The Kotlin Gradle plugin's default launcher is `ChromiumHeadless`, which the
// `useKarma { useChromeHeadlessNoSandbox() }` DSL only *appends* to — leaving the
// browser list as `[ChromiumHeadless, ChromeHeadlessNoSandbox]`. Karma launches
// every listed browser, and `ChromiumHeadless` fails on CI (not installed on the
// Windows runner; no usable sandbox on Ubuntu 24.04), failing the whole test task.
//
// This file is appended after the generated karma config, so `config.set` here
// overwrites the browser list with exactly one launcher: Chrome headless with
// `--no-sandbox` (Chrome ships on every GitHub runner; the flag clears the
// Ubuntu 24.04 AppArmor restriction).
config.set({
    browsers: ['ChromeHeadlessNoSandbox'],
    customLaunchers: {
        ChromeHeadlessNoSandbox: {
            base: 'ChromeHeadless',
            flags: ['--no-sandbox'],
        },
    },
});
