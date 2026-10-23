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

    fun start(pcUuid: String, game: RecentGame?, onStatus: (StartStatus) -> Unit) {
        cancel()
        finished = false
        onStatus(StartStatus.Connecting)

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val b = service as ComputerManagerService.ComputerManagerBinder
                Thread {
                    b.waitForReady()
                    binder = b
                    val computer = b.getComputer(pcUuid)
                    if (computer == null) {
                        finish(StartStatus.Failed("This PC isn't saved any more. Add it again under All PCs."), onStatus)
                        return@Thread
                    }
                    b.invalidateStateForComputer(computer.uuid)
                    b.startPolling(object : ComputerManagerListener {
                        override fun notifyComputerUpdated(details: ComputerDetails) {
                            if (!details.uuid.equals(pcUuid, ignoreCase = true)) return
                            if (details.state == ComputerDetails.State.UNKNOWN) return
                            main.post { handle(details, b, game, onStatus) }
                        }
                    })
                }.start()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                binder = null
            }
        }
        this.connection = connection
        bound = activity.bindService(Intent(activity, ComputerManagerService::class.java), connection, Context.BIND_AUTO_CREATE)
        main.postDelayed({ finish(StartStatus.Failed("The PC didn't answer. Check the hotspot and Apollo."), onStatus) }, TIMEOUT_MS)
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
            else -> {
                val app = when {
                    details.runningGameId != 0 -> NvApp(null, details.runningGameUUID, details.runningGameId, false)
                    game != null -> NvApp(game.appName, game.appUuid, game.appId, false)
                    else -> null
                }
                val intent = if (app != null) {
                    val vd = PreferenceConfiguration.readPreferences(activity).useVirtualDisplay
                    ServerHelper.createStartIntent(activity, app, details, b, vd)
                } else {
                    Intent(activity, AppView::class.java)
                        .putExtra(AppView.UUID_EXTRA, details.uuid)
                        .putExtra(AppView.NAME_EXTRA, details.name)
                }
                finish(StartStatus.Started, onStatus)
                activity.startActivity(intent)
            }
        }
    }

    private fun finish(status: StartStatus, onStatus: (StartStatus) -> Unit) {
        main.post {
            if (finished) return@post
            finished = true
            onStatus(status)
            cancel()
        }
    }

    /** Stops polling and unbinds; safe to call any time (e.g. from onStop). */
    fun cancel() {
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
