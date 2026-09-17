package com.draborneagle.drabornportal.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.draborneagle.drabornportal.translation.TranslationEngine
import com.draborneagle.drabornportal.translation.TranslationHistoryItem
import com.draborneagle.drabornportal.translation.TranslationHistoryStore
import com.draborneagle.drabornportal.translation.TranslationOverlayBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

private val PortalBackground = Color(0xFF080B14)
private val PortalSurface = Color(0xFF11182A)
private val PortalBlue = Color(0xFF36A7FF)
private val PortalCyan = Color(0xFF4EF2D1)
private val PortalText = Color(0xFFF5F8FF)
private val PortalMuted = Color(0xFFA9B3C9)
private enum class ModelState { PREPARING, READY, ERROR }

@Composable
fun DraBornPortalApp(incomingImageUri: Uri? = null) {
    MaterialTheme { Surface(color = PortalBackground) { TranslatorScreen(incomingImageUri) } }
}

@Composable
private fun TranslatorScreen(incomingImageUri: Uri?) {
    val context = LocalContext.current
    val engine = remember { TranslationEngine(context.applicationContext) }
    val historyStore = remember { TranslationHistoryStore(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var modelState by remember { mutableStateOf(ModelState.PREPARING) }
    var isTranslating by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var history by remember { mutableStateOf(historyStore.load()) }
    var screenshot by remember { mutableStateOf<ImageBitmap?>(null) }
    var overlays by remember { mutableStateOf<List<TranslationOverlayBlock>>(emptyList()) }
    var imageWidth by remember { mutableStateOf(0) }
    var imageHeight by remember { mutableStateOf(0) }
    var sourceText by remember { mutableStateOf("") }
    var translatedText by remember { mutableStateOf("") }
    var showOriginal by remember { mutableStateOf(false) }
    var lastIncoming by remember { mutableStateOf<String?>(null) }

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

    fun processUri(uri: Uri) {
        if (modelState != ModelState.READY || isTranslating) return
        scope.launch {
            isTranslating = true
            errorText = null
            showOriginal = false
            runCatching {
                val bitmap = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                } ?: error("Görüntü açılamadı")
                val result = engine.translateScreenshot(uri)
                bitmap.asImageBitmap() to result
            }.onSuccess { (bitmap, result) ->
                screenshot = bitmap
                imageWidth = result.imageWidth.takeIf { it > 0 } ?: bitmap.width
                imageHeight = result.imageHeight.takeIf { it > 0 } ?: bitmap.height
                overlays = result.overlays
                sourceText = result.sourceText
                translatedText = result.translatedText
                if (result.hadText) history = historyStore.add(result.sourceText, result.translatedText)
                else errorText = "Bu görüntüde çevrilebilir İngilizce metin bulunamadı."
            }.onFailure {
                errorText = "Görüntü çevrilemedi: ${it.message ?: "Bilinmeyen hata"}"
            }
            isTranslating = false
        }
    }

    val picker = rememberLauncherForActivityResult(PickVisualMedia()) { uri -> uri?.let(::processUri) }
    LaunchedEffect(Unit) { prepareModel() }
    LaunchedEffect(incomingImageUri, modelState) {
        val key = incomingImageUri?.toString()
        if (incomingImageUri != null && modelState == ModelState.READY && key != lastIncoming) {
            lastIncoming = key
            processUri(incomingImageUri)
        }
    }
    DisposableEffect(engine) { onDispose { engine.close() } }

    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF071020), PortalBackground, Color(0xFF0B1020)))
        )
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text("DraBornPortal", color = PortalText, fontWeight = FontWeight.Black, fontSize = 30.sp)
                Text("PLAY • CAPTURE • TÜRKÇE", color = PortalCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            item { StatusCard(modelState, isTranslating) }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = PortalSurface),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text("v0.2 • Görüntü üstü çeviri", color = PortalBlue, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Ekran görüntüsü seç veya PlayStation App'te Yakalananlar → Paylaş → DraBornPortal de. Türkçe, oyundaki İngilizce metnin bulunduğu yere yazılır.",
                            color = PortalText, fontSize = 16.sp, lineHeight = 23.sp
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            enabled = modelState == ModelState.READY && !isTranslating,
                            onClick = { picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (isTranslating) "ÇEVRİLİYOR…" else "OYUN EKRAN GÖRÜNTÜSÜ SEÇ") }
                        if (modelState == ModelState.ERROR) {
                            OutlinedButton(onClick = { scope.launch { prepareModel() } }, modifier = Modifier.fillMaxWidth()) {
                                Text("MODELİ TEKRAR HAZIRLA")
                            }
                        }
                    }
                }
            }
            errorText?.let { message -> item { Text(message, color = Color(0xFFFFB4AB), fontWeight = FontWeight.SemiBold) } }

            if (screenshot != null && imageWidth > 0 && imageHeight > 0) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("OYUN GÖRÜNTÜSÜ", color = PortalText, fontWeight = FontWeight.Black, fontSize = 18.sp)
                            Text("${overlays.size} metin bölgesi çevrildi", color = PortalMuted, fontSize = 12.sp)
                        }
                        TextButton(onClick = { showOriginal = !showOriginal }) {
                            Text(if (showOriginal) "TÜRKÇEYİ GÖSTER" else "ORİJİNALİ GÖSTER")
                        }
                    }
                }
                item {
                    OverlayScreenshot(
                        bitmap = screenshot!!,
                        imageWidth = imageWidth,
                        imageHeight = imageHeight,
                        blocks = if (showOriginal) emptyList() else overlays
                    )
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("GEÇMİŞ", color = PortalText, fontWeight = FontWeight.Black, fontSize = 18.sp)
                    if (history.isNotEmpty()) TextButton(onClick = { historyStore.clear(); history = emptyList() }) { Text("Temizle") }
                }
            }
            if (history.isEmpty()) item { Text("Henüz çeviri yok.", color = PortalMuted) }
            else items(history.take(10), key = { it.createdAt }) { item -> HistoryCard(item) }

            item {
                HorizontalDivider(color = Color(0xFF26314A))
                Spacer(Modifier.height(6.dp))
                Text(
                    "PSN güvenli modu: Sony hesabı veya tokenı DraBornPortal'a verilmez. PlayStation App'in resmi Paylaş akışı kullanılır.",
                    color = PortalMuted, fontSize = 13.sp, lineHeight = 19.sp
                )
            }
        }
    }
}

@Composable
private fun OverlayScreenshot(bitmap: ImageBitmap, imageWidth: Int, imageHeight: Int, blocks: List<TranslationOverlayBlock>) {
    val ratio = imageWidth.toFloat() / imageHeight.toFloat()
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().aspectRatio(ratio).clip(RoundedCornerShape(18.dp)).background(Color.Black)
    ) {
        Image(bitmap = bitmap, contentDescription = "Oyun ekran görüntüsü", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        blocks.forEach { block ->
            val x = maxWidth * (block.left.toFloat() / imageWidth)
            val y = maxHeight * (block.top.toFloat() / imageHeight)
            val w = maxWidth * ((block.right - block.left).toFloat() / imageWidth)
            val rawHeight = maxHeight * ((block.bottom - block.top).toFloat() / imageHeight)
            val boxHeight = if (rawHeight < 24.dp) 24.dp else rawHeight
            val textSize = if ((block.bottom - block.top) > 80) 14.sp else 11.sp
            Box(
                modifier = Modifier.offset(x, y).width(w).heightIn(min = boxHeight)
                    .background(Color(0xE9141B2A), RoundedCornerShape(5.dp)).padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(block.translated, color = Color.White, fontWeight = FontWeight.Bold, fontSize = textSize, lineHeight = textSize * 1.12f)
            }
        }
    }
}

@Composable
private fun StatusCard(modelState: ModelState, isTranslating: Boolean) {
    val (label, accent) = when {
        isTranslating -> "Metin bölgeleri okunuyor ve çevriliyor" to PortalCyan
        modelState == ModelState.PREPARING -> "İngilizce → Türkçe modeli hazırlanıyor" to PortalBlue
        modelState == ModelState.READY -> "Yerel çeviri motoru hazır" to PortalCyan
        else -> "Çeviri modeli hazır değil" to Color(0xFFFFB4AB)
    }
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1B2D)), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (modelState == ModelState.PREPARING || isTranslating) CircularProgressIndicator(modifier = Modifier.height(24.dp), strokeWidth = 3.dp)
            Column { Text(label, color = PortalText, fontWeight = FontWeight.Bold); Text("Ücretli çeviri API'si: 0 TL", color = accent, fontSize = 13.sp) }
        }
    }
}

@Composable
private fun HistoryCard(item: TranslationHistoryItem) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1321)), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(DateFormat.getTimeFormat(LocalContext.current).format(Date(item.createdAt)), color = PortalBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text(item.translated, color = PortalText, fontWeight = FontWeight.SemiBold, maxLines = 4)
        }
    }
}
