package com.ganj.vpn.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ganj.vpn.R

@Composable
internal fun StitchOnboardingScreen(
    telegramLinked: Boolean,
    telegramBusy: Boolean,
    onTelegramLogin: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var step by remember { mutableIntStateOf(0) }
    val lastStep = step == 2

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = responsiveHorizontalPadding(), vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.ganj_logo_official),
            contentDescription = "نشان رسمی گنج VPN",
            modifier = Modifier.size(86.dp),
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = onboardingTitle(step),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Text(
                text = onboardingSubtitle(step),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        OnboardingProgress(step = step, count = 3)

        when (step) {
            0 -> OnboardingWelcomeCard()
            1 -> OnboardingPlansCard()
            else -> OnboardingConnectCard()
        }

        GanjLiquidAction(
            onClick = {
                if (lastStep) onComplete() else step += 1
            },
            accent = MaterialTheme.colorScheme.primary,
            shapeRadius = 999.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = if (lastStep) "شروع استفاده رایگان" else "ادامه",
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }

        if (step == 0) {
            OnboardingTelegramAction(
                linked = telegramLinked,
                busy = telegramBusy,
                onClick = onTelegramLogin,
            )
        }

        if (!lastStep) {
            Box(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(role = Role.Button, onClick = onComplete)
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "رد کردن راهنما و شروع رایگان",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun OnboardingTelegramAction(
    linked: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
) {
    val enabled = !linked && !busy
    val title = when {
        linked -> "حساب تلگرام متصل است"
        busy -> "در حال باز کردن تلگرام…"
        else -> "ورود اختیاری با تلگرام"
    }
    val body = if (linked) {
        "سرویس‌های خریداری‌شده حساب شما بعد از پایان راهنما همگام می‌شوند."
    } else {
        "فقط برای بازیابی سرویس‌های خریداری‌شده؛ استفاده رایگان همچنان بدون ورود ممکن است."
    }

    GanjGlassSurface(
        role = GanjGlassRole.Regular,
        accent = if (linked) MaterialTheme.colorScheme.primary else GanjGold,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        shapeRadius = 20.dp,
        padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (linked) MaterialTheme.colorScheme.primary else GanjGold,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OnboardingWelcomeCard() {
    GanjGlassSurface(
        role = GanjGlassRole.Prominent,
        accent = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth(),
        shapeRadius = 26.dp,
        padding = PaddingValues(18.dp),
    ) {
        OnboardingPoint(
            title = "بدون اجبار به ورود",
            body = "برای شروع استفاده رایگان لازم نیست شماره، کد تلگرام یا فرم حساب وارد کنید.",
        )
        OnboardingPoint(
            title = "حریم خصوصی در اولویت",
            body = "گنج VPN محتوای ترافیک، مقصدها و اطلاعات حساس اتصال را برای نمایش‌های تبلیغاتی جمع‌آوری نمی‌کند.",
        )
        OnboardingPoint(
            title = "حساب تلگرام اختیاری است",
            body = "برای سرویس‌های خریداری‌شده می‌توانید همین‌جا یا بعداً از بخش پروفایل حساب تلگرام خود را متصل کنید.",
        )
    }
}

@Composable
private fun OnboardingPlansCard() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GanjGlassSurface(
            role = GanjGlassRole.Regular,
            accent = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(),
            shapeRadius = 22.dp,
            padding = PaddingValues(16.dp),
        ) {
            Text(
                text = "رایگان",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "سرورهای رایگانی که از سمت گنج فعال باشند بدون ورود در دسترس قرار می‌گیرند.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        GanjGlassSurface(
            role = GanjGlassRole.Prominent,
            accent = GanjGold,
            modifier = Modifier.fillMaxWidth(),
            shapeRadius = 22.dp,
            padding = PaddingValues(16.dp),
        ) {
            Text(
                text = "پریمیوم",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = GanjGold,
            )
            Text(
                text = "برای دسترسی به سرویس‌های خریداری‌شده، پلن‌های فعال حساب شما از فروشگاه و پروفایل نمایش داده می‌شوند.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OnboardingConnectCard() {
    GanjGlassSurface(
        role = GanjGlassRole.Regular,
        accent = MaterialTheme.colorScheme.tertiary,
        modifier = Modifier.fillMaxWidth(),
        shapeRadius = 26.dp,
        padding = PaddingValues(18.dp),
    ) {
        OnboardingStep(number = "۱", title = "سرور را انتخاب کنید")
        OnboardingStep(number = "۲", title = "دکمه اتصال را بزنید")
        OnboardingStep(number = "۳", title = "اجازه VPN خود Android را تأیید کنید")
        Text(
            text = "قبل از درخواست مجوز سیستمی، گنج VPN توضیح کوتاهی درباره علت نیاز به آن نمایش می‌دهد.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OnboardingPoint(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OnboardingStep(number: String, title: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.34f),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun OnboardingProgress(step: Int, count: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(count) { index ->
            val selected = index == step
            Box(
                modifier = Modifier
                    .size(width = if (selected) 28.dp else 8.dp, height = 8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.30f),
                    ),
            )
        }
    }
}

private fun onboardingTitle(step: Int): String = when (step) {
    0 -> "به گنج VPN خوش آمدید"
    1 -> "رایگان شروع کنید، بعداً ارتقا دهید"
    else -> "اتصال در سه مرحله"
}

private fun onboardingSubtitle(step: Int): String = when (step) {
    0 -> "شروع سریع، فارسی و بدون اجبار به ساخت حساب"
    1 -> "پلن مناسب را هر زمان که نیاز داشتید انتخاب کنید"
    else -> "کنترل اتصال همیشه دست خود شماست"
}
