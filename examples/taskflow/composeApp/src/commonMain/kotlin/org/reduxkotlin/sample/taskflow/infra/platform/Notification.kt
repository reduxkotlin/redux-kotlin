package org.reduxkotlin.sample.taskflow.infra.platform

import org.reduxkotlin.concurrent.NotificationContext

/**
 * Builds the platform [NotificationContext] that marshals store-subscriber callbacks
 * to the UI main thread.
 *
 * The concurrent store invokes listeners on the dispatching thread by default
 * ([NotificationContext.Inline]); since dispatches may originate off-main (e.g. from
 * effect coroutines on a background dispatcher), each `actual` posts the callback onto
 * the platform's main/UI thread so Compose state reads never run off-main.
 *
 * @return a [NotificationContext] that runs subscriber callbacks on the main thread.
 */
public expect fun mainNotificationContext(): NotificationContext
