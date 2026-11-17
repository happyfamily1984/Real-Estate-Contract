package com.happyfamily.contract.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.happyfamily.contract.data.UpgradeWorker
import com.happyfamily.contract.data.ai.AiGateway
import com.happyfamily.contract.data.ai.ApiKeyStore
import com.happyfamily.contract.data.ai.Cooldowns
import com.happyfamily.contract.data.ai.ModelStore
import com.happyfamily.contract.ui.theme.Brand
import kotlinx.coroutines.launch

/** AI 설정: 분석 순서 안내, 경로별 상태, 내 Gemini API 키 관리, 기본 분석 결과 다시 분석 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var refresh by remember { mutableIntStateOf(0) }
    var newKey by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    var localCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(refresh) { localCount = runCatching { UpgradeWorker.localCount() }.getOrDefault(0) }

    val routes = remember(refresh) { AiGateway.routes(context) }
    val keys = remember(refresh) { ApiKeyStore.keys(context) }

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.background(Brand.ScreenGradient),
        topBar = {
            TopAppBar(
                title = { Text("AI 설정", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "뒤로") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, titleContentColor = Brand.Navy),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SettingsCard("분석 순서") {
                    Step("1", "Firebase Gemini (무료)", "모델 ${AiGateway.models(context).joinToString(" → ")} 순서. 모델마다 무료 한도가 따로라 하나가 막히면 다음 모델로 넘어갑니다. 서버가 붐비면(503) 잠시 뒤 자동으로 다시 시도합니다.")
                    Step("2", "내 Gemini API 키 (선택)", "아래에 넣은 키를 차례로 씁니다. 키마다 한도가 따로라 가족 구글 계정으로 만든 키를 여러 개 넣으면 그만큼 더 쓸 수 있습니다.")
                    Step("3", "휴대폰 안 글자 인식 (항상 동작)", "AI 를 전혀 못 쓰면 휴대폰이 직접 글자를 읽어 금액·날짜·주소를 규칙으로 뽑습니다. 인터넷·한도 없이 되지만 정확도는 낮습니다.")
                    Step("4", "자동 다시 분석", "3번으로 만든 기본 결과는 AI 를 다시 쓸 수 있게 되면 자동으로 AI 로 다시 분석합니다.")
                }
            }
            item {
                SettingsCard("지금 상태") {
                    routes.forEach { r ->
                        val available = Cooldowns.until(context, r.id) <= System.currentTimeMillis()
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(r.label, color = Brand.Navy, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Text(
                                if (available) "사용 가능" else Cooldowns.describe(context, r.id),
                                color = if (available) Brand.Blue else Brand.TextSub,
                                fontSize = 12.sp,
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text("휴대폰 안 글자 인식", color = Brand.Navy, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text("항상 사용 가능", color = Brand.Blue, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(onClick = {
                        Cooldowns.clearAll(context)
                        refresh++
                        scope.launch { snackbar.showSnackbar("한도 기록을 지웠어요. 다음 분석 때 모든 AI 를 다시 시도합니다.") }
                    }) { Text("한도 기록 지우고 다시 시도") }
                }
            }
            item {
                SettingsCard("기본 분석 결과") {
                    Text(
                        "AI 를 못 써서 휴대폰 글자 인식으로 만든 결과 ${localCount}건",
                        color = Brand.Navy,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                val n = runCatching { UpgradeWorker.upgradeNow(context, ignoreCooldown = true) }.getOrDefault(0)
                                snackbar.showSnackbar(if (n > 0) "${n}건을 AI 분석 대기열에 넣었어요." else "다시 분석할 계약서가 없어요.")
                            }
                        },
                        enabled = localCount > 0,
                    ) { Text("지금 AI 로 다시 분석") }
                }
            }
            item {
                var modelsText by remember(refresh) { mutableStateOf(AiGateway.models(context).joinToString(", ")) }
                SettingsCard("사용할 모델") {
                    Text(
                        "앞에서부터 차례로 씁니다. 구글이 모델 이름을 바꾸면 여기서 고치세요. 이름이 없는 모델(404)은 자동으로 건너뜁니다.",
                        color = Brand.TextSub,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = modelsText,
                        onValueChange = { modelsText = it },
                        label = { Text("모델 이름 (쉼표로 구분)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row {
                        Button(onClick = {
                            ModelStore.save(context, modelsText)
                            Cooldowns.clearAll(context)
                            refresh++
                            scope.launch { snackbar.showSnackbar("모델 순서를 저장했어요.") }
                        }) { Text("저장") }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = {
                            ModelStore.save(context, "")
                            Cooldowns.clearAll(context)
                            refresh++
                            scope.launch { snackbar.showSnackbar("기본 모델 순서로 되돌렸어요.") }
                        }) { Text("기본값으로") }
                    }
                }
            }
            item {
                SettingsCard("내 Gemini API 키") {
                    Text(
                        "Google AI Studio 에서 무료로 만들 수 있습니다. 키는 이 휴대폰 안에만 저장되고 클라우드·GitHub 로 올라가지 않습니다.",
                        color = Brand.TextSub,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )
                    TextButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey")))
                    }) { Text("키 만들러 가기 (aistudio.google.com)") }
                    keys.forEachIndexed { i, key ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("키 ${i + 1}  ${ApiKeyStore.mask(key)}", color = Brand.Navy, modifier = Modifier.weight(1f))
                            IconButton(onClick = { ApiKeyStore.remove(context, key); refresh++ }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "키 삭제", tint = Brand.TextSub)
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = newKey,
                        onValueChange = { newKey = it.trim() },
                        singleLine = true,
                        label = { Text("API 키 붙여넣기") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = {
                                testing = true
                                scope.launch {
                                    val (ok, message) = ApiKeyStore.test(context, newKey)
                                    testing = false
                                    if (ok) {
                                        ApiKeyStore.add(context, newKey)
                                        newKey = ""
                                        refresh++
                                    }
                                    snackbar.showSnackbar(if (ok) "키를 추가했어요. $message" else "추가하지 못했어요: $message")
                                }
                            },
                            enabled = newKey.length >= 20 && !testing,
                        ) { Text("확인 후 추가") }
                        if (testing) {
                            Spacer(Modifier.width(12.dp))
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, color = Brand.Blue, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun Step(number: String, title: String, body: String) {
    Row(Modifier.padding(vertical = 5.dp)) {
        Text(
            number,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(Brand.Blue, RoundedCornerShape(50))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, color = Brand.Navy, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(body, color = Brand.TextSub, fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}
