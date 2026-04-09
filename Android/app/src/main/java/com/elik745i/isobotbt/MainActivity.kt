package com.elik745i.isobotbt

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.webkit.MimeTypeMap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.webkit.WebViewAssetLoader
import com.elik745i.isobotbt.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
    // Inferred from the original remote service sequence 4,4,4,B.
    private val tPoseRawCode = 786876L
    private val assetLoader by lazy {
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
            refreshBondedDevices()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupModelPreview()
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
        binding.modelPreviewWebView.destroy()
        closeSocket()
        super.onDestroy()
    }

    private fun setupModelPreview() {
        with(binding.modelPreviewWebView) {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest,
                ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)
            }
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            settings.mediaPlaybackRequiresUserGesture = false
            overScrollMode = WebView.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN,
                    MotionEvent.ACTION_MOVE -> view.parent?.requestDisallowInterceptTouchEvent(true)
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> view.parent?.requestDisallowInterceptTouchEvent(false)
                }
                false
            }
            loadUrl("https://appassets.androidplatform.net/assets/viewer/viewer.html")
        }
    }

    private fun setupActions() {
        binding.settingsButton.setOnClickListener { showSettingsMenu() }

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

    private fun showSettingsMenu() {
        PopupMenu(this, binding.settingsButton).apply {
            menu.add(0, 1, 0, R.string.settings_t_pose)
            menu.add(0, 2, 1, R.string.manual_actions)
            menu.add(0, 3, 2, R.string.manual_service)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> {
                        showTPoseDialog()
                        true
                    }
                    2 -> {
                        openManualFromAssets("manuals/Actions.pdf", "Actions.pdf")
                        true
                    }
                    3 -> {
                        openManualFromAssets("manuals/Service_Manual.pdf", "Service_Manual.pdf")
                        true
                    }
                    else -> false
                }
            }
        }.show()
    }

    private fun showTPoseDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_t_pose)
            .setMessage(R.string.settings_t_pose_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.send) { _, _ ->
                sendRawCommand(tPoseRawCode, getString(R.string.status_t_pose_sent))
            }
            .show()
    }

    private fun openManualFromAssets(assetPath: String, outputName: String) {
        startActivity(
            Intent(this, PdfViewerActivity::class.java)
                .putExtra(PdfViewerActivity.EXTRA_ASSET_PATH, assetPath)
                .putExtra(PdfViewerActivity.EXTRA_TITLE, outputName),
        )
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
                socket = connectSocket(device)

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

    @SuppressLint("MissingPermission")
    private fun connectSocket(device: BluetoothDevice): BluetoothSocket {
        val attempts = listOfNotNull(
            runCatching { device.createRfcommSocketToServiceRecord(serialUuid) }.getOrNull(),
            runCatching { device.createInsecureRfcommSocketToServiceRecord(serialUuid) }.getOrNull(),
            runCatching {
                @Suppress("UNCHECKED_CAST")
                device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                    .invoke(device, 1) as BluetoothSocket
            }.getOrNull(),
        )

        var lastError: IOException? = null
        attempts.forEach { candidate ->
            try {
                candidate.connect()
                return candidate
            } catch (error: IOException) {
                lastError = error
                try {
                    candidate.close()
                } catch (_: IOException) {
                }
            }
        }

        throw lastError ?: IOException("Unable to open Bluetooth socket")
    }

    private fun sendRawCommand(rawCode: Long, successMessage: String) {
        val currentSocket = socket
        if (currentSocket == null || !currentSocket.isConnected) {
            toast(getString(R.string.status_not_connected))
            return
        }

        Thread {
            try {
                currentSocket.outputStream.write("RAW:$rawCode\n".toByteArray())
                currentSocket.outputStream.flush()
                runOnUiThread {
                    setStatus(successMessage)
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
