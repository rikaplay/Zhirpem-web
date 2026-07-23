package com.RIKAPLAY.zhirpem_app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import zhirpem_app.composeapp.generated.resources.*
import zhirpem_app.composeapp.generated.resources.Res
import zhirpem_app.composeapp.generated.resources.onboarding
import zhirpem_app.composeapp.generated.resources.onboarding1
import zhirpem_app.composeapp.generated.resources.onboarding2
import zhirpem_app.composeapp.generated.resources.onboarding3
import zhirpem_app.composeapp.generated.resources.onboarding4

data class OnboardingPage(val imageRes: org.jetbrains.compose.resources.DrawableResource, val title: String, val description: String)

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pages = listOf(
        OnboardingPage(
            Res.drawable.onboarding4,
            "Удобная лента и посты",
            "Читайте публикации, следите за обновлениями друзей и будьте в курсе всех событий в единой интерактивной ленте."
        ),
        OnboardingPage(
            Res.drawable.onboarding3,
            "Личный профиль",
            "Настраивайте свой профиль, набирайте читателей и делитесь важными моментами своей жизни."
        ),
        OnboardingPage(
            Res.drawable.onboarding2,
            "Делитесь контентом",
            "Публикуйте мысли, прикрепляйте фото, видео, GIF-анимации и создавайте опросы в пару кликов."
        ),
        OnboardingPage(
            Res.drawable.onboarding1,
            "Личные чаты",
            "Общайтесь с друзьями тет-а-тет, отправляйте стикеры, медиафайлы и голосовые сообщения."
        ),
        OnboardingPage(
            Res.drawable.onboarding,
            "Полная кастомизация",
            "Гибко настраивайте внешний вид: выбирайте акцентные цвета, темы оформления и эффект жидкого стекла под себя."
        )
    )

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { position ->
            val page = pages[position]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(page.imageRes),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    contentScale = ContentScale.Fit
                )

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = page.title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = page.description,
                    fontSize = 16.sp,
                    color = Color(0xFFCCCCCC),
                    textAlign = TextAlign.Center
                )
            }
        }

        // Индикаторы и кнопка внизу
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Индикаторы (Dots)
            Row(
                Modifier
                    .height(50.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(pages.size) { iteration ->
                    val color = if (pagerState.currentPage == iteration) Color.White else Color.Gray
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(color)
                            .size(10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (pagerState.currentPage < pages.size - 1) {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    } else {
                        settingsManager.isFirstLaunch = false
                        onFinish()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(56.dp)
                    .bounceClick(),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = if (pagerState.currentPage == pages.size - 1) "Начать" else "Далее",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}
