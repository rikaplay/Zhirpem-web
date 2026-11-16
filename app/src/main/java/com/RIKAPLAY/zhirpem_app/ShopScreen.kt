package com.RIKAPLAY.zhirpem_app

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

/** Результат попытки покупки. */
enum class PurchaseResult { OK, NEGATIVE_REPUTATION, NOT_ENOUGH, ALREADY_OWNED, UNKNOWN_ITEM, ERROR }

object ShopManager {

    /**
     * Покупает рамку за очки репутации. Всё происходит в одной транзакции,
     * чтобы нельзя было уйти в минус или купить предмет дважды.
     * При отрицательной репутации (очки < 0) покупки запрещены.
     */
    fun buyFrame(
        db: FirebaseFirestore,
        username: String,
        frameId: String,
        onResult: (PurchaseResult) -> Unit
    ) {
        val frame = AvatarFrames.byId(frameId)
        if (frame == null) {
            onResult(PurchaseResult.UNKNOWN_ITEM)
            return
        }
        val ref = db.collection("users").document(username.replace("@", "").trim())

        db.runTransaction { tr ->
            val snap = tr.get(ref)
            val points = (snap.getLong("reputationPoints")
                ?: ((snap.getLong("reputation") ?: 0L) * ReputationManager.POINTS_PER_LEVEL)).toInt()
            val owned = (snap.get("ownedFrames") as? List<*>)?.filterIsInstance<String>() ?: emptyList()

            when {
                points < 0 -> PurchaseResult.NEGATIVE_REPUTATION
                frameId in owned -> PurchaseResult.ALREADY_OWNED
                points < frame.price -> PurchaseResult.NOT_ENOUGH
                else -> {
                    val newPoints = points - frame.price
                    val newLevel = (newPoints / ReputationManager.POINTS_PER_LEVEL).coerceIn(-10, 10)
                    tr.update(
                        ref, mapOf(
                            "reputationPoints" to newPoints,
                            "reputation" to newLevel,
                            "ownedFrames" to FieldValue.arrayUnion(frameId),
                            "equippedFrame" to frameId
                        )
                    )
                    PurchaseResult.OK
                }
            }
        }.addOnSuccessListener { result ->
            onResult(result)
        }.addOnFailureListener {
            onResult(PurchaseResult.ERROR)
        }
    }


    /** Покупает баннер за очки репутации. Правила те же, что и у рамок. */
    fun buyBanner(
        db: FirebaseFirestore,
        username: String,
        bannerId: String,
        onResult: (PurchaseResult) -> Unit
    ) {
        val banner = ShopBanners.byId(bannerId)
        if (banner == null) {
            onResult(PurchaseResult.UNKNOWN_ITEM)
            return
        }
        val ref = db.collection("users").document(username.replace("@", "").trim())

        db.runTransaction { tr ->
            val snap = tr.get(ref)
            val points = (snap.getLong("reputationPoints")
                ?: ((snap.getLong("reputation") ?: 0L) * ReputationManager.POINTS_PER_LEVEL)).toInt()
            val owned = (snap.get("ownedBanners") as? List<*>)?.filterIsInstance<String>() ?: emptyList()

            when {
                points < 0 -> PurchaseResult.NEGATIVE_REPUTATION
                bannerId in owned -> PurchaseResult.ALREADY_OWNED
                points < banner.price -> PurchaseResult.NOT_ENOUGH
                else -> {
                    val newPoints = points - banner.price
                    val newLevel = (newPoints / ReputationManager.POINTS_PER_LEVEL).coerceIn(-10, 10)
                    tr.update(
                        ref, mapOf(
                            "reputationPoints" to newPoints,
                            "reputation" to newLevel,
                            "ownedBanners" to FieldValue.arrayUnion(bannerId),
                            "equippedBanner" to bannerId
                        )
                    )
                    PurchaseResult.OK
                }
            }
        }.addOnSuccessListener { result ->
            onResult(result)
        }.addOnFailureListener {
            onResult(PurchaseResult.ERROR)
        }
    }

    /** Надевает купленный баннер (bannerId = null: снять). */
    fun equipBanner(db: FirebaseFirestore, username: String, bannerId: String?) {
        db.collection("users").document(username.replace("@", "").trim())
            .update("equippedBanner", bannerId ?: "")
    }

    /** Надевает купленную рамку (frameId = null — снять). */
    fun equipFrame(db: FirebaseFirestore, username: String, frameId: String?) {
        db.collection("users").document(username.replace("@", "").trim())
            .update("equippedFrame", frameId ?: "")
    }
}

private fun parseBanner(hex: String, fallback: Color): Color =
    try { Color(android.graphics.Color.parseColor(hex)) } catch (e: Exception) { fallback }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("user_session", android.content.Context.MODE_PRIVATE) }
    val myUsername = (sharedPrefs.getString("username", "") ?: "").replace("@", "").trim()
    val db = remember { FirebaseFirestore.getInstance() }

    var points by remember { mutableIntStateOf(0) }
    var avatarUrl by remember { mutableStateOf<String?>(null) }
    var bannerColorHex by remember { mutableStateOf("#808080") }
    var ownedFrames by remember { mutableStateOf(listOf<String>()) }
    var equippedFrame by remember { mutableStateOf<String?>(null) }
    var isBuying by remember { mutableStateOf(false) }
    var frameToBuy by remember { mutableStateOf<AvatarFrame?>(null) }
    var ownedBanners by remember { mutableStateOf(listOf<String>()) }
    var equippedBanner by remember { mutableStateOf<String?>(null) }
    var bannerToBuy by remember { mutableStateOf<ShopBanner?>(null) }
    var shopTab by remember { mutableIntStateOf(0) } // 0 = рамки, 1 = баннеры
    var rarityFilter by remember { mutableStateOf<FrameRarity?>(null) }
    val visibleFrames = remember(rarityFilter) {
        AvatarFrames.all.filter { rarityFilter == null || it.rarity == rarityFilter }
    }
    val visibleBanners = remember(rarityFilter) {
        ShopBanners.all.filter { rarityFilter == null || it.rarity == rarityFilter }
    }

    // Обучение показываем один раз при первом открытии магазина
    var showTutorial by remember { mutableStateOf(!sharedPrefs.getBoolean("shop_tutorial_seen", false)) }

    DisposableEffect(myUsername) {
        val registration = if (myUsername.isNotEmpty()) {
            db.collection("users").document(myUsername).addSnapshotListener { doc, _ ->
                if (doc != null && doc.exists()) {
                    points = (doc.getLong("reputationPoints")
                        ?: ((doc.getLong("reputation") ?: 0L) * ReputationManager.POINTS_PER_LEVEL)).toInt()
                    avatarUrl = doc.getString("avatarUrl")
                    bannerColorHex = doc.getString("bannerColor") ?: "#808080"
                    ownedFrames = (doc.get("ownedFrames") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    equippedFrame = doc.getString("equippedFrame")?.takeIf { it.isNotEmpty() }
                    ownedBanners = (doc.get("ownedBanners") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    equippedBanner = doc.getString("equippedBanner")?.takeIf { it.isNotEmpty() }
                }
            }
        } else null
        onDispose { registration?.remove() }
    }

    val bannerColor = parseBanner(bannerColorHex, MaterialTheme.colorScheme.primary)
    val isNegative = points < 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Магазин", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { showTutorial = true }) {
                        Icon(Icons.Default.HelpOutline, contentDescription = "Как это работает")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Баланс
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isNegative) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Ваш баланс", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Text("$points очков репутации", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                        if (isNegative) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "У вас отрицательная репутация, поэтому покупки недоступны. " +
                                        "Набирайте очки (посты, комментарии, ежедневный вход), чтобы выйти в плюс.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // Вкладки магазина
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = shopTab == 0,
                        onClick = { shopTab = 0 },
                        label = { Text("Рамки (${AvatarFrames.all.size})") }
                    )
                    FilterChip(
                        selected = shopTab == 1,
                        onClick = { shopTab = 1 },
                        label = { Text("Баннеры (${ShopBanners.all.size})") }
                    )
                }
            }

            item {
                Text(
                    if (shopTab == 0) "Рамки для аватарки" else "Баннеры профиля",
                    fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Фильтр по редкости
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = rarityFilter == null,
                        onClick = { rarityFilter = null },
                        label = { Text("Все") }
                    )
                    FrameRarity.values().forEach { rarity ->
                        FilterChip(
                            selected = rarityFilter == rarity,
                            onClick = { rarityFilter = if (rarityFilter == rarity) null else rarity },
                            label = { Text(rarity.title) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = rarity.color.copy(alpha = 0.25f),
                                selectedLabelColor = rarity.color
                            )
                        )
                    }
                }
            }

            items(if (shopTab == 0) visibleFrames else emptyList(), key = { it.id }) { frame ->
                val owned = frame.id in ownedFrames
                val equipped = equippedFrame == frame.id
                val canAfford = points >= frame.price

                Card(
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.5.dp, frame.rarity.color.copy(alpha = 0.6f)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Превью на вашей аватарке
                        Box(modifier = Modifier.size(84.dp), contentAlignment = Alignment.Center) {
                            Box(modifier = Modifier.size(84.dp)) {
                                if (!avatarUrl.isNullOrEmpty()) {
                                    AsyncImage(
                                        model = avatarUrl,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(4.dp)
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(4.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    )
                                }
                                AvatarFrameOverlay(frameId = frame.id, bannerColor = bannerColor)
                            }
                        }

                        Spacer(Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(frame.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = frame.rarity.color.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    frame.rarity.title,
                                    color = frame.rarity.color,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                frame.description,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                            )
                            Spacer(Modifier.height(10.dp))

                            when {
                                equipped -> OutlinedButton(
                                    onClick = { ShopManager.equipFrame(db, myUsername, null) },
                                    shape = RoundedCornerShape(14.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) { Text("Снять") }

                                owned -> Button(
                                    onClick = { ShopManager.equipFrame(db, myUsername, frame.id) },
                                    shape = RoundedCornerShape(14.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) { Text("Надеть") }

                                else -> Button(
                                    onClick = { frameToBuy = frame },
                                    enabled = !isNegative && canAfford && !isBuying,
                                    colors = ButtonDefaults.buttonColors(containerColor = frame.rarity.color),
                                    shape = RoundedCornerShape(14.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    if (isNegative) {
                                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(6.dp))
                                    }
                                    Text("${frame.price} очков", fontWeight = FontWeight.Bold)
                                }
                            }

                            if (!owned && !isNegative && !canAfford) {
                                Text(
                                    "Не хватает ${frame.price - points}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            items(
                if (shopTab == 1) visibleBanners else emptyList(),
                key = { "banner_" + it.id }
            ) { banner ->
                val owned = banner.id in ownedBanners
                val equipped = equippedBanner == banner.id
                val canAfford = points >= banner.price

                Card(
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.5.dp, banner.rarity.color.copy(alpha = 0.6f)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // Превью баннера
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(96.dp)
                                .clip(RoundedCornerShape(16.dp))
                        ) {
                            ShopBannerArt(bannerId = banner.id)
                        }

                        Spacer(Modifier.height(10.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(banner.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = banner.rarity.color.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    banner.rarity.title,
                                    color = banner.rarity.color,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            banner.description,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                        )
                        Spacer(Modifier.height(10.dp))

                        when {
                            equipped -> OutlinedButton(
                                onClick = { ShopManager.equipBanner(db, myUsername, null) },
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) { Text("Снять") }

                            owned -> Button(
                                onClick = { ShopManager.equipBanner(db, myUsername, banner.id) },
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) { Text("Надеть") }

                            else -> Button(
                                onClick = { bannerToBuy = banner },
                                enabled = !isNegative && canAfford && !isBuying,
                                colors = ButtonDefaults.buttonColors(containerColor = banner.rarity.color),
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                if (isNegative) {
                                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text("${banner.price} очков", fontWeight = FontWeight.Bold)
                            }
                        }

                        if (!owned && !isNegative && !canAfford) {
                            Text(
                                "Не хватает ${banner.price - points}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    "Скоро в магазине появятся новые предметы",
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                )
            }
        }
    }

    // Подтверждение покупки
    frameToBuy?.let { frame ->
        AlertDialog(
            onDismissRequest = { frameToBuy = null },
            title = { Text("Купить «${frame.name}»?") },
            text = {
                Text(
                    "Будет списано ${frame.price} очков репутации. " +
                            "Останется ${points - frame.price}. Уровень репутации может понизиться."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    frameToBuy = null
                    isBuying = true
                    ShopManager.buyFrame(db, myUsername, frame.id) { result ->
                        isBuying = false
                        val message = when (result) {
                            PurchaseResult.OK -> "Куплено и надето: ${frame.name}"
                            PurchaseResult.NEGATIVE_REPUTATION -> "С отрицательной репутацией покупки недоступны"
                            PurchaseResult.NOT_ENOUGH -> "Недостаточно очков репутации"
                            PurchaseResult.ALREADY_OWNED -> "Этот предмет у вас уже есть"
                            PurchaseResult.UNKNOWN_ITEM -> "Предмет не найден"
                            PurchaseResult.ERROR -> "Не удалось совершить покупку, попробуйте ещё раз"
                        }
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Купить") }
            },
            dismissButton = { TextButton(onClick = { frameToBuy = null }) { Text("Отмена") } }
        )
    }

    bannerToBuy?.let { banner ->
        AlertDialog(
            onDismissRequest = { bannerToBuy = null },
            title = { Text("Купить баннер «${banner.name}»?") },
            text = {
                Text(
                    "Будет списано ${banner.price} очков репутации. " +
                            "Останется ${points - banner.price}. Уровень репутации может понизиться. " +
                            "Пока баннер надет, он показывается вместо вашего загруженного баннера."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    bannerToBuy = null
                    isBuying = true
                    ShopManager.buyBanner(db, myUsername, banner.id) { result ->
                        isBuying = false
                        val message = when (result) {
                            PurchaseResult.OK -> "Куплено и надето: ${banner.name}"
                            PurchaseResult.NEGATIVE_REPUTATION -> "С отрицательной репутацией покупки недоступны"
                            PurchaseResult.NOT_ENOUGH -> "Недостаточно очков репутации"
                            PurchaseResult.ALREADY_OWNED -> "Этот предмет у вас уже есть"
                            PurchaseResult.UNKNOWN_ITEM -> "Предмет не найден"
                            PurchaseResult.ERROR -> "Не удалось совершить покупку, попробуйте ещё раз"
                        }
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Купить") }
            },
            dismissButton = { TextButton(onClick = { bannerToBuy = null }) { Text("Отмена") } }
        )
    }

    if (showTutorial) {
        ShopTutorialDialog(
            onFinish = {
                sharedPrefs.edit().putBoolean("shop_tutorial_seen", true).apply()
                showTutorial = false
            }
        )
    }
}

private data class TutorialStep(val emoji: String, val title: String, val text: String)

@Composable
private fun ShopTutorialDialog(onFinish: () -> Unit) {
    val steps = remember {
        listOf(
            TutorialStep(
                "🛍️", "Добро пожаловать в магазин!",
                "Здесь очки репутации можно потратить на украшения для профиля. Сейчас доступны рамки для аватарки и баннеры профиля, дальше будет больше."
            ),
            TutorialStep(
                "⭐", "Чем платить",
                "Платите очками репутации: их дают за ежедневный вход, посты, комментарии и лайки. Покупка уменьшает ваши очки, а вместе с ними может снизиться и уровень репутации."
            ),
            TutorialStep(
                "💎", "Редкость и цена",
                "Чем реже предмет, тем он дороже:\n• Обычный (серый)\n• Необычный (синий)\n• Редкий (зелёный)\n• Эпический (фиолетовый)\n• Легендарный (оранжевый)\nВыбирай нужную редкость фильтром над списком."
            ),
            TutorialStep(
                "🚫", "Важное условие",
                "Если ваша репутация отрицательная, покупать предметы нельзя. Сначала выйдите в плюс, а потом возвращайтесь за покупками."
            ),
            TutorialStep(
                "🖼️", "Как надеть",
                "Купленная рамка или баннер надеваются сразу. Сменить или снять их можно в магазине в любой момент, а увидят их все в вашем профиле. Надетый баннер показывается вместо загруженного вами. Эту подсказку можно открыть снова кнопкой «?» сверху."
            )
        )
    }
    var step by remember { mutableIntStateOf(0) }
    val current = steps[step]
    val isLast = step == steps.lastIndex

    AlertDialog(
        onDismissRequest = { /* закрываем только кнопками, чтобы обучение не пропустили случайно */ },
        icon = { Text(current.emoji, fontSize = 36.sp) },
        title = { Text(current.title, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(current.text, fontSize = 15.sp)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    steps.indices.forEach { i ->
                        Box(
                            modifier = Modifier
                                .size(if (i == step) 9.dp else 7.dp)
                                .clip(CircleShape)
                                .background(
                                    if (i == step) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                                )
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (isLast) onFinish() else step++ }) {
                Text(if (isLast) "Понятно!" else "Далее")
            }
        },
        dismissButton = {
            if (!isLast) TextButton(onClick = onFinish) { Text("Пропустить") }
        }
    )
}
