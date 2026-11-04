package com.tripmate.ai.di

import android.content.Context
import androidx.room.Room
import com.tripmate.ai.data.db.TripMateDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): TripMateDatabase =
        Room.databaseBuilder(context, TripMateDatabase::class.java, "tripmate.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
}
