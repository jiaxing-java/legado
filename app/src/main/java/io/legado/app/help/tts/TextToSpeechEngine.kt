package io.legado.app.help.tts

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import io.legado.app.App
import io.legado.app.help.i18n.androidAppString
import io.legado.app.utils.buildMainHandler
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Android TextToSpeech 引擎封装。
 *
 * 负责:
 * - 延迟构造 TextToSpeech 实例(首次 [ensureReady] 调用时才创建)
 * - 指定引擎名(为空表示系统默认)
 * - 把 onInit 异步信号包装成 [ensureReady] 回调
 * - 转发 [TextToSpeech.speak] 的 QUEUE_FLUSH / QUEUE_ADD 模式
 *
 * **不**负责:断句/分段/章节跟踪/音频焦点/通知。这些是调用方业务。
 *
 * 设计目标是同时服务两个场景:
 * 1. [AndroidSystemTtsEngine] — 单段文本快速播报(选词朗读 / RSS 朗读, 共享 [OneShotTts])
 * 2. [io.legado.app.service.TTSReadAloudService] — 章节级朗读
 */
class TextToSpeechEngine(private val engineName: String? = null) {

    @Volatile
    var isReady: Boolean = false
        private set

    val isSpeaking: Boolean get() = tts?.isSpeaking ?: false

    /** init 失败时回调,默认弹 toast */
    var onInitFailed: () -> Unit = { App.instance.toastOnUi(androidAppString("tts_init_failed")) }

    /** Utterance 进度回调,需要在 [ensureReady] 之前或之后设置均可 */
    var progressListener: UtteranceProgressListener? = null
        set(value) {
            field = value
            tts?.setOnUtteranceProgressListener(value)
        }

    private var tts: TextToSpeech? = null
    private var pendingOnReady: (() -> Unit)? = null
    private var pendingOnError: ((Int) -> Unit)? = null

    /**
     * 确保引擎已初始化。已就绪时同步执行 [block];否则触发异步初始化,初始化成功后执行 [block]。
     *
     * 在初始化过程中重复调用,只保留最后一个 [block]。
     */
    @Synchronized
    fun ensureReady(block: () -> Unit, onError: (Int) -> Unit = {}) {
        if (isReady) {
            block()
            return
        }
        pendingOnReady = block
        pendingOnError = onError
        if (tts != null) return // 初始化中
        val listener = TextToSpeech.OnInitListener { status -> onInit(status) }
        tts = if (engineName.isNullOrBlank()) {
            TextToSpeech(App.instance, listener)
        } else {
            TextToSpeech(App.instance, listener, engineName)
        }
    }

    @Synchronized
    private fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            val failedEngine = tts
            val error = pendingOnError
            tts = null
            isReady = false
            pendingOnReady = null
            pendingOnError = null
            failedEngine?.shutdown()
            onInitFailed()
            error?.invoke(status)
            return
        }
        progressListener?.let { tts?.setOnUtteranceProgressListener(it) }
        isReady = true
        val block = pendingOnReady
        pendingOnReady = null
        pendingOnError = null
        block?.invoke()
    }

    /** 立即播放(清空已有队列)。 */
    fun speak(text: String, utteranceId: String? = null): Int {
        return tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            ?: TextToSpeech.ERROR
    }

    /** 追加到队列尾部。 */
    fun enqueue(text: String, utteranceId: String? = null): Int {
        return tts?.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
            ?: TextToSpeech.ERROR
    }

    fun setSpeechRate(rate: Float) {
        tts?.setSpeechRate(rate)
    }

    fun stop() {
        tts?.stop()
    }

    @Synchronized
    fun shutdown() {
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
        isReady = false
        pendingOnReady = null
        pendingOnError = null
    }
}

/**
 * 注册 Android 的 [SystemTtsEngine] (选中文字/RSS 一次性朗读经 [TtsEngineProvider] 取用)。
 *
 * 宿主启动早期调用一次 (App.onCreate), 与 `registerAndroidReadBookPlatform` 同批。
 */
fun registerAndroidSystemTtsEngine() {
    TtsEngineProvider.register(AndroidSystemTtsEngine())
}

/**
 * 注册 app 端 [HttpTtsPlayer] 工厂 (朗读试听经 [TtsEngineProvider] 取用)。
 * 正式朗读不经此工厂 (HttpReadAloudService 下载调度 + 缓存播放)。
 */
fun registerAndroidHttpTtsPlayer() {
    TtsEngineProvider.registerHttpTtsPlayerFactory { AndroidHttpTtsPlayer() }
}

/**
 * [TextToSpeechEngine] 的 [SystemTtsEngine] 适配: 章节朗读走 `TTSReadAloudService` 自己的实例,
 * 本实例只服务共享的一次性朗读 (commonMain `OneShotTts`)。
 *
 * `TextToSpeech` 无暂停能力, [pause] 停播并置标志、[resume] 只清标志 (接口已写明不支持暂停的
 * 引擎 no-op); [speechRate] 底层只有 setter, 故适配器自存一份供回读。
 */
private class AndroidSystemTtsEngine : SystemTtsEngine {

    private val engine = TextToSpeechEngine()

    private val handler by lazy { buildMainHandler() }

    /**
     * 一分钟无朗读就释放底层 TextToSpeech (对照原版 `TTS.clearRunnable`)。
     * 本实例是适配器私有的, 章节朗读用 `TTSReadAloudService` 自己那个, 释放互不影响;
     * 下次 [speak] 经 `ensureReady` 自动重建。
     */
    private val idleShutdown = Runnable { engine.shutdown() }

    private var paused = false
    private val listeners = TtsProgressListenerRegistry()

    override val isReady: Boolean get() = engine.isReady

    override val isSpeaking: Boolean get() = engine.isSpeaking

    override val isPaused: Boolean get() = paused

    /** `TextToSpeech.QUEUE_ADD` 是真队列, 批量入队段间无缝 (对照原版 `TTS.emitPending`)。 */
    override val supportsQueue: Boolean get() = true

    override var speechRate: Float = 1f
        set(value) {
            field = value
            engine.setSpeechRate(value)
        }

    override fun registerProgressListener(
        listener: TtsProgressListener,
        utteranceIdPrefix: String?,
    ): TtsProgressListenerToken = listeners.register(listener, utteranceIdPrefix)

    override fun unregisterProgressListener(token: TtsProgressListenerToken) {
        listeners.unregister(token)
    }

    init {
        engine.progressListener = object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                handler.removeCallbacks(idleShutdown)
                listeners.onStart(utteranceId.orEmpty())
            }

            override fun onDone(utteranceId: String?) {
                handler.postDelayed(idleShutdown, IDLE_TIMEOUT_MS)
                listeners.onDone(utteranceId.orEmpty())
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = Unit

            override fun onError(utteranceId: String?, errorCode: Int) {
                listeners.onError(utteranceId.orEmpty(), errorCode)
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                listeners.onRangeStart(utteranceId.orEmpty(), start, end, frame)
            }
        }
    }

    override fun init(onReady: (() -> Unit)?) {
        engine.ensureReady({ onReady?.invoke() })
    }

    override fun init(onReady: (() -> Unit)?, onError: (errorCode: Int) -> Unit) {
        engine.ensureReady({ onReady?.invoke() }, onError)
    }

    override fun speak(text: String, utteranceId: String): Boolean {
        handler.removeCallbacks(idleShutdown)
        paused = false
        return reportIfError(engine.speak(text, utteranceId), utteranceId)
    }

    override fun enqueue(text: String, utteranceId: String): Boolean {
        return reportIfError(engine.enqueue(text, utteranceId), utteranceId)
    }

    /**
     * `TextToSpeech.speak` 返回 ERROR 时不会再有任何 utterance 回调, 改由这里补一次
     * onError —— 不补的话按 onDone 串行/计数推进的调用方 (如 [OneShotTts]) 会永远等不到结束。
     */
    private fun reportIfError(result: Int, utteranceId: String): Boolean {
        if (result == TextToSpeech.ERROR) return false
        return true
    }

    override fun pause() {
        paused = true
        engine.stop()
    }

    override fun resume() {
        paused = false
    }

    override fun stop() {
        handler.removeCallbacks(idleShutdown)
        paused = false
        engine.stop()
    }

    override fun shutdown() {
        handler.removeCallbacks(idleShutdown)
        paused = false
        engine.shutdown()
    }

    /**
     * 枚举本机已安装的系统 TTS 引擎（MultiTTS / 微软 / 厂商语音都在这里）。
     *
     * 另起一个临时实例而不是复用 [engine]：枚举要求引擎已 init，复用会打乱当前朗读的
     * 初始化与空闲释放时序，读到的也可能只是上一次 init 的快照。
     *
     * 构造必须在主线程（TextToSpeech 内部要 Looper），但 `engines` 是同步 binder 调用、
     * 会阻塞等引擎进程回包，所以读取放 IO，读完再回主线程销毁。
     */
    override suspend fun listEngines(): List<SystemTtsEngineInfo> {
        val probe = createProbe() ?: return emptyList()
        return try {
            withContext(Dispatchers.IO) {
                probe.engines.orEmpty()
                    .distinctBy { it.name }
                    .map { info ->
                        val label = info.label?.takeIf(String::isNotBlank) ?: info.name
                        SystemTtsEngineInfo(info.name, label)
                    }
                    .sortedBy { it.label.lowercase() }
            }
        } finally {
            // NonCancellable: 探测实例是 native 资源, 调用方取消(关弹窗)时也必须 shutdown,
            // 否则可取消的 withContext 会直接抛 CancellationException 跳过清理
            withContext(NonCancellable + Dispatchers.Main.immediate) {
                probe.stop()
                probe.shutdown()
            }
        }
    }

    private suspend fun createProbe(): TextToSpeech? =
        withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { continuation ->
                var instance: TextToSpeech? = null
                val listener = TextToSpeech.OnInitListener { status ->
                    val created = instance
                    when {
                        !continuation.isActive -> created?.shutdown()
                        status == TextToSpeech.SUCCESS -> continuation.resume(created)
                        else -> {
                            created?.shutdown()
                            continuation.resume(null)
                        }
                    }
                }
                instance = TextToSpeech(App.instance, listener)
            }
        }

    private companion object {
        const val IDLE_TIMEOUT_MS = 60_000L
    }
}
