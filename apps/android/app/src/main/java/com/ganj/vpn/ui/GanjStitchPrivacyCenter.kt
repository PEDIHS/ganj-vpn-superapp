package com.ganj.vpn.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal data class PrivacySafetyFact(
    val title: String,
    val body: String,
    val positive: Boolean = true,
)

internal val canonicalPrivacySafetyFacts = listOf(
    PrivacySafetyFact(
        title = "ترافیک شما محتواخوانی نمی‌شود",
        body = "محتوای ترافیک، DNS query، کانفیگ VPN و Profile اتصال جزو داده‌های مجاز Analytics/Log/Diagnostics نیستند.",
    ),
    PrivacySafetyFact(
        title = "Secretها از گزارش‌ها حذف هستند",
        body = "Token، Authorization، purchase token، password، private key و لینک‌های vless/vmess/trojan/ss نباید در Log یا Payload پشتیبانی ثبت شوند.",
    ),
    PrivacySafetyFact(
        title = "شناسه‌های تبلیغاتی و سخت‌افزاری ممنوع‌اند",
        body = "Advertising ID، IMEI و شماره تلفن در قرارداد observability گنج جمع‌آوری نمی‌شوند.",
    ),
    PrivacySafetyFact(
        title = "Analytics فقط با رضایت",
        body = "Analytics باید opt-in باشد و فقط event/propertyهای نسخه‌شده و allowlist‌شده را بپذیرد؛ شناسه خام Telegram و User ID برای Analytics مجاز نیست.",
    ),
    PrivacySafetyFact(
        title = "Diagnostics محدود و زمان‌دار است",
        body = "Diagnostic payload فقط با رضایت صریح ارسال می‌شود و سیاست فعلی نگهداری آن حداکثر ۳۰ روز است، مگر اینکه Ticket مرتبط باز باشد.",
    ),
)

@Composable
internal fun StitchPrivacyCenter(
    onBack: () -> Unit,
    onOpenSecuritySupport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = responsiveHorizontalPadding(), vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppHeader(
            title = "حریم خصوصی و ایمنی داده",
            subtitle = "خلاصه‌ی داخل اپ از قرارداد Privacy/Observability گنج",
            onRefresh = null,
        )
        AccountBackAction(onBack)

        GanjGlassSurface(
            role = GanjGlassRole.Prominent,
            accent = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(),
            shapeRadius = 24.dp,
            padding = PaddingValues(18.dp),
        ) {
            GanjStatusPill("Privacy by design", GanjStatusTone.Positive)
            Text(
                text = "حداقل داده، رضایت صریح، بدون کانفیگ و Secret",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "گنج برای عیب‌یابی فقط فیلدهای از قبل Allowlist‌شده را می‌پذیرد. جمع‌آوری آزاد و بعداً Redact کردن، سیاست مجاز محصول نیست.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        GanjSectionHeader(
            title = "خلاصه Data Safety",
            supporting = "موارد زیر مستقیماً از قرارداد داخلی حریم خصوصی پروژه گرفته شده‌اند.",
        )
        canonicalPrivacySafetyFacts.forEach { fact ->
            GanjGlassSurface(
                role = GanjGlassRole.Regular,
                accent = if (fact.positive) MaterialTheme.colorScheme.primary else GanjWarning,
                modifier = Modifier.fillMaxWidth(),
                shapeRadius = 21.dp,
                padding = PaddingValues(15.dp),
            ) {
                Text(
                    text = fact.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = fact.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        GanjGlassSurface(
            role = GanjGlassRole.Dense,
            accent = GanjGold,
            modifier = Modifier.fillMaxWidth(),
            shapeRadius = 22.dp,
            padding = PaddingValues(16.dp),
        ) {
            Text(
                text = "گزارش امنیتی یا حریم خصوصی",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "برای گزارش مشکل امنیتی از مرکز پشتیبانی استفاده کنید و دسته «امنیت» را انتخاب کنید. کانفیگ، توکن یا کلید خصوصی را داخل پیام نفرستید.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GanjLiquidAction(
                onClick = onOpenSecuritySupport,
                accent = GanjGold,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "باز کردن پشتیبانی امن",
                    modifier = Modifier.align(Alignment.Center),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondary,
                )
            }
        }

        Text(
            text = "این صفحه جایگزین Privacy Policy/Terms حقوقی نهایی نیست؛ لینک‌های حقوقی رسمی تا زمان انتشار canonical policy جداگانه نمایش داده نمی‌شوند.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
