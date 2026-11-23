package io.legado.app.data.dao

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import io.legado.app.data.entities.CloudTtsEngineEntity
import kotlinx.coroutines.flow.Flow

/** 云 TTS 引擎配置（一家服务商可多条配置）。 */
@Dao
interface CloudTtsEngineDao {

    @Query("select * from cloud_tts_engines order by name")
    fun flowAll(): Flow<List<CloudTtsEngineEntity>>

    @Query("select * from cloud_tts_engines where enabled = 1 order by name")
    suspend fun enabled(): List<CloudTtsEngineEntity>

    @Query("select * from cloud_tts_engines order by name")
    suspend fun all(): List<CloudTtsEngineEntity>

    @Query("select * from cloud_tts_engines where id = :id")
    suspend fun get(id: String): CloudTtsEngineEntity?

    @Query("select count(*) from cloud_tts_engines")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(vararg engines: CloudTtsEngineEntity)

    @Update
    suspend fun update(vararg engines: CloudTtsEngineEntity)

    @Delete
    suspend fun delete(engine: CloudTtsEngineEntity)
}
