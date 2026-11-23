package io.legado.app.help.tts

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/**
 * 每个 HTTP TTS 源自己的合成配额。
 *
 * 为什么要按源限流：在线朗读源大多是"借来"的第三方接口，并发上去了就 429 / 直接封。
 * 源作者最清楚自家上限，所以把上限写在源配置里（[io.legado.app.data.entities.HttpTTS.synthesisThreadCount]），
 * 而不是用一个全局数字去猜——一个源只能 1 并发、另一个能 4 并发，全局值总归有一头不合适。
 *
 * 作用范围：实时朗读、试听、预合成、流式读取**共用**同一个信号量，
 * 否则预合成占满配额后实时朗读会一直排队、听感是"卡住"。
 *
 * 实现细节：
 * - 按 sourceId 懒建信号量；limit 变化时重建（源配置被改过）。
 * - `tryAcquire`/`release` 包在 `withPermit` 里，协程取消也能归还额度。
 */
object HttpTtsSynthesisLimiter {

    /** 与 [io.legado.app.data.entities.HttpTTS.MAX_SYNTHESIS_THREAD_COUNT] 一致。 */
    const val MAX_PERMITS = 8

    private val guard = Mutex()

    /** sourceId → (当前上限, 信号量)。 */
    private val slots = mutableMapOf<String, Slot>()

    private class Slot(val permits: Int, val semaphore: Semaphore)

    /**
     * 在 [limit] 个并发额度内执行 [block]。
     *
     * @param sourceId 源 id（用 [io.legado.app.data.entities.HttpTTS.id] 的字符串形式）
     * @param limit 该源的并发上限，越界会被夹到 1..[MAX_PERMITS]
     */
    suspend fun <T> withQuota(sourceId: String, limit: Int, block: suspend () -> T): T {
        val semaphore = semaphoreFor(sourceId, limit.coerceIn(1, MAX_PERMITS))
        return semaphore.withPermit { block() }
    }

    private suspend fun semaphoreFor(sourceId: String, permits: Int): Semaphore {
        guard.withLock {
            val existing = slots[sourceId]
            if (existing != null && existing.permits == permits) return existing.semaphore
            val created = Slot(permits, Semaphore(permits))
            slots[sourceId] = created
            return created.semaphore
        }
    }

    /** 源被删除时清掉它的额度，避免长期运行后 map 只增不减。 */
    suspend fun forget(sourceId: String) {
        guard.withLock { slots.remove(sourceId) }
    }

    /** 测试用：清空全部额度。 */
    suspend fun reset() {
        guard.withLock { slots.clear() }
    }
}
