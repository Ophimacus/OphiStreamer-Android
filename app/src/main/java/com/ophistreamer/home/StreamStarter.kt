package com.ophistreamer.home

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.limelight.AppView
import com.limelight.computers.ComputerManagerListener
import com.limelight.computers.ComputerManagerService
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvApp
import com.limelight.nvstream.http.PairingManager
import com.limelight.preferences.PreferenceConfiguration
import com.limelight.utils.ServerHelper

/** What the Home card shows while starting a stream. No dialogs: the caller renders this inline. */
sealed class StartStatus {
    data object Connecting : StartStatus()
    data object Started : StartStatus()
    data object Offline : StartStatus()
    data object NotPaired : StartStatus()
    data class Failed(val message: String) : StartStatus()
}

/**
 * Starts a stream the way Artemis's ShortcutTrampoline does (poll the PC, check pairing, build the
 * Game intent) but reports progress through [onStatus] instead of spinner and error dialogs.
 * If a game is already running on the PC, it resumes that one.
 */
class StreamStarter(private val activity: Activity) {
    private val main = Handler(Looper.getMainLooper())
    private var binder: ComputerManagerService.ComputerManagerBinder? = null
    private var bound = false
    private var finished = false
    @Volatile private var generation = 0
    private var coopEnabled = false

    fun start(pcUuid: String, game: RecentGame?, onStatus: (StartStatus) -> Unit) {
        cancel()
        finished = false
        val request = generation
        coopEnabled = false
        onStatus(StartStatus.Connecting)

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val b = service as ComputerManagerService.ComputerManagerBinder
                Thread {
                    b.waitForReady()
                    if (request != generation) return@Thread
                    val computer = b.getComputer(pcUuid)
                    if (computer == null) {
                        finish(StartStatus.Failed("This PC isn't saved any more. Add it again under All PCs."), onStatus, request)
                        return@Thread
                    }
                    val host = (computer.manualAddress ?: computer.localAddress)?.address
                    val shared = host?.let { PhoneLinkClient.status(it)?.coopEnabled } == true
                    main.post {
                        if (request != generation || finished) return@post
                        binder = b
                        coopEnabled = shared
                        Coop.rememberHostMode(activity, pcUuid, shared)
                        b.invalidateStateForComputer(computer.uuid)
                        b.startPolling(object : ComputerManagerListener {
                            override fun notifyComputerUpdated(details: ComputerDetails) {
                                if (!details.uuid.equals(pcUuid, ignoreCase = true)) return
                                if (details.state == ComputerDetails.State.UNKNOWN) return
                                main.post { if (request == generation) handle(details, b, game, onStatus) }
                            }
                        })
                    }
                }.start()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                if (request == generation) binder = null
            }
        }
        this.connection = connection
        bound = activity.bindService(Intent(activity, ComputerManagerService::class.java), connection, Context.BIND_AUTO_CREATE)
        main.postDelayed({ finish(StartStatus.Failed("The PC didn't answer. Check the hotspot and Apollo."), onStatus, request) }, TIMEOUT_MS)
    }

    private var connection: ServiceConnection? = null

    private fun handle(
        details: ComputerDetails,
        b: ComputerManagerService.ComputerManagerBinder,
        game: RecentGame?,
        onStatus: (StartStatus) -> Unit,
    ) {
        if (finished) return
        when {
            details.state == ComputerDetails.State.OFFLINE -> finish(StartStatus.Offline, onStatus)
            details.pairState != PairingManager.PairState.PAIRED -> finish(StartStatus.NotPaired, onStatus)
            Coop.isGuest(activity) && !coopEnabled -> finish(StartStatus.Failed("Enable two-phone co-op in the Windows app first."), onStatus)
            Coop.isGuest(activity) && details.runningGameId == 0 && details.runningGameUUID.isNullOrBlank() -> finish(StartStatus.Failed("Waiting for the host phone to start a game. Then tap Join game."), onStatus)
            else -> {
                val app = when {
                    details.runningGameId != 0 || !details.runningGameUUID.isNullOrBlank() -> NvApp(null, details.runningGameUUID, details.runningGameId, false)
                    game != null -> NvApp(game.appName, game.appUuid, game.appId, false)
                    else -> null
                }
                val intent = if (app != null) {
                    val vd = PreferenceConfiguration.readPreferences(activity).useVirtualDisplay && !Coop.isGuest(activity)
                    ServerHelper.createStartIntent(activity, app, details, b, vd)
                        .putExtra(Coop.EXTRA_GUEST, Coop.isGuest(activity))
                        .putExtra(Coop.EXTRA_SHARED, coopEnabled)
                } else {
                    Intent(activity, AppView::class.java)
                        .putExtra(AppView.UUID_EXTRA, details.uuid)
                        .putExtra(AppView.NAME_EXTRA, details.name)
                }
                // Claim this request before launching: polling may deliver another update immediately.
                finished = true
                onStatus(StartStatus.Started)
                cancel()
                activity.startActivity(intent)
            }
        }
    }

    private fun finish(status: StartStatus, onStatus: (StartStatus) -> Unit, request: Int = generation) {
        main.post {
            if (request != generation || finished) return@post
            finished = true
            onStatus(status)
            cancel()
        }
    }

    /** Stops polling and unbinds; safe to call any time (e.g. from onStop). */
    fun cancel() {
        generation++
        finished = true
        main.removeCallbacksAndMessages(null)
        binder?.stopPolling()
        binder = null
        if (bound) {
            connection?.let { runCatching { activity.unbindService(it) } }
            bound = false
        }
    }

    companion object {
        private const val TIMEOUT_MS = 15_000L
    }
}
