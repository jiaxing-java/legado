package io.legado.app.data

import androidx.room3.AutoMigration
import androidx.room3.ColumnTypeConverters
import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.TypeConverters
import io.legado.app.data.dao.BookChapterDao
import io.legado.app.data.dao.BookDao
import io.legado.app.data.dao.BookGroupDao
import io.legado.app.data.dao.BookSourceDao
import io.legado.app.data.dao.BookmarkDao
import io.legado.app.data.dao.CacheDao
import io.legado.app.data.dao.CastCharacterDao
import io.legado.app.data.dao.BgmPoolDao
import io.legado.app.data.dao.CloudTtsEngineDao
import io.legado.app.data.dao.CookieDao
import io.legado.app.data.dao.DictRuleDao
import io.legado.app.data.dao.ExactChapterPageCountDao
import io.legado.app.data.dao.HttpTTSDao
import io.legado.app.data.dao.KeyboardAssistsDao
import io.legado.app.data.dao.KeywordHighlightDao
import io.legado.app.data.dao.ReadAloudAudioDownloadDao
import io.legado.app.data.dao.ReadAloudVoiceDao
import io.legado.app.data.dao.ReadRecordDao
import io.legado.app.data.dao.RegexCastDao
import io.legado.app.data.dao.ReplaceRuleDao
import io.legado.app.data.dao.RuleSubDao
import io.legado.app.data.dao.SearchKeywordDao
import io.legado.app.data.dao.ServerDao
import io.legado.app.data.dao.SourceFilterRuleDao
import io.legado.app.data.dao.TxtTocRuleDao
import io.legado.app.data.dao.VoicePoolDao
import io.legado.app.data.entities.BgmPoolEntity
import io.legado.app.data.entities.BgmPoolGroupEntity
import io.legado.app.data.entities.BgmPoolMember
import io.legado.app.data.entities.BgmSceneMark
import io.legado.app.data.entities.BgmTrackEntity
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookCastMemory
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookGroup
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.BookVoiceBindingEntity
import io.legado.app.data.entities.Bookmark
import io.legado.app.data.entities.Cache
import io.legado.app.data.entities.CastCharacter
import io.legado.app.data.entities.ChapterRoleAssignment
import io.legado.app.data.entities.ChapterSpeechAnalysisEntity
import io.legado.app.data.entities.ChapterSpeechSegmentEntity
import io.legado.app.data.entities.CloudTtsEngineEntity
import io.legado.app.data.entities.Cookie
import io.legado.app.data.entities.DictRule
import io.legado.app.data.entities.ExactChapterPageCountEntity
import io.legado.app.data.entities.HttpTTS
import io.legado.app.data.entities.KeyboardAssist
import io.legado.app.data.entities.KeywordHighlight
import io.legado.app.data.entities.ReadAloudAudioDownload
import io.legado.app.data.entities.ReadAloudVoiceEntity
import io.legado.app.data.entities.ReadRecord
import io.legado.app.data.entities.RegexCastGroup
import io.legado.app.data.entities.RegexCastRule
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.data.entities.RuleSub
import io.legado.app.data.entities.SearchKeyword
import io.legado.app.data.entities.Server
import io.legado.app.data.entities.SourceFilterRule
import io.legado.app.data.entities.TxtTocRule
import io.legado.app.data.entities.VoiceEffectPreset
import io.legado.app.data.entities.VoicePoolEntity
import io.legado.app.data.entities.VoicePoolGroupEntity
import io.legado.app.data.entities.VoicePoolMember

/**
 * AppDatabase 主体下沉 commonMain。
 *
 * - @Database 注解 + abstract DAO 属性 + companion 常量 在 commonMain (平台无关)
 * - appDb 单例 + dbCallback 留 app 端 (依赖 appCtx + AndroidSQLiteConnection + Locale.CHINESE + DefaultData)
 * - DatabaseMigrations (80..83 手写 Migration) 在 commonMain, 各端 actual 只提供 Migration 子类
 *   (官方 room3 的 migrate 是 suspend, 鸿蒙 CPF fork 不是)
 */
@Database(
    version = 90,
    exportSchema = true,
    entities = [Book::class, BookGroup::class, BookSource::class, BookChapter::class,
        ReplaceRule::class, SearchKeyword::class, Cookie::class,
        Bookmark::class, TxtTocRule::class, ReadRecord::class,
        HttpTTS::class, Cache::class,
        RuleSub::class, DictRule::class, KeyboardAssist::class, Server::class,
        SourceFilterRule::class, KeywordHighlight::class,
        // ---- v90 新增: 多角色朗读 / 云 TTS / 背景音乐 / 分页计数 ----
        // 表名与字段刻意与 MD3 保持一致, 便于将来备份互操作 (决策 D2)
        CastCharacter::class, ChapterRoleAssignment::class, BookCastMemory::class,
        VoicePoolGroupEntity::class, VoicePoolEntity::class, VoicePoolMember::class,
        VoiceEffectPreset::class,
        ReadAloudVoiceEntity::class, BookVoiceBindingEntity::class,
        ChapterSpeechAnalysisEntity::class, ChapterSpeechSegmentEntity::class,
        RegexCastGroup::class, RegexCastRule::class,
        BgmPoolGroupEntity::class, BgmPoolEntity::class, BgmPoolMember::class,
        BgmTrackEntity::class, BgmSceneMark::class,
        ReadAloudAudioDownload::class, CloudTtsEngineEntity::class,
        ExactChapterPageCountEntity::class],
    views = [BookSourcePart::class],
    autoMigrations = [
        AutoMigration(from = 83, to = 84),
        AutoMigration(from = 84, to = 85, spec = Migration84To85::class),
        AutoMigration(from = 85, to = 86),
        AutoMigration(from = 86, to = 87),
        AutoMigration(from = 87, to = 88),
        AutoMigration(from = 88, to = 89),
        // 89 -> 90 纯增表 (21 张), 无列变更, 可由 Room 自动生成 CREATE TABLE
        AutoMigration(from = 89, to = 90),
    ]
)
// DATABASE 作用域注册 Book.Converters: iOS/ohos KSP 处理 BookChapter.ForeignKey 跨实体解析时,
// 需在 Database 级可见 ReadConfig <-> String 转换链 (与 Book 实体上的 ENTITY 作用域双重注册,
// 详见 Book.kt 顶部注释)。缺此注册会导致 iOS/ohos KSP 报 ReadConfig 类型链解析失败。
// 鸿蒙 fork (alpha01 API) KSP 只认 TypeConverters 旧名, 与 ColumnTypeConverters 并存双注册。
@ColumnTypeConverters(Book.Converters::class)
// 旧名同步注册: 缺此注册时 fork KSP 看不到 DATABASE 作用域转换链, DAO 里以 ReadConfig 作
// @Query 参数的 PATCH 方法 (如 updateReadConfig) 会报"参数类型无法转换为数据库列"
@TypeConverters(Book.Converters::class)
// 非 Android 平台 (iOS/desktop/鸿蒙) Room3 要求显式 @ConstructedBy 提供实例工厂
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {

    abstract val bookDao: BookDao
    abstract val bookGroupDao: BookGroupDao
    abstract val bookSourceDao: BookSourceDao
    abstract val bookChapterDao: BookChapterDao
    abstract val replaceRuleDao: ReplaceRuleDao
    abstract val searchKeywordDao: SearchKeywordDao
    abstract val bookmarkDao: BookmarkDao
    abstract val cookieDao: CookieDao
    abstract val txtTocRuleDao: TxtTocRuleDao
    abstract val readRecordDao: ReadRecordDao
    abstract val httpTTSDao: HttpTTSDao
    abstract val cacheDao: CacheDao
    abstract val ruleSubDao: RuleSubDao
    abstract val dictRuleDao: DictRuleDao
    abstract val keyboardAssistsDao: KeyboardAssistsDao
    abstract val keywordHighlightDao: KeywordHighlightDao
    abstract val serverDao: ServerDao
    abstract val sourceFilterRuleDao: SourceFilterRuleDao

    // ---- v90 新增 ----
    abstract val castCharacterDao: CastCharacterDao
    abstract val voicePoolDao: VoicePoolDao
    abstract val readAloudVoiceDao: ReadAloudVoiceDao
    abstract val bgmPoolDao: BgmPoolDao
    abstract val cloudTtsEngineDao: CloudTtsEngineDao
    abstract val readAloudAudioDownloadDao: ReadAloudAudioDownloadDao
    abstract val exactChapterPageCountDao: ExactChapterPageCountDao
    abstract val regexCastDao: RegexCastDao

    companion object {

        const val DATABASE_NAME = "legado.db"

        const val BOOK_TABLE_NAME = "books"
        const val BOOK_SOURCE_TABLE_NAME = "book_sources"
    }

}

// Room3 编译期生成 actual (KSP expect/actual 约定), 各平台源集无需手写
@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
