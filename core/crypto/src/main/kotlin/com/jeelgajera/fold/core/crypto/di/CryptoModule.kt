package com.jeelgajera.fold.core.crypto.di

import android.content.Context
import com.jeelgajera.fold.core.crypto.BiometricGate
import com.jeelgajera.fold.core.crypto.VaultRepository
import com.jeelgajera.fold.core.storage.di.IoDispatcher
import com.jeelgajera.fold.core.storage.provider.FileSystemProviderFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CryptoModule {

    @Provides
    @Singleton
    fun biometricGate(): BiometricGate = BiometricGate()

    @Provides
    @Singleton
    fun vaultRepository(
        @ApplicationContext context: Context,
        factory: FileSystemProviderFactory,
        @IoDispatcher io: CoroutineDispatcher,
    ): VaultRepository = VaultRepository(context, factory.current, io)
}
