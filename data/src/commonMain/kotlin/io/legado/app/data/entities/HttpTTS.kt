package io.legado.app.data.entities

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import io.legado.app.utils.systemCurrentTimeMillis
import com.script.jsdispatch.JsApi
import kotlinx.serialization.Serializable

/**
 * F2: HttpTTS 已下沉到 shared jvmAndAndroidMain, JS 可见的 JsExtensions 面由 JsExtensionsCommon
 * 接口默认实现承载, evalJS 直接绑定源实例自身。
 *
 * @JsApi: KSP 生成静态分派表 (app 端 jsapi.extraClasses 接线), JS 桥属性/方法访问先查表、miss 落反射。
 */
@Serializable
@Entity(tableName = "httpTTS")
@JsApi
data class HttpTTS(
    @PrimaryKey
    val id: Long = systemCurrentTimeMillis(),
    var name: String = "",
    var url: String = "",
    var contentType: String? = null,
    @ColumnInfo(defaultValue = "0")
    override var concurrentRate: String? = "0",
    override var loginUrl: String? = null,
    // loginUi 的 JSON 值可能是数组/对象, 需原样转字符串 (复刻原 GSON 全局 StringJsonDeserializer)
    @Serializable(with = RawJsonStringSerializer::class)
    override var loginUi: String? = null,
    override var header: String? = null,
    override var jsLib: String? = null,
    @ColumnInfo(defaultValue = "0")
    override var enabledCookieJar: Boolean? = false,
    @ColumnInfo(defaultValue = "0")
    override var enableDangerousApi: Boolean? = false,
    var loginCheckJs: String? = null,
    /**
     * 源级语速，0..80，实际倍速 = (speed + 5) / 10；null = 该源未声明，沿用全局语速。
     *
     * 与全局语速的分工：源自己声明的是"这个接口合成的天然语速"（各家接口对
     * `{{speakSpeed}}` 的解释不同，5 = 1.0 倍是源作者的标定值），
     * 用户的语速滑杆作用在播放层，两者不叠乘到同一个参数上。
     * 仅在源接口支持 `{{speakSpeed}}` 时影响合成结果。
     */
    var speed: Int? = null,
    /**
     * 源自己声明的合成并发上限，1..8。
     *
     * 外部源（含参考实现导出的 httpTTS.json）会带这个字段，导入时保留；
     * 实时、试听、预合成共用此源的配额（见 HttpTtsSynthesisLimiter）。
     */
    @ColumnInfo(defaultValue = "1")
    var synthesisThreadCount: Int = 1,
    @ColumnInfo(defaultValue = "0")
    var lastUpdateTime: Long = systemCurrentTimeMillis()
) : BaseSource {

    override fun getTag(): String {
        return name
    }

    override fun getKey(): String {
        return "httpTts:$id"
    }

    override fun getSourceType(): Int {
        return io.legado.app.constant.SourceType.tts
    }

    @Suppress("unused")
    companion object {

        /** 源级合成并发上限的合法上界（与朗读设置里全局并发的取值范围一致）。 */
        const val MAX_SYNTHESIS_THREAD_COUNT = 8
    }
}

