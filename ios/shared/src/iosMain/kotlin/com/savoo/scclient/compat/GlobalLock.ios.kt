package com.savoo.scclient.compat

import platform.Foundation.NSRecursiveLock

actual object GlobalLock {
    private val lock = NSRecursiveLock()
    actual fun lock() = lock.lock()
    actual fun unlock() = lock.unlock()
}
