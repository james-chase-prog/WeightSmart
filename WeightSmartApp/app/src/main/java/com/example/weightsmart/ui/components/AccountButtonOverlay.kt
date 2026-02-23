package com.example.weightsmart.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.Toast
import com.example.weightsmart.R

/**
 * Small overlay button that launches the Profile screen (an Activity, not a Fragment).
 *
 * By default this tries to open com.example.weightsmart.ui.activities.ProfileActivity.
 * If your Profile activity class lives elsewhere, set [targetActivityClass] from MainActivity.
 */
class AccountButtonOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    /** Optionally set this from MainActivity if your profile activity has a custom class. */
    var targetActivityClass: Class<out Activity>? = null

    init {
        // Inflate the simple one-button layout (overlay_profile_button.xml)
        LayoutInflater.from(context).inflate(R.layout.overlay_profile_button, this, true)

        findViewById<ImageButton>(R.id.profile_button)?.setOnClickListener {
            val host = context.findHostActivity() ?: run {
                Toast.makeText(context, "Unable to open profile.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Prefer explicit class set by the host; otherwise try default ProfileActivity.
            val clazz = targetActivityClass ?: runCatching {
                @Suppress("UNCHECKED_CAST")
                Class.forName(
                    "com.example.weightsmart.ui.activities.ProfileActivity"
                ) as Class<out Activity>
            }.getOrNull()

            if (clazz != null) {
                host.startActivity(Intent(host, clazz))
            } else {
                Toast.makeText(context, "Profile screen not found.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

private fun Context.findHostActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
