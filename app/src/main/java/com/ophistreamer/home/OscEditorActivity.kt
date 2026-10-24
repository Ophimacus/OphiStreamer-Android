package com.ophistreamer.home

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.limelight.R
import com.limelight.binding.input.virtual_controller.VirtualController
import com.limelight.binding.input.virtual_controller.VirtualController.ControllerMode
import com.ophistreamer.ui.OphiCardShape
import com.ophistreamer.ui.OphiMuted
import com.ophistreamer.ui.OphiSegmented
import com.ophistreamer.ui.OphiText
import com.ophistreamer.ui.OphiTextButton

/** The SteamOS bar shown while editing a layout, both here and over a live game. */
@Composable
internal fun OscEditBar(
    name: String,
    onMode: (ControllerMode) -> Unit,
    onReset: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    var mode by remember { mutableStateOf(ControllerMode.MoveButtons) }
    Column(
        Modifier
            .background(Color(0xE60E141B), OphiCardShape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Editing · $name", color = OphiText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            OphiSegmented(
                listOf(ControllerMode.MoveButtons, ControllerMode.ResizeButtons, ControllerMode.DisableEnableButtons),
                mode,
                {
                    when (it) {
                        ControllerMode.MoveButtons -> "Move"
                        ControllerMode.ResizeButtons -> "Resize"
                        else -> "Hide / show"
                    }
                },
                Modifier.width(300.dp),
            ) {
                mode = it
                onMode(it)
            }
            OphiTextButton("Reset") { onReset(); onMode(mode) }
            OphiTextButton("Cancel", onClick = onCancel)
            OphiTextButton("Save", onClick = onSave)
        }
        Text(
            when (mode) {
                ControllerMode.MoveButtons -> "Drag any button to move it."
                ControllerMode.ResizeButtons -> "Drag on a button to make it bigger or smaller."
                else -> "Tap a button to hide it. Tap a faded one to bring it back."
            },
            color = OphiMuted, fontSize = 11.sp,
        )
    }
}

private fun barParams(activity: Activity) =
    FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        topMargin = (8 * activity.resources.displayMetrics.density).toInt()
    }

/** Editing over a running stream: the buttons stay where the game shows them, the bar sits on top. */
object InGameLayoutEdit {
    @JvmStatic
    private var editing = false

    @JvmStatic
    fun start(activity: Activity, root: FrameLayout, controller: VirtualController, onDone: Runnable) {
        if (editing) return
        editing = true
        val saved = OscLayouts.backup(activity)
        controller.setEditMode(ControllerMode.MoveButtons)
        lateinit var bar: ComposeView
        fun finish() {
            root.removeView(bar)
            editing = false
            onDone.run()
        }
        bar = ComposeView(activity).apply {
            setContent {
                OscEditBar(
                    name = OscLayouts.active(activity),
                    onMode = { controller.setEditMode(it) },
                    onReset = {
                        OscLayouts.resetActive(activity)
                        controller.refreshLayout()
                    },
                    onCancel = {
                        OscLayouts.restoreBackup(activity, saved) // also undoes a Reset
                        controller.refreshLayout()
                        finish()
                    },
                    onSave = {
                        controller.saveLayout()
                        finish()
                    },
                )
            }
        }
        root.addView(bar, barParams(activity))
    }
}

/**
 * Edits the selected on-screen layout without starting a game: the same buttons the stream shows,
 * full screen in landscape, with a SteamOS bar instead of Artemis's hidden gear.
 */
class OscEditorActivity : ComponentActivity() {
    private lateinit var controller: VirtualController
    private lateinit var saved: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val root = FrameLayout(this).apply { setBackgroundResource(R.drawable.ophi_bg_gradient) }
        val stage = FrameLayout(this)
        root.addView(stage, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        // No stream: input goes nowhere, only the layout is edited.
        controller = VirtualController(null, stage, this)
        saved = OscLayouts.backup(this)

        val bar = ComposeView(this).apply {
            setContent {
                OscEditBar(
                    name = remember { OscLayouts.active(this@OscEditorActivity) },
                    onMode = { controller.setEditMode(it) },
                    onReset = {
                        OscLayouts.resetActive(this@OscEditorActivity)
                        controller.refreshLayout()
                    },
                    onCancel = {
                        OscLayouts.restoreBackup(this@OscEditorActivity, saved) // also undoes a Reset
                        finish()
                    },
                    onSave = {
                        controller.saveLayout()
                        finish()
                    },
                )
            }
        }
        root.addView(bar, barParams(this))
        setContentView(root)
        // Build the buttons once the landscape size is final, so positions match the stream.
        stage.post {
            controller.refreshLayout()
            controller.setEditMode(ControllerMode.MoveButtons)
        }
    }
}
