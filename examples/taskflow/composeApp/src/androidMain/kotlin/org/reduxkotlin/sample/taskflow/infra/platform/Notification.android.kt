package org.reduxkotlin.sample.taskflow.infra.platform

import android.os.Handler
import android.os.Looper
import org.reduxkotlin.concurrent.NotificationContext
import org.reduxkotlin.concurrent.coalescingNotificationContext

/**
 * Android [mainNotificationContext]: runs callbacks inline when already on the main looper,
 * else posts — avoids a stale frame for main-thread dispatches.
 *
 * @return a [NotificationContext] backed by a main-[Looper] [Handler].
 */
public actual fun mainNotificationContext(): NotificationContext {
    val handler = Handler(Looper.getMainLooper())
    return coalescingNotificationContext(
        isOnTargetThread = { Looper.myLooper() == Looper.getMainLooper() },
        post = { block -> handler.post(block) },
    )
}
