package io.legado.app.ui.compose.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.RadioButton as MiuixRadioButton

/**
 * 单选钮：MD3 形态，委托 miuix [MiuixRadioButton]。
 *
 * 旧实现是 M2 RadioButton（accent 着色、MD2 双圈），miuix 版为 MD3 的圆点 + 填充动画，
 * 选中色由 miuix 色板的 primary 提供（primary 由主题强调色经 Monet 展开，仍随用户主题色变化）。
 * 签名不变，16 处调用点无需改动。
 */
@Composable
fun AppRadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    MiuixRadioButton(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
    )
}
