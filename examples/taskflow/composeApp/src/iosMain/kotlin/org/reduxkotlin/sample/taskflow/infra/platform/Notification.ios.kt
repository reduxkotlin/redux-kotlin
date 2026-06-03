package org.reduxkotlin.sample.taskflow.infra.platform

import org.reduxkotlin.concurrent.NotificationContext
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * iOS [mainNotificationContext]: dispatches subscriber callbacks asynchronously onto the
 * main dispatch queue so they run on the UI thread.
 *
 * @return a [NotificationContext] backed by the main `dispatch_queue`.
 */
public actual fun mainNotificationContext(): NotificationContext = NotificationContext { block ->
    dispatch_async(dispatch_get_main_queue()) { block() }
}
