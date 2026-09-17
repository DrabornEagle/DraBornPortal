package com.draborneagle.drabornportal.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.draborneagle.drabornportal.translation.TranslationEngine
import com.draborneagle.drabornportal.translation.TranslationOverlayBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val PortalBackground = Color(0xFF050816)
private val PortalSurface = Color(0xFF10182D)
private val PortalSurface2 = Color(0xFF151F3B)
private val PortalBlue = Color(0xFF35A7FF)
private val PortalCyan = Color(0xFF45F2D0)
private val PortalPurple = Color(0xFF8D5CFF)
private val PortalPink = Color(0xFFFF5EC4)
private val PortalGold = Color(0xFFFFCF5A)
private val PortalText = Color(0xFFF7F9FF)
private val PortalMuted = Color(0xFFAEB9D6)
private enum class ModelState { PREPARING, READY, ERROR }

@Composable
fun DraBornPortalApp(incomingImageUri: Uri? = null) {
    var splashVisible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(1650)
        splashVisible = false
    }
    MaterialTheme {
        Surface(color = PortalBackground) {
            if (splashVisible) PortalAnimatedSplash() else TranslatorScreen(incomingImageUri)
        }
    }
}

@Composable
private fun PortalAnimatedSplash() {
    val transition = rememberInfiniteTransition(label = "portalSplash")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "portalRotation"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "portalPulse"
    )

    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.radialGradient(listOf(Color(0xFF173C72), Color(0xFF10092B), PortalBackground))
        ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier.width(116.dp).height(116.dp)
                        .graphicsLayer { rotationZ = rotation; alpha = 0.82f }
                        .border(4.dp, PortalCyan, RoundedCornerShape(34.dp))
                )
                Box(
                    Modifier.width(86.dp).height(86.dp)
                        .graphicsLayer { scaleX = pulse; scaleY = pulse }
                        .background(Brush.linearGradient(listOf(PortalPurple, PortalBlue)), RoundedCornerShape(27.dp))
                        .border(2.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(27.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("DP", color = Color.White, fontWeight = FontWeight.Black, fontSize = 28.sp)
                }
            }
            Spacer(Modifier.height(28.dp))
            Text("DraBornPortal", color = PortalText, fontWeight = FontWeight.Black, fontSize = 30.sp)
            Text("OYUN EKRANI TÜRKÇELEŞTİRİLİYOR", color = PortalCyan, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Spacer(Modifier.height(22.dp))
            CircularProgressIndicator(color = PortalCyan, strokeWidth = 3.dp, modifier = Modifier.width(32.dp).height(32.dp))
        }
    }
}

@Composable
private fun TranslatorScreen(incomingImageUri: Uri?) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val engine = remember { TranslationEngine(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var modelState by remember { mutableStateOf(ModelState.PREPARING) }
    var isTranslating by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var screenshot by remember { mutableStateOf<ImageBitmap?>(null) }
    var overlays by remember { mutableStateOf<List<TranslationOverlayBlock>>(emptyList()) }
    var imageWidth by remember { mutableStateOf(0) }
    var imageHeight by remember { mutableStateOf(0) }
    var translatedText by remember { mutableStateOf("") }
    var showOriginal by remember { mutableStateOf(false) }
    var showFullscreen by remember { mutableStateOf(false) }
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
            showFullscreen = false
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
                translatedText = result.translatedText
                if (!result.hadText) errorText = "Bu görüntüde çevrilebilir İngilizce metin bulunamadı."
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
            Brush.verticalGradient(
                listOf(Color(0xFF07152D), PortalBackground, Color(0xFF130B29), Color(0xFF07111E))
            )
        )
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { HeroHeader() }
            item { StatusCard(modelState, isTranslating) }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().border(
                        BorderStroke(1.dp, Color(0x5535A7FF)), RoundedCornerShape(26.dp)
                    ),
                    colors = CardDefaults.cardColors(containerColor = Color(0xE6111930)),
                    shape = RoundedCornerShape(26.dp)
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("v0.4.1 • DrabornEagle Oyun Çevirisi", color = PortalCyan, fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Ekran görüntüsünü seç. DrabornEagle Oyun Çevirisi görev metinlerini doğal Türkçeye dönüştürür ve görüntünün üzerinde doğru konumda gösterir.",
                            color = PortalText, fontSize = 16.sp, lineHeight = 24.sp
                        )
                        Spacer(Modifier.height(18.dp))
                        Button(
                            enabled = modelState == ModelState.READY && !isTranslating,
                            onClick = { picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) },
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PortalPurple,
                                disabledContainerColor = Color(0xFF29304B)
                            ),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text(if (isTranslating) "METİNLER OKUNUYOR…" else "OYUN GÖRÜNTÜSÜ SEÇ", fontWeight = FontWeight.Black)
                        }
                        if (modelState == ModelState.ERROR) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = { scope.launch { prepareModel() } }, modifier = Modifier.fillMaxWidth()) {
                                Text("MODELİ TEKRAR HAZIRLA")
                            }
                        }
                    }
                }
            }

            errorText?.let { message ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF3A1727)), shape = RoundedCornerShape(16.dp)) {
                        Text(message, color = Color(0xFFFFB7C9), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(14.dp))
                    }
                }
            }

            if (screenshot != null && imageWidth > 0 && imageHeight > 0) {
                item {
                    SectionTitle(
                        title = "OYUN GÖRÜNTÜSÜ",
                        subtitle = "${overlays.size} metin bölgesi çevrildi",
                        action = "TAM EKRAN • DOKUN"
                    )
                }
                item {
                    OriginalScreenshotPreview(
                        bitmap = screenshot!!,
                        imageWidth = imageWidth,
                        imageHeight = imageHeight,
                        onClick = { showFullscreen = true }
                    )
                }
            }

            item {
                SectionTitle(
                    title = "TÜRKÇE METİNLER",
                    subtitle = "Görüntüde okunan metinlerin sade ve okunabilir çevirisi",
                    action = null
                )
            }
            item { TranslationTextCard(translatedText) }

            item {
                HorizontalDivider(color = Color(0xFF253456))
                Spacer(Modifier.height(8.dp))
                Text("PSN GÜVENLİ MOD", color = PortalCyan, fontWeight = FontWeight.Black, fontSize = 12.sp)
                Text(
                    "Sony hesabı veya tokenı DraBornPortal'a verilmez. PlayStation App'in resmi Paylaş akışı kullanılır.",
                    color = PortalMuted, fontSize = 13.sp, lineHeight = 19.sp
                )
            }
        }
    }

    if (showFullscreen && screenshot != null && imageWidth > 0 && imageHeight > 0) {
        FullscreenTranslationViewer(
            bitmap = screenshot!!,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            blocks = if (showOriginal) emptyList() else overlays,
            showOriginal = showOriginal,
            onToggleOriginal = { showOriginal = !showOriginal },
            onDismiss = { showFullscreen = false }
        )
    }
}

@Composable
private fun HeroHeader() {
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("DraBornPortal", color = PortalText, fontWeight = FontWeight.Black, fontSize = 32.sp)
            Text(
                "v0.4.1",
                color = Color(0xFF08101E),
                fontWeight = FontWeight.Black,
                fontSize = 12.sp,
                modifier = Modifier.background(
                    Brush.horizontalGradient(listOf(PortalCyan, PortalBlue)), RoundedCornerShape(999.dp)
                ).padding(horizontal = 9.dp, vertical = 5.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text("PLAY • CAPTURE • TÜRKÇE", color = PortalCyan, fontWeight = FontWeight.Black, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            MiniChip("CİHAZ İÇİ OCR", PortalBlue)
            MiniChip("DRABORNEAGLE", PortalCyan)
            MiniChip("EN → TR", PortalPink)
        }
    }
}

@Composable
private fun MiniChip(text: String, accent: Color) {
    Text(
        text,
        color = accent,
        fontSize = 10.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier.background(accent.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
            .padding(horizontal = 9.dp, vertical = 6.dp)
    )
}

@Composable
private fun SectionTitle(title: String, subtitle: String, action: String?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(title, color = PortalText, fontWeight = FontWeight.Black, fontSize = 19.sp)
            Text(subtitle, color = PortalMuted, fontSize = 12.sp, lineHeight = 17.sp)
        }
        if (action != null) {
            Text(action, color = PortalBlue, fontWeight = FontWeight.Black, fontSize = 11.sp, modifier = Modifier.padding(start = 10.dp, top = 3.dp))
        }
    }
}

@Composable
private fun TranslationTextCard(translatedText: String) {
    Card(
        modifier = Modifier.fillMaxWidth().border(1.dp, Color(0x3345F2D0), RoundedCornerShape(22.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xE60D1428)),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.width(4.dp).height(20.dp).background(PortalCyan, RoundedCornerShape(9.dp)))
                Text(if (translatedText.isBlank()) "Çeviri bekleniyor" else "Okunabilir metin görünümü", color = PortalCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = translatedText.ifBlank { "Henüz çeviri yok." },
                color = if (translatedText.isBlank()) PortalMuted else PortalText,
                fontSize = 17.sp,
                lineHeight = 27.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun OriginalScreenshotPreview(bitmap: ImageBitmap, imageWidth: Int, imageHeight: Int, onClick: () -> Unit) {
    val ratio = imageWidth.toFloat() / imageHeight.toFloat()
    Box(
        modifier = Modifier.fillMaxWidth().aspectRatio(ratio).clip(RoundedCornerShape(22.dp))
            .border(1.dp, Color(0x5535A7FF), RoundedCornerShape(22.dp))
            .background(Color.Black).clickable(onClick = onClick)
    ) {
        Image(bitmap = bitmap, contentDescription = "Oyun ekran görüntüsü", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color(0xB8050816)))
            )
        )
        Text(
            "TAM EKRAN TÜRKÇE ÇEVİRİ  ›",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.align(Alignment.BottomCenter)
                .background(Brush.horizontalGradient(listOf(Color(0xE68D5CFF), Color(0xE635A7FF))), RoundedCornerShape(999.dp))
                .padding(horizontal = 15.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun FullscreenTranslationViewer(
    bitmap: ImageBitmap,
    imageWidth: Int,
    imageHeight: Int,
    blocks: List<TranslationOverlayBlock>,
    showOriginal: Boolean,
    onToggleOriginal: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF02040B)) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().background(
                        Brush.horizontalGradient(listOf(Color(0xFF11172B), Color(0xFF17102E), Color(0xFF0B1D31)))
                    ).padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onDismiss) { Text("KAPAT", color = PortalBlue, fontWeight = FontWeight.Black, fontSize = 11.sp) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (showOriginal) "ORİJİNAL GÖRÜNTÜ" else "AKILLI TÜRKÇE KATMAN", color = PortalText, fontWeight = FontWeight.Black, fontSize = 13.sp)
                        Text("Yakınlaştır • sürükle • metinleri rahat oku", color = PortalMuted, fontSize = 9.sp)
                    }
                    TextButton(onClick = onToggleOriginal) {
                        Text(if (showOriginal) "TÜRKÇE" else "ORİJİNAL", color = if (showOriginal) PortalCyan else PortalGold, fontWeight = FontWeight.Black, fontSize = 11.sp)
                    }
                }
                Box(Modifier.fillMaxSize()) {
                    ZoomableOverlayScreenshot(bitmap, imageWidth, imageHeight, blocks, Modifier.fillMaxSize())
                    if (!showOriginal && blocks.isNotEmpty()) {
                        Text(
                            "${blocks.size} ÇEVİRİ • DrabornEagle Oyun Çevirisi",
                            color = PortalCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.BottomCenter)
                                .padding(bottom = 14.dp)
                                .background(Color(0xE60B1426), RoundedCornerShape(999.dp))
                                .border(1.dp, Color(0x5545F2D0), RoundedCornerShape(999.dp))
                                .padding(horizontal = 13.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomableOverlayScreenshot(
    bitmap: ImageBitmap,
    imageWidth: Int,
    imageHeight: Int,
    blocks: List<TranslationOverlayBlock>,
    modifier: Modifier = Modifier
) {
    var scale by remember(bitmap) { mutableFloatStateOf(1f) }
    var pan by remember(bitmap) { mutableStateOf(Offset.Zero) }
    val ratio = imageWidth.toFloat() / imageHeight.toFloat()

    BoxWithConstraints(
        modifier = modifier.background(Color.Black).pointerInput(bitmap) {
            detectTransformGestures { _, gesturePan, gestureZoom, _ ->
                val nextScale = (scale * gestureZoom).coerceIn(1f, 8f)
                scale = nextScale
                pan = if (nextScale <= 1.01f) Offset.Zero else pan + gesturePan
            }
        },
        contentAlignment = Alignment.Center
    ) {
        val containerRatio = if (maxHeight.value > 0f) maxWidth.value / maxHeight.value else ratio
        val fittedWidth = if (containerRatio > ratio) maxHeight * ratio else maxWidth
        val fittedHeight = if (containerRatio > ratio) maxHeight else maxWidth / ratio

        Box(
            modifier = Modifier.width(fittedWidth).height(fittedHeight).graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = pan.x
                translationY = pan.y
            }
        ) {
            Image(bitmap = bitmap, contentDescription = "Yakınlaştırılabilir oyun ekran görüntüsü", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            if (blocks.isNotEmpty()) {
                TranslationOverlayLayer(imageWidth, imageHeight, blocks)
            }
        }
    }
}

@Composable
private fun TranslationOverlayLayer(imageWidth: Int, imageHeight: Int, blocks: List<TranslationOverlayBlock>) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        blocks.forEach { block ->
            val x = maxWidth * (block.left.toFloat() / imageWidth)
            val y = maxHeight * (block.top.toFloat() / imageHeight)
            val originalW = maxWidth * ((block.right - block.left).toFloat() / imageWidth)
            val originalH = maxHeight * ((block.bottom - block.top).toFloat() / imageHeight)
            val sourceLength = block.source.filterNot { it.isWhitespace() }.length.coerceAtLeast(1)
            val translatedLength = block.translated.filterNot { it.isWhitespace() }.length.coerceAtLeast(1)
            val lengthRatio = translatedLength.toFloat() / sourceLength
            val titleLike = block.source.length <= 42 && !block.source.contains(".") && !block.source.contains("?")
            val widthFactor = if (titleLike) lengthRatio.coerceIn(1.08f, 1.48f) else 1.08f
            val heightFactor = if (titleLike) 1.55f else lengthRatio.coerceIn(1.18f, 1.65f)
            val availableW = (maxWidth - x).coerceAtLeast(1.dp)
            val availableH = (maxHeight - y).coerceAtLeast(1.dp)
            val w = (originalW * widthFactor).coerceAtMost(availableW)
            val h = (originalH * heightFactor).coerceAtMost(availableH)
            AutoFitTranslationBlock(
                text = block.translated,
                modifier = Modifier.offset(x, y).width(w).height(h)
            )
        }
    }
}

@Composable
private fun AutoFitTranslationBlock(text: String, modifier: Modifier = Modifier) {
    var fittedSize by remember(text) { mutableFloatStateOf(10.5f) }
    Box(
        modifier = modifier
            .background(Color(0xF208101F), RoundedCornerShape(2.dp))
            .border(0.5.dp, Color(0x6635A7FF), RoundedCornerShape(2.dp))
            .padding(horizontal = 1.dp)
    ) {
        Text(
            text = text,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = fittedSize.sp,
            lineHeight = (fittedSize * 1.02f).sp,
            maxLines = 12,
            overflow = TextOverflow.Clip,
            onTextLayout = { result ->
                if (result.hasVisualOverflow && fittedSize > 2.6f) {
                    fittedSize = (fittedSize - 0.35f).coerceAtLeast(2.6f)
                }
            }
        )
    }
}

@Composable
private fun StatusCard(modelState: ModelState, isTranslating: Boolean) {
    val (label, detail, accent) = when {
        isTranslating -> Triple("Oyun metinleri taranıyor", "OCR + Türkçe çeviri çalışıyor", PortalCyan)
        modelState == ModelState.PREPARING -> Triple("Çeviri motoru hazırlanıyor", "İngilizce → Türkçe cihaz içi model", PortalBlue)
        modelState == ModelState.READY -> Triple("Çeviri motoru hazır", "Görsel seç ve hemen Türkçeleştir", PortalCyan)
        else -> Triple("Çeviri modeli hazır değil", "Tekrar hazırlamayı dene", Color(0xFFFF8AAE))
    }
    Card(
        modifier = Modifier.fillMaxWidth().border(1.dp, accent.copy(alpha = 0.3f), RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xE60D1A31)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (modelState == ModelState.PREPARING || isTranslating) CircularProgressIndicator(modifier = Modifier.width(26.dp).height(26.dp), strokeWidth = 3.dp, color = accent)
            else Box(Modifier.width(10.dp).height(10.dp).background(accent, RoundedCornerShape(999.dp)))
            Column(Modifier.weight(1f)) {
                Text(label, color = PortalText, fontWeight = FontWeight.Black, fontSize = 15.sp)
                Text(detail, color = PortalMuted, fontSize = 12.sp)
            }
            Text("DRABORN AI", color = accent, fontWeight = FontWeight.Black, fontSize = 10.sp)
        }
    }
}
