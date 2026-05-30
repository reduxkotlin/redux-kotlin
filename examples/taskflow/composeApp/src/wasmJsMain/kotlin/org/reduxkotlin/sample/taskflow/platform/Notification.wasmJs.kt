package org.reduxkotlin.sample.taskflow.platform

import org.reduxkotlin.concurrent.NotificationContext

/**
 * wasmJs [mainNotificationContext]: the browser runtime is single-threaded, so dispatches and
 * subscriber callbacks already share the one (UI) thread. Runs the block inline, equivalent to
 * [NotificationContext.Inline].
 *
 * @return a [NotificationContext] that runs callbacks inline on the single JS thread.
 */
public actual fun mainNotificationContext(): NotificationContext = NotificationContext { block -> block() }
