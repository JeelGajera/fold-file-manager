package com.jeelgajera.fold.feature.glyph.di

import android.content.Context
import com.jeelgajera.fold.core.storage.di.ApplicationScope
import com.jeelgajera.fold.feature.glyph.GlyphController
import com.jeelgajera.fold.feature.glyph.GlyphControllerFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object GlyphModule {

    /**
     * One controller for the process.
     *
     * Glyph hardware does not appear or disappear at runtime, so this is resolved
     * once. On every device without it the binding is `NoOpGlyphController`, which
     * is why nothing downstream needs a null check or a capability branch.
     */
    @Provides
    @Singleton
    fun glyphController(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
    ): GlyphController = GlyphControllerFactory.create(context, scope)
}
