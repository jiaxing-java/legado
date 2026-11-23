package io.legado.app.data.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import io.legado.app.data.entities.BookVoiceBindingEntity
import io.legado.app.data.entities.ChapterSpeechAnalysisEntity
import io.legado.app.data.entities.ChapterSpeechSegmentEntity
import io.legado.app.data.entities.ReadAloudVoiceEntity
import kotlinx.coroutines.flow.Flow

/**
 * 朗读音色目录、书→音色绑定、章节朗读切分结果。
 *
 * 音色行由引擎同步（[SyncReadAloudVoicesUseCase] 对应物）以 REPLACE 重建，
 * 所以池成员关系放在 [VoicePoolDao] 的独立表里，不随本表重建而丢。
 */
@Dao
interface ReadAloudVoiceDao {

    // ---- 音色目录 ----

    @Query("select * from read_aloud_voices where enabled = 1 and excluded = 0 order by displayName")
    fun flowAvailable(): Flow<List<ReadAloudVoiceEntity>>

    @Query("select * from read_aloud_voices where engineType = :engineType and engineId = :engineId and enabled = 1 and excluded = 0 order by displayName")
    suspend fun byEngine(engineType: String, engineId: String): List<ReadAloudVoiceEntity>

    @Query("select * from read_aloud_voices where id = :id")
    suspend fun get(id: String): ReadAloudVoiceEntity?

    @Query("select * from read_aloud_voices where engineType = :engineType and engineId = :engineId and speakerId = :speakerId limit 1")
    suspend fun find(engineType: String, engineId: String, speakerId: String): ReadAloudVoiceEntity?

    @Query("select * from read_aloud_voices")
    suspend fun all(): List<ReadAloudVoiceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(vararg voices: ReadAloudVoiceEntity)

    @Query("update read_aloud_voices set excluded = :excluded, updatedAt = :updatedAt where id = :id")
    suspend fun setExcluded(id: String, excluded: Boolean, updatedAt: Long)

    @Query("delete from read_aloud_voices where engineType = :engineType and engineId = :engineId and managedBy = 'engine'")
    suspend fun deleteEngineManaged(engineType: String, engineId: String)

    // ---- 书 → 主体音色绑定 ----

    @Query("select * from book_voice_bindings where bookUrl = :bookUrl")
    suspend fun bindings(bookUrl: String): List<BookVoiceBindingEntity>

    @Query("select * from book_voice_bindings where bookUrl = :bookUrl and subjectType = :subjectType and subjectId = :subjectId")
    suspend fun binding(bookUrl: String, subjectType: String, subjectId: String): BookVoiceBindingEntity?

    @Query("select * from book_voice_bindings")
    suspend fun allBindings(): List<BookVoiceBindingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBindings(vararg bindings: BookVoiceBindingEntity)

    @Query("delete from book_voice_bindings where bookUrl = :bookUrl")
    suspend fun deleteBindings(bookUrl: String)

    // ---- 章节朗读分析 ----

    @Query("select * from chapter_speech_analysis where bookUrl = :bookUrl and chapterIndex = :chapterIndex and contentHash = :contentHash and resolverVersion = :resolverVersion")
    suspend fun analysis(bookUrl: String, chapterIndex: Int, contentHash: String, resolverVersion: String): ChapterSpeechAnalysisEntity?

    @Query("select * from chapter_speech_analysis where id = :id")
    suspend fun analysisById(id: String): ChapterSpeechAnalysisEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAnalysis(analysis: ChapterSpeechAnalysisEntity)

    @Query("select * from chapter_speech_segments where analysisId = :analysisId order by chapterPosition")
    suspend fun segments(analysisId: String): List<ChapterSpeechSegmentEntity>

    @Query("select * from chapter_speech_segments where bookUrl = :bookUrl and chapterIndex = :chapterIndex order by chapterPosition")
    suspend fun segmentsOfChapter(bookUrl: String, chapterIndex: Int): List<ChapterSpeechSegmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSegments(vararg segments: ChapterSpeechSegmentEntity)

    @Query("delete from chapter_speech_segments where analysisId = :analysisId")
    suspend fun deleteSegments(analysisId: String)

    @Query("delete from chapter_speech_analysis where bookUrl = :bookUrl")
    suspend fun deleteAnalysisByBook(bookUrl: String)

    @Query("delete from chapter_speech_segments where bookUrl = :bookUrl")
    suspend fun deleteSegmentsByBook(bookUrl: String)
}
