package io.legado.app.ui.compose.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle
import top.yukonga.miuix.kmp.theme.defaultTextStyles

/**
 * MD3 皮肤令牌层的接入点: 把本工程既有的主题数据 ([accent] + isDark) 翻译成
 * miuix 的色板与字阶, 供 miuix 组件消费。
 *
 * 为什么用 Monet 从种子色生成, 而不是逐字段构造 miuix `Colors`:
 * miuix 的 `Colors` 有 60+ 个字段 (primary/onPrimary/disabledPrimaryButton/
 * surfaceContainerHighest/onSurfaceVariantActions ...), 手工填一遍既容易漏, 也会
 * 在 miuix 升级加字段时静默失配。`ThemeController` 的 Monet 模式内部用 materialkolor
 * 从单个种子生成整套色板, 且与 MD3 皮肤自己开 `useMiuixMonet` 时的行为完全一致。
 *
 * 分工与 MD3 相同 —— miuix 色板只负责**组件内部**取色 (按钮/卡片/开关/列表行),
 * 页面底色仍由 [AppColors.background] 经 AppBackground 绘制, 因此用户自定义背景色
 * 与背景图不会被 Monet 生成的 background 顶掉。
 *
 * @param isDark 当前是否深色 (由已有的 [AppColors.isDark] 提供, 不重复读系统配置)
 * @param accent 现有主题强调色; 未指定 [palette] 时作为种子色
 * @param palette MD3 内置配色预设; 非空时优先于 [accent] 作为种子色
 * @param paletteStyle 调色风格 (对应 MD3 的 PaletteStyle 档位), 默认 TonalSpot
 */
@Composable
fun ProvideMiuixTheme(
    isDark: Boolean,
    accent: Color,
    palette: LegadoMiuixPalette? = null,
    paletteStyle: ThemePaletteStyle = ThemePaletteStyle.TonalSpot,
    content: @Composable () -> Unit,
) {
    val seed = palette?.seedFor(isDark) ?: accent
    val controller = remember(isDark, seed, paletteStyle) {
        ThemeController(
            // 不用 Light/Dark (那两档是 miuix 内置的紫/灰默认板, 与本工程强调色无关),
            // 也不用 System (宿主已把"跟随系统"解析成明确的深浅, 再读一次系统模式会在
            // 系统主题切换而宿主未重建时拿到过期值 —— 与 MD3 的取舍一致)。
            colorSchemeMode = if (isDark) ColorSchemeMode.MonetDark else ColorSchemeMode.MonetLight,
            keyColor = seed,
            paletteStyle = paletteStyle,
            isDark = isDark,
        )
    }
    val textStyles = remember { defaultTextStyles() }
    MiuixTheme(
        controller = controller,
        textStyles = textStyles,
        content = content,
    )
}
