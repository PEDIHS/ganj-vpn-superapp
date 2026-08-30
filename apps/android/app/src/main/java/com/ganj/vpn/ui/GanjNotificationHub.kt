package com.ganj.vpn.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.ganj.vpn.composition.NotificationCompositionRegistry
import com.ganj.vpn.core.controlapi.ApiError
import com.ganj.vpn.core.controlapi.ApiResult
import com.ganj.vpn.core.controlapi.NotificationActionType
import com.ganj.vpn.core.controlapi.NotificationPreferences
import com.ganj.vpn.core.controlapi.UserNotification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class NotificationHubSurface { CENTER, PREFERENCES }

@Composable
internal fun StitchNotificationHub(
    onBack: () -> Unit,
    onOpenSupport: (String?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val api = remember { NotificationCompositionRegistry.currentApi() }
    val scope = rememberCoroutineScope()
    var surface by remember { mutableStateOf(NotificationHubSurface.CENTER) }
    var state by remember { mutableStateOf<NotificationUiState>(NotificationUiState.Loading) }
    var nextCursor by remember { mutableStateOf<String?>(null) }
    var loadingMore by remember { mutableStateOf(false) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var preferences by remember { mutableStateOf<NotificationPreferences?>(null) }
    var preferencesLoading by remember { mutableStateOf(false) }
    var preferencesSaving by remember { mutableStateOf(false) }
    var preferencesError by remember { mutableStateOf<String?>(null) }
    var permissionGranted by remember { mutableStateOf(notificationPermissionGranted(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted || notificationPermissionGranted(context)
    }

    fun errorMessage(error: ApiError): Pair<String, Boolean> = when (error) {
        is ApiError.AuthenticationRequired,
        is ApiError.AuthenticationExpired -> "نشست حساب منقضی شده است؛ دوباره وارد شوید." to false
        is ApiError.Network -> "اتصال اینترنت را بررسی کنید." to true
        is ApiError.RateLimited -> "درخواست‌های زیادی ارسال شده است؛ کمی بعد دوباره تلاش کنید." to true
        is ApiError.Forbidden -> "دسترسی این حساب به اعلان‌ها محدود شده است." to false
        is ApiError.Server -> "سرویس اعلان‌ها موقتاً در دسترس نیست." to error.retryable
        else -> "اطلاعات اعلان‌ها دریافت نشد." to true
    }

    fun refresh() {
        val active = api ?: run {
            state = NotificationUiState.Error("سرویس اعلان‌ها در این نسخه فعال نیست.", false)
            return
        }
        state = NotificationUiState.Loading
        actionMessage = null
        scope.launch {
            when (val result = withContext(Dispatchers.IO) { active.notifications(limit = 30) }) {
                is ApiResult.Success -> {
                    nextCursor = result.value.nextCursor
                    GanjNotificationUnreadRegistry.update(result.value.unreadCount)
                    state = NotificationUiState.Ready(
                        items = result.value.items,
                        unreadCount = result.value.unreadCount,
                        hasMore = result.value.hasMore,
                        loadingMore = false,
                        errorMessage = null,
                    )
                }
                is ApiResult.Failure -> {
                    val (message, retryable) = errorMessage(result.error)
                    val authFailure = result.error is ApiError.AuthenticationRequired || result.error is ApiError.AuthenticationExpired
                    if (authFailure) GanjNotificationUnreadRegistry.clear()
                    state = if (authFailure) {
                        NotificationUiState.AuthRequired
                    } else {
                        NotificationUiState.Error(message, retryable)
                    }
                }
            }
        }
    }

    fun loadMore() {
        val active = api ?: return
        val current = state as? NotificationUiState.Ready ?: return
        val cursor = nextCursor ?: return
        if (loadingMore || !current.hasMore) return
        loadingMore = true
        state = current.copy(loadingMore = true, errorMessage = null)
        scope.launch {
            when (val result = withContext(Dispatchers.IO) { active.notifications(cursor = cursor, limit = 30) }) {
                is ApiResult.Success -> {
                    val known = current.items.asSequence().map { it.id }.toHashSet()
                    val merged = current.items + result.value.items.filterNot { it.id in known }
                    nextCursor = result.value.nextCursor
                    GanjNotificationUnreadRegistry.update(result.value.unreadCount)
                    state = current.copy(
                        items = merged,
                        unreadCount = result.value.unreadCount,
                        hasMore = result.value.hasMore,
                        loadingMore = false,
                        errorMessage = null,
                    )
                }
                is ApiResult.Failure -> {
                    val (message, _) = errorMessage(result.error)
                    state = current.copy(loadingMore = false, errorMessage = message)
                }
            }
            loadingMore = false
        }
    }

    fun markRead(item: UserNotification) {
        if (item.read) return
        val active = api ?: return
        scope.launch {
            when (withContext(Dispatchers.IO) { active.markRead(item.id) }) {
                is ApiResult.Success -> {
                    val current = state as? NotificationUiState.Ready ?: return@launch
                    val nextUnreadCount = (current.unreadCount - 1).coerceAtLeast(0)
                    GanjNotificationUnreadRegistry.update(nextUnreadCount)
                    state = current.copy(
                        items = current.items.map { if (it.id == item.id) it.copy(read = true) else it },
                        unreadCount = nextUnreadCount,
                    )
                }
                is ApiResult.Failure -> actionMessage = "وضعیت خواندن اعلان ذخیره نشد؛ دوباره تلاش کنید."
            }
        }
    }

    fun markAllRead() {
        val active = api ?: return
        scope.launch {
            when (withContext(Dispatchers.IO) { active.markAllRead() }) {
                is ApiResult.Success -> {
                    val current = state as? NotificationUiState.Ready ?: return@launch
                    GanjNotificationUnreadRegistry.update(0)
                    state = current.copy(items = current.items.map { it.copy(read = true) }, unreadCount = 0)
                }
                is ApiResult.Failure -> actionMessage = "خواندن همه اعلان‌ها ذخیره نشد."
            }
        }
    }

    fun refreshPreferences() {
        val active = api ?: run {
            preferencesError = "سرویس تنظیمات اعلان فعال نیست."
            return
        }
        preferencesLoading = true
        preferencesError = null
        scope.launch {
            when (val result = withContext(Dispatchers.IO) { active.preferences() }) {
                is ApiResult.Success -> preferences = result.value
                is ApiResult.Failure -> preferencesError = errorMessage(result.error).first
            }
            preferencesLoading = false
        }
    }

    fun savePreferences(next: NotificationPreferences) {
        val active = api ?: return
        if (preferencesSaving) return
        preferencesSaving = true
        preferencesError = null
        scope.launch {
            when (val result = withContext(Dispatchers.IO) { active.updatePreferences(next) }) {
                is ApiResult.Success -> preferences = result.value
                is ApiResult.Failure -> preferencesError = errorMessage(result.error).first
            }
            preferencesSaving = false
        }
    }

    fun handleAction(item: UserNotification) {
        when (item.action?.type) {
            NotificationActionType.OPEN_SETTINGS -> {
                surface = NotificationHubSurface.PREFERENCES
                refreshPreferences()
            }
            NotificationActionType.OPEN_SUPPORT -> onOpenSupport(item.action.id)
            null -> Unit
            else -> actionMessage = "این اعلان خوانده شد. مقصد داخلی آن در مرحله بعدی مسیریابی اپ یکپارچه می‌شود."
        }
    }

    LaunchedEffect(Unit) { refresh() }

    when (surface) {
        NotificationHubSurface.CENTER -> StitchNotificationsScreen(
            state = when (val current = state) {
                is NotificationUiState.Ready -> current.copy(errorMessage = actionMessage ?: current.errorMessage)
                else -> current
            },
            onBack = onBack,
            onRefresh = ::refresh,
            onMarkRead = ::markRead,
            onMarkAllRead = ::markAllRead,
            onLoadMore = ::loadMore,
            onOpenPreferences = {
                surface = NotificationHubSurface.PREFERENCES
                refreshPreferences()
            },
            onAction = ::handleAction,
            modifier = modifier,
        )
        NotificationHubSurface.PREFERENCES -> StitchNotificationPreferencesScreen(
            preferences = preferences,
            loading = preferencesLoading,
            saving = preferencesSaving,
            errorMessage = preferencesError,
            systemPermissionRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
            systemPermissionGranted = permissionGranted,
            onBack = { surface = NotificationHubSurface.CENTER },
            onRefresh = ::refreshPreferences,
            onSave = ::savePreferences,
            onRequestSystemPermission = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onOpenSystemSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                )
            },
            modifier = modifier,
        )
    }
}

private fun notificationPermissionGranted(context: android.content.Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
