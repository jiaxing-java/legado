package io.legado.app.help.tts

import io.legado.app.constant.AppConst
import io.legado.app.data.entities.HttpTTS
import io.legado.app.exception.NoStackTraceException
import io.legado.app.exception.RegexTimeoutException
import io.legado.app.help.http.KmpResponse
import io.legado.app.help.http.header
import io.legado.app.model.analyzeRule.AnalyzeUrlFactories
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.RuleRegexes
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.CoroutineContext

/**
 * HttpTTS 请求的纯参数拼装/响应校验/缓存 key/熔断计数,从 HttpReadAloudService 平移。
 * 网络执行(AnalyzeUrl)、JS 求值与文件 IO 留在 Service。
 */
object HttpTtsRequest {

    /** 网络朗读源读取超时(毫秒) */
    const val READ_TIMEOUT_MS = 300 * 1000L

    /** 期望 Content-Type 正则的单次匹配上限 (毫秒)。 */
    private const val CONTENT_TYPE_TIMEOUT = 3000L

    /**
     * 算出要注入 `{{speakSpeed}}` 的值。
     *
     * 源声明了语速就按源的标定值（(speed + 5)，10 = 1.0 倍）——源作者最清楚自家接口的
     * 自然语速；没声明才沿用全局语速，保证存量源导入后听感与升级前完全一致。
     *
     * 流式播放（[HttpTtsPlayer.setRequest]）也走这一个函数：两条路径算出的
     * speakSpeed 必须一致，否则"流式"与"下载后播"会听到两种语速。
     */
    fun resolveSpeakSpeed(config: HttpTTS, globalRate: Int): Int =
        config.speed?.let { (it + 5).coerceIn(0, 90) } ?: globalRate

    /** 注入 url 模板 JS 作用域的变量表 */
    fun speakVariables(
        speakText: String,
        speakSpeed: Int,
        speakVoice: String = "",
        speakEmotion: String = "",
    ): Map<AppConst.JsVarName, Any> {
        return mapOf(
            AppConst.JsVarName.SPEAK_TEXT to speakText,
            AppConst.JsVarName.SPEAK_SPEED to speakSpeed,
            // 多角色：源里写 {{speakVoice}} / {{speakEmotion}} 就会按角色换音色；
            // 单音色时两者为空串，源若没用这两个变量则完全无感
            AppConst.JsVarName.SPEAK_VOICE to speakVoice,
            AppConst.JsVarName.SPEAK_EMOTION to speakEmotion,
        )
    }

    /** Android 和 iOS 共用的音频请求：执行完整规则，而不是把原始 URL 交给播放器。 */
    suspend fun audioResponse(
        config: HttpTTS,
        text: String,
        speechRate: Int,
        context: CoroutineContext,
        factory: HttpTtsAnalyzeUrlFactory = HttpTtsAnalyzeUrlFactory { url, source, timeout, ctx, variables ->
            AnalyzeUrlFactories.create(
                url, source = source, readTimeout = timeout,
                coroutineContext = ctx, variables = variables,
            )
        },
        /** 该段说话人（`SpeechVoiceRef.speakerId`）；空 = 源里的 `{{speakVoice}}` 得到空串。 */
        speakVoice: String = "",
        /** 该段情绪；空 = 源里的 `{{speakEmotion}}` 得到空串。 */
        speakEmotion: String = "",
    ): KmpResponse {
        val speakSpeed = resolveSpeakSpeed(config, speechRate)
        // 同一源的实时/试听/预合成共用一个配额，避免多路并发把上游打出 429。
        return HttpTtsSynthesisLimiter.withQuota(
            config.id.toString(),
            config.synthesisThreadCount,
        ) {
            doAudioResponse(config, text, speakSpeed, context, factory, speakVoice, speakEmotion)
        }
    }

    private suspend fun doAudioResponse(
        config: HttpTTS,
        text: String,
        speakSpeed: Int,
        context: CoroutineContext,
        factory: HttpTtsAnalyzeUrlFactory,
        speakVoice: String = "",
        speakEmotion: String = "",
    ): KmpResponse {
        val request = factory.create(
            config.url, config, READ_TIMEOUT_MS, context,
            speakVariables(text, speakSpeed, speakVoice, speakEmotion),
        )
        var response = request.getResponseAwait()
        try {
            context.ensureActive()
            config.loginCheckJs?.takeIf { it.isNotBlank() }?.let {
                val checked = request.evalJS(it, response) as KmpResponse
                response = checked
            }
            if (response.code !in 200..299) {
                throw NoStackTraceException(
                    "TTS服务器返回 HTTP ${response.code}：${response.body.string().take(500)}",
                )
            }
            when (checkContentType(response.header("Content-Type"), config.contentType)) {
                ContentTypeVerdict.ERROR_BODY -> throw NoStackTraceException(response.body.string())
                ContentTypeVerdict.ERROR_MISMATCH ->
                    throw NoStackTraceException("TTS服务器返回错误：" + response.body.string())
                ContentTypeVerdict.OK -> Unit
            }
            context.ensureActive()
            return response
        } catch (error: Throwable) {
            response.close()
            throw error
        }
    }

    /** Content-Type 校验结果 */
    enum class ContentTypeVerdict {
        OK,

        /** json/text 响应,响应体即错误信息 */
        ERROR_BODY,

        /** 与源配置的期望 Content-Type 正则不匹配 */
        ERROR_MISMATCH,
    }

    /**
     * 校验响应 Content-Type: json/text 一律视为错误体;源配置了期望正则时需匹配。
     */
    fun checkContentType(header: String?, expected: String?): ContentTypeVerdict {
        val contentType = header?.substringBefore(";") ?: return ContentTypeVerdict.OK
        if (contentType == "application/json" || contentType.startsWith("text/")) {
            return ContentTypeVerdict.ERROR_BODY
        }
        if (expected?.isNotBlank() == true) {
            // 期望正则来自 HttpTTS 源配置 (用户填写), native 端 `kotlin.text.Regex` 的编译与
            // 匹配都会递归, 畸形规则可把线程栈写穿闪退; 匹配交给 [RuleRegexes] 的平台实现
            val matched = try {
                RuleRegexes.get()
                    .matchesEntire(contentType, expected, multiline = false, timeout = CONTENT_TYPE_TIMEOUT)
            } catch (_: RegexTimeoutException) {
                false
            }
            if (!matched) return ContentTypeVerdict.ERROR_MISMATCH
        }
        return ContentTypeVerdict.OK
    }

    /**
     * 音频缓存文件名: md5(章节名)_md5(url-|-语速-|-内容)
     *
     * [voiceTag] 非空时并入 hash 并加 `v2|` 前缀：同一句台词配不同角色必须落成不同文件，
     * 否则换了角色还会命中上一次合成的音频（听感上就是「改了没生效」）。前缀让多角色 key
     * 与旧 key 空间完全不重叠；为空时 hash 输入与旧版逐字相同，单音色用户的存量缓存不动。
     */
    fun speakFileName(
        title: String?,
        url: String?,
        speechRate: Int,
        content: String,
        voiceTag: String = "",
    ): String {
        val key = if (voiceTag.isEmpty()) {
            "$url-|-$speechRate-|-$content"
        } else {
            "v2|$url-|-$speechRate-|-$voiceTag-|-$content"
        }
        return MD5Utils.md5Encode16(title ?: "") + "_" + MD5Utils.md5Encode16(key)
    }

    /**
     * 连续下载失败熔断计数: 成功清零,失败累计,超过阈值(5)放弃。
     */
    class DownloadErrorBreaker {

        private var count = 0

        fun reset() {
            count = 0
        }

        /** 记一次失败,返回是否已超过阈值 */
        fun record(): Boolean {
            count++
            return count > 5
        }
    }
}
