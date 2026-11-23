package io.legado.app.data.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import io.legado.app.data.entities.ReadAloudAudioDownload
import kotlinx.coroutines.flow.Flow

/** 听书音频下载记录（文件本身在 filesDir/readAloudAudio 下，这里只记清单）。 */
@Dao
interface ReadAloudAudioDownloadDao {

    @Query("select * from read_aloud_audio_downloads where bookUrl = :bookUrl order by chapterIndex")
    fun flowByBook(bookUrl: String): Flow<List<ReadAloudAudioDownload>>

    @Query("select * from read_aloud_audio_downloads where bookUrl = :bookUrl order by chapterIndex")
    suspend fun byBook(bookUrl: String): List<ReadAloudAudioDownload>

    @Query("select * from read_aloud_audio_downloads where bookUrl = :bookUrl and chapterIndex = :chapterIndex")
    suspend fun get(bookUrl: String, chapterIndex: Int): ReadAloudAudioDownload?

    @Query("select * from read_aloud_audio_downloads order by createTime desc")
    suspend fun all(): List<ReadAloudAudioDownload>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(vararg downloads: ReadAloudAudioDownload)

    @Query("delete from read_aloud_audio_downloads where bookUrl = :bookUrl and chapterIndex = :chapterIndex")
    suspend fun delete(bookUrl: String, chapterIndex: Int)

    @Query("delete from read_aloud_audio_downloads where bookUrl = :bookUrl")
    suspend fun deleteByBook(bookUrl: String)

    @Query("delete from read_aloud_audio_downloads")
    suspend fun deleteAll()
}
