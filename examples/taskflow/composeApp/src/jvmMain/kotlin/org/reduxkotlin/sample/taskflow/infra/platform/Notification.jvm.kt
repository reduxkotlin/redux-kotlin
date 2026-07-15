package org.reduxkotlin.sample.taskflow.infra.platform

import org.reduxkotlin.concurrent.NotificationContext
import org.reduxkotlin.concurrent.coalescingNotificationContext
import javax.swing.SwingUtilities

/**
 * JVM (desktop) [mainNotificationContext]: runs subscriber callbacks on the Swing
 * Event Dispatch Thread.
 *
 * Runs an idle block inline on the EDT (avoids a needless re-post and keeps
 * subscribers synchronous with the dispatch); otherwise it joins the FIFO drain
 * marshaled via [SwingUtilities.invokeLater].
 *
 * @return a [NotificationContext] that runs callbacks on the EDT.
 */
public actual fun mainNotificationContext(): NotificationContext = coalescingNotificationContext(
    isOnTargetThread = { SwingUtilities.isEventDispatchThread() },
    post = { block -> SwingUtilities.invokeLater(block) },
)
