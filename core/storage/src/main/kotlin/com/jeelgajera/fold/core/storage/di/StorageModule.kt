package com.jeelgajera.fold.core.storage.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.room.Room
import com.jeelgajera.fold.core.storage.index.ContentSearcher
import com.jeelgajera.fold.core.storage.index.FileIndexDao
import com.jeelgajera.fold.core.storage.index.FileIndexer
import com.jeelgajera.fold.core.storage.index.FoldDatabase
import com.jeelgajera.fold.core.storage.prefs.FoldSettings
import com.jeelgajera.fold.core.storage.prefs.FoldSettingsSerializer
import com.jeelgajera.fold.core.storage.provider.FileSystemProviderFactory
import com.jeelgajera.fold.core.storage.provider.VaultLocations
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import javax.inject.Qualifier
import javax.inject.Singleton

/** The dispatcher every blocking filesystem call is confined to. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** A process-lifetime scope for work that outlives any one screen. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {

    @Provides
    @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(@IoDispatcher io: CoroutineDispatcher): CoroutineScope =
        CoroutineScope(SupervisorJob() + io)

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): FoldDatabase =
        Room.databaseBuilder(context, FoldDatabase::class.java, FoldDatabase.NAME)
            // The index is a cache of the filesystem, not a source of truth. If a
            // schema change makes the old rows unreadable, throwing them away and
            // re-walking is strictly better than shipping migration code that has
            // to be right about data the device can regenerate for free.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun fileIndexDao(database: FoldDatabase): FileIndexDao = database.fileIndexDao()

    @Provides
    @Singleton
    fun settingsStore(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
    ): DataStore<FoldSettings> = DataStoreFactory.create(
        serializer = FoldSettingsSerializer,
        corruptionHandler = ReplaceFileCorruptionHandler { FoldSettings() },
        scope = scope,
        produceFile = { File(context.filesDir, FoldSettingsSerializer.FILE_NAME) },
    )

    @Provides
    @Singleton
    fun providerFactory(
        @ApplicationContext context: Context,
        @IoDispatcher io: CoroutineDispatcher,
    ): FileSystemProviderFactory {
        // Create the vault's directory and its .nomedia marker before anything can
        // ask PathGuard about them: a denied root that does not exist yet cannot
        // be canonicalised, and would silently drop out of the deny list.
        VaultLocations.ensureNoMedia(context)
        return FileSystemProviderFactory(context, io)
    }

    @Provides
    @Singleton
    fun fileIndexer(
        dao: FileIndexDao,
        factory: FileSystemProviderFactory,
        @IoDispatcher io: CoroutineDispatcher,
    ): FileIndexer = FileIndexer(dao, factory.guard(), io)

    @Provides
    fun contentSearcher(
        factory: FileSystemProviderFactory,
        @IoDispatcher io: CoroutineDispatcher,
    ): ContentSearcher = ContentSearcher(factory.current, io)
}
