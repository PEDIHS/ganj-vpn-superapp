package com.ganj.vpn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

private val TelegramBlue = Color(0xFF229ED9)
private val AccountEmerald = Color(0xFF72FCB6)
private val AccountGold = Color(0xFFD5A63A)
private val TelegramFallbackErrors = setOf(
    "auth.bot_approval_unavailable",
    "auth.bot_approval_start_failed",
)

@Composable
internal fun StitchTelegramAccountCard(
    linked: Boolean,
    busy: Boolean,
    waitingForApproval: Boolean,
    errorCode: String?,
    syncFeedback: TelegramServiceSyncFeedback?,
    onLogin: () -> Unit,
    onCancelApproval: () -> Unit,
    onFallbackLogin: () -> Unit,
    onRetrySync: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showLoginConfirmation by remember { mutableStateOf(false) }
    var showLogoutConfirmation by remember { mutableStateOf(false) }
    val accent = if (linked) MaterialTheme.colorScheme.primary else TelegramBlue
    val title = when {
        waitingForApproval -> "منتظر تأیید در ربات گنج"
        linked -> "حساب تلگرام متصل است"
        else -> "ورود با تلگرام"
    }
    val description = when {
        waitingForApproval -> "در ربات گنج درخواست ورود را تأیید کنید، سپس به برنامه برگردید. هیچ کد یا رمز تلگرامی از شما گرفته نمی‌شود."
        linked -> "سرویس‌های خریداری‌شده و حساب گنج با این نشست همگام می‌شوند."
        else -> "برای استفاده از سرورهای رایگان نیازی به ورود نیست؛ برای سرویس‌های خریداری‌شده حساب تلگرام را متصل کنید."
    }
    val showSafeFallback = !waitingForApproval && !busy &&
        errorCode != null && errorCode in TelegramFallbackErrors
    val requiresRelogin = errorCode == "auth.session_expired" ||
        syncFeedback is TelegramServiceSyncFeedback.AuthRequired

    GanjGlassSurface(
        role = GanjGlassRole.Prominent,
        accent = accent,
        modifier = modifier.fillMaxWidth(),
        shapeRadius = 24.dp,
        padding = PaddingValues(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(TelegramBlue.copy(alpha = 0.13f))
                    .border(1.dp, TelegramBlue.copy(alpha = 0.42f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = when {
                        waitingForApproval -> "…"
                        linked -> "✓"
                        else -> "TG"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (linked && !waitingForApproval) AccountEmerald else TelegramBlue,
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AccountStatusBadge(
                text = when {
                    waitingForApproval -> "در انتظار"
                    linked -> "متصل"
                    else -> "مهمان"
                },
                positive = linked && !waitingForApproval,
            )
        }

        if (busy) {
            GanjInlineStatusBanner(
                message = when {
                    waitingForApproval -> "در حال بررسی نتیجه تأیید…"
                    linked -> "در حال به‌روزرسانی حساب…"
                    else -> "در حال ساخت درخواست امن و باز کردن تلگرام…"
                },
                tone = AccountBannerTone.Info,
            )
        } else if (waitingForApproval && errorCode == null) {
            GanjInlineStatusBanner(
                message = "اگر درخواست را در ربات تأیید کرده‌اید، به برنامه برگردید یا «بررسی وضعیت» را بزنید.",
                tone = AccountBannerTone.Info,
            )
        }

        errorCode?.let { code ->
            GanjInlineStatusBanner(
                message = telegramAuthErrorMessage(code),
                tone = if (code == "auth.bot_approval_cancelled") AccountBannerTone.Info else AccountBannerTone.Error,
            )
        }

        if (linked && !waitingForApproval && errorCode == null) {
            syncFeedback?.let { feedback ->
                GanjInlineStatusBanner(
                    message = telegramServiceSyncMessage(feedback),
                    tone = when (feedback) {
                        TelegramServiceSyncFeedback.AuthRequired,
                        is TelegramServiceSyncFeedback.Failed -> AccountBannerTone.Error
                        else -> AccountBannerTone.Info
                    },
                )
            }
        }

        when {
            waitingForApproval -> {
                GanjLiquidAction(
                    onClick = onLogin,
                    enabled = !busy,
                    accent = TelegramBlue,
                    shapeRadius = 999.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = if (busy) "در حال بررسی…" else "بررسی وضعیت تأیید",
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (!busy) {
                    AccountSecondaryAction(
                        text = "لغو این درخواست",
                        enabled = true,
                        onClick = onCancelApproval,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            linked -> {
                if (requiresRelogin && !busy) {
                    AccountSecondaryAction(
                        text = "ورود دوباره با تلگرام",
                        enabled = true,
                        onClick = { showLoginConfirmation = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                val failedSync = syncFeedback as? TelegramServiceSyncFeedback.Failed
                if (errorCode == null && failedSync?.retryable == true && !busy) {
                    AccountSecondaryAction(
                        text = "تلاش دوباره برای همگام‌سازی",
                        enabled = true,
                        onClick = onRetrySync,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (showSafeFallback) {
                    GanjInlineStatusBanner(
                        message = "ورود اصلی از طریق ربات موقتاً شروع نشد. برای تازه‌سازی نشست می‌توانید از ورود امن جایگزین استفاده کنید.",
                        tone = AccountBannerTone.Info,
                    )
                    AccountSecondaryAction(
                        text = "ورود جایگزین امن",
                        enabled = !busy,
                        onClick = onFallbackLogin,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (errorCode == "auth.logout_failed" && !busy) {
                    AccountSecondaryAction(
                        text = "تلاش دوباره برای خروج",
                        enabled = true,
                        destructive = true,
                        onClick = onLogout,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    AccountSecondaryAction(
                        text = if (busy) "لطفاً صبر کنید…" else "خروج از حساب تلگرام",
                        enabled = !busy,
                        destructive = true,
                        onClick = { showLogoutConfirmation = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            else -> {
                GanjLiquidAction(
                    onClick = { showLoginConfirmation = true },
                    enabled = !busy,
                    accent = TelegramBlue,
                    shapeRadius = 999.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = if (busy) "در حال باز کردن تلگرام…" else "ورود با تلگرام",
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }

                if (showSafeFallback) {
                    GanjInlineStatusBanner(
                        message = "اگر ورود از طریق ربات موقتاً در دسترس نیست، می‌توانید از ورود امن جایگزین استفاده کنید. این گزینه مسیر اصلی نیست.",
                        tone = AccountBannerTone.Info,
                    )
                    AccountSecondaryAction(
                        text = "ورود جایگزین امن",
                        enabled = true,
                        onClick = onFallbackLogin,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    if (showLoginConfirmation) {
        GanjLiquidConfirmDialog(
            title = if (linked) "ورود دوباره با ربات گنج" else "ورود با ربات گنج",
            body = if (linked) {
                "برای تازه‌سازی نشست حساب، تلگرام باز می‌شود و فقط با تأیید خودتان در ربات گنج یک نشست جدید برای همین دستگاه ساخته می‌شود."
            } else {
                "سرورهای رایگان بدون ورود قابل استفاده‌اند. با ادامه، تلگرام باز می‌شود و فقط اگر خودتان در ربات تأیید کنید حساب خریدهای گنج به همین دستگاه متصل می‌شود. هیچ کد ورود یا رمز تلگرام وارد اپ نمی‌کنید."
            },
            confirmText = "باز کردن تلگرام",
            dismissText = "انصراف",
            onConfirm = {
                showLoginConfirmation = false
                onLogin()
            },
            onDismiss = { showLoginConfirmation = false },
        )
    }

    if (showLogoutConfirmation) {
        GanjLiquidConfirmDialog(
            title = "خروج از حساب تلگرام؟",
            body = "نشست فعلی از این دستگاه پاک می‌شود. برای دیدن دوباره سرویس‌های حساب باید مجدداً وارد شوید.",
            confirmText = "خروج",
            dismissText = "انصراف",
            destructive = true,
            onConfirm = {
                showLogoutConfirmation = false
                onLogout()
            },
            onDismiss = { showLogoutConfirmation = false },
        )
    }
}

@Composable
internal fun GanjLiquidConfirmDialog(
    title: String,
    body: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    Dialog(onDismissRequest = onDismiss) {
        GanjGlassSurface(
            role = GanjGlassRole.Prominent,
            accent = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(),
            shapeRadius = 28.dp,
            padding = PaddingValues(20.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AccountSecondaryAction(
                    text = dismissText,
                    enabled = true,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                GanjLiquidAction(
                    onClick = onConfirm,
                    accent = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    shapeRadius = 999.dp,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = confirmText,
                        modifier = Modifier.align(Alignment.Center),
                        color = if (destructive) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

private enum class AccountBannerTone { Info, Error }

@Composable
private fun GanjInlineStatusBanner(message: String, tone: AccountBannerTone) {
    val accent = if (tone == AccountBannerTone.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(accent.copy(alpha = 0.10f))
            .border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = if (tone == AccountBannerTone.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun AccountStatusBadge(text: String, positive: Boolean) {
    val accent = if (positive) MaterialTheme.colorScheme.primary else AccountGold
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(accent.copy(alpha = 0.12f))
            .border(1.dp, accent.copy(alpha = 0.32f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (positive) AccountEmerald else AccountGold,
        )
    }
}

@Composable
private fun AccountSecondaryAction(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
) {
    val shape = RoundedCornerShape(999.dp)
    val accent = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = if (enabled) 0.62f else 0.30f))
            .border(1.dp, accent.copy(alpha = 0.30f), shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                destructive -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

private fun telegramServiceSyncMessage(feedback: TelegramServiceSyncFeedback): String = when (feedback) {
    TelegramServiceSyncFeedback.Syncing -> "حساب متصل شد؛ در حال همگام‌سازی سرویس‌های شما…"
    is TelegramServiceSyncFeedback.Success ->
        "حساب متصل شد و ${feedback.serviceCount.toPersianDigits()} سرویس با موفقیت همگام شد."
    TelegramServiceSyncFeedback.Empty -> "حساب متصل شد؛ سرویس فعالی برای این حساب پیدا نشد."
    TelegramServiceSyncFeedback.AuthRequired ->
        "حساب متصل شد اما نشست برای دریافت سرویس‌ها معتبر نیست. دوباره وارد شوید."
    is TelegramServiceSyncFeedback.Failed -> if (feedback.retryable) {
        "حساب متصل شد اما همگام‌سازی سرویس‌ها کامل نشد. می‌توانید دوباره تلاش کنید."
    } else {
        "حساب متصل شد اما سرویس‌ها فعلاً قابل دریافت نیستند."
    }
}

internal fun telegramAuthErrorMessage(code: String): String = when (code) {
    "auth.redirect_not_configured" -> "ورود تلگرام هنوز برای این نسخه پیکربندی نشده است."
    "auth.pkce_unavailable" -> "ایجاد درخواست امنیتی ورود ممکن نشد. دوباره تلاش کنید."
    "auth.bot_approval_start_failed" -> "ساخت درخواست ورود در ربات انجام نشد. دوباره تلاش کنید."
    "auth.bot_approval_status_failed" -> "وضعیت تأیید از سرور دریافت نشد. دوباره بررسی کنید."
    "auth.bot_approval_exchange_failed" -> "تأیید انجام شد اما تکمیل نشست ممکن نشد. ورود را دوباره شروع کنید."
    "auth.bot_approval_unavailable" -> "ورود از طریق ربات موقتاً در دسترس نیست."
    "auth.bot_approval_offline" -> "برای بررسی تأیید، اتصال اینترنت را برقرار کنید و دوباره تلاش کنید."
    "auth.bot_approval_pending" -> "درخواست هنوز در ربات تأیید نشده است."
    "auth.bot_approval_denied" -> "درخواست ورود در ربات رد شد. برای ورود دوباره یک درخواست جدید بسازید."
    "auth.bot_approval_expired" -> "مهلت تأیید ورود تمام شده است. دوباره «ورود با تلگرام» را بزنید."
    "auth.bot_approval_replayed" -> "این درخواست ورود قبلاً مصرف شده است. یک درخواست جدید بسازید."
    "auth.bot_approval_wrong_device" -> "این درخواست متعلق به این دستگاه نیست یا دیگر معتبر نیست."
    "auth.bot_approval_binding_mismatch" -> "اعتبارسنجی امنیتی درخواست ورود ناموفق بود. ورود را دوباره شروع کنید."
    "auth.bot_approval_not_pending" -> "درخواست فعالی برای تأیید در ربات وجود ندارد."
    "auth.bot_approval_cancelled" -> "درخواست ورود در این دستگاه لغو شد. برای ورود، یک درخواست جدید بسازید."
    "auth.session_expired" -> "نشست امن دستگاه منقضی شده است. ورود را دوباره شروع کنید."
    "auth.telegram_start_failed" -> "شروع ورود جایگزین تلگرام انجام نشد. اتصال اینترنت را بررسی کنید."
    "auth.flow_persistence_failed" -> "ذخیره امن درخواست ورود انجام نشد. دوباره تلاش کنید."
    "auth.flow_missing_or_consumed" -> "این درخواست ورود قبلاً استفاده شده یا دیگر معتبر نیست."
    "auth.flow_expired" -> "مهلت این درخواست ورود تمام شده است. ورود را دوباره شروع کنید."
    "auth.callback_invalid" -> "پاسخ ورود تلگرام معتبر نبود."
    "auth.callback_redirect_mismatch" -> "بازگشت ورود از مسیر مورد انتظار انجام نشد."
    "auth.callback_state_mismatch" -> "اعتبارسنجی امنیتی ورود ناموفق بود. ورود را دوباره شروع کنید."
    "auth.flow_clear_failed" -> "پاک‌سازی امن درخواست ورود انجام نشد. دوباره تلاش کنید."
    "auth.telegram_exchange_failed" -> "تکمیل ورود جایگزین تلگرام انجام نشد. دوباره تلاش کنید."
    "auth.logout_failed" -> "خروج از سرور تأیید نشد. نشست این دستگاه حفظ شده تا بتوانید دوباره تلاش کنید."
    "auth.telegram_launch_failed" -> "باز کردن مسیر ورود تلگرام ممکن نشد."
    "auth.unavailable" -> "سرویس ورود در این نسخه در دسترس نیست."
    else -> "ورود تلگرام با خطا روبه‌رو شد. دوباره تلاش کنید."
}
