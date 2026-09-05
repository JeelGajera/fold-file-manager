package com.jeelgajera.fold.core.storage.model

/** A live filesystem event, as reported by the provider's observer. */
sealed interface FsChange {
    val path: FsPath

    data class Created(override val path: FsPath) : FsChange
    data class Deleted(override val path: FsPath) : FsChange
    data class Modified(override val path: FsPath) : FsChange
    data class MovedFrom(override val path: FsPath) : FsChange
    data class MovedTo(override val path: FsPath) : FsChange

    /**
     * The observer lost track and the caller should re-list.
     *
     * FileObserver drops events under load and stops entirely when the watched
     * directory is deleted, so a provider that cannot guarantee delivery says so
     * rather than letting the UI drift.
     */
    data class Overflow(override val path: FsPath) : FsChange
}
