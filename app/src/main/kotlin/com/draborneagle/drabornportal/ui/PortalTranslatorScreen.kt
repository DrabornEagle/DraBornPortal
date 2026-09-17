package com.draborneagle.drabornportal.ui

import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.draborneagle.drabornportal.translation.TranslationEngine
import com.draborneagle.drabornportal.translation.TranslationHistoryItem
import com.draborneagle.drabornportal.translation.TranslationHistoryStore
import kotlinx.coroutines.launch
import java.util.Date

private val PortalBackground = Color(0xFF080B14)
private val PortalSurface = Color(0xFF11182A)
private val PortalBlue = Color(0xFF36A7FF)
private val PortalCyan = Color(0xFF4EF2D1)
private val PortalText = Color(0xFFF5F8FF)
private val PortalMuted = Color(0xFFA9B3C9)

private enum class ModelState { PREPARING, READY, ERROR }

@Composable
fun DraBornPortalApp() {
    MaterialTheme {
        Surface(color = PortalBackground) {
            TranslatorScreen()
        }
    }
}

@Composable
private fun TranslatorScreen() {
    val context = LocalContext.current
    val engine = remember { TranslationEngine(context.applicationContext) }
    val historyStore = remember { TranslationHistoryStore(context.applicationContext) }
    val scope = rememberCoroutineScope()

    var modelState by remember { mutableStateOf(ModelState.PREPARING) }
    var sourceText by remember { mutableStateOf("") }
    var translatedText by remember { mutableStateOf("") }
    var isTranslating by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var history by remember { mutableStateOf(historyStore.load()) }

    suspend fun prepareModel() {
        modelState = ModelState.PREPARING
        errorText = null
        runCatching { engine.prepareModel() }
            .onSuccess { modelState = ModelState.READY }
            .onFailure {
                modelState = ModelState.ERROR
                errorText = "Çeviri modeli hazırlanamadı: ${it.message ?: "Bilinmeyen hata"}"
            }
    }

    val picker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null && modelState == ModelState.READY) {
            scope.launch {
                isTranslating = true
                errorText = null
                runCatching { engine.translateScreenshot(uri) }
                    .onSuccess { result ->
                        sourceText = result.sourceText
                        translatedText = result.translatedText
                        if (result.hadText) {
                            history = historyStore.add(result.sourceText, result.translatedText)
                        } else {
                            errorText = "Bu görüntüde okunabilir metin bulunamadı."
                        }
                    }
                    .onFailure {
                        errorText = "Görüntü çevrilemedi: ${it.message ?: "Bilinmeyen hata"}"
                    }
                isTranslating = false
            }
        }
    }

    LaunchedEffect(Unit) { prepareModel() }
    DisposableEffect(engine) { onDispose { engine.close() } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF071020), PortalBackground, Color(0xFF0B1020))
                )
            )
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    text = "DraBornPortal",
                    color = PortalText,
                    fontWeight = FontWeight.Black,
                    fontSize = 30.sp
                )
                Text(
                    text = "PLAY • CAPTURE • TÜRKÇE",
                    color = PortalCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            item { StatusCard(modelState = modelState, isTranslating = isTranslating) }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = PortalSurface),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text("v0.1 • Yerel çeviri testi", color = PortalBlue, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Önce telefondaki bir oyun ekran görüntüsünü seç. OCR ve İngilizce → Türkçe çeviri telefon içinde çalışır.",
                            color = PortalText,
                            fontSize = 16.sp,
                            lineHeight = 23.sp
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            enabled = modelState == ModelState.READY && !isTranslating,
                            onClick = { picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isTranslating) "ÇEVRİLİYOR…" else "OYUN EKRAN GÖRÜNTÜSÜ SEÇ")
                        }
                        if (modelState == ModelState.ERROR) {
                            OutlinedButton(
                                onClick = { scope.launch { prepareModel() } },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("MODELİ TEKRAR HAZIRLA") }
                        }
                    }
                }
            }

            errorText?.let { message ->
                item { Text(message, color = Color(0xFFFFB4AB), fontWeight = FontWeight.SemiBold) }
            }

            if (translatedText.isNotBlank()) {
                item {
                    TranslationCard(
                        title = "TÜRKÇE",
                        text = translatedText,
                        accent = PortalCyan,
                        large = true
                    )
                }
                item {
                    TranslationCard(
                        title = "ORİJİNAL / OCR",
                        text = sourceText,
                        accent = PortalBlue,
                        large = false
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("GEÇMİŞ", color = PortalText, fontWeight = FontWeight.Black, fontSize = 18.sp)
                    if (history.isNotEmpty()) {
                        TextButton(onClick = {
                            historyStore.clear()
                            history = emptyList()
                        }) { Text("Temizle") }
                    }
                }
            }

            if (history.isEmpty()) {
                item { Text("Henüz çeviri yok.", color = PortalMuted) }
            } else {
                items(history.take(10), key = { it.createdAt }) { item -> HistoryCard(item) }
            }

            item {
                HorizontalDivider(color = Color(0xFF26314A))
                Spacer(Modifier.height(6.dp))
                Text(
                    "Sonraki aşama: Portal Create → Sony Cloud Gallery → otomatik yeni capture algılama. Sony bağlantısı deneysel ve çeviri motorundan bağımsız tutulur.",
                    color = PortalMuted,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            }
        }
    }
}

@Composable
private fun StatusCard(modelState: ModelState, isTranslating: Boolean) {
    val (label, accent) = when {
        isTranslating -> "Görüntü okunuyor ve çevriliyor" to PortalCyan
        modelState == ModelState.PREPARING -> "İngilizce → Türkçe modeli hazırlanıyor" to PortalBlue
        modelState == ModelState.READY -> "Yerel çeviri motoru hazır" to PortalCyan
        else -> "Çeviri modeli hazır değil" to Color(0xFFFFB4AB)
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1B2D)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (modelState == ModelState.PREPARING || isTranslating) {
                CircularProgressIndicator(modifier = Modifier.height(24.dp), strokeWidth = 3.dp)
            }
            Column {
                Text(label, color = PortalText, fontWeight = FontWeight.Bold)
                Text("Ücretli çeviri API'si: 0 TL", color = accent, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun TranslationCard(title: String, text: String, accent: Color, large: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = PortalSurface),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, color = accent, fontWeight = FontWeight.Black, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            Text(
                text = text,
                color = PortalText,
                fontSize = if (large) 21.sp else 15.sp,
                lineHeight = if (large) 29.sp else 21.sp,
                fontWeight = if (large) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun HistoryCard(item: TranslationHistoryItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1321)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                DateFormat.getTimeFormat(LocalContext.current).format(Date(item.createdAt)),
                color = PortalBlue,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(5.dp))
            Text(item.translated, color = PortalText, fontWeight = FontWeight.SemiBold, maxLines = 4)
        }
    }
}
