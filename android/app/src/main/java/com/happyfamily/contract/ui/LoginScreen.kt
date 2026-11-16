package com.happyfamily.contract.ui

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.happyfamily.contract.R
import com.happyfamily.contract.data.AuthRepository
import com.happyfamily.contract.ui.theme.Brand
import kotlinx.coroutines.launch

val AppFeatures = listOf(
    Feature(Icons.Filled.CameraAlt, Brand.Blue, "계약서 촬영", "간단하게 촬영"),
    Feature(Icons.Filled.AutoAwesome, Brand.Violet, "AI 분석", "핵심 정보 추출"),
    Feature(Icons.Outlined.CloudUpload, Brand.Blue, "안전한 보관", "언제든지 확인"),
)

@Composable
fun LoginScreen() {
    val activity = LocalContext.current as Activity
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brand.ScreenGradient),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.login_hero),
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Description,
                    contentDescription = null,
                    tint = Brand.Navy,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    twoToneTitle("부동산 ", "계약서함"),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "계약서를 찍으면 AI가 내용을 정리하고\n사진과 함께 클라우드에 보관합니다.\n같은 구글 계정으로 어느 기기에서나 볼 수 있어요.",
                textAlign = TextAlign.Center,
                color = Brand.Navy,
                fontSize = 16.sp,
                lineHeight = 26.sp,
                modifier = Modifier.padding(horizontal = 24.dp),
            )

            Spacer(Modifier.height(28.dp))
            FeatureRow(AppFeatures, Modifier.padding(horizontal = 20.dp))

            Spacer(Modifier.height(24.dp))
            Text(
                "AI 분석에는 Google Gemini 무료 버전을 씁니다.\n무료 버전은 보낸 사진이 Google 서비스 개선에 쓰일 수 있습니다.",
                textAlign = TextAlign.Center,
                color = Brand.TextSub,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(Modifier.height(32.dp))
            if (loading) {
                CircularProgressIndicator(Modifier.height(60.dp))
            } else {
                GradientPillButton(
                    text = "Google 계정으로 로그인",
                    onClick = {
                        loading = true
                        error = null
                        scope.launch {
                            try {
                                AuthRepository.signInWithGoogle(activity)
                            } catch (_: GetCredentialCancellationException) {
                                // 사용자가 취소함
                            } catch (e: Exception) {
                                error = "로그인 실패: ${e.message}"
                            } finally {
                                loading = false
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 40.dp),
                    leading = {
                        WhiteCircle {
                            Image(
                                painterResource(R.drawable.ic_google_g),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                )
            }
            error?.let {
                Spacer(Modifier.height(16.dp))
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        }
    }
}
