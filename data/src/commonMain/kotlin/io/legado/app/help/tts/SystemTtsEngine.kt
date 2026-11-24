package io.legado.app.help.tts

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * 本机已安装的系统 TTS 引擎（Android 对应 `TextToSpeech.EngineInfo`）。
 *
 * [id] 是引擎的包名/类名，既作为 `TextToSpeech(context, listener, id)` 的第三个参数，
 * 也直接写进 `ReadAloud.ttsEngine` 的 `SelectItem.value`；[label] 只用于展示。
 */
data class SystemTtsEngineInfo(val id: String, val label: String)

/**
 * 一个引擎下的具体音色。
 *
 * [id] 是该引擎的原生标识（Android `Voice.name`、iOS `AVSpeechSynthesisVoice.identifier`），
 * 写进 `read_aloud_voices.speakerId`，朗读时原样回传给 [SystemTtsEngine.setVoice]。
 *
 * [label] 只是展示名：Android 的 `Voice.name` 形如 `zh-cn-x-ccc-local`，直接暴露给用户
 * 无法辨认，故由平台侧拼出「中文（中国）· 女声」这类可读文本。
 */
data class SystemTtsVoice(
    val id: String,
    val label: String,
    /** BCP-47 语言标签，空 = 平台没给。 */
    val locale: String = "",
    /** 是否需要联网（Android `Voice.isNetworkConnectionRequired`）。 */
    val networkRequired: Boolean = false,
)

/**
 * 系统 TTS 引擎抽象接口（KMP 版）。
 *
 * 各平台只负责单段播放与平台事件适配；业务通过订阅 token 独立持有监听器，不能互相覆盖。
 */
interface SystemTtsEngine {

    val isReady: Boolean

    val isSpeaking: Boolean

    val isPaused: Boolean

    /** 是否真正支持追加队列。 */
    val supportsQueue: Boolean get() = false

    /** 是否真正支持原位暂停/恢复；false 的路由暂停前必须先失效当前播放 token。 */
    val supportsPause: Boolean get() = false

    var speechRate: Float

    /** 注册独立进度订阅；token 必须支持任意注销顺序。 */
    fun registerProgressListener(
        listener: TtsProgressListener,
        utteranceIdPrefix: String? = null,
    ): TtsProgressListenerToken

    /** 精确注销 [token] 对应的订阅，重复或乱序注销均不得影响其它订阅。 */
    fun unregisterProgressListener(token: TtsProgressListenerToken)

    /** 显式初始化；默认平台同步就绪。 */
    fun init(onReady: (() -> Unit)? = null) {
        onReady?.invoke()
    }

    /**
     * 带失败闭环的初始化入口。异步初始化平台应覆写并在失败时调用 [onError]。
     * 保留上方单回调重载，避免未迁移平台被接口变更破坏。
     */
    fun init(onReady: (() -> Unit)?, onError: (errorCode: Int) -> Unit) {
        init(onReady)
    }

    /** 接受播放请求；false 表示同步提交失败且不会保证产生进度回调。 */
    fun speak(text: String, utteranceId: String): Boolean

    fun enqueue(text: String, utteranceId: String): Boolean = speak(text, utteranceId)

    fun pause() = Unit

    fun resume() = Unit

    fun stop()

    fun shutdown()

    fun synthesizeToBuffer(text: String, utteranceId: String): ByteArray? = null

    /**
     * 列出本机已安装的系统 TTS 引擎（不含"系统默认"这一项）。
     *
     * 只有 Android 有真实实现 —— 第三方引擎（MultiTTS、微软、各家厂商语音）都会出现在
     * `TextToSpeech.engines` 里。其余平台的合成器是系统唯一的，没有"挑引擎"的语义，
     * 返回空表即正确行为，UI 据此不渲染分组。
     *
     * 返回的 [SystemTtsEngineInfo.id] 会被原样写进 `ReadAloud.ttsEngine`，由
     * Android 端 `TextToSpeechEngine(engineName)` 消费；因此这里**不要**做任何改写。
     */
    suspend fun listEngines(): List<SystemTtsEngineInfo> = emptyList()

    /**
     * 列出当前引擎可用的音色。
     *
     * 与 [listEngines] 同一条原则：有真实枚举能力的平台覆写，其余返回空表即"没有挑音色的
     * 语义"，UI 据此隐藏音色分组而不是摆一个空列表。
     */
    suspend fun listVoices(): List<SystemTtsVoice> = emptyList()

    /**
     * 指定下一段朗读使用的音色（[speakerId] 来自 [SystemTtsVoice.id]）。
     *
     * 是**实例状态**：设置后对所有后续 [speak] 生效，直到再次调用或实例被换掉。传空/null
     * 表示回到引擎默认音色。平台不支持时静默忽略 —— 调用方不该为"这个引擎没有音色概念"
     * 付一次异常开销。
     */
    fun setVoice(speakerId: String?) = Unit
}

/** 不透明监听订阅 token；身份即注册表 key，不携带 previous-listener 栈。 */
class TtsProgressListenerToken internal constructor()

/**
 * 平台适配器共用的监听注册表。平台事件先在锁内取快照，再在锁外分发，允许监听器回调中注销自身。
 */
class TtsProgressListenerRegistry {
    private data class Entry(
        val listener: TtsProgressListener,
        val utteranceIdPrefix: String?,
    )

    private val lock = SynchronizedObject()
    private val listeners = mutableMapOf<TtsProgressListenerToken, Entry>()

    fun register(
        listener: TtsProgressListener,
        utteranceIdPrefix: String? = null,
    ): TtsProgressListenerToken = synchronized(lock) {
        TtsProgressListenerToken().also {
            listeners[it] = Entry(listener, utteranceIdPrefix)
        }
    }

    fun unregister(token: TtsProgressListenerToken) {
        synchronized(lock) { listeners.remove(token) }
    }

    private fun snapshot(utteranceId: String): List<TtsProgressListener> = synchronized(lock) {
        listeners.values.filter {
            it.utteranceIdPrefix == null || utteranceId.startsWith(it.utteranceIdPrefix)
        }.map { it.listener }
    }

    fun onStart(utteranceId: String) = snapshot(utteranceId).forEach { it.onStart(utteranceId) }

    fun onDone(utteranceId: String) = snapshot(utteranceId).forEach { it.onDone(utteranceId) }

    fun onError(utteranceId: String, errorCode: Int) =
        snapshot(utteranceId).forEach { it.onError(utteranceId, errorCode) }

    fun onRangeStart(utteranceId: String, start: Int, end: Int, frame: Int) =
        snapshot(utteranceId).forEach { it.onRangeStart(utteranceId, start, end, frame) }
}

/** TTS 朗读进度回调（KMP 版，对标 Android UtteranceProgressListener）。 */
interface TtsProgressListener {
    fun onStart(utteranceId: String)

    fun onDone(utteranceId: String)

    fun onError(utteranceId: String, errorCode: Int)

    fun onRangeStart(utteranceId: String, start: Int, end: Int, frame: Int)
}
