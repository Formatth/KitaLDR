package com.formatth.kitaldr

import android.app.Activity
import android.os.Build

/** Compatibility bridge for requesting runtime permissions from Compose code. */
private object ActivityResolver {
    fun current(): Activity? {
        return try {
            val threadClass = Class.forName("android.app.ActivityThread")
            val thread = threadClass.getMethod("currentActivityThread").invoke(null)
            val field = threadClass.getDeclaredField("mActivities").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val activities = field.get(thread) as? Map<Any, Any> ?: return null
            activities.values.asSequence().mapNotNull { record ->
                runCatching {
                    val activityField = record.javaClass.getDeclaredField("activity").apply { isAccessible = true }
                    activityField.get(record) as? Activity
                }.getOrNull()
            }.firstOrNull { activity ->
                runCatching {
                    val resumedField = activity.javaClass.getDeclaredField("mResumed").apply { isAccessible = true }
                    resumedField.getBoolean(activity)
                }.getOrDefault(!activity.isFinishing)
            }
        } catch (_: Throwable) {
            null
        }
    }
}

@Suppress("UNUSED_PARAMETER")
fun requestPermissions(permissions: Array<String>, requestCode: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        ActivityResolver.current()?.requestPermissions(permissions, requestCode)
    }
}
