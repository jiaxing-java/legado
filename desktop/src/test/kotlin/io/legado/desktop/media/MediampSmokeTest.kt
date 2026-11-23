package io.legado.desktop.media

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import org.openani.mediamp.MediaStatus
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.mpv.MpvMediampPlayer
import org.openani.mediamp.source.UriMediaData
import java.io.File
import javax.swing.SwingUtilities
import kotlin.coroutines.CoroutineContext

/**
 * mediamp-mpv 桌面播放冒烟测试（人工触发的集成测试，CI 上自动跳过）。
 *
 * 只覆盖升级 mediamp 后最容易坏的三件事，全部是"库内行为"，不需要 GUI：
 * 1. `prepareLibraries(<解包目录>, false)` 能把 37 个 native 装进进程（wrapper dll 及其依赖找得到）
 * 2. `MediampPlayer(...)` 能经 ServiceLoader 解析出 mpv 后端并打开本地音频（不报 MPV_ERROR_LOADING_FAILED）
 * 3. 播放位置真的推进（解码 + 时钟 + 音频输出链路都没被版本变更打断）
 *
 * 运行方式（runtime 目录由 jar 清单 `mpv-natives-<平台>.txt` 平铺解包得到）：
 * ```
 * MEDIAMP_SMOKE_DIR=<dir> MEDIAMP_SMOKE_WAV=<file> ./gradlew :desktop:test --tests "*MediampSmokeTest*"
 * ```
 * 未设置环境变量时整条测试 skip —— 它依赖外部下载的 21MB 工件与音频文件，不适合默认跑。
 */
class MediampSmokeTest {

    /** mediamp 的播放控制契约要求 AWT 线程；不依赖 Main dispatcher 是否已安装 */
    private val edt = object : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            SwingUtilities.invokeLater(block)
        }
    }

    @Test(timeout = 180_000)
    fun mpvLoadsAndPlaysLocalAudio() {
        val dirPath = System.getenv("MEDIAMP_SMOKE_DIR")
        Assume.assumeTrue("未设置 MEDIAMP_SMOKE_DIR，跳过 mediamp 播放冒烟测试", !dirPath.isNullOrBlank())
        val dir = File(dirPath!!)
        val wrapper = listOf("mediampv.dll", "libmediampv.dylib", "libmediampv.so")
            .map { File(dir, it) }
            .firstOrNull { it.isFile }
        Assume.assumeTrue("目录里没有 mediamp native wrapper: ${dir.absolutePath}", wrapper != null)

        val wavPath = System.getenv("MEDIAMP_SMOKE_WAV")
        Assume.assumeTrue("未设置 MEDIAMP_SMOKE_WAV，跳过", !wavPath.isNullOrBlank())
        val wav = File(wavPath!!)
        Assume.assumeTrue("音频文件不存在: ${wav.absolutePath}", wav.isFile)
        // 默认走本地 HTTP：应用里音频源就是 http(s)，file:// 只在测试里出现
        val mediaUrl = System.getenv("MEDIAMP_SMOKE_URL")?.takeIf { it.isNotBlank() }
            ?: "http://127.0.0.1:8123/${wav.name}"

        // 1. native 装载
        MpvMediampPlayer.prepareLibraries(dir.absolutePath, false)

        var lastStatus = "unknown"
        var positionMs = -1L
        var durationMs = -1L
        var mpvVersion = ""

        runBlocking {
            val player: MediampPlayer = MediampPlayer(Unit, edt)
            try {
                delay(300) // 等 mpv 实例建好，才能读 mpv-version
                mpvVersion = runCatching {
                    (player.impl as? org.openani.mediamp.mpv.MPVHandle)
                        ?.getPropertyString("mpv-version") ?: "n/a"
                }.getOrDefault("n/a")
                val handle = player.impl as? org.openani.mediamp.mpv.MPVHandle
                val ffmpegVer = runCatching { handle?.getPropertyString("ffmpeg-version") ?: "(none)" }
                    .getOrDefault("(error)")
                val config = runCatching { handle?.getPropertyString("configuration") ?: "(none)" }
                    .getOrDefault("(error)")
                println("[mediamp-smoke] url=$mediaUrl mpv=$mpvVersion ffmpeg=$ffmpegVer")
                println("[mediamp-smoke] mpv configuration: ${config.take(1200)}")
                player.setMediaData(
                    data = UriMediaData(mediaUrl),
                    playWhenReady = true,
                    startPositionMillis = 0,
                )
                val targetMs = System.getenv("MEDIAMP_SMOKE_MS")?.toLongOrNull() ?: 300L
                val deadline = System.currentTimeMillis() + 60_000
                while (System.currentTimeMillis() < deadline) {
                    val state = player.state.value
                    lastStatus = state.mediaStatus.toString()
                    positionMs = player.currentPositionMillis.value
                    durationMs = player.mediaProperties.value?.durationMillis ?: -1L
                    val status = state.mediaStatus
                    if (status is MediaStatus.Error) {
                        throw AssertionError("mpv 打开失败: $status")
                    }
                    if ((status == MediaStatus.Ready || status == MediaStatus.Ended) && positionMs > targetMs) break
                    delay(200)
                }
            } finally {
                kotlinx.coroutines.withContext(edt) { runCatching { player.close() } }
            }
        }

        assertTrue(
            "播放位置未推进 (url=$mediaUrl, mpv=$mpvVersion, status=$lastStatus, " +
                "position=$positionMs ms, duration=$durationMs ms) —— 说明解码/音频输出链路异常",
            positionMs > 300,
        )
    }
}
