package com.example.weightsmart.notification

import android.Manifest
import android.content.Context
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * SMS sending utility. Sends a celebratory message when the user hits/passes goal weight.
 * NOTE: Requires Manifest.permission.SEND_SMS and user consent (store that in the user record).
 */
object SmsManagmentUtil {

    /** Build the celebration message with emojis. */
    fun formatCelebrationMessage(
        nicknameOrUsername: String,
        goalWeightLb: Double
    ): String {
        val goalStr = String.format(Locale.US, "%.1f", goalWeightLb)
        // matches your requested wording (spelling fixed) + emojis
        return "Congratulations $nicknameOrUsername! You have hit your goal weight of $goalStr lb 🎉🏆💪 Great work, consider setting new goals so you can continue to reach new heights!"
    }

    /**
     * Send the celebration SMS. Returns Result<Unit>.
     * - Fails if SEND_SMS permission not granted or phone invalid/blank.
     */
    fun sendCelebrationSms(
        context: Context,
        phoneNumber: String,
        nicknameOrUsername: String,
        goalWeightLb: Double
    ): Result<Unit> {
        // Quick permission check
        if (!SmsPermissionHelper.hasSendSmsPermission(context)) {
            return Result.failure(IllegalStateException("${Manifest.permission.SEND_SMS} not granted"))
        }
        val phone = phoneNumber.trim()
        if (phone.isEmpty()) {
            return Result.failure(IllegalArgumentException("Phone number is empty"))
        }

        val smsManager = getSmsManager(context)
        val message = formatCelebrationMessage(nicknameOrUsername, goalWeightLb)

        return runCatching {
            // Use multipart if needed (long message / unicode)
            val parts = smsManager.divideMessage(message)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(phone, null, message, null, null)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun getSmsManager(context: Context): SmsManager {
        // Modern way:
        return ContextCompat.getSystemService(context, SmsManager::class.java)
            ?: SmsManager.getDefault() // fallback for older devices
    }
}
