package com.RIKAPLAY.zhirpem_app

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.RIKAPLAY.zhirpem_app.platform.PushManager
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import zhirpem_app.composeapp.generated.resources.Res
import zhirpem_app.composeapp.generated.resources.jirpem_logo

@Composable
fun AuthScreen(onAuthSuccess: () -> Unit) {
    val db = Firebase.firestore
    val sessionManager = remember { SessionManager() }
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    var isLoginTab by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }

    if (showForgotPasswordDialog) {
        ForgotPasswordDialog(onDismiss = { showForgotPasswordDialog = false })
    }

    fun processAuth() {
        focusManager.clearFocus()
        if (username.isEmpty() || password.isEmpty() || (!isLoginTab && name.isEmpty())) {
            errorMessage = "Пожалуйста, заполните все поля!"
            return
        }

        val cleanUsername = username.lowercase().trim().replace("@", "")
        isLoading = true
        errorMessage = ""

        scope.launch {
            try {
                if (isLoginTab) {
                    val doc = db.collection("users").document(cleanUsername).get()
                    isLoading = false
                    if (doc.exists && doc.get<String>("password") == password) {
                        PushManager().setExternalId(cleanUsername)
                        PushManager().requestPermission()

                        sessionManager.isLoggedIn = true
                        sessionManager.username = cleanUsername
                        sessionManager.name = doc.get<String>("name")
                        onAuthSuccess()
                    } else {
                        errorMessage = "Неверный юзернейм или пароль!"
                    }
                } else {
                    val doc = db.collection("users").document(cleanUsername).get()
                    if (doc.exists) {
                        isLoading = false
                        errorMessage = "Этот юзернейм уже занят!"
                    } else {
                        val backupCode = (100000..999999).random().toString()
                        val newUser = mapOf(
                            "name" to name.trim(),
                            "username" to cleanUsername,
                            "password" to password,
                            "backupCode" to backupCode
                        )
                        db.collection("users").document(cleanUsername).set(newUser)
                        isLoading = false
                        sessionManager.isLoggedIn = true
                        sessionManager.username = cleanUsername
                        sessionManager.name = name.trim()
                        onAuthSuccess()
                    }
                }
            } catch (e: Exception) {
                isLoading = false
                errorMessage = "Ошибка: ${e.message}"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(Res.drawable.jirpem_logo),
            contentDescription = "Логотип",
            modifier = Modifier.height(60.dp).padding(bottom = 16.dp)
        )

        Text(
            text = if (isLoginTab) "С возвращением!" else "Создать аккаунт",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(modifier = Modifier.height(24.dp))

        AnimatedVisibility(
            visible = !isLoginTab,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            TextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Как вас зовут?") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                singleLine = true
            )
        }

        TextField(
            value = username,
            onValueChange = { username = it.replace(" ", "") },
            label = { Text("Юзернейм (без @)") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            singleLine = true
        )

        TextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Пароль") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { processAuth() }),
            singleLine = true
        )

        if (errorMessage.isNotEmpty()) {
            Text(errorMessage, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
        }

        Button(
            onClick = { processAuth() },
            modifier = Modifier.fillMaxWidth().height(56.dp).bounceClick(),
            enabled = !isLoading,
            shape = RoundedCornerShape(28.dp)
        ) {
            if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            else Text(if (isLoginTab) "Войти" else "Зарегистрироваться", fontWeight = FontWeight.Bold)
        }

        TextButton(onClick = { isLoginTab = !isLoginTab }) {
            Text(if (isLoginTab) "Нет аккаунта? Создать" else "Уже есть аккаунт? Войти")
        }

        if (isLoginTab) {
            TextButton(onClick = { showForgotPasswordDialog = true }) {
                Text("Забыли пароль?", fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
fun ForgotPasswordDialog(onDismiss: () -> Unit) {
    val db = Firebase.firestore
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(1) }
    var username by remember { mutableStateOf("") }
    var backupCode by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (step == 1) "Восстановление пароля" else "Новый пароль") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (step == 1) {
                    TextField(
                        value = username,
                        onValueChange = { username = it.trim().lowercase().replace("@", "") },
                        label = { Text("Юзернейм") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextField(
                        value = backupCode,
                        onValueChange = { backupCode = it.trim() },
                        label = { Text("Код восстановления") },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    TextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("Новый пароль") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Подтвердите пароль") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (errorMessage.isNotEmpty()) {
                    Text(errorMessage, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (step == 1) {
                    if (username.isEmpty() || backupCode.isEmpty()) return@Button
                    isLoading = true
                    scope.launch {
                        try {
                            val doc = db.collection("users").document(username).get()
                            isLoading = false
                            if (doc.exists && doc.get<String>("backupCode") == backupCode) {
                                step = 2
                                errorMessage = ""
                            } else {
                                errorMessage = "Неверный юзернейм или код!"
                            }
                        } catch (e: Exception) {
                            isLoading = false
                            errorMessage = "Ошибка сети"
                        }
                    }
                } else {
                    if (newPassword.isEmpty() || newPassword != confirmPassword) {
                        errorMessage = "Пароли не совпадают!"
                        return@Button
                    }
                    isLoading = true
                    scope.launch {
                        try {
                            db.collection("users").document(username).update("password" to newPassword)
                            isLoading = false
                            onDismiss()
                        } catch (e: Exception) {
                            isLoading = false
                            errorMessage = "Ошибка обновления"
                        }
                    }
                }
            }) {
                Text(if (step == 1) "Проверить" else "Сменить")
            }
        }
    )
}
