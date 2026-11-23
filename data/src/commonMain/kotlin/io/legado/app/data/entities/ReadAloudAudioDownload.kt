package io.legado.app.data.entities

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import kotlinx.serialization.Serializable
import io.legado.app.utils.systemCurrentTimeMillis

/**
 * 一章的听书音频下载记录：音频文件本身落在 `filesDir/readAloudAudio/<书的 md5>/` 下，
 * 文件名的算法与朗读缓存完全一致（引擎 + 语速 + 句子文本的 md5），所以朗读时只要在这两个
 * 目录里找到同名文件就直接播，不再联网合成。
 *
 * [files] 是这一章用到的文件名清单（逗号分隔）。文件名是内容哈希，反推不出「哪一章产出了
 * 哪些文件」，不记下来就没法把一章干净地删掉；顺带它就是「这一章下载了几句」。
 */
@Serializable
@Entity(
    tableName = "read_aloud_audio_downloads",
    primaryKeys = ["bookUrl", "chapterIndex"],
)
data class ReadAloudAudioDownload(
    var bookUrl: String = "",
    var chapterIndex: Int = 0,
    /** 章节标题，列表里显示用。 */
    var title: String = "",
    /** 这一章下载到的音频文件名，逗号分隔。 */
    var files: String = "",
    var createTime: Long = 0,
    /** 旧版本未记录应下载的句数，保留文件供播放和续下。 */
    var expectedSentenceCount: Int? = null,
) {
    val sentenceCount: Int get() = if (files.isBlank()) 0 else files.split(',').size

    val isComplete: Boolean
        get() = sentenceCount > 0 && sentenceCount == expectedSentenceCount

    /** 这一章下载到的音频文件名（不含扩展名）。 */
    val fileNames: List<String> get() = files.split(',').filter { it.isNotBlank() }
}

/**
 * 云 TTS 引擎配置行：一家云服务商的一条连接配置（可配多条，比如同一家两个 region）。
 *
 * provider 决定走哪个实现（azure/openai/volcengine/...），baseUrl 供自建/代理端点覆盖，
 * optionsJson 存放该 provider 特有的参数（音色列表、风格、采样率等），避免为每家加列。
 */
@Serializable
@Entity(
    tableName = "cloud_tts_engines",
    primaryKeys = ["id"],
    indices = [Index(value = ["provider", "enabled"])],
)
data class CloudTtsEngineEntity(
    val id: String,
    val name: String,
    val provider: String,
    @ColumnInfo(defaultValue = "")
    val baseUrl: String = "",
    @ColumnInfo(defaultValue = "")
    val apiKey: String = "",
    @ColumnInfo(defaultValue = "")
    val secretKey: String = "",
    @ColumnInfo(defaultValue = "")
    val region: String = "",
    @ColumnInfo(defaultValue = "")
    val appId: String = "",
    @ColumnInfo(defaultValue = "")
    val model: String = "",
    @ColumnInfo(defaultValue = "{}")
    val optionsJson: String = "{}",
    @ColumnInfo(defaultValue = "1")
    val enabled: Boolean = true,
    val createdAt: Long = systemCurrentTimeMillis(),
    val updatedAt: Long = systemCurrentTimeMillis(),
)
