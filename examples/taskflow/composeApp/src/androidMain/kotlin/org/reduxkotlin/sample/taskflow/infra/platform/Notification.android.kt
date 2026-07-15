package org.reduxkotlin.sample.taskflow.infra.platform

import android.os.Handler
import android.os.Looper
import org.reduxkotlin.concurrent.NotificationContext
import org.reduxkotlin.concurrent.coalescingNotificationContext

/**
 * Android [mainNotificationContext]: runs callbacks inline when already on the main looper,
 * else joins its FIFO queue — avoiding both a stale frame and overtaking an
 * older worker notification.
 *
 * @return a [NotificationContext] backed by a main-[Looper] [Handler].
 */
public actual fun mainNotificationContext(): NotificationContext {
    val handler = Handler(Looper.getMainLooper())
    return coalescingNotificationContext(
        isOnTargetThread = { Looper.myLooper() == Looper.getMainLooper() },
        post = { block -> check(handler.post(block)) { "Main looper rejected notification delivery" } },
    )
}
