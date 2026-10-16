package com.hakiosk

import android.content.Context

/** Reads the version from the package manager so we don't need the BuildConfig feature. */
object BuildConfigInfo {
    fun versionName(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: ""
}
