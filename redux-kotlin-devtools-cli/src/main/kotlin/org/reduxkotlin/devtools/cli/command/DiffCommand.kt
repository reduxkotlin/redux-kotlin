package org.reduxkotlin.devtools.cli.command

import org.reduxkotlin.devtools.cli.capture.Format

/** `diff` — alias for `actions --format diff`. */
internal class DiffCommand : ActionsCommand(forced = Format.DIFF)
