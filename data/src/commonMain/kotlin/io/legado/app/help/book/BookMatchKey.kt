package io.legado.app.help.book

/**
 * 「是否算同一部作品」的比对键。
 *
 * 加入书架查重靠它判断书架里有没有同一本书：不同书源给出的书名经常只差全角/半角、
 * 多余空格、大小写，直接按原始字符串比会漏判（于是书架上出现两本看着一模一样的书）。
 *
 * ## 为什么不用 NFKC
 *
 * MD3 用 `java.text.Normalizer.normalize(value, NFKC)`。那是 JVM 专有的，Kotlin/Native
 * （iOS/鸿蒙）与 JS 都没有等价物，写进 commonMain 会直接链接失败。这里只做两件真正
 * 影响中文书名比对的事 —— 全角 ASCII 折半角、全角空格折普通空格 —— 再折叠空白与大小写。
 * 覆盖面比 NFKC 窄（西里尔/带音标拉丁字符不折叠），但对书源场景足够：差异几乎都出在
 * 全角标点与空格上。
 */
object BookMatchKey {

    private val WHITESPACE = Regex("\\s+")

    fun of(value: String): String = foldFullWidth(value)
        .trim()
        .replace(WHITESPACE, " ")
        .lowercase()

    /** 任一方作者为空时视为可能同一部作品（很多源的作者字段缺失）。 */
    fun authorCompatible(left: String, right: String): Boolean =
        left.isBlank() || right.isBlank() || left == right

    /**
     * 全角转半角：U+FF01..U+FF5E 与 U+0021..U+007E 一一对应（差 0xFEE0），
     * U+3000（全角空格）折成 U+0020。其余字符原样保留。
     */
    private fun foldFullWidth(value: String): String {
        if (value.none { it.code == 0x3000 || it.code in FULL_WIDTH_RANGE }) return value
        val sb = StringBuilder(value.length)
        for (ch in value) {
            sb.append(
                when {
                    ch.code == 0x3000 -> ' '
                    ch.code in FULL_WIDTH_RANGE -> (ch.code - 0xFEE0).toChar()
                    else -> ch
                }
            )
        }
        return sb.toString()
    }

    private val FULL_WIDTH_RANGE = 0xFF01..0xFF5E
}
