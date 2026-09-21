package com.geopedometer.di

import android.content.Context
import com.geopedometer.data.local.PedometerDatabase
import com.geopedometer.data.repository.StepRepositoryImpl
import com.geopedometer.domain.repository.StepRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PedometerBindingsModule {
    @Binds
    @Singleton
    abstract fun bindStepRepository(impl: StepRepositoryImpl): StepRepository
}

@Module
@InstallIn(SingletonComponent::class)
object PedometerProvidersModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PedometerDatabase {
        val passphrase = "geo-pedometer-secret-key-2025".toByteArray()
        return PedometerDatabase.getInstance(context, passphrase)
    }
}
