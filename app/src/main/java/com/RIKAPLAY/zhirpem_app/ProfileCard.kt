package com.RIKAPLAY.zhirpem_app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.RuntimeShader
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

// ======================================================================
//  ССЫЛКИ НА ПРОФИЛЬ (то, что зашито в QR) И ОБРАБОТКА ВХОДЯЩЕЙ ССЫЛКИ
// ======================================================================

object ProfileLinks {
    const val SCHEME = "zhirpem"

    /**
     * Адрес веб-версии на Vercel, например "https://zhirpem.vercel.app".
     * Пока пусто, в QR зашита прямая ссылка zhirpem://user/ник — она открывает приложение,
     * но многие камеры не считают её кликабельной. Если указать адрес сайта, в QR будет
     * https-ссылка: любая камера её откроет, а страница /u/ник (см. web/src/app/u) сама
     * перекинет в приложение.
     */
    const val WEB_BASE = ""

    fun forUser(username: String): String {
        val clean = username.replace("@", "").trim()
        return if (WEB_BASE.isNotBlank()) "${WEB_BASE.trimEnd('/')}/u/$clean" else "$SCHEME://user/$clean"
    }

    /** Достаёт ник из zhirpem://user/ник или из <WEB_BASE>/u/ник. */
    fun parse(uri: Uri?): String? {
        if (uri == null) return null
        val segments = uri.pathSegments
        val name = when {
            uri.scheme == SCHEME && uri.host == "user" -> segments.firstOrNull()
            (uri.scheme == "https" || uri.scheme == "http") && WEB_BASE.isNotBlank() &&
                uri.host == Uri.parse(WEB_BASE).host && segments.size >= 2 && segments[0] == "u" -> segments[1]
            else -> null
        }
        return name?.replace("@", "")?.trim()?.takeIf { it.isNotEmpty() }
    }
}

/** Профиль, который нужно открыть после перехода по QR/ссылке. */
object PendingProfileLink {
    var username by mutableStateOf<String?>(null)

    fun consume(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        ProfileLinks.parse(intent.data)?.let {
            username = it
            intent.data = null
        }
    }
}

// ======================================================================
//  ШЕЙДЕР ПРЕЛОМЛЕНИЯ (AGSL, Android 13+)
// ======================================================================

internal const val LENS_AGSL = """
uniform shader content;
uniform float2 center;
uniform float radius;
uniform float strength;
uniform float rim;

half4 main(float2 p) {
    float2 d = p - center;
    float r = length(d);
    if (r >= radius) {
        return content.eval(p);
    }
    float k = r / radius;
    float f = 1.0 - strength * (1.0 - k * k);
    float ab = 0.035 * strength * k;
    half4 c = content.eval(center + d * f);
    half rr = content.eval(center + d * (f * (1.0 - ab))).r;
    half bb = content.eval(center + d * (f * (1.0 + ab))).b;
    float edge = smoothstep(0.86, 1.0, k);
    half3 rgb = half3(rr, c.g, bb);
    rgb = mix(rgb, half3(c.a), edge * rim * strength);
    rgb = min(rgb, half3(c.a));
    return half4(rgb, c.a);
}
"""

@RequiresApi(33)
internal fun createLensShader(): RuntimeShader = RuntimeShader(LENS_AGSL)

@RequiresApi(33)
internal fun buildLensEffect(
    shader: RuntimeShader,
    cx: Float,
    cy: Float,
    radius: Float,
    strength: Float,
    rim: Float = 0.18f
): androidx.compose.ui.graphics.RenderEffect {
    shader.setFloatUniform("center", cx, cy)
    shader.setFloatUniform("radius", radius)
    shader.setFloatUniform("strength", strength)
    shader.setFloatUniform("rim", rim)
    return android.graphics.RenderEffect
        .createRuntimeShaderEffect(shader, "content")
        .asComposeRenderEffect()
}

// Чёрный фон логотипа становится прозрачным, глаза и надпись — белыми: чёрно-белое «стекло»
private val LogoWhiteMask = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            0f, 0f, 0f, 0f, 255f,
            0f, 0f, 0f, 0f, 255f,
            0f, 0f, 0f, 0f, 255f,
            1.1f, 2.2f, 0.4f, 0f, 0f
        )
    )
)

private const val CARD_RATIO = 1.586f // пропорции банковской карты
private const val LOGO_X = 0.76f
private const val LOGO_Y = 0.43f

// ======================================================================
//  ДИАЛОГ С КАРТОЙ
// ======================================================================

@Composable
fun ProfileCardDialog(
    username: String,
    fallbackName: String,
    fallbackAvatarUrl: String?,
    fallbackStatus: String?,
    onDismiss: () -> Unit,
    onOpenProfile: () -> Unit
) {
    val cleanUsername = remember(username) { username.replace("@", "").trim() }
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val animationsEnabled = LocalAnimationsEnabled.current

    // ----- данные пользователя -----
    var avatarUrl by remember { mutableStateOf(fallbackAvatarUrl) }
    var status by remember { mutableStateOf(fallbackStatus.orEmpty()) }
    var bannerColorHex by remember { mutableStateOf("#808080") }
    var bannerUrl by remember { mutableStateOf<String?>(null) }
    var equippedBanner by remember { mutableStateOf<String?>(null) }
    var equippedFrame by remember { mutableStateOf<String?>(null) }
    var privacyLastSeen by remember { mutableStateOf("all") }
    var isOnline by remember { mutableStateOf(false) }
    var followers by remember { mutableIntStateOf(0) }

    DisposableEffect(cleanUsername) {
        val db = FirebaseFirestore.getInstance()
        val rtdb = FirebaseDatabase.getInstance()
        var alive = true

        val userReg = db.collection("users").document(cleanUsername)
            .addSnapshotListener { doc, _ ->
                if (doc != null && doc.exists()) {
                    avatarUrl = doc.getString("avatarUrl") ?: avatarUrl
                    status = doc.getString("status") ?: ""
                    bannerColorHex = doc.getString("bannerColor") ?: "#808080"
                    bannerUrl = doc.getString("bannerUrl")
                    equippedBanner = doc.getString("equippedBanner")?.takeIf { it.isNotEmpty() }
                    equippedFrame = doc.getString("equippedFrame")?.takeIf { it.isNotEmpty() }
                    privacyLastSeen = doc.getString("privacyLastSeen") ?: "all"
                }
            }

        // Один агрегирующий запрос вместо подписки на весь список подписчиков
        db.collection("follows").whereEqualTo("following", cleanUsername)
            .count().get(AggregateSource.SERVER)
            .addOnSuccessListener { if (alive) followers = it.count.toInt() }

        val statusRef = rtdb.getReference("status/$cleanUsername/state")
        val statusListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                isOnline = snapshot.getValue(String::class.java) == "online"
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        statusRef.addValueEventListener(statusListener)

        onDispose {
            alive = false
            userReg.remove()
            statusRef.removeEventListener(statusListener)
        }
    }

    val bannerColor = remember(bannerColorHex) {
        try { Color(android.graphics.Color.parseColor(bannerColorHex)) } catch (e: Exception) { Color(0xFF808080) }
    }
    val showOnline = isOnline && privacyLastSeen != "none"

    // ----- состояние жестов -----
    var showQr by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    var lensHeld by remember { mutableStateOf(false) }
    var touch by remember { mutableStateOf<Offset?>(null) }
    val rotX = remember { Animatable(0f) }
    val rotY = remember { Animatable(0f) }

    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 260f)) }

    val lens by animateFloatAsState(
        targetValue = if (lensHeld) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
        label = "lens"
    )
    val qrShift by animateFloatAsState(
        targetValue = if (showQr) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 300f),
        label = "qrShift"
    )
    val pressScale by animateFloatAsState(if (pressed) 0.975f else 1f, label = "pressScale")

    // Лёгкое покачивание карты, пока её не трогают
    val sway by rememberInfiniteTransition(label = "sway").animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "swayValue"
    )

    val lensShader = remember { if (Build.VERSION.SDK_INT >= 33) createLensShader() else null }

    val link = remember(cleanUsername) { ProfileLinks.forUser(cleanUsername) }
    val qrMatrix = remember(link) { buildQrMatrix(link) }

    Dialog(
        onDismissRequest = { if (showQr) showQr = false else onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f * appear.value))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    if (showQr) showQr = false else onDismiss()
                },
            contentAlignment = Alignment.Center
        ) {
            val cardW = minOf(maxWidth - 32.dp, 400.dp)
            val cardH = cardW / CARD_RATIO
            val screenH = maxHeight

            // ================= КАРТА =================
            Box(
                modifier = Modifier
                    .width(cardW)
                    .height(cardH)
                    .graphicsLayer {
                        val idle = touch == null && animationsEnabled
                        rotationY = rotY.value + if (idle) sway * 3f else 0f
                        rotationX = rotX.value + if (idle) sway * 1.2f else 0f
                        cameraDistance = 16f * density
                        val s = (0.86f + 0.14f * appear.value) * pressScale * (1f - 0.1f * qrShift)
                        scaleX = s
                        scaleY = s
                        alpha = appear.value.coerceIn(0f, 1f)
                        translationY = -(screenH.toPx() * 0.29f) * qrShift + (1f - appear.value) * 40.dp.toPx()
                    }
                    .clip(RoundedCornerShape(22.dp))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                pressed = true
                                tryAwaitRelease()
                                pressed = false
                            },
                            onLongPress = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showQr = true
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        fun tilt(pos: Offset) {
                            val nx = (pos.x / size.width - 0.5f) * 2f
                            val ny = (pos.y / size.height - 0.5f) * 2f
                            touch = pos
                            scope.launch {
                                rotY.snapTo((nx * 14f).coerceIn(-16f, 16f))
                                rotX.snapTo((-ny * 14f).coerceIn(-16f, 16f))
                            }
                        }
                        fun release() {
                            touch = null
                            scope.launch { rotY.animateTo(0f, spring(0.5f, 180f)) }
                            scope.launch { rotX.animateTo(0f, spring(0.5f, 180f)) }
                        }
                        detectDragGestures(
                            onDragStart = { tilt(it) },
                            onDragEnd = { release() },
                            onDragCancel = { release() },
                            onDrag = { change, _ ->
                                change.consume()
                                tilt(change.position)
                            }
                        )
                    }
                    .drawWithContent {
                        drawContent()
                        // блик, который ездит за пальцем (или за наклоном)
                        val t = touch
                        val cx = t?.x ?: (size.width * (0.5f + 0.32f * sway))
                        val cy = t?.y ?: (size.height * 0.28f)
                        drawRect(
                            Brush.radialGradient(
                                listOf(Color.White.copy(alpha = if (t != null) 0.30f else 0.16f), Color.Transparent),
                                center = Offset(cx, cy),
                                radius = size.width * 0.62f
                            )
                        )
                        // диагональная «фольга»
                        drawRect(
                            Brush.linearGradient(
                                listOf(Color.Transparent, Color.White.copy(alpha = 0.11f), Color.Transparent),
                                start = Offset(cx - size.width * 0.35f, 0f),
                                end = Offset(cx + size.width * 0.35f, size.height)
                            )
                        )
                        // кромка карты
                        drawRoundRect(
                            Brush.linearGradient(
                                listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.32f))
                            ),
                            cornerRadius = CornerRadius(22.dp.toPx()),
                            style = Stroke(width = 1.2.dp.toPx())
                        )
                    }
            ) {
                // ---- слой 1: фон (баннер) + текстура пластика. На нём работает преломление ----
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val shader = lensShader
                            renderEffect = if (Build.VERSION.SDK_INT >= 33 && shader != null && lens > 0.01f) {
                                val r = size.width * (0.12f + 0.11f * lens)
                                buildLensEffect(shader, size.width * LOGO_X, size.height * LOGO_Y, r, 0.62f * lens)
                            } else null
                        }
                ) {
                    when {
                        equippedBanner != null -> {
                            Box(Modifier.fillMaxSize().background(bannerColor))
                            ShopBannerArt(bannerId = equippedBanner)
                        }
                        !bannerUrl.isNullOrEmpty() -> {
                            Box(Modifier.fillMaxSize().background(bannerColor))
                            AsyncImage(
                                model = bannerUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        else -> {
                            Box(
                                Modifier.fillMaxSize().background(
                                    Brush.linearGradient(
                                        listOf(
                                            bannerColor.lighten(0.18f),
                                            bannerColor,
                                            bannerColor.darken(0.35f)
                                        )
                                    )
                                )
                            )
                        }
                    }
                    CardPlasticTexture()
                    // затемнение под текстом в левом нижнем углу
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.linearGradient(
                                0f to Color.Transparent,
                                0.55f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.55f),
                                start = Offset(900f, 0f),
                                end = Offset(0f, 900f)
                            )
                        )
                    )
                }

                // ---- слой 2: содержимое карты ----
                // Аватарка
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 18.dp, top = 18.dp)
                        .size(56.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f))
                            .border(2.dp, Color.White.copy(alpha = 0.7f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!avatarUrl.isNullOrEmpty()) {
                            AsyncImage(
                                model = CloudinaryUrls.avatar(avatarUrl),
                                contentDescription = "Аватар",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(
                                text = (fallbackName.ifEmpty { cleanUsername }).take(1).uppercase(),
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 22.sp
                            )
                        }
                    }
                    AvatarFrameOverlay(frameId = equippedFrame, bannerColor = bannerColor)
                }

                // Кнопка «Профиль» (овальная, справа сверху)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 20.dp, end = 18.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.22f))
                        .border(1.dp, Color.White.copy(alpha = 0.45f), CircleShape)
                        .clickable {
                            onDismiss()
                            onOpenProfile()
                        }
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Профиль",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 2f), 4f))
                    )
                }

                // Чип
                CardChip(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = 20.dp, y = cardH * 0.37f)
                        .width(46.dp)
                        .height(35.dp)
                )

                // Лого (вместо алмаза): при удержании увеличивается и преломляет фон
                GlassLogo(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = cardW * LOGO_X - 40.dp, y = cardH * LOGO_Y - 40.dp)
                        .size(80.dp),
                    lens = lens,
                    onHold = { held ->
                        lensHeld = held
                        if (held) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                )

                // Кольцо стекла вокруг увеличенного лого
                Canvas(Modifier.fillMaxSize()) {
                    if (lens > 0.01f) {
                        val c = Offset(size.width * LOGO_X, size.height * LOGO_Y)
                        val r = size.width * (0.12f + 0.11f * lens)
                        drawCircle(Color.White.copy(alpha = 0.5f * lens), radius = r, center = c, style = Stroke(1.6.dp.toPx()))
                        drawCircle(
                            Brush.radialGradient(
                                listOf(Color.Transparent, Color.White.copy(alpha = 0.16f * lens)),
                                center = c,
                                radius = r
                            ),
                            radius = r,
                            center = c
                        )
                    }
                }

                // Низ слева: статус, в сети, ник
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 20.dp, bottom = 16.dp, end = 120.dp)
                ) {
                    if (status.isNotEmpty()) {
                        Text(
                            text = status,
                            color = Color.White.copy(alpha = 0.92f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 2f), 4f))
                        )
                        Spacer(Modifier.height(3.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .background(if (showOnline) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.45f), CircleShape)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (showOnline) "в сети" else "не в сети",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 2f), 4f))
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    EmbossedText(
                        text = cleanUsername.uppercase(),
                        fontSize = 19.sp
                    )
                }

                // Низ справа: счётчик подписчиков
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 18.dp, bottom = 16.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black.copy(alpha = 0.22f))
                        .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = formatCount(followers),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(text = "подписчиков", color = Color.White.copy(alpha = 0.75f), fontSize = 9.sp)
                }
            }

            // ================= ПАНЕЛЬ С QR =================
            AnimatedVisibility(
                visible = showQr,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(spring(0.85f, 300f)) { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                QrPanel(
                    link = link,
                    username = cleanUsername,
                    matrix = qrMatrix,
                    accent = bannerColor,
                    onCopy = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Профиль Жирпем", link))
                        Toast.makeText(context, "Ссылка скопирована", Toast.LENGTH_SHORT).show()
                    },
                    onClose = { showQr = false }
                )
            }
        }
    }
}

// ======================================================================
//  ЭЛЕМЕНТЫ КАРТЫ
// ======================================================================

/** Лого: чёрно-белое, при удержании увеличивается (вместе с ним работает шейдер на фоне). */
@Composable
private fun GlassLogo(
    modifier: Modifier,
    lens: Float,
    onHold: (Boolean) -> Unit
) {
    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onHold(true)
                        tryAwaitRelease()
                        onHold(false)
                    }
                )
            }
            .graphicsLayer {
                val s = 1f + 0.85f * lens
                scaleX = s
                scaleY = s
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.jirpem_logo),
            contentDescription = "Жирпем",
            colorFilter = LogoWhiteMask,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                // у исходника большие чёрные поля: приближаем, чтобы глаза и надпись заполнили квадрат
                .graphicsLayer {
                    scaleX = 1.3f
                    scaleY = 1.3f
                    alpha = 0.78f + 0.22f * lens
                }
        )
    }
}

/** Чип: золотая пластина с контактными линиями. */
@Composable
private fun CardChip(modifier: Modifier) {
    Canvas(modifier) {
        val r = 7.dp.toPx()
        drawRoundRect(
            Brush.linearGradient(
                listOf(Color(0xFFF6E3A1), Color(0xFFD4A94A), Color(0xFFF0D688), Color(0xFFB88A2E)),
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            ),
            cornerRadius = CornerRadius(r)
        )
        val line = Color(0xFF7A5A14).copy(alpha = 0.65f)
        val sw = 1.dp.toPx()
        // центральная площадка
        drawRoundRect(
            line,
            topLeft = Offset(size.width * 0.34f, size.height * 0.28f),
            size = Size(size.width * 0.32f, size.height * 0.44f),
            cornerRadius = CornerRadius(3.dp.toPx()),
            style = Stroke(sw)
        )
        // дорожки
        drawLine(line, Offset(0f, size.height * 0.5f), Offset(size.width * 0.34f, size.height * 0.5f), sw)
        drawLine(line, Offset(size.width * 0.66f, size.height * 0.5f), Offset(size.width, size.height * 0.5f), sw)
        drawLine(line, Offset(0f, size.height * 0.28f), Offset(size.width * 0.34f, size.height * 0.28f), sw)
        drawLine(line, Offset(size.width * 0.66f, size.height * 0.28f), Offset(size.width, size.height * 0.28f), sw)
        drawLine(line, Offset(0f, size.height * 0.72f), Offset(size.width * 0.34f, size.height * 0.72f), sw)
        drawLine(line, Offset(size.width * 0.66f, size.height * 0.72f), Offset(size.width, size.height * 0.72f), sw)
        drawLine(line, Offset(size.width * 0.5f, 0f), Offset(size.width * 0.5f, size.height * 0.28f), sw)
        drawLine(line, Offset(size.width * 0.5f, size.height * 0.72f), Offset(size.width * 0.5f, size.height), sw)
        // блик
        drawRoundRect(
            Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.45f), Color.Transparent),
                start = Offset.Zero,
                end = Offset(size.width * 0.7f, size.height * 0.7f)
            ),
            cornerRadius = CornerRadius(r)
        )
        drawRoundRect(Color(0xFF8A6A1E).copy(alpha = 0.7f), cornerRadius = CornerRadius(r), style = Stroke(sw))
    }
}

/** Текстура пластика: перелив, гильоше, зерно. Рисуется один раз и кэшируется. */
@Composable
private fun CardPlasticTexture() {
    Box(
        Modifier
            .fillMaxSize()
            .drawWithCache {
                val w = size.width
                val h = size.height
                // тонкие волнистые линии, как защитный узор на банкнотах
                val guilloche = Path().apply {
                    for (i in 0 until 16) {
                        val y0 = h * (0.05f + i * 0.065f)
                        moveTo(0f, y0)
                        var x = 0f
                        while (x <= w) {
                            lineTo(x, y0 + sin(x * 0.022f + i * 0.55f) * 12f)
                            x += 6f
                        }
                    }
                }
                val rnd = Random(7)
                val light = List(700) { Offset(rnd.nextFloat() * w, rnd.nextFloat() * h) }
                val dark = List(700) { Offset(rnd.nextFloat() * w, rnd.nextFloat() * h) }
                onDrawBehind {
                    drawRect(
                        Brush.linearGradient(
                            listOf(Color.White.copy(alpha = 0.22f), Color.Transparent, Color.Black.copy(alpha = 0.24f)),
                            start = Offset.Zero,
                            end = Offset(w, h)
                        )
                    )
                    drawPath(guilloche, Color.White.copy(alpha = 0.07f), style = Stroke(0.7f))
                    drawPoints(light, PointMode.Points, Color.White.copy(alpha = 0.07f), 1.3f, StrokeCap.Round)
                    drawPoints(dark, PointMode.Points, Color.Black.copy(alpha = 0.09f), 1.3f, StrokeCap.Round)
                }
            }
    )
}

/** Выдавленный текст, как цифры на настоящей карте. */
@Composable
private fun EmbossedText(text: String, fontSize: androidx.compose.ui.unit.TextUnit) {
    Box {
        Text(
            text = text,
            modifier = Modifier.offset(y = 1.2.dp),
            color = Color.White.copy(alpha = 0.38f),
            fontSize = fontSize,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 2.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = text,
            color = Color.White,
            fontSize = fontSize,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 2.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.5f), Offset(-1f, -1.5f), 2f))
        )
    }
}

// ======================================================================
//  QR
// ======================================================================

@Composable
private fun QrPanel(
    link: String,
    username: String,
    matrix: BitMatrix?,
    accent: Color,
    onCopy: () -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp))
            .background(
                Brush.verticalGradient(
                    listOf(accent.lighten(0.12f), accent.darken(0.3f))
                )
            )
            // чтобы нажатие по панели не закрывало диалог
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(Modifier.size(14.dp)) {
                        val sw = 2.dp.toPx()
                        drawLine(Color.White, Offset(0f, 0f), Offset(size.width, size.height), sw, StrokeCap.Round)
                        drawLine(Color.White, Offset(size.width, 0f), Offset(0f, size.height), sw, StrokeCap.Round)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.78f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.White)
                    .padding(22.dp),
                contentAlignment = Alignment.Center
            ) {
                if (matrix != null) {
                    QrCode(matrix = matrix, modifier = Modifier.fillMaxSize())
                    // лого в центре вместо алмаза
                    Box(
                        modifier = Modifier
                            .fillMaxSize(0.23f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(R.drawable.jirpem_logo),
                            contentDescription = null,
                            colorFilter = LogoWhiteMask,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().graphicsLayer {
                                scaleX = 1.35f
                                scaleY = 1.35f
                            }
                        )
                    }
                } else {
                    Text("Не удалось создать QR", color = Color.Black, textAlign = TextAlign.Center)
                }
            }

            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.92f))
                    .clickable { onCopy() }
                    .padding(horizontal = 22.dp, vertical = 10.dp)
            ) {
                Text("Копировать ссылку", color = accent.darken(0.45f), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Отсканируйте код камерой — откроется профиль @$username в Жирпем",
                color = Color.White,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun buildQrMatrix(text: String): BitMatrix? = try {
    val hints = mapOf<EncodeHintType, Any>(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H, // запас на лого в центре
        EncodeHintType.MARGIN to 0,
        EncodeHintType.CHARACTER_SET to "UTF-8"
    )
    QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
} catch (e: Exception) {
    null
}

/** Круглые модули, скруглённые «глаза» по углам, пустое место под лого. */
@Composable
private fun QrCode(matrix: BitMatrix, modifier: Modifier) {
    Canvas(modifier) {
        val n = matrix.width
        val cell = size.width / n
        val mid = n / 2f
        val hole = n * 0.25f / 2f + 0.5f

        fun inFinder(x: Int, y: Int) =
            (x < 7 && y < 7) || (x >= n - 7 && y < 7) || (x < 7 && y >= n - 7)

        for (y in 0 until n) {
            for (x in 0 until n) {
                if (!matrix.get(x, y) || inFinder(x, y)) continue
                if (abs(x + 0.5f - mid) < hole && abs(y + 0.5f - mid) < hole) continue
                drawRoundRect(
                    Color.Black,
                    topLeft = Offset(x * cell + cell * 0.06f, y * cell + cell * 0.06f),
                    size = Size(cell * 0.88f, cell * 0.88f),
                    cornerRadius = CornerRadius(cell * 0.44f)
                )
            }
        }

        fun finder(ox: Int, oy: Int) {
            val x0 = ox * cell
            val y0 = oy * cell
            drawRoundRect(
                Color.Black,
                topLeft = Offset(x0 + cell * 0.5f, y0 + cell * 0.5f),
                size = Size(cell * 6f, cell * 6f),
                cornerRadius = CornerRadius(cell * 2f),
                style = Stroke(cell)
            )
            drawRoundRect(
                Color.Black,
                topLeft = Offset(x0 + cell * 2f, y0 + cell * 2f),
                size = Size(cell * 3f, cell * 3f),
                cornerRadius = CornerRadius(cell * 1f)
            )
        }
        finder(0, 0)
        finder(n - 7, 0)
        finder(0, n - 7)
    }
}

// ======================================================================
//  УТИЛИТЫ
// ======================================================================

private fun Color.lighten(f: Float) = Color(
    red = red + (1f - red) * f,
    green = green + (1f - green) * f,
    blue = blue + (1f - blue) * f,
    alpha = alpha
)

private fun Color.darken(f: Float) = Color(
    red = red * (1f - f),
    green = green * (1f - f),
    blue = blue * (1f - f),
    alpha = alpha
)

private fun formatCount(n: Int): String {
    fun short(value: Int, unit: String): String {
        val tenths = value / 100 // десятые доли тысячи/миллиона
        return if (tenths % 10 == 0) "${tenths / 10}$unit" else "${tenths / 10},${tenths % 10}$unit"
    }
    return when {
        n >= 10_000_000 -> "${n / 1_000_000}M"
        n >= 1_000_000 -> short(n / 1000, "M")
        n >= 10_000 -> "${n / 1000}K"
        n >= 1_000 -> short(n, "K")
        else -> n.toString()
    }
}
