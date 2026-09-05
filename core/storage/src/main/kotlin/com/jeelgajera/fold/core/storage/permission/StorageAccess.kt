package com.jeelgajera.fold.core.storage.permission

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings

/** Which storage mode FOLD is running in right now. */
enum class StorageAccessLevel {
    /** MANAGE_EXTERNAL_STORAGE granted. Every file on the device is visible. */
    ALL_FILES,

    /** One or more SAF tree grants. FOLD works inside them and nowhere else. */
    LIMITED,

    /** Nothing granted yet. The onboarding screen is the only destination. */
    NONE,
}

/**
 * Where the permission flow starts and ends.
 *
 * The rule that matters here is a product one, not a technical one: FOLD never
 * asks cold. The system's All Files Access screen is only ever opened from behind
 * the rationale screen, after the user has read what the permission is for and
 * chosen to continue. The onboarding copy says "FOLD will not ask twice", and
 * `FoldSettings.allFilesAccessAsked` is what keeps that true.
 */
object StorageAccess {

    fun level(context: Context): StorageAccessLevel = when {
        hasAllFilesAccess() -> StorageAccessLevel.ALL_FILES
        grantedTreeCount(context) > 0 -> StorageAccessLevel.LIMITED
        else -> StorageAccessLevel.NONE
    }

    fun hasAllFilesAccess(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()

    fun grantedTreeCount(context: Context): Int =
        context.contentResolver.persistedUriPermissions.count { it.isReadPermission }

    /**
     * The system screen for All Files Access.
     *
     * The package-scoped action drops the user on FOLD's own row. Some OEM builds
     * do not handle it, so [allFilesAccessFallbackIntent] exists for the caller to
     * fall back to when this one cannot be resolved -- silently failing to open
     * anything after the user pressed "Allow" is the worst possible outcome here.
     */
    fun allFilesAccessIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )

    /** The device-wide list, for builds that do not honour the per-app intent. */
    fun allFilesAccessFallbackIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)

    /**
     * Whether either intent can actually be handled.
     *
     * Checked before the rationale screen offers the button, so FOLD does not
     * promise a screen the device cannot open.
     */
    fun canRequestAllFilesAccess(context: Context): Boolean {
        val manager = context.packageManager
        return allFilesAccessIntent(context).resolveActivity(manager) != null ||
            allFilesAccessFallbackIntent().resolveActivity(manager) != null
    }
}
