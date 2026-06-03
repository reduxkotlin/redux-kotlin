package org.reduxkotlin.sample.taskflow.infra.platform

import android.os.Handler
import android.os.Looper
import org.reduxkotlin.concurrent.NotificationContext

/**
 * Android [mainNotificationContext]: posts subscriber callbacks to the main looper so they run
 * on the UI thread.
 *
 * @return a [NotificationContext] backed by a main-[Looper] [Handler].
 */
public actual fun mainNotificationContext(): NotificationContext {
    val handler = Handler(Looper.getMainLooper())
    return NotificationContext { block -> handler.post(block) }
}
