package com.alexivanov.snapsell.update

import com.alexivanov.snapsell.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Outcome of a user-initiated check, for the Settings screen. */
sealed interface ManualCheck {
    data object Idle : ManualCheck
    data object Checking : ManualCheck
    data object UpToDate : ManualCheck
    data class Available(val info: UpdateInfo) : ManualCheck
    data object Failed : ManualCheck
}

/**
 * Runs the update check at most once per process automatically (first
 * Inventory display), or again on request from Settings. "Not now" persists
 * the dismissed version code so the same release is not shown again; a newer
 * one is.
 */
class UpdateRepository(
    private val checker: UpdateChecker,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
    val currentVersionCode: Int,
    val currentVersionName: String,
) {
    private val latest = MutableStateFlow<UpdateInfo?>(null)
    private val lock = Mutex()
    private var checkedOnce = false

    /** The latest known update, whatever the user did with it. */
    val available: StateFlow<UpdateInfo?> = latest

    /** The update to show in the Inventory banner: newer than the build and not dismissed. */
    val banner: StateFlow<UpdateInfo?> = combine(latest, settings.dismissedUpdateVersionCode) { info, dismissed ->
        info?.takeIf { it.versionCode > dismissed }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    private val _manual = MutableStateFlow<ManualCheck>(ManualCheck.Idle)
    val manual: StateFlow<ManualCheck> = _manual

    /** Automatic check: runs the first time it is called in this process, no-op afterwards. */
    fun checkOnce() {
        scope.launch {
            val run = lock.withLock { if (checkedOnce) false else { checkedOnce = true; true } }
            if (run) {
                val info = checker.check(currentVersionCode)
                if (info != null) latest.value = info
            }
        }
    }

    /** User-initiated check from Settings; unlike [checkOnce] it reports "up to date" vs "could not check". */
    fun checkNow() {
        scope.launch {
            _manual.value = ManualCheck.Checking
            lock.withLock { checkedOnce = true }
            val outcome = checker.checkDetailed(currentVersionCode)
            _manual.value = when (outcome) {
                is UpdateChecker.Outcome.Newer -> { latest.value = outcome.info; ManualCheck.Available(outcome.info) }
                is UpdateChecker.Outcome.UpToDate -> ManualCheck.UpToDate
                is UpdateChecker.Outcome.Failed -> ManualCheck.Failed
            }
        }
    }

    fun dismiss(versionCode: Int) {
        scope.launch { settings.setDismissedUpdateVersionCode(versionCode) }
    }
}
