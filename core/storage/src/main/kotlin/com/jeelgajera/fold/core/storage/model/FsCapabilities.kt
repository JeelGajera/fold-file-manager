package com.jeelgajera.fold.core.storage.model

/**
 * What the currently-installed provider can actually do.
 *
 * The UI reads this rather than checking a permission flag, which is what makes
 * the reduced-permission screens a real mode instead of an error state. If Play
 * review rejects All Files Access -- or the policy tightens later -- FOLD keeps
 * working through SAF and the affected controls simply stop being offered.
 */
data class FsCapabilities(
    /** Can the provider list any path on the device, or only granted subtrees? */
    val canListArbitraryPaths: Boolean,
    val canWrite: Boolean,
    val canDelete: Boolean,
    val canMove: Boolean,
    /** Can changes be observed live, or does the app rely on periodic reconciliation? */
    val canObserve: Boolean,
    /** Can the whole tree be indexed for search, or only the granted roots? */
    val canIndexWholeDevice: Boolean,
    /** Roots the user can browse right now. In SAF mode, exactly the granted trees. */
    val roots: List<FsRoot>,
) {
    val isLimited: Boolean get() = !canListArbitraryPaths
}

/** A browsable top-level location. */
data class FsRoot(
    val path: FsPath,
    val label: String,
    val isPrimary: Boolean,
    val isRemovable: Boolean = false,
)
