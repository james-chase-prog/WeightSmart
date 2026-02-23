package com.example.weightsmart.notification

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Tiny helper for SEND_SMS runtime permission.
 *
 * Usage (inside an Activity):
 *   private lateinit var smsPermLauncher: ActivityResultLauncher<String>
 *   override fun onCreate(...) {
 *       smsPermLauncher = SmsPermissionHelper.registerRequestPermission(this) { granted ->
 *           if (granted) { /* proceed */ } else { /* show rationale or settings */ }
 *       }
 *   }
 *   // To request:
 *   if (!SmsPermissionHelper.hasSendSmsPermission(this)) {
 *       smsPermLauncher.launch(Manifest.permission.SEND_SMS)
 *   }
 */
object SmsPermissionHelper {

    fun hasSendSmsPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

    fun shouldShowRationale(activity: Activity): Boolean =
        ActivityCompat.shouldShowRequestPermissionRationale(
            activity, Manifest.permission.SEND_SMS
        )

    fun openAppSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun registerRequestPermission(
        activity: ComponentActivity,
        onResult: (Boolean) -> Unit
    ): ActivityResultLauncher<String> =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission(), onResult)
}
