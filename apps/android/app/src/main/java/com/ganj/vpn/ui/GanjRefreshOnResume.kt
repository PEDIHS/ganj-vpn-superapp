package com.ganj.vpn.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.awaitCancellation

/**
 * Refresh on return from background without a poller, timer or background
 * worker. Initial load belongs to each screen and is not duplicated here.
 */
@Composable
internal fun GanjRefreshOnResume(refresh: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    val currentRefresh = rememberUpdatedState(refresh)
    LaunchedEffect(owner) {
        var initialResume = true
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (initialResume) {
                initialResume = false
            } else {
                currentRefresh.value()
            }
            awaitCancellation()
        }
    }
}
