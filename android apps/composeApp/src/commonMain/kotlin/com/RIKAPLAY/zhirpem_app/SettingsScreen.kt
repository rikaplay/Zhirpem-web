package com.RIKAPLAY.zhirpem_app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.*
import com.RIKAPLAY.zhirpem_app.platform.*
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onNavigateToSecuritySettings: () -> Unit,
    onNavigateToOptimization: () -> Unit,
    currentTheme: AppThemeMode,
    onThemeChange: (AppThemeMode) -> Unit,
    onPerformanceModeChanged: (Boolean) -> Unit,
    onFontSizeChanged: (Float) -> Unit,
    onGlassModeChanged: (Boolean) -> Unit,
    onGlassAlphaChanged: (Float) -> Unit
) {
    val db = Firebase.firestore
    val sessionManager = remember { SessionManager() }
    val settingsManager = remember { SettingsManager() }
    val themeManager = remember { ThemeManager() }
    val updater = remember { AppUpdater() }
    val coroutineScope = rememberCoroutineScope()
    var myUsername by remember { mutableStateOf(sessionManager.username ?: "") }

    // Состояния для обновления
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateUrl by remember { mutableStateOf<String?>(null) }

    // Состояния данных пользователя
    var name by remember { mutableStateOf(sessionManager.name ?: "") }
    var isLoading by remember { mutableStateOf(true) }

    // Локальные настройки
    var isLowPerf by remember { mutableStateOf(settingsManager.isLowPerformanceMode) }
    var isSplashEnabled by remember { mutableStateOf(settingsManager.isSplashScreenEnabled) }
    var isSplashSoundEnabled by remember { mutableStateOf(settingsManager.isSplashSoundEnabled) }
    var isGlassEnabled by remember { mutableStateOf(settingsManager.isGlassEnabled) }
    var glassAlpha by remember { mutableStateOf(settingsManager.glassAlpha) }
    var fontSizeMultiplier by remember { mutableStateOf(settingsManager.fontSizeMultiplier) }
    
    // Новые настройки темы
    var customThemeType by remember { mutableStateOf(themeManager.themeType) }
    var selectedColorHex by remember { mutableStateOf(themeManager.customColor) }
    var showColorPickerDialog by remember { mutableStateOf(false) }

    val pastelPresets = listOf(
        "#B2F2BB" to "Мята",
        "#E5DBFF" to "Лаванда",
        "#FFD8A8" to "Персик",
        "#A5D8FF" to "Небесный",
        "#FFD1DC" to "Сакура"
    )
    
    val notificationOptions = listOf("Все", "Читаемые", "Никто")
    val notificationValues = listOf("all", "following", "none")
    var selectedNotificationIndex by remember {
        mutableIntStateOf(0)
    }
    var notificationSetting by remember { mutableStateOf("all") }

    var showThemeDialog by remember { mutableStateOf(false) }
    var showUsernameDialog by remember { mutableStateOf(false) }
    var newUsername by remember { mutableStateOf(myUsername) }
    var usernameError by remember { mutableStateOf<String?>(null) }
    var showIconDialog by remember { mutableStateOf(false) }

    LaunchedEffect(myUsername) {
        if (myUsername.isNotEmpty()) {
            try {
                val doc = db.collection("users").document(myUsername).get()
                name = doc.get<String>("name") ?: ""
                notificationSetting = doc.get<String>("notificationSetting") ?: "all"
                selectedNotificationIndex = notificationValues.indexOf(notificationSetting).coerceAtLeast(0)
                isLoading = false
            } catch (e: Exception) {
                isLoading = false
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            GlassTopBar(
                isGlassEnabled = LocalGlassEnabled.current,
                title = { Text("Настройки", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // --- СЕКЦИЯ: ВНЕШНИЙ ВИД ---
                Text("Внешний вид", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        SettingsClickableItem(
                            title = "Тема оформления",
                            subtitle = when(currentTheme) {
                                AppThemeMode.LIGHT -> "Светлая (Зеленый акцент)"
                                AppThemeMode.DARK -> "Темная (Зеленый акцент)"
                                AppThemeMode.AMOLED -> "AMOLED (Черная)"
                                AppThemeMode.SYSTEM -> "Системная"
                                AppThemeMode.MATERIAL_YOU_LIGHT -> "Material You (Светлая)"
                                AppThemeMode.MATERIAL_YOU_DARK -> "Material You (Темная)"
                            },
                            icon = Icons.Default.Palette,
                            onClick = { showThemeDialog = true }
                        )
                        
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                        
                        SettingsClickableItem(
                            title = "Иконка приложения",
                            subtitle = "Изменить значок на рабочем столе",
                            icon = Icons.Default.Apps,
                            onClick = { showIconDialog = true }
                        )

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                        // --- КАСТОМНАЯ ТЕМА ---
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Персонализация", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                    Text("Активировать свою цветовую схему", fontSize = 12.sp, color = Color.Gray)
                                }
                                Switch(
                                    checked = customThemeType != ThemeManager.TYPE_DEFAULT,
                                    onCheckedChange = { 
                                        if (it) {
                                            customThemeType = ThemeManager.TYPE_MY_DARK
                                            themeManager.themeType = ThemeManager.TYPE_MY_DARK
                                        } else {
                                            customThemeType = ThemeManager.TYPE_DEFAULT
                                            themeManager.themeType = ThemeManager.TYPE_DEFAULT
                                        }
                                    }
                                )
                            }
                            
                            if (customThemeType != ThemeManager.TYPE_DEFAULT) {
                                Spacer(modifier = Modifier.height(12.dp))

                                // Переключатель Светлая/Темная для кастомной темы
                                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                    SegmentedButton(
                                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                        onClick = { 
                                            customThemeType = ThemeManager.TYPE_MY_LIGHT
                                            themeManager.themeType = ThemeManager.TYPE_MY_LIGHT
                                        },
                                        selected = customThemeType == ThemeManager.TYPE_MY_LIGHT
                                    ) {
                                        Text("Светлая")
                                    }
                                    SegmentedButton(
                                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                        onClick = { 
                                            customThemeType = ThemeManager.TYPE_MY_DARK
                                            themeManager.themeType = ThemeManager.TYPE_MY_DARK
                                        },
                                        selected = customThemeType == ThemeManager.TYPE_MY_DARK
                                    ) {
                                        Text("Темная")
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    pastelPresets.forEach { (hex, presetName) ->
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(parseColor(hex), RoundedCornerShape(18.dp))
                                                .border(
                                                    width = if (selectedColorHex == hex) 2.dp else 0.dp,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    shape = RoundedCornerShape(18.dp)
                                                )
                                                .clickable {
                                                    selectedColorHex = hex
                                                    themeManager.customColor = hex
                                                }
                                        )
                                    }
                                    
                                    IconButton(
                                        onClick = { showColorPickerDialog = true },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(18.dp))
                                    ) {
                                        Icon(Icons.Default.ColorLens, contentDescription = "Палитра", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Эффект стекла", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Размытие панелей и матовое стекло", 
                                    fontSize = 12.sp, 
                                    color = Color.Gray
                                )
                            }
                            Switch(
                                checked = isGlassEnabled,
                                onCheckedChange = { newValue ->
                                    isGlassEnabled = newValue
                                    onGlassModeChanged(newValue)
                                }
                            )
                        }

                        if (isGlassEnabled) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Прозрачность стекла", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                        Text("Регулировка матовости панелей", fontSize = 12.sp, color = Color.Gray)
                                    }
                                    Text(
                                        text = "${(glassAlpha * 100).toInt()}%",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Slider(
                                    value = glassAlpha,
                                    onValueChange = { 
                                        glassAlpha = it
                                        onGlassAlphaChanged(it)
                                    },
                                    valueRange = 0.1f..0.9f,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Облегченные анимации", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Отключает сложные эффекты для плавности", 
                                    fontSize = 12.sp, 
                                    color = Color.Gray
                                )
                            }
                            Switch(
                                checked = isLowPerf,
                                onCheckedChange = { newValue ->
                                    isLowPerf = newValue
                                    onPerformanceModeChanged(newValue)
                                }
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Splash Screen", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Показывать логотип при запуске", 
                                    fontSize = 12.sp, 
                                    color = Color.Gray
                                )
                            }
                            Switch(
                                checked = isSplashEnabled,
                                onCheckedChange = { newValue ->
                                    isSplashEnabled = newValue
                                    settingsManager.isSplashScreenEnabled = newValue
                                }
                            )
                        }

                        AnimatedVisibility(
                            visible = isSplashEnabled,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Звук сплеш-скрина",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            "Воспроизводить звук при запуске",
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                    }
                                    Switch(
                                        checked = isSplashSoundEnabled,
                                        onCheckedChange = { newValue ->
                                            isSplashSoundEnabled = newValue
                                            settingsManager.isSplashSoundEnabled = newValue
                                        }
                                    )
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                        // --- РАЗМЕР ШРИФТА ---
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Размер шрифта", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                    Text("Увеличение текста постов и сообщений", fontSize = 12.sp, color = Color.Gray)
                                }
                                Text(
                                    text = "${(fontSizeMultiplier * 100).toInt()}%",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = fontSizeMultiplier,
                                onValueChange = { 
                                    fontSizeMultiplier = it
                                    onFontSizeChanged(it)
                                },
                                valueRange = 0.8f..1.5f,
                                steps = 6,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // --- СЕКЦИЯ: АККАУНТ ---
                Text("Аккаунт", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        SettingsClickableItem(
                            title = "Юзернейм",
                            subtitle = "@$myUsername",
                            icon = Icons.Default.AlternateEmail,
                            onClick = { 
                                newUsername = myUsername
                                usernameError = null
                                showUsernameDialog = true 
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                        SettingsClickableItem(
                            title = "Безопасность",
                            subtitle = "Код восстановления доступа",
                            icon = Icons.Default.Shield,
                            onClick = onNavigateToSecuritySettings
                        )
                    }
                }

                // --- СЕКЦИЯ: ПРОФИЛЬ ---
                Text("Профиль", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Имя") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    try {
                                        db.collection("users").document(myUsername).update("name" to name)
                                        sessionManager.name = name
                                        showToast("Профиль обновлен!")
                                    } catch (e: Exception) {
                                        showToast("Ошибка обновления")
                                    }
                                }
                            },
                            modifier = Modifier.align(Alignment.End),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Сохранить")
                        }
                    }
                }

                // --- СЕКЦИЯ: СИСТЕМА ---
                Text("Система", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        SettingsClickableItem(
                            title = "Оптимизация",
                            subtitle = "Управление кэшем и очистка",
                            icon = Icons.Default.DeleteSweep,
                            onClick = onNavigateToOptimization
                        )

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("Уведомления:", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp))
                            notificationOptions.forEachIndexed { index, option ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { 
                                            selectedNotificationIndex = index
                                            val newVal = notificationValues[index]
                                            coroutineScope.launch {
                                                db.collection("users").document(myUsername).update("notificationSetting" to newVal)
                                            }
                                        }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selectedNotificationIndex == index,
                                        onClick = { 
                                            selectedNotificationIndex = index 
                                            val newVal = notificationValues[index]
                                            coroutineScope.launch {
                                                db.collection("users").document(myUsername).update("notificationSetting" to newVal)
                                            }
                                        }
                                    )
                                    Text(text = option, fontSize = 15.sp, modifier = Modifier.padding(start = 8.dp))
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                        ListItem(
                            headlineContent = { Text("Проверить обновления", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text("Поиск новой версии на GitHub", fontSize = 13.sp, color = Color.Gray) },
                            leadingContent = {
                                if (isCheckingUpdate) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray) },
                            modifier = Modifier.clickable {
                                if (!isCheckingUpdate) {
                                    coroutineScope.launch {
                                        isCheckingUpdate = true
                                        val url = updater.checkForUpdates()
                                        isCheckingUpdate = false
                                        if (url != null) {
                                            updateUrl = url
                                            showUpdateDialog = true
                                        } else {
                                            showToast("У вас установлена последняя версия")
                                        }
                                    }
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Выйти из аккаунта", fontWeight = FontWeight.Bold)
                }

                Text(
                    text = "Версия: ${getAppVersion()}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp)
                )
            }
        }
    }

    // Диалог выбора темы
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Выберите тему") },
            text = {
                Column {
                    val themes = listOf(
                        AppThemeMode.LIGHT to "☀️ Светлая (Зеленая)",
                        AppThemeMode.DARK to "🌙 Тёмная (Зеленая)",
                        AppThemeMode.AMOLED to "⬛ AMOLED (Черная)",
                        AppThemeMode.SYSTEM to "🤖 Как в системе",
                        AppThemeMode.MATERIAL_YOU_LIGHT to "🎨 Material You (Светлая)",
                        AppThemeMode.MATERIAL_YOU_DARK to "🎨 Material You (Тёмная)"
                    )
                    themes.forEach { (mode, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { 
                                    onThemeChange(mode)
                                    showThemeDialog = false 
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = currentTheme == mode, onClick = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text("Закрыть") }
            }
        )
    }

    // Диалог выбора иконки
    if (showIconDialog) {
        AlertDialog(
            onDismissRequest = { showIconDialog = false },
            title = { Text("Иконка приложения") },
            text = {
                Column {
                    Text("Выберите стиль значка для рабочего стола:", fontSize = 14.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { 
                                    changeAppIcon("MainActivity")
                                    showIconDialog = false
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Оригинал")
                            }
                            OutlinedButton(
                                onClick = { 
                                    changeAppIcon("MainActivityAlias1")
                                    showIconDialog = false
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Жирпем 1")
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { 
                                    changeAppIcon("MainActivityAlias2")
                                    showIconDialog = false
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Жирпем 2")
                            }
                            OutlinedButton(
                                onClick = { 
                                    changeAppIcon("MainActivityAlias3")
                                    showIconDialog = false
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Жирпем 3")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showIconDialog = false }) { Text("Отмена") }
            }
        )
    }

    // Диалог изменения юзернейма
    if (showUsernameDialog) {
        AlertDialog(
            onDismissRequest = { showUsernameDialog = false },
            title = { Text("Изменить юзернейм") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newUsername,
                        onValueChange = { 
                            newUsername = it
                            usernameError = null
                        },
                        label = { Text("Новый юзернейм") },
                        prefix = { Text("@") },
                        isError = usernameError != null,
                        supportingText = { if (usernameError != null) Text(usernameError!!) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newUsername == myUsername) {
                            showUsernameDialog = false
                            return@Button
                        }
                        if (newUsername.isBlank()) {
                            usernameError = "Введите юзернейм"
                            return@Button
                        }

                        coroutineScope.launch {
                            try {
                                val snapshot = db.collection("users")
                                    .whereEqualTo("username", newUsername)
                                    .get()
                                if (!snapshot.isEmpty) {
                                    usernameError = "Этот юзернейм уже занят!"
                                } else {
                                    db.collection("users").document(myUsername).update("username" to newUsername)
                                    sessionManager.username = newUsername
                                    myUsername = newUsername
                                    showUsernameDialog = false
                                    showToast("Юзернейм изменен!")
                                }
                            } catch (e: Exception) {
                                usernameError = "Ошибка"
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUsernameDialog = false }) { Text("Отмена") }
            }
        )
    }

    // Диалог кастомного цвета
    if (showColorPickerDialog) {
        var customHex by remember { mutableStateOf(selectedColorHex) }
        AlertDialog(
            onDismissRequest = { showColorPickerDialog = false },
            title = { Text("Кастомный цвет (HEX)") },
            text = {
                OutlinedTextField(
                    value = customHex,
                    onValueChange = { customHex = it.uppercase() },
                    placeholder = { Text("#FFFFFF") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                Button(onClick = {
                    try {
                        parseColor(customHex)
                        selectedColorHex = customHex
                        themeManager.customColor = customHex
                        showColorPickerDialog = false
                    } catch (e: Exception) {
                        showToast("Неверный формат HEX")
                    }
                }) { Text("Применить") }
            },
            dismissButton = {
                TextButton(onClick = { showColorPickerDialog = false }) { Text("Отмена") }
            }
        )
    }

    // Диалог обновления
    if (showUpdateDialog) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = { Text("Доступно обновление") },
            text = { Text("Найдена новая версия приложения. Хотите обновить?") },
            confirmButton = {
                Button(onClick = {
                    showUpdateDialog = false
                    updateUrl?.let { updater.downloadAndInstall(it) }
                }) {
                    Text("Обновить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
fun SettingsClickableItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 13.sp, color = Color.Gray)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
    }
}
