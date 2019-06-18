/*
 * Copyright 2019 Ricoh Company, Ltd. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package skunkworks.bleremoterelease

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.os.ParcelUuid
import android.util.Log
import com.theta360.pluginlibrary.activity.PluginActivity
import com.theta360.pluginlibrary.values.LedTarget
import org.theta4j.osc.OptionSet
import org.theta4j.webapi.BluetoothPower
import org.theta4j.webapi.CaptureMode
import org.theta4j.webapi.Options.BLUETOOTH_POWER
import org.theta4j.webapi.Options.CAPTURE_MODE
import org.theta4j.webapi.Theta
import java.util.*
import java.util.concurrent.Executors

class MainActivity : PluginActivity() {
    companion object {
        private val TAG = "BLE_REMOTE_RELEASE"
        private val PDLD_LINK = UUID.fromString("b3b36901-50d3-4044-808d-50835b13a6cd")
    }

    private val executor = Executors.newSingleThreadExecutor()

    private val theta = Theta.createForPlugin()

    private var mBluetoothLeScanner: BluetoothLeScanner? = null

    private var mOptionsBackup: OptionSet? = null

    override fun onResume() {
        super.onResume()

        // Init and backup settings
        executor.submit {
            mOptionsBackup = theta.getOptions(CAPTURE_MODE, BLUETOOTH_POWER)
            theta.setOptions(
                OptionSet.Builder()
                    .put(CAPTURE_MODE, CaptureMode.IMAGE)
                    .put(BLUETOOTH_POWER, BluetoothPower.ON)
                    .build()
            )
        }

        // Init BLE
        val bluetoothManager = getSystemService(BluetoothManager::class.java)
        mBluetoothLeScanner = bluetoothManager.adapter.bluetoothLeScanner

        startScan()
    }

    override fun onPause() {
        super.onPause()

        // Terminate BLE
        stopScan()
        mBluetoothLeScanner = null

        // Restore settings
        executor.submit { theta.setOptions(mOptionsBackup!!) }
    }

    private fun startScan() {
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(PDLD_LINK))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        mBluetoothLeScanner?.startScan(Arrays.asList(filter), settings, scanCallback)

        notificationLedShow(LedTarget.LED4)
    }

    private fun stopScan() {
        notificationLedHide(LedTarget.LED4)

        mBluetoothLeScanner?.stopScan(scanCallback)
    }

    private val scanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            Log.d(TAG, "Result $result")

            stopScan()
            executor.submit { theta.takePicture() }
            result.device.connectGatt(applicationContext, false, gattCallback)
        }
    }
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothGatt.STATE_CONNECTED -> {
                    Log.d(TAG, "Bluetooth GATT State: CONNECTED")

                    gatt.disconnect()
                }
                BluetoothGatt.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Bluetooth GATT State: DISCONNECTED")

                    gatt.close()
                    Thread.sleep(8000)
                    startScan()
                }
                else -> Log.d(TAG, "Bluetooth GATT State: $newState")
            }
        }
    }
}
