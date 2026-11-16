package com.happyfamily.contract.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** 디자인 시안 색상 */
object Brand {
    val Blue = Color(0xFF3F5BEA)
    val BlueLight = Color(0xFF6E8BF7)
    val Violet = Color(0xFF7C5CF0)
    val Navy = Color(0xFF1B2340)
    val TextSub = Color(0xFF6B7186)
    val Background = Color(0xFFF9F9FE)
    val CardTint = Color(0xFFEDEFFE)
    val Divider = Color(0xFFC9CEF2)

    /** 로그인·촬영 버튼의 가로 그라데이션 */
    val ButtonGradient = Brush.horizontalGradient(listOf(BlueLight, Blue))

    /** 화면 배경: 위아래가 살짝 보랏빛 */
    val ScreenGradient = Brush.verticalGradient(
        0f to Color(0xFFF3F5FE),
        0.45f to Background,
        1f to Color(0xFFE8ECFE),
    )
}

private val LightColors = lightColorScheme(
    primary = Brand.Blue,
    onPrimary = Color.White,
    primaryContainer = Brand.CardTint,
    onPrimaryContainer = Brand.Navy,
    secondary = Brand.Violet,
    background = Brand.Background,
    onBackground = Brand.Navy,
    surface = Color.White,
    onSurface = Brand.Navy,
    surfaceVariant = Brand.CardTint,
    onSurfaceVariant = Brand.TextSub,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Brand.CardTint,
    outline = Brand.Divider,
    outlineVariant = Brand.Divider,
)

/** 시안이 밝은 화면 기준이라 항상 밝은 테마를 쓴다. */
@Composable
fun ContractTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, content = content)
}
