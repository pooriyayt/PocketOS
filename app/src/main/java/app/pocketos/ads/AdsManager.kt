package app.pocketos.ads

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import app.pocketos.BuildConfig
import com.adivery.sdk.Adivery
import com.adivery.sdk.AdiveryListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Adivery identifiers from BuildConfig (injected via git-ignored local.properties). */
object AdConfig {
    val APP_ID: String get() = BuildConfig.ADIVERY_APP_ID
    val REWARDED_PLACEMENT: String get() = BuildConfig.ADIVERY_REWARDED_PLACEMENT
    val NATIVE_PLACEMENT: String get() = BuildConfig.ADIVERY_NATIVE_PLACEMENT
    val isConfigured: Boolean get() = APP_ID.isNotBlank()
}

/**
 * Ads policy: the main place for ads is the opt-in "Support PocketOS"
 * screen (rewarded video the user chooses to watch). Elsewhere only a calm,
 * clearly labelled native card appears now and then: never in the first
 * days of use, at most on one in three app sessions, and never as a pop-up.
 */
class AdsManager(private val app: Application) {

    sealed interface RewardedState {
        data object Idle : RewardedState
        data object Loading : RewardedState
        data object Ready : RewardedState
        data object Showing : RewardedState
        data object Unavailable : RewardedState
    }

    private val prefs = app.getSharedPreferences("ads", Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())
    private var configured = false

    private val _rewarded = MutableStateFlow<RewardedState>(RewardedState.Idle)
    val rewarded: StateFlow<RewardedState> = _rewarded.asStateFlow()

    private val _supportCount = MutableStateFlow(prefs.getInt(KEY_SUPPORT_COUNT, 0))
    val supportCount: StateFlow<Int> = _supportCount.asStateFlow()

    /** Bumped every time a rewarded ad is completed, so the UI can celebrate. */
    private val _thanks = MutableStateFlow(0)
    val thanks: StateFlow<Int> = _thanks.asStateFlow()

    private val loadTimeout = Runnable {
        if (_rewarded.value == RewardedState.Loading) _rewarded.value = RewardedState.Unavailable
    }

    private val listener = object : AdiveryListener() {
        override fun onRewardedAdLoaded(placementId: String) {
            main.removeCallbacks(loadTimeout)
            if (_rewarded.value != RewardedState.Showing) _rewarded.value = RewardedState.Ready
        }

        override fun onRewardedAdShown(placementId: String) {
            _rewarded.value = RewardedState.Showing
        }

        override fun onRewardedAdClosed(placementId: String, isRewarded: Boolean) {
            if (isRewarded) {
                val count = _supportCount.value + 1
                prefs.edit().putInt(KEY_SUPPORT_COUNT, count).apply()
                _supportCount.value = count
                _thanks.value = _thanks.value + 1
            }
            _rewarded.value = RewardedState.Idle
        }
    }

    /** Called once from Application.onCreate. Failures never affect the rest of the app. */
    fun init() {
        if (!AdConfig.isConfigured) return
        configured = runCatching {
            Adivery.setLoggingEnabled(BuildConfig.DEBUG)
            Adivery.configure(app, AdConfig.APP_ID)
            if (AdConfig.REWARDED_PLACEMENT.isNotBlank()) {
                Adivery.addPlacementListener(AdConfig.REWARDED_PLACEMENT, listener)
            }
        }.isSuccess
        prefs.edit().putInt(KEY_SESSIONS, sessions + 1).apply()
        if (!prefs.contains(KEY_FIRST_OPEN)) prefs.edit().putLong(KEY_FIRST_OPEN, System.currentTimeMillis()).apply()
    }

    private val sessions: Int get() = prefs.getInt(KEY_SESSIONS, 0)

    /** Starts loading a rewarded ad (no-op if one is already loading or ready). */
    fun prepareRewarded(context: Context) {
        if (!configured) {
            _rewarded.value = RewardedState.Unavailable
            return
        }
        if (Adivery.isLoaded(AdConfig.REWARDED_PLACEMENT)) {
            _rewarded.value = RewardedState.Ready
            return
        }
        if (_rewarded.value == RewardedState.Loading) return
        _rewarded.value = RewardedState.Loading
        main.removeCallbacks(loadTimeout)
        main.postDelayed(loadTimeout, 20_000)
        runCatching { Adivery.prepareRewardedAd(context, AdConfig.REWARDED_PLACEMENT) }
            .onFailure { _rewarded.value = RewardedState.Unavailable }
    }

    fun showRewarded(context: Context) {
        if (configured && Adivery.isLoaded(AdConfig.REWARDED_PLACEMENT)) {
            runCatching { Adivery.showAd(AdConfig.REWARDED_PLACEMENT) }
        } else {
            prepareRewarded(context)
        }
    }

    /**
     * Whether this session may show the occasional in-app native card:
     * not during the first two days, and only on every third session.
     */
    val inlineAdsAllowed: Boolean
        get() {
            if (!configured) return false
            val firstOpen = prefs.getLong(KEY_FIRST_OPEN, System.currentTimeMillis())
            val days = (System.currentTimeMillis() - firstOpen) / 86_400_000L
            return days >= 2 && sessions % 3 == 0
        }

    private companion object {
        const val KEY_SUPPORT_COUNT = "support_count"
        const val KEY_SESSIONS = "sessions"
        const val KEY_FIRST_OPEN = "first_open"
    }
}
