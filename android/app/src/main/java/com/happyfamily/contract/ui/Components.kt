package com.happyfamily.contract.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.happyfamily.contract.ui.theme.Brand

/** 시안의 파란 그라데이션 알약 버튼. 왼쪽 아이콘, 가운데 글자, 오른쪽 화살표. */
@Composable
fun GradientPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 60.dp,
    enabled: Boolean = true,
    showArrow: Boolean = true,
    leading: @Composable () -> Unit = {},
) {
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier = modifier
            .height(height)
            .shadow(14.dp, shape, ambientColor = Brand.Blue, spotColor = Brand.Blue)
            .clip(shape)
            .background(Brand.ButtonGradient)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        leading()
        Spacer(Modifier.width(12.dp))
        Text(text, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        if (showArrow) {
            Spacer(Modifier.width(10.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.White,
            )
        }
    }
}

/** 흰 바탕 알약 버튼 (파일 업로드 등 보조 동작) */
@Composable
fun WhitePillButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 60.dp,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier = modifier
            .height(height)
            .shadow(10.dp, shape, ambientColor = Brand.Blue.copy(alpha = 0.3f), spotColor = Brand.Blue.copy(alpha = 0.3f))
            .clip(shape)
            .background(Color.White)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Brand.Blue, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = Brand.Blue, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** 흰 동그라미 안의 아이콘 (Google 로고 등) */
@Composable
fun WhiteCircle(size: Dp = 34.dp, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) { content() }
}

data class Feature(val icon: ImageVector, val tint: Color, val title: String, val subtitle: String)

/** "계약서 촬영 | AI 분석 | 안전한 보관" 3칸 카드 */
@Composable
fun FeatureRow(features: List<Feature>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brand.CardTint.copy(alpha = 0.75f))
            .padding(vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        features.forEachIndexed { index, f ->
            if (index > 0) {
                Box(
                    Modifier
                        .width(1.dp)
                        .height(56.dp)
                        .background(Brand.Divider),
                )
            }
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(f.icon, contentDescription = null, tint = f.tint, modifier = Modifier.size(34.dp))
                Spacer(Modifier.height(10.dp))
                Text(f.title, color = Brand.Navy, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(f.subtitle, color = Brand.TextSub, fontSize = 12.sp)
            }
        }
    }
}

/** 앞부분은 남색, 뒷부분은 파란색인 제목 (예: 부동산 / 계약서함) */
fun twoToneTitle(plain: String, highlight: String, tail: String = "") = buildAnnotatedString {
    withStyle(SpanStyle(color = Brand.Navy)) { append(plain) }
    withStyle(SpanStyle(color = Brand.Blue)) { append(highlight) }
    withStyle(SpanStyle(color = Brand.Navy)) { append(tail) }
}
