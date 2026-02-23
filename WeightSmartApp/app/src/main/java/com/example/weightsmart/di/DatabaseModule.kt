package com.example.weightsmart.di

import android.content.Context
import androidx.room.Room
import com.example.weightsmart.data.AppDatabase
import com.example.weightsmart.data.dao.GoalWeightDao
import com.example.weightsmart.data.dao.UserDao
import com.example.weightsmart.data.dao.WeightDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDb(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "weightsmart.db")
            // For development
            //.fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideUserDao(db: AppDatabase): UserDao = db.userDao()

    @Provides
    fun provideWeightDao(db: AppDatabase): WeightDao = db.weightDao()

    @Provides
    fun provideGoalDao(db: AppDatabase): GoalWeightDao = db.goalWeightDao()
}
