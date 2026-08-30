package com.ganj.vpn

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.ganj.vpn.composition.GanjCompositionOwner
import com.ganj.vpn.composition.TelegramAuthResult
import com.ganj.vpn.core.controlapi.ApiError
import com.ganj.vpn.core.controlapi.ApiResult
import com.ganj.vpn.core.controlapi.CurrentAccount
import com.ganj.vpn.presentation.ConnectionEffectResult
import com.ganj.vpn.presentation.UiFailure
import com.ganj.vpn.presentation.UiFailureKind
import com.ganj.vpn.ui.AndroidGanjUserPreferencesStore
import com.ganj.vpn.ui.GanjLiquidConfirmDialog
import com.ganj.vpn.ui.GanjTheme
import com.ganj.vpn.ui.GanjUserPreferences
import com.ganj.vpn.ui.GanjVpnApp
import com.ganj.vpn.ui.resolvedGanjDarkTheme
import com.ganj.vpn.ui.resolvedGanjVisualEffectsPolicy
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

class MainActivity : ComponentActivity() {
    private var vpnPermissionContinuation: CancellableContinuation<Boolean>? = null
    private var vpnPermissionExplanationContinuation: CancellableContinuation<Boolean>? = null
    private lateinit var owner: GanjCompositionOwner
    private lateinit var userPreferencesStore: AndroidGanjUserPreferencesStore

    private val telegramLinked = mutableStateOf(false)
    private val telegramBusy = mutableStateOf(false)
    private val telegramWaiting = mutableStateOf(false)
    private val telegramErrorCode = mutableStateOf<String?>(null)
    private val currentAccount = mutableStateOf<CurrentAccount?>(null)
    private val accountIdentityLoading = mutableStateOf(false)
    private val accountIdentityErrorCode = mutableStateOf<String?>(null)
    private val accountRefreshGeneration = mutableStateOf(0)
    private val userPreferences = mutableStateOf(GanjUserPreferences())
    private val vpnPermissionExplanationVisible = mutableStateOf(false)
    private val vpnPermissionDeniedVisible = mutableStateOf(false)

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val granted = result.resultCode == Activity.RESULT_OK
        val continuation = vpnPermissionContinuation
        vpnPermissionContinuation = null
        if (!granted) vpnPermissionDeniedVisible.value = true
        if (continuation?.isActive == true) {
            continuation.resume(granted)
        }
    }

    override fun attachBaseContext(newBase: Context) {
        val persian = Locale.forLanguageTag("fa")
        val configuration = Configuration(newBase.resources.configuration).apply {
            setLocale(persian)
            setLayoutDirection(persian)
        }
        super.attachBaseContext(newBase.createConfigurationContext(configuration))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        userPreferencesStore = AndroidGanjUserPreferencesStore(application)
        userPreferences.value = userPreferencesStore.restore()

        owner = ViewModelProvider(
            this,
            GanjCompositionOwner.Factory(
                application = application,
                endpoint = BuildConfig.CONTROL_API_BASE_URL,
                telegramRedirectUri = BuildConfig.TELEGRAM_REDIRECT_URI,
            ),
        )[GanjCompositionOwner::class.java]

        telegramLinked.value = owner.telegramAuth?.isLinked() == true
        telegramWaiting.value = owner.telegramAuth?.hasPendingBotApproval() == true
        handleTelegramIntent(intent)

        val composition = owner.composition
        setContent {
            val preferences = userPreferences.value
            GanjTheme(
                darkTheme = resolvedGanjDarkTheme(preferences),
                visualEffectsPolicy = resolvedGanjVisualEffectsPolicy(preferences),
            ) {
                GanjVpnApp(
                    composition = composition,
                    telegramLinked = telegramLinked.value,
                    telegramBusy = telegramBusy.value,
                    telegramWaiting = telegramWaiting.value,
                    telegramErrorCode = telegramErrorCode.value,
                    currentAccount = currentAccount.value,
                    accountIdentityLoading = accountIdentityLoading.value,
                    accountIdentityErrorCode = accountIdentityErrorCode.value,
                    accountRefreshGeneration = accountRefreshGeneration.value,
                    userPreferences = preferences,
                    onTelegramLogin = ::beginTelegramLogin,
                    onTelegramCancel = ::cancelTelegramApproval,
                    onTelegramFallback = ::beginTelegramOidcFallback,
                    onTelegramLogout = ::logoutTelegram,
                    onRetryAccountIdentity = ::refreshCurrentAccount,
                    onThemePreferenceChanged = { theme ->
                        updateUserPreferences { it.copy(theme = theme) }
                    },
                    onReduceMotionChanged = { enabled ->
                        updateUserPreferences { it.copy(reduceMotion = enabled) }
                    },
                    onReduceTransparencyChanged = { enabled ->
                        updateUserPreferences { it.copy(reduceTransparency = enabled) }
                    },
                    onOnboardingCompleted = {
                        updateUserPreferences { it.copy(onboardingCompleted = true) }
                    },
                    onRestartOnboarding = {
                        updateUserPreferences { it.copy(onboardingCompleted = false) }
                    },
                    onLaunchGooglePlay = { handle ->
                        composition.launchGooglePlayCheckout(this@MainActivity, handle)
                    },
                    onLaunchVpn = { handle ->
                        if (ensureVpnPermission()) {
                            composition.launchVpnConnection(handle)
                        } else {
                            ConnectionEffectResult.Failed(
                                UiFailure(
                                    UiFailureKind.CONFIGURATION,
                                    "connection.permission_denied",
                                    retryable = true,
                                ),
                            )
                        }
                    },
                )

                if (vpnPermissionExplanationVisible.value) {
                    GanjLiquidConfirmDialog(
                        title = "اجازه اتصال VPN",
                        body = "برای ساخت تونل امن، Android باید اجازه VPN را تأیید کند. گنج VPN به محتوای شخصی شما دسترسی اضافه‌ای درخواست نمی‌کند و صفحه بعدی متعلق به خود Android است.",
                        confirmText = "ادامه",
                        dismissText = "فعلاً نه",
                        onConfirm = { completeVpnPermissionExplanation(true) },
                        onDismiss = { completeVpnPermissionExplanation(false) },
                    )
                }

                if (vpnPermissionDeniedVisible.value) {
                    GanjLiquidConfirmDialog(
                        title = "اجازه VPN داده نشد",
                        body = "بدون تأیید مجوز VPN، اتصال شروع نمی‌شود. هر زمان آماده بودید دوباره دکمه اتصال را بزنید و درخواست Android را تأیید کنید.",
                        confirmText = "متوجه شدم",
                        dismissText = "بستن",
                        onConfirm = { vpnPermissionDeniedVisible.value = false },
                        onDismiss = { vpnPermissionDeniedVisible.value = false },
                    )
                }
            }
        }

        if (telegramLinked.value) refreshCurrentAccount()
    }

    override fun onResume() {
        super.onResume()
        if (::owner.isInitialized && owner.telegramAuth?.hasPendingBotApproval() == true) {
            resumeTelegramApproval()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleTelegramIntent(intent)
    }

    private fun updateUserPreferences(transform: (GanjUserPreferences) -> GanjUserPreferences) {
        val next = transform(userPreferences.value)
        if (next == userPreferences.value) return
        if (userPreferencesStore.save(next)) {
            userPreferences.value = next
        }
    }

    private fun refreshCurrentAccount() {
        if (!::owner.isInitialized || accountIdentityLoading.value) return
        accountIdentityLoading.value = true
        accountIdentityErrorCode.value = null
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { owner.currentAccount() }
            accountIdentityLoading.value = false
            when (result) {
                null -> accountIdentityErrorCode.value = "account.unavailable"
                is ApiResult.Success -> {
                    currentAccount.value = result.value
                    telegramLinked.value = result.value.telegramLinked
                    owner.telegramAuth?.reconcileLinkedState(result.value.telegramLinked)
                    accountIdentityErrorCode.value = null
                }
                is ApiResult.Failure -> {
                    accountIdentityErrorCode.value = accountIdentityErrorCode(result.error)
                }
            }
        }
    }

    private fun accountIdentityErrorCode(error: ApiError): String = when (error) {
        is ApiError.Network -> "account.offline"
        is ApiError.AuthenticationRequired,
        is ApiError.AuthenticationExpired -> "account.auth_required"
        is ApiError.Forbidden -> if (error.code == "account_inactive") "account.inactive" else "account.unavailable"
        is ApiError.NotFound -> if (error.code == "account_not_found") "account.not_found" else "account.unavailable"
        else -> "account.unavailable"
    }

    private fun beginTelegramLogin() {
        if (telegramBusy.value) return
        val auth = owner.telegramAuth ?: run {
            telegramErrorCode.value = "auth.unavailable"
            return
        }
        if (auth.hasPendingBotApproval()) {
            resumeTelegramApproval()
            return
        }

        telegramBusy.value = true
        telegramWaiting.value = false
        telegramErrorCode.value = null
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { auth.begin() }
            telegramBusy.value = false
            when (result) {
                is TelegramAuthResult.Launch -> launchTelegram(result.authorizationUrl, waitingAfterLaunch = true)
                else -> applyTelegramResult(result)
            }
        }
    }

    private fun beginTelegramOidcFallback() {
        if (telegramBusy.value) return
        val auth = owner.telegramAuth ?: run {
            telegramErrorCode.value = "auth.unavailable"
            return
        }
        if (auth.hasPendingBotApproval()) {
            telegramErrorCode.value = "auth.bot_approval_pending"
            return
        }

        telegramBusy.value = true
        telegramWaiting.value = false
        telegramErrorCode.value = null
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { auth.beginOidcFallback() }
            telegramBusy.value = false
            when (result) {
                is TelegramAuthResult.Launch -> launchTelegram(result.authorizationUrl, waitingAfterLaunch = false)
                else -> applyTelegramResult(result)
            }
        }
    }

    private fun launchTelegram(url: String, waitingAfterLaunch: Boolean) {
        val launched = runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.isSuccess
        telegramWaiting.value = launched && waitingAfterLaunch
        if (!launched) telegramErrorCode.value = "auth.telegram_launch_failed"
    }

    private fun resumeTelegramApproval() {
        if (telegramBusy.value || !::owner.isInitialized) return
        val auth = owner.telegramAuth ?: return
        if (!auth.hasPendingBotApproval()) return

        telegramBusy.value = true
        telegramWaiting.value = true
        telegramErrorCode.value = null
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { auth.resumeBotApproval() }
            telegramBusy.value = false
            applyTelegramResult(result)
        }
    }

    private fun cancelTelegramApproval() {
        if (telegramBusy.value || !::owner.isInitialized) return
        val auth = owner.telegramAuth ?: return
        val result = auth.cancelPendingBotApproval()
        applyTelegramResult(result)
    }

    private fun handleTelegramIntent(intent: Intent?) {
        val callback = intent
            ?.takeIf { it.action == Intent.ACTION_VIEW }
            ?.dataString
            ?: return
        if (!::owner.isInitialized || telegramBusy.value) return

        val auth = owner.telegramAuth ?: return
        telegramBusy.value = true
        telegramErrorCode.value = null
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { auth.complete(callback) }
            telegramBusy.value = false
            applyTelegramResult(result)
        }
    }

    private fun applyTelegramResult(result: TelegramAuthResult) {
        when (result) {
            TelegramAuthResult.Linked -> {
                telegramLinked.value = true
                telegramWaiting.value = false
                telegramErrorCode.value = null
                accountRefreshGeneration.value += 1
                refreshCurrentAccount()
            }
            TelegramAuthResult.Waiting -> {
                telegramWaiting.value = true
                telegramErrorCode.value = null
            }
            TelegramAuthResult.Cancelled -> {
                telegramLinked.value = owner.telegramAuth?.isLinked() == true
                telegramWaiting.value = false
                telegramErrorCode.value = "auth.bot_approval_cancelled"
            }
            TelegramAuthResult.LoggedOut -> {
                telegramLinked.value = false
                telegramWaiting.value = false
                telegramErrorCode.value = null
                currentAccount.value = null
                accountIdentityErrorCode.value = null
            }
            is TelegramAuthResult.Failed -> {
                telegramLinked.value = owner.telegramAuth?.isLinked() == true
                telegramWaiting.value = owner.telegramAuth?.hasPendingBotApproval() == true
                telegramErrorCode.value = result.code
            }
            is TelegramAuthResult.Launch -> Unit
        }
    }

    private fun logoutTelegram() {
        if (telegramBusy.value) return
        val auth = owner.telegramAuth ?: run {
            telegramErrorCode.value = "auth.unavailable"
            return
        }

        telegramBusy.value = true
        telegramErrorCode.value = null
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { auth.logout() }
            telegramBusy.value = false
            telegramLinked.value = auth.isLinked()
            telegramWaiting.value = false
            if (result is TelegramAuthResult.LoggedOut) {
                accountRefreshGeneration.value += 1
                currentAccount.value = null
                accountIdentityErrorCode.value = null
            }
            applyTelegramResult(result)
        }
    }

    private suspend fun ensureVpnPermission(): Boolean {
        val request: Intent = VpnService.prepare(this) ?: return true
        if (!awaitVpnPermissionExplanation()) return false
        return awaitSystemVpnPermission(request)
    }

    private suspend fun awaitVpnPermissionExplanation(): Boolean =
        suspendCancellableCoroutine { continuation ->
            if (vpnPermissionExplanationContinuation != null || vpnPermissionContinuation != null) {
                continuation.resume(false)
                return@suspendCancellableCoroutine
            }
            vpnPermissionExplanationContinuation = continuation
            vpnPermissionExplanationVisible.value = true
            continuation.invokeOnCancellation {
                if (vpnPermissionExplanationContinuation === continuation) {
                    vpnPermissionExplanationContinuation = null
                    vpnPermissionExplanationVisible.value = false
                }
            }
        }

    private fun completeVpnPermissionExplanation(accepted: Boolean) {
        vpnPermissionExplanationVisible.value = false
        val continuation = vpnPermissionExplanationContinuation
        vpnPermissionExplanationContinuation = null
        if (continuation?.isActive == true) continuation.resume(accepted)
    }

    private suspend fun awaitSystemVpnPermission(request: Intent): Boolean =
        suspendCancellableCoroutine { continuation ->
            if (vpnPermissionContinuation != null) {
                continuation.resume(false)
                return@suspendCancellableCoroutine
            }
            vpnPermissionContinuation = continuation
            continuation.invokeOnCancellation {
                if (vpnPermissionContinuation === continuation) {
                    vpnPermissionContinuation = null
                }
            }
            vpnPermissionLauncher.launch(request)
        }

    override fun onDestroy() {
        val explanation = vpnPermissionExplanationContinuation
        vpnPermissionExplanationContinuation = null
        vpnPermissionExplanationVisible.value = false
        if (explanation?.isActive == true) explanation.resume(false)

        val permission = vpnPermissionContinuation
        vpnPermissionContinuation = null
        if (permission?.isActive == true) permission.resume(false)
        super.onDestroy()
    }
}
