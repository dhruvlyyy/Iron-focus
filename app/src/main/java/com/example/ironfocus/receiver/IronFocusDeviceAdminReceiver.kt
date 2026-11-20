package com.example.ironfocus.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.example.R
import com.example.ironfocus.data.LockRepository

/**
 * Anti-Uninstall Device Administrator Receiver.
 * While active, Android prevents uninstalling IronFocus from launcher or App Info.
 */
class IronFocusDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        val repository = LockRepository.getInstance(context)
        return if (repository.isLockCurrentlyActive()) {
            context.getString(R.string.device_admin_disable_warning)
        } else {
            context.getString(R.string.device_admin_description)
        }
    }
}
