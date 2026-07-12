package io.janda.vorwahlguard.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.janda.vorwahlguard.data.db.VorwahlGuardDatabase
import io.janda.vorwahlguard.data.events.CallEventDao
import io.janda.vorwahlguard.data.rules.RuleDao
import javax.inject.Singleton

/**
 * Provides the Room database and its DAOs. No `fallbackToDestructiveMigration()` — a schema
 * change without a real migration must fail loudly in development, not silently drop the
 * user's rules and call history in production.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): VorwahlGuardDatabase =
        Room.databaseBuilder(context, VorwahlGuardDatabase::class.java, "vorwahlguard.db")
            .build()

    @Provides
    @Singleton
    fun provideRuleDao(database: VorwahlGuardDatabase): RuleDao = database.ruleDao()

    @Provides
    @Singleton
    fun provideCallEventDao(database: VorwahlGuardDatabase): CallEventDao = database.callEventDao()
}
