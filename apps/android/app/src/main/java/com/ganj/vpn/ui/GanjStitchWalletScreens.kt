package com.ganj.vpn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ganj.vpn.core.controlapi.Money
import com.ganj.vpn.core.controlapi.WalletSnapshot
import com.ganj.vpn.core.controlapi.WalletTransaction
import com.ganj.vpn.core.controlapi.WalletTransactionDirection
import com.ganj.vpn.core.controlapi.WalletTransactionType
import java.text.NumberFormat
import java.util.Locale

internal sealed interface WalletUiState {
    data object Loading : WalletUiState
    data class Ready(
        val snapshot: WalletSnapshot,
        val recentTransactions: List<WalletTransaction>,
    ) : WalletUiState
    data object Empty : WalletUiState
    data object AuthRequired : WalletUiState
    data class Error(val message: String, val retryable: Boolean) : WalletUiState
}

@Composable
internal fun StitchWalletEntry(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = WalletNavigationEntry(
    title = "کیف پول",
    description = "موجودی و آخرین تراکنش‌ها",
    symbol = "﷼",
    onClick = onClick,
    modifier = modifier,
)

@Composable
internal fun StitchTransactionsEntry(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = WalletNavigationEntry(
    title = "تراکنش‌ها",
    description = "تاریخچه کامل گردش حساب",
    symbol = "↕",
    onClick = onClick,
    modifier = modifier,
)

@Composable
private fun WalletNavigationEntry(
    title: String,
    description: String,
    symbol: String,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.56f))
            .border(1.dp, LocalGanjGlassPalette.current.borderSoft.copy(alpha = 0.62f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(symbol, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("‹", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun StitchWalletScreen(
    state: WalletUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenTransactions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = responsiveHorizontalPadding(),
            end = responsiveHorizontalPadding(),
            top = 18.dp,
            bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            WalletHeader(
                title = "کیف پول",
                subtitle = "موجودی واقعی حساب گنج",
                onBack = onBack,
            )
        }

        when (state) {
            WalletUiState.Loading -> item { WalletLoadingCard() }
            WalletUiState.Empty -> item {
                WalletMessageCard(
                    title = "کیف پول آماده است",
                    body = "هنوز گردش مالی برای این حساب ثبت نشده است.",
                    action = "به‌روزرسانی",
                    onAction = onRefresh,
                )
            }
            WalletUiState.AuthRequired -> item {
                WalletMessageCard(
                    title = "ورود لازم است",
                    body = "برای مشاهده کیف پول باید حساب تلگرام شما به گنج متصل باشد.",
                    action = null,
                    onAction = null,
                )
            }
            is WalletUiState.Error -> item {
                WalletMessageCard(
                    title = "کیف پول در دسترس نیست",
                    body = state.message,
                    action = if (state.retryable) "تلاش دوباره" else null,
                    onAction = if (state.retryable) onRefresh else null,
                )
            }
            is WalletUiState.Ready -> {
                item {
                    WalletBalanceCard(state.snapshot)
                }
                item {
                    GanjGlassSurface(
                        role = GanjGlassRole.Dense,
                        accent = GanjGold,
                        modifier = Modifier.fillMaxWidth(),
                        shapeRadius = 22.dp,
                        padding = PaddingValues(15.dp),
                    ) {
                        Text(
                            "افزایش موجودی",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "روش افزایش موجودی پس از اتصال Provider پرداخت واقعی فعال می‌شود؛ مبلغ ساختگی یا واریز مستقیم داخل اپ ایجاد نشده است.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "آخرین تراکنش‌ها",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "مشاهده همه",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .clickable(role = Role.Button, onClick = onOpenTransactions)
                                .padding(horizontal = 8.dp, vertical = 14.dp),
                        )
                    }
                }
                if (state.recentTransactions.isEmpty()) {
                    item {
                        WalletMessageCard(
                            title = "تراکنشی ثبت نشده",
                            body = "وقتی خرید، افزایش موجودی یا بازگشت وجه واقعی ثبت شود اینجا نمایش داده می‌شود.",
                            action = null,
                            onAction = null,
                        )
                    }
                } else {
                    items(state.recentTransactions.take(5), key = { it.id }) { transaction ->
                        WalletTransactionRow(transaction)
                    }
                }
                item {
                    WalletTextAction("به‌روزرسانی موجودی", onRefresh)
                }
            }
        }
    }
}

@Composable
internal fun StitchTransactionsScreen(
    transactions: List<WalletTransaction>,
    loading: Boolean,
    loadingMore: Boolean,
    errorMessage: String?,
    hasMore: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = responsiveHorizontalPadding(),
            end = responsiveHorizontalPadding(),
            top = 18.dp,
            bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            WalletHeader(
                title = "تراکنش‌ها",
                subtitle = "گردش واقعی کیف پول",
                onBack = onBack,
            )
        }

        if (loading && transactions.isEmpty()) {
            item { WalletLoadingCard() }
        } else if (errorMessage != null && transactions.isEmpty()) {
            item {
                WalletMessageCard(
                    title = "دریافت تراکنش‌ها ناموفق بود",
                    body = errorMessage,
                    action = "تلاش دوباره",
                    onAction = onRefresh,
                )
            }
        } else if (transactions.isEmpty()) {
            item {
                WalletMessageCard(
                    title = "تراکنشی وجود ندارد",
                    body = "هنوز هیچ ورودی واقعی در دفتر کیف پول این حساب ثبت نشده است.",
                    action = "به‌روزرسانی",
                    onAction = onRefresh,
                )
            }
        } else {
            items(transactions, key = { it.id }) { transaction ->
                WalletTransactionRow(transaction)
            }
            if (errorMessage != null) {
                item {
                    WalletMessageCard(
                        title = "ادامه فهرست دریافت نشد",
                        body = errorMessage,
                        action = "تلاش دوباره",
                        onAction = if (hasMore) onLoadMore else onRefresh,
                    )
                }
            }
            if (hasMore) {
                item {
                    if (loadingMore) {
                        WalletLoadingCard(compact = true)
                    } else {
                        WalletTextAction("نمایش تراکنش‌های بیشتر", onLoadMore)
                    }
                }
            }
        }
    }
}

@Composable
private fun WalletBalanceCard(snapshot: WalletSnapshot) {
    GanjGlassSurface(
        role = GanjGlassRole.Regular,
        accent = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth(),
        shapeRadius = 28.dp,
        padding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
    ) {
        Text(
            "موجودی قابل استفاده",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            formatMoney(snapshot.balance),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "مقدار بالا مستقیم از Control API خوانده شده است.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WalletTransactionRow(transaction: WalletTransaction) {
    val credit = transaction.direction == WalletTransactionDirection.CREDIT
    val accent = if (credit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
    GanjGlassSurface(
        role = GanjGlassRole.Dense,
        accent = accent,
        modifier = Modifier.fillMaxWidth(),
        shapeRadius = 20.dp,
        padding = PaddingValues(horizontal = 15.dp, vertical = 13.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.12f))
                    .border(1.dp, accent.copy(alpha = 0.28f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (credit) "+" else "−", color = accent, fontWeight = FontWeight.Black)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    transactionTitle(transaction.type),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                transaction.description?.takeIf(String::isNotBlank)?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    transaction.createdAt.replace('T', ' ').substringBefore('.').removeSuffix("Z").toPersianDigits(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    (if (credit) "+" else "−") + formatMoney(transaction.amount),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
                Text(
                    "مانده ${formatMoney(transaction.balanceAfter)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!transaction.referenceId.isNullOrBlank()) {
            Text(
                "مرجع: ${transaction.referenceId}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WalletHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.58f))
                .border(1.dp, LocalGanjGlassPalette.current.borderSoft.copy(alpha = 0.62f), RoundedCornerShape(999.dp))
                .clickable(role = Role.Button, onClick = onBack)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("بازگشت", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WalletLoadingCard(compact: Boolean = false) {
    GanjGlassSurface(
        role = GanjGlassRole.Dense,
        accent = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth(),
        shapeRadius = 22.dp,
        padding = PaddingValues(if (compact) 12.dp else 20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            Text("در حال دریافت اطلاعات واقعی کیف پول…", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun WalletMessageCard(
    title: String,
    body: String,
    action: String?,
    onAction: (() -> Unit)?,
) {
    GanjGlassSurface(
        role = GanjGlassRole.Dense,
        accent = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth(),
        shapeRadius = 22.dp,
        padding = PaddingValues(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null && onAction != null) {
            WalletTextAction(action, onAction)
        }
    }
}

@Composable
private fun WalletTextAction(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f), RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    }
}

private fun transactionTitle(type: WalletTransactionType): String = when (type) {
    WalletTransactionType.TOPUP -> "افزایش موجودی"
    WalletTransactionType.PURCHASE -> "خرید اشتراک"
    WalletTransactionType.REFUND -> "بازگشت وجه"
    WalletTransactionType.ADJUSTMENT -> "اصلاح موجودی"
    WalletTransactionType.REVERSAL -> "برگشت تراکنش"
}

private fun formatMoney(money: Money): String {
    val grouped = NumberFormat.getIntegerInstance(Locale.US).format(money.amountMinor)
    val unit = when (money.currency) {
        "IRR" -> "ریال"
        else -> money.currency
    }
    return "$grouped $unit".toPersianDigits()
}
