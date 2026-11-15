package com.karuhundeveloper.notification.aurapay.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AuraPayDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(record: AuraPayRecord): Long

    @Query("SELECT * FROM aurapay_records WHERE idempotencyKey = :key LIMIT 1")
    suspend fun getByKey(key: String): AuraPayRecord?

    @Query("UPDATE aurapay_records SET status = :status, matchedOrderId = :matchedOrderId, lastError = :error WHERE idempotencyKey = :key")
    suspend fun updateStatus(key: String, status: String, matchedOrderId: String? = null, error: String? = null)

    @Query("SELECT * FROM aurapay_records ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 15): Flow<List<AuraPayRecord>>

    @Query("SELECT * FROM aurapay_records ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<AuraPayRecord>>

    @Query("SELECT * FROM aurapay_records WHERE status = :status ORDER BY timestamp DESC")
    fun observeByStatus(status: String): Flow<List<AuraPayRecord>>

    @Query("SELECT * FROM aurapay_records WHERE status = 'LOCAL_QUEUE' ORDER BY timestamp ASC")
    suspend fun getPendingQueue(): List<AuraPayRecord>

    @Query("SELECT COUNT(*) FROM aurapay_records")
    fun countTotal(): Flow<Int>

    @Query("SELECT COUNT(*) FROM aurapay_records WHERE status = 'MATCHED_CREDITED'")
    fun countMatched(): Flow<Int>

    @Query("SELECT COUNT(*) FROM aurapay_records WHERE status = 'UNMATCHED_ORPHAN'")
    fun countOrphans(): Flow<Int>

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM aurapay_records WHERE timestamp >= :startOfDay AND status IN ('MATCHED_CREDITED', 'UNMATCHED_ORPHAN')")
    fun observeTodayAmount(startOfDay: Long): Flow<Double>

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM aurapay_records WHERE status IN ('MATCHED_CREDITED', 'UNMATCHED_ORPHAN')")
    fun observeAllTimeAmount(): Flow<Double>
}
