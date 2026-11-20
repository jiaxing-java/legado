package io.legado.app.ui.compose.theme

import androidx.compose.ui.graphics.Color

/**
 * MD3 皮肤的 12 套内置配色, 对应 HapeLee/legado-with-MD3 的 `AppThemeMode` 档位
 * (Dynamic/Custom 两档不是固定配色, 不在本枚举内)。
 *
 * 只保留**种子色**而非整套 M3 ColorScheme: miuix 的 `ThemeController` 在 Monet 模式下
 * 用 materialkolor 从单一种子生成整套 miuix Colors (60+ 字段)。原样搬运 MD3 的 12 套
 * `lightColorScheme(...)` 常量 (12 × 108 行) 反而要自己写 M3→miuix 的字段映射, 且 miuix
 * 升级新增字段时会静默漏掉。
 *
 * 浅/深两色分别取该套 `lightScheme.primary` 与 `darkScheme.primary` —— MD3 的深色不是
 * 浅色的机械反相, 是各自调过的独立值, 只取一个会丢掉深色下的观感。
 *
 * 色值来源: md3-src 的 app/src/main/java/io/legado/app/ui/theme/colorScheme/ 下各 ColorScheme.kt
 */
enum class LegadoMiuixPalette(
    val lightSeed: Color,
    val darkSeed: Color,
) {
    /** 绿 */
    GR(Color(0xFF4C662B), Color(0xFFB1D18A)),

    /** 柠檬 */
    Lemon(Color(0xFF6D5E0F), Color(0xFFDBC66E)),

    /** 灰白 */
    WH(Color(0xFF5C5C5C), Color(0xFFC7C6C6)),

    /** 墨水屏 (纯黑白) */
    Elink(Color(0xFF000000), Color(0xFFFFFFFF)),

    /** 天空 */
    Sora(Color(0xFF3B608F), Color(0xFFA5C9FE)),

    /** 八月 */
    August(Color(0xFF8F4C37), Color(0xFFFFB59F)),

    /** 卡洛塔 */
    Carlotta(Color(0xFF8B4A62), Color(0xFFFFB0CA)),

    /** 小春 */
    Koharu(Color(0xFF8F4A4D), Color(0xFFFFB3B4)),

    /** 优香 */
    Yuuka(Color(0xFF565992), Color(0xFFBFC2FF)),

    /** 菲比 */
    Phoebe(Color(0xFF6B5E10), Color(0xFFD9C76F)),

    /** 睦月 */
    Mujika(Color(0xFF8E4958), Color(0xFFFFB1C0)),

    /** 透明 (浅色以纯黑为种子, 实际底色由页面背景决定) */
    Transparent(Color(0xFF000000), Color(0xA0FFFFFF)),
    ;

    fun seedFor(isDark: Boolean): Color = if (isDark) darkSeed else lightSeed
}
