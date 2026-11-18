package com.btcswing.simulator.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TradeEntity::class, DecisionEntity::class, CandleEntity::class,
        PortfolioEntity::class, EquityEntity::class, ReportEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tradeDao(): TradeDao
    abstract fun decisionDao(): DecisionDao
    abstract fun candleDao(): CandleDao
    abstract fun portfolioDao(): PortfolioDao
    abstract fun equityDao(): EquityDao
    abstract fun reportDao(): ReportDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "btc_swing.db"
            ).fallbackToDestructiveMigration().build().also { instance = it }
        }
    }
}
