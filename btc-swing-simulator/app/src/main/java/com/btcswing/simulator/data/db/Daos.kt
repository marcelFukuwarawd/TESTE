package com.btcswing.simulator.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TradeDao {
    @Insert suspend fun insert(t: TradeEntity): Long
    @Query("SELECT * FROM trades ORDER BY time DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TradeEntity>>
    @Query("SELECT * FROM trades ORDER BY time DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<TradeEntity>
    @Query("SELECT * FROM trades WHERE time >= :since ORDER BY time ASC")
    suspend fun since(since: Long): List<TradeEntity>
    @Query("SELECT MAX(time) FROM trades") suspend fun lastTradeTime(): Long?
    @Query("DELETE FROM trades") suspend fun clear()
}

@Dao
interface DecisionDao {
    @Insert suspend fun insert(d: DecisionEntity): Long
    @Query("SELECT * FROM decisions ORDER BY time DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<DecisionEntity>>
    @Query("SELECT * FROM decisions ORDER BY time DESC LIMIT 1")
    suspend fun latest(): DecisionEntity?
    @Query("DELETE FROM decisions WHERE time < :before") suspend fun prune(before: Long)
    @Query("DELETE FROM decisions") suspend fun clear()
}

@Dao
interface CandleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(candles: List<CandleEntity>)
    @Query("SELECT * FROM candles WHERE symbol=:symbol AND timeframe=:tf ORDER BY openTime DESC LIMIT :limit")
    suspend fun latest(symbol: String, tf: String, limit: Int): List<CandleEntity>
    @Query("DELETE FROM candles WHERE symbol=:symbol AND timeframe=:tf AND openTime < :before")
    suspend fun prune(symbol: String, tf: String, before: Long)
    @Query("DELETE FROM candles") suspend fun clear()
}

@Dao
interface PortfolioDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(p: PortfolioEntity)
    @Query("SELECT * FROM portfolio WHERE id=1") suspend fun get(): PortfolioEntity?
    @Query("SELECT * FROM portfolio WHERE id=1") fun observe(): Flow<PortfolioEntity?>
    @Query("DELETE FROM portfolio") suspend fun clear()
}

@Dao
interface EquityDao {
    @Insert suspend fun insert(e: EquityEntity)
    @Query("SELECT * FROM equity_points WHERE time >= :since ORDER BY time ASC")
    suspend fun since(since: Long): List<EquityEntity>
    @Query("SELECT * FROM equity_points ORDER BY time DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<EquityEntity>>
    @Query("DELETE FROM equity_points") suspend fun clear()
}

@Dao
interface ReportDao {
    @Insert suspend fun insert(r: ReportEntity): Long
    @Query("SELECT * FROM reports ORDER BY generatedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ReportEntity>>
    @Query("SELECT * FROM reports ORDER BY generatedAt DESC LIMIT 1")
    suspend fun latest(): ReportEntity?
    @Query("DELETE FROM reports") suspend fun clear()
}
