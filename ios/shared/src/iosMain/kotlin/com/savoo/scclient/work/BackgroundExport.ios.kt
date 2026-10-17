package com.savoo.scclient.work

import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSinceNow

actual object BackgroundExport {
    const val TASK_ID = "com.savoo.resona.autoexport"

    actual fun schedule(intervalHours: Long) {
        val request = BGAppRefreshTaskRequest(identifier = TASK_ID)
        request.earliestBeginDate = NSDate.dateWithTimeIntervalSinceNow(intervalHours * 3600.0)
        BGTaskScheduler.sharedScheduler.cancelTaskRequestWithIdentifier(TASK_ID)
        BGTaskScheduler.sharedScheduler.submitTaskRequest(request, null)
    }

    actual fun cancel() {
        BGTaskScheduler.sharedScheduler.cancelTaskRequestWithIdentifier(TASK_ID)
    }
}
