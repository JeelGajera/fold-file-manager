package com.jeelgajera.fold.feature.transfer.di

import android.content.Context
import com.jeelgajera.fold.core.storage.di.ApplicationScope
import com.jeelgajera.fold.core.storage.prefs.SettingsRepository
import com.jeelgajera.fold.core.storage.provider.FileSystemProviderFactory
import com.jeelgajera.fold.feature.glyph.GlyphController
import com.jeelgajera.fold.feature.transfer.TransferRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TransferModule {

    /**
     * One repository for the process.
     *
     * The foreground service, the share screens and the home-screen widget all
     * read the same instance -- three views of one server, not three servers.
     */
    @Provides
    @Singleton
    fun transferRepository(
        @ApplicationContext context: Context,
        providerFactory: FileSystemProviderFactory,
        settings: SettingsRepository,
        glyph: GlyphController,
        @ApplicationScope scope: CoroutineScope,
    ): TransferRepository = TransferRepository(context, providerFactory, settings, glyph, scope)
}
