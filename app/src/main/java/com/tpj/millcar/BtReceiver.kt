package com.tpj.millcar

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Az autó (FM transzmitter) BT-jére csatlakozva megnyitja, lecsatlakozva bezárja a MillCart. */
class BtReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        @Suppress("DEPRECATION")
        val dev: BluetoothDevice = i.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
        val car = App.I.pref("car_mac")
        if (car.isEmpty() || dev.address != car) return
        when (i.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED ->
                c.startActivity(Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            BluetoothDevice.ACTION_ACL_DISCONNECTED ->
                App.main?.finishAndRemoveTask()
        }
    }
}
