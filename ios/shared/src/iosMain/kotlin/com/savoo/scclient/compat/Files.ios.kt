package com.savoo.scclient.compat

import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.timeIntervalSince1970

actual fun fileLastModified(path: String): Long {
    val attrs = NSFileManager.defaultManager.attributesOfItemAtPath(path, null) ?: return 0L
    val date = attrs[NSFileModificationDate] as? NSDate ?: return 0L
    return (date.timeIntervalSince1970 * 1000).toLong()
}
