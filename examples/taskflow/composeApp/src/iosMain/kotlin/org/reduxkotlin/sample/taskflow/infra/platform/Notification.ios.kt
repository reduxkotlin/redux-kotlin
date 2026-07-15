package org.reduxkotlin.sample.taskflow.infra.platform

import org.reduxkotlin.concurrent.NotificationContext
import org.reduxkotlin.concurrent.coalescingNotificationContext
import platform.Foundation.NSThread
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * iOS [mainNotificationContext]: runs an idle callback inline on the main thread;
 * otherwise it joins the FIFO main-queue drain, avoiding a stale frame without
 * overtaking older worker notifications.
 *
 * @return a [NotificationContext] backed by the main `dispatch_queue`.
 */
public actual fun mainNotificationContext(): NotificationContext = coalescingNotificationContext(
    isOnTargetThread = { NSThread.isMainThread() },
    post = { block -> dispatch_async(dispatch_get_main_queue()) { block() } },
)
