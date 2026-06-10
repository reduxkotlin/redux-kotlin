package org.reduxkotlin.sample.taskflow.infra.platform

import org.reduxkotlin.concurrent.NotificationContext
import org.reduxkotlin.concurrent.coalescingNotificationContext
import platform.Foundation.NSThread
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * iOS [mainNotificationContext]: runs callbacks inline when already on the main thread,
 * else dispatches asynchronously onto the main queue — avoids a stale frame for
 * main-thread dispatches.
 *
 * @return a [NotificationContext] backed by the main `dispatch_queue`.
 */
public actual fun mainNotificationContext(): NotificationContext = coalescingNotificationContext(
    isOnTargetThread = { NSThread.isMainThread() },
    post = { block -> dispatch_async(dispatch_get_main_queue()) { block() } },
)
