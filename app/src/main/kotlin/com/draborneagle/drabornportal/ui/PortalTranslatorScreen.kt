package com.draborneagle.drabornportal.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

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
                        Text("v0.2 • Tam ekran görüntü çevirisi", color = PortalBlue, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Ekran görüntüsünü seç veya PlayStation App'te Yakalananlar → Paylaş → DraBornPortal de. Ana ekranda görüntü temiz kalır; görüntüye dokununca Türkçe çeviri tam ekranda açılır.",
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
                    Column {
                        Text("OYUN GÖRÜNTÜSÜ", color = PortalText, fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Text("${overlays.size} metin bölgesi çevrildi • Tam ekran çeviri için görüntüye dokun", color = PortalMuted, fontSize = 12.sp)
                    }
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
                Text("ÇEVİRİ METNİ", color = PortalText, fontWeight = FontWeight.Black, fontSize = 18.sp)
                Text("Görüntüde bulunan Türkçe çevirinin düz metin hali", color = PortalMuted, fontSize = 12.sp)
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1321)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = translatedText.ifBlank { "Henüz çeviri yok." },
                        color = if (translatedText.isBlank()) PortalMuted else PortalText,
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

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
private fun OriginalScreenshotPreview(
    bitmap: ImageBitmap,
    imageWidth: Int,
    imageHeight: Int,
    onClick: () -> Unit
) {
    val ratio = imageWidth.toFloat() / imageHeight.toFloat()
    Box(
        modifier = Modifier.fillMaxWidth().aspectRatio(ratio).clip(RoundedCornerShape(18.dp))
            .background(Color.Black).clickable(onClick = onClick)
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = "Oyun ekran görüntüsü - tam ekran aç",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )
        Text(
            "TAM EKRAN ÇEVİRİ • DOKUN",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.align(Alignment.BottomCenter)
                .background(Color(0xCC0B1020), RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                .padding(horizontal = 14.dp, vertical = 7.dp)
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
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF03050A)) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().background(Color(0xF20B1020)).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onDismiss) { Text("KAPAT") }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("TAM EKRAN ÇEVİRİ", color = PortalText, fontWeight = FontWeight.Black, fontSize = 14.sp)
                        Text("İki parmakla yakınlaştır • sürükleyerek gez", color = PortalMuted, fontSize = 10.sp)
                    }
                    TextButton(onClick = onToggleOriginal) {
                        Text(if (showOriginal) "TÜRKÇE" else "ORİJİNAL")
                    }
                }
                ZoomableOverlayScreenshot(
                    bitmap = bitmap,
                    imageWidth = imageWidth,
                    imageHeight = imageHeight,
                    blocks = blocks,
                    modifier = Modifier.fillMaxSize()
                )
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
            Image(
                bitmap = bitmap,
                contentDescription = "Yakınlaştırılabilir oyun ekran görüntüsü",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )
            TranslationOverlayLayer(imageWidth, imageHeight, blocks)
        }
    }
}

@Composable
private fun TranslationOverlayLayer(
    imageWidth: Int,
    imageHeight: Int,
    blocks: List<TranslationOverlayBlock>
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        blocks.forEach { block ->
            val x = maxWidth * (block.left.toFloat() / imageWidth)
            val y = maxHeight * (block.top.toFloat() / imageHeight)
            val w = maxWidth * ((block.right - block.left).toFloat() / imageWidth)
            val h = maxHeight * ((block.bottom - block.top).toFloat() / imageHeight)
            val chars = block.translated.length.coerceAtLeast(1)
            val fittedSp = sqrt(((w.value.coerceAtLeast(6f) * h.value.coerceAtLeast(6f)) / chars) * 1.55f)
                .coerceIn(5.5f, 13f).sp

            Box(
                modifier = Modifier.offset(x, y).width(w).height(h)
                    .background(Color(0xDC11182A), RoundedCornerShape(3.dp))
                    .padding(horizontal = 2.dp, vertical = 1.dp)
            ) {
                Text(
                    text = block.translated,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = fittedSp,
                    lineHeight = fittedSp,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis
                )
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
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
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
