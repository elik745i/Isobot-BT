package com.elik745i.isobotbt

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.elik745i.isobotbt.databinding.ActivityMainBinding
import java.io.IOException
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val bluetoothAdapter: BluetoothAdapter?
        get() = getSystemService(BluetoothManager::class.java)?.adapter
    private val serialUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var bondedDevices: List<BluetoothDevice> = emptyList()
    private var socket: BluetoothSocket? = null
    private var selectedCategory: CommandCategory = CommandCategory.ALL
    private val commandAdapter = CommandAdapter(::sendCommand)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
            refreshBondedDevices()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupCommandList()
        setupQuickButtons()
        setupActions()
        bindCategoryActions()

        if (bluetoothAdapter == null) {
            setStatus(getString(R.string.status_bluetooth_unavailable))
            binding.connectButton.isEnabled = false
            binding.refreshDevicesButton.isEnabled = false
            disableQuickButtons()
            return
        }

        ensurePermissionsAndRefresh()
    }

    override fun onDestroy() {
        closeSocket()
        super.onDestroy()
    }

    private fun setupActions() {
        binding.refreshDevicesButton.setOnClickListener {
            ensurePermissionsAndRefresh()
        }

        binding.connectButton.setOnClickListener {
            connectToSelectedDevice()
        }

        binding.disconnectButton.setOnClickListener {
            closeSocket()
            setStatus(getString(R.string.status_disconnected))
        }

        binding.searchField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                refreshCommandList()
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun setupCommandList() {
        binding.commandRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.commandRecyclerView.adapter = commandAdapter
        refreshCommandList()
    }

    private fun setupQuickButtons() {
        val quickButtons = listOf(
            binding.quickForward to RobotCommands.quick.first { it.index == 0 },
            binding.quickBackward to RobotCommands.quick.first { it.index == 1 },
            binding.quickSideRight to RobotCommands.quick.first { it.index == 2 },
            binding.quickSideLeft to RobotCommands.quick.first { it.index == 3 },
            binding.quickForwardLeft to RobotCommands.quick.first { it.index == 4 },
            binding.quickForwardRight to RobotCommands.quick.first { it.index == 5 },
            binding.quickBackwardLeft to RobotCommands.quick.first { it.index == 6 },
            binding.quickBackwardRight to RobotCommands.quick.first { it.index == 7 },
            binding.quickForwardClockwise to RobotCommands.quick.first { it.index == 8 },
            binding.quickForwardCounter to RobotCommands.quick.first { it.index == 9 },
            binding.quickBackwardClockwise to RobotCommands.quick.first { it.index == 10 },
            binding.quickBackwardCounter to RobotCommands.quick.first { it.index == 11 },
        )

        quickButtons.forEach { (button, command) ->
            button.text = command.label
            button.setOnClickListener { sendCommand(command) }
        }
    }

    private fun disableQuickButtons() {
        listOf(
            binding.quickForward,
            binding.quickBackward,
            binding.quickSideRight,
            binding.quickSideLeft,
            binding.quickForwardLeft,
            binding.quickForwardRight,
            binding.quickBackwardLeft,
            binding.quickBackwardRight,
            binding.quickForwardClockwise,
            binding.quickForwardCounter,
            binding.quickBackwardClockwise,
            binding.quickBackwardCounter,
        ).forEach { it.isEnabled = false }
    }

    private fun bindCategoryActions() {
        val categoryButtons = mapOf(
            binding.filterAll to CommandCategory.ALL,
            binding.filterMovement to CommandCategory.MOVEMENT,
            binding.filterCombat to CommandCategory.COMBAT,
            binding.filterSocial to CommandCategory.SOCIAL,
            binding.filterShowcase to CommandCategory.SHOWCASE,
        )

        categoryButtons.forEach { (button, category) ->
            button.setOnClickListener {
                selectedCategory = category
                updateCategorySelection()
                refreshCommandList()
            }
        }

        updateCategorySelection()
    }

    private fun updateCategorySelection() {
        binding.filterAll.isChecked = selectedCategory == CommandCategory.ALL
        binding.filterMovement.isChecked = selectedCategory == CommandCategory.MOVEMENT
        binding.filterCombat.isChecked = selectedCategory == CommandCategory.COMBAT
        binding.filterSocial.isChecked = selectedCategory == CommandCategory.SOCIAL
        binding.filterShowcase.isChecked = selectedCategory == CommandCategory.SHOWCASE
    }

    private fun refreshCommandList() {
        val commands = RobotCommands.filter(binding.searchField.text?.toString().orEmpty(), selectedCategory)
        commandAdapter.submitList(commands)
        binding.commandCount.text = getString(R.string.command_count, commands.size)
        binding.emptyCommandsText.visibility = if (commands.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun ensurePermissionsAndRefresh() {
        val missingPermissions = requiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions.toTypedArray())
            return
        }

        refreshBondedDevices()
    }

    private fun requiredPermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    @SuppressLint("MissingPermission")
    private fun refreshBondedDevices() {
        val adapter = bluetoothAdapter ?: return

        if (!adapter.isEnabled) {
            setStatus(getString(R.string.status_enable_bluetooth))
            return
        }

        bondedDevices = adapter.bondedDevices.orEmpty().sortedBy { it.name ?: it.address }
        val labels = if (bondedDevices.isEmpty()) {
            listOf(getString(R.string.no_paired_devices))
        } else {
            bondedDevices.map { device -> "${device.name ?: getString(R.string.unknown_device)} (${device.address})" }
        }

        binding.deviceSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            labels,
        )

        setStatus(
            if (bondedDevices.isEmpty()) {
                getString(R.string.status_no_devices)
            } else {
                getString(R.string.status_devices_loaded, bondedDevices.size)
            },
        )
    }

    @SuppressLint("MissingPermission")
    private fun connectToSelectedDevice() {
        if (bondedDevices.isEmpty()) {
            toast(getString(R.string.no_paired_devices))
            return
        }

        val device = bondedDevices[binding.deviceSpinner.selectedItemPosition]
        setBusy(true)
        setStatus(getString(R.string.status_connecting, device.name ?: device.address))

        Thread {
            try {
                closeSocket()
                bluetoothAdapter?.cancelDiscovery()
                socket = device.createRfcommSocketToServiceRecord(serialUuid).apply { connect() }

                runOnUiThread {
                    setBusy(false)
                    setStatus(getString(R.string.status_connected, device.name ?: device.address))
                }
            } catch (error: IOException) {
                closeSocket()
                runOnUiThread {
                    setBusy(false)
                    setStatus(getString(R.string.status_connection_failed, error.localizedMessage ?: "I/O error"))
                }
            }
        }.start()
    }

    private fun sendCommand(command: RobotCommand) {
        val currentSocket = socket
        if (currentSocket == null || !currentSocket.isConnected) {
            toast(getString(R.string.status_not_connected))
            return
        }

        Thread {
            try {
                currentSocket.outputStream.write("${command.index}\n".toByteArray())
                currentSocket.outputStream.flush()
                runOnUiThread {
                    setStatus(getString(R.string.status_sent, command.index, command.label))
                }
            } catch (error: IOException) {
                closeSocket()
                runOnUiThread {
                    setStatus(getString(R.string.status_send_failed, error.localizedMessage ?: "I/O error"))
                }
            }
        }.start()
    }

    private fun closeSocket() {
        try {
            socket?.close()
        } catch (_: IOException) {
        } finally {
            socket = null
        }
    }

    private fun setBusy(isBusy: Boolean) {
        binding.connectButton.isEnabled = !isBusy
        binding.disconnectButton.isEnabled = !isBusy
        binding.refreshDevicesButton.isEnabled = !isBusy
        binding.searchField.isEnabled = !isBusy
    }

    private fun setStatus(message: String) {
        binding.statusText.text = message
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}