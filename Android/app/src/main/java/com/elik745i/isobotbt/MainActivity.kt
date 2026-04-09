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
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.LinearLayout
import android.webkit.MimeTypeMap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebChromeClient
import android.widget.ScrollView
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.webkit.WebViewAssetLoader
import com.elik745i.isobotbt.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.IOException
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private enum class ExpandedSection {
        NONE,
        QUICK,
        REMOTE,
        CATALOG,
    }

    private enum class ControlChannel(
        val persistedValue: String,
        @StringRes val labelRes: Int,
        val remoteControlDebug: String,
        val zeroDebug: String,
    ) {
        A("A", R.string.channel_a, "RCA", "ZEROA"),
        B("B", R.string.channel_b, "RCB", "ZEROB");

        companion object {
            fun fromPersisted(value: String?): ControlChannel {
                return entries.firstOrNull { it.persistedValue == value } ?: A
            }
        }
    }

    private enum class AppearanceMode(
        val persistedValue: String,
        @StringRes val labelRes: Int,
        val nightMode: Int,
    ) {
        SYSTEM("system", R.string.appearance_system, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
        LIGHT("light", R.string.appearance_light, AppCompatDelegate.MODE_NIGHT_NO),
        DARK("dark", R.string.appearance_dark, AppCompatDelegate.MODE_NIGHT_YES);

        companion object {
            fun fromPersisted(value: String?): AppearanceMode {
                return entries.firstOrNull { it.persistedValue == value } ?: SYSTEM
            }
        }
    }

    private enum class ConnectionState(
        @StringRes val labelRes: Int,
        val colorRes: Int,
    ) {
        IDLE(R.string.connection_state_idle, R.color.indicator_idle),
        CONNECTING(R.string.connection_state_connecting, R.color.indicator_connecting),
        CONNECTED(R.string.connection_state_connected, R.color.indicator_connected),
        ERROR(R.string.connection_state_error, R.color.indicator_error),
    }

    private lateinit var binding: ActivityMainBinding

    private val bluetoothAdapter: BluetoothAdapter?
        get() = getSystemService(BluetoothManager::class.java)?.adapter
    private val serialUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var bondedDevices: List<BluetoothDevice> = emptyList()
    private var socket: BluetoothSocket? = null
    private var selectedCategory: CommandCategory = CommandCategory.ALL
    private val commandAdapter = CommandAdapter(::sendCommand)
    private val preferredDeviceName = "ISOBOT"
    private val preferences by lazy { getSharedPreferences("isobot_prefs", MODE_PRIVATE) }
    private val reconnectHandler = Handler(Looper.getMainLooper())
    @Volatile private var autoReconnectEnabled = true
    @Volatile private var isConnecting = false
    private var selectedBondedDeviceIndex = 0
    private var selectedControlChannel = ControlChannel.fromPersisted(null)
    private var selectedAppearanceMode = AppearanceMode.SYSTEM
    private var expandedSection = ExpandedSection.NONE
    private var connectionState = ConnectionState.IDLE
    private val assetLoader by lazy {
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
    }
    private val reconnectRunnable = Runnable {
        if (autoReconnectEnabled && !isConnecting && (socket == null || socket?.isConnected != true)) {
            ensurePermissionsAndRefresh()
        }
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
            refreshBondedDevices()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        val startupPreferences = getSharedPreferences("isobot_prefs", MODE_PRIVATE)
        selectedAppearanceMode = AppearanceMode.fromPersisted(
            startupPreferences.getString("appearance_mode", AppearanceMode.SYSTEM.persistedValue),
        )
        AppCompatDelegate.setDefaultNightMode(selectedAppearanceMode.nightMode)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        selectedControlChannel = ControlChannel.fromPersisted(
            preferences.getString("control_channel", ControlChannel.A.persistedValue),
        )

        setupModelPreview()
        setupCommandList()
        setupQuickButtons()
        setupRemoteControls()
        setupActions()
        bindCategoryActions()
        setupAccordion()
        updateConnectionStatusUi()

        if (bluetoothAdapter == null) {
            setStatus(getString(R.string.status_bluetooth_unavailable), ConnectionState.ERROR)
            disableQuickButtons()
            return
        }

        ensurePermissionsAndRefresh()
    }

    override fun onDestroy() {
        autoReconnectEnabled = false
        reconnectHandler.removeCallbacksAndMessages(null)
        binding.modelPreviewWebView.destroy()
        closeSocket()
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        autoReconnectEnabled = true
        scheduleReconnect(250)
    }

    override fun onStop() {
        reconnectHandler.removeCallbacksAndMessages(null)
        super.onStop()
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
            menu.add(0, 1, 0, R.string.settings_bluetooth)
            menu.add(0, 2, 1, R.string.settings_channel)
            menu.add(0, 3, 2, R.string.settings_appearance)
            menu.add(0, 4, 3, R.string.settings_t_pose)
            menu.add(0, 5, 4, R.string.settings_t_pose_debug)
            menu.add(0, 6, 5, R.string.manual_actions)
            menu.add(0, 7, 6, R.string.manual_service)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> {
                        showBluetoothSettingsDialog()
                        true
                    }
                    2 -> {
                        showChannelDialog()
                        true
                    }
                    3 -> {
                        showAppearanceDialog()
                        true
                    }
                    4 -> {
                        showTPoseDialog()
                        true
                    }
                    5 -> {
                        showTPoseDebugDialog()
                        true
                    }
                    6 -> {
                        openManualFromAssets("manuals/Actions.pdf", "Actions.pdf")
                        true
                    }
                    7 -> {
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
                sendDebugSequence(
                    listOf(selectedControlChannel.remoteControlDebug, selectedControlChannel.zeroDebug),
                    getString(
                        R.string.status_t_pose_sent,
                        getString(selectedControlChannel.labelRes),
                    ),
                )
            }
            .show()
    }

    private fun showBluetoothSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_bluetooth_settings, null)
        val deviceSpinner = dialogView.findViewById<android.widget.Spinner>(R.id.bluetoothSettingsSpinner)
        val refreshButton = dialogView.findViewById<android.widget.Button>(R.id.bluetoothSettingsRefreshButton)
        val connectButton = dialogView.findViewById<android.widget.Button>(R.id.bluetoothSettingsConnectButton)
        val disconnectButton = dialogView.findViewById<android.widget.Button>(R.id.bluetoothSettingsDisconnectButton)
        val statusText = dialogView.findViewById<android.widget.TextView>(R.id.bluetoothSettingsStatusText)

        fun updateDialogState() {
            val labels = if (bondedDevices.isEmpty()) {
                listOf(getString(R.string.no_paired_devices))
            } else {
                bondedDevices.map { device -> "${device.name ?: getString(R.string.unknown_device)} (${device.address})" }
            }

            deviceSpinner.adapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                labels,
            )
            if (selectedBondedDeviceIndex in bondedDevices.indices) {
                deviceSpinner.setSelection(selectedBondedDeviceIndex)
            }
            statusText.text = binding.statusText.text
        }

        updateDialogState()
        deviceSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?,
                view: android.view.View?,
                position: Int,
                id: Long,
            ) {
                selectedBondedDeviceIndex = position
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }

        refreshButton.setOnClickListener {
            ensurePermissionsAndRefresh()
            updateDialogState()
        }

        connectButton.setOnClickListener {
            autoReconnectEnabled = true
            selectedBondedDeviceIndex = deviceSpinner.selectedItemPosition
            connectToSelectedDevice(selectedBondedDeviceIndex)
            statusText.text = binding.statusText.text
        }

        disconnectButton.setOnClickListener {
            autoReconnectEnabled = false
            reconnectHandler.removeCallbacksAndMessages(null)
            closeSocket()
            setStatus(getString(R.string.status_disconnected), ConnectionState.IDLE)
            statusText.text = binding.statusText.text
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_bluetooth)
            .setView(dialogView)
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showChannelDialog() {
        val channels = ControlChannel.entries.toTypedArray()
        val labels = channels.map { getString(it.labelRes) }.toTypedArray()
        val checkedItem = channels.indexOf(selectedControlChannel)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_channel)
            .setSingleChoiceItems(labels, checkedItem) { dialog, which ->
                selectedControlChannel = channels[which]
                preferences.edit().putString("control_channel", selectedControlChannel.persistedValue).apply()
                binding.remoteModeHintText.text = getString(
                    R.string.remote_mode_hint_with_channel,
                    getString(selectedControlChannel.labelRes),
                )
                setStatus(getString(R.string.channel_selected, getString(selectedControlChannel.labelRes)), connectionState)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showAppearanceDialog() {
        val modes = AppearanceMode.entries.toTypedArray()
        val labels = modes.map { getString(it.labelRes) }.toTypedArray()
        val checkedItem = modes.indexOf(selectedAppearanceMode)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_appearance)
            .setSingleChoiceItems(labels, checkedItem) { dialog, which ->
                selectedAppearanceMode = modes[which]
                preferences.edit().putString("appearance_mode", selectedAppearanceMode.persistedValue).apply()
                AppCompatDelegate.setDefaultNightMode(selectedAppearanceMode.nightMode)
                setStatus(
                    getString(R.string.appearance_selected, getString(selectedAppearanceMode.labelRes)),
                    connectionState,
                )
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showTPoseDebugDialog() {
        val steps = listOf(
            "HOME" to getString(R.string.tpose_debug_home),
            "RCA" to getString(R.string.tpose_debug_rc_a),
            "ZEROA" to getString(R.string.tpose_debug_zero_a),
            "LEGACY" to getString(R.string.tpose_debug_legacy_zero),
            "RCB" to getString(R.string.tpose_debug_rc_b),
            "ZEROB" to getString(R.string.tpose_debug_zero_b),
            "FULL" to getString(R.string.tpose_debug_full),
        )

        val contentPadding = dpToPx(20)
        val buttonSpacing = dpToPx(10)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(contentPadding, 0, contentPadding, 0)
        }

        steps.forEachIndexed { index, (command, label) ->
            val button = com.google.android.material.button.MaterialButton(this).apply {
                text = label
                isAllCaps = false
                setOnClickListener {
                    sendDebugCommand(
                        command,
                        getString(R.string.status_tpose_debug_sent, label),
                    )
                }
            }

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                if (index > 0) {
                    topMargin = buttonSpacing
                }
            }

            container.addView(button, params)
        }

        val scrollView = ScrollView(this).apply {
            addView(
                container,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_t_pose_debug)
            .setMessage(R.string.settings_t_pose_debug_message)
            .setView(scrollView)
            .setNegativeButton(android.R.string.cancel, null)
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

    private fun setupRemoteControls() {
        binding.remoteModeHintText.text = getString(
            R.string.remote_mode_hint_with_channel,
            getString(selectedControlChannel.labelRes),
        )

        val remoteButtons = listOf(
            binding.remoteUpButton to RobotCommands.all.first { it.index == 0 },
            binding.remoteDownButton to RobotCommands.all.first { it.index == 1 },
            binding.remoteRightButton to RobotCommands.all.first { it.index == 2 },
            binding.remoteLeftButton to RobotCommands.all.first { it.index == 3 },
            binding.remoteHeadLeftButton to RobotCommands.all.first { it.index == 12 },
            binding.remoteHeadRightButton to RobotCommands.all.first { it.index == 13 },
            binding.remoteLeanForwardButton to RobotCommands.all.first { it.index == 14 },
            binding.remoteLeanBackButton to RobotCommands.all.first { it.index == 15 },
            binding.remoteLeftArmButton to RobotCommands.all.first { it.index == 16 },
            binding.remoteRightArmButton to RobotCommands.all.first { it.index == 21 },
            binding.remoteLeftGuardButton to RobotCommands.all.first { it.index == 45 },
            binding.remoteRightGuardButton to RobotCommands.all.first { it.index == 46 },
            binding.remoteLeftLegButton to RobotCommands.all.first { it.index == 33 },
            binding.remoteRightLegButton to RobotCommands.all.first { it.index == 34 },
        )

        remoteButtons.forEach { (button, command) ->
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

    private fun setupAccordion() {
        binding.quickSectionToggle.setOnClickListener { toggleSection(ExpandedSection.QUICK) }
        binding.remoteSectionToggle.setOnClickListener { toggleSection(ExpandedSection.REMOTE) }
        binding.catalogSectionToggle.setOnClickListener { toggleSection(ExpandedSection.CATALOG) }
        applyAccordionState()
    }

    private fun toggleSection(section: ExpandedSection) {
        val openingSection = expandedSection != section
        expandedSection = if (openingSection) section else ExpandedSection.NONE
        applyAccordionState()

        if (openingSection && section == ExpandedSection.REMOTE) {
            activateRemoteControlMode()
        }
    }

    private fun applyAccordionState() {
        binding.quickSectionContent.visibility =
            if (expandedSection == ExpandedSection.QUICK) android.view.View.VISIBLE else android.view.View.GONE
        binding.remoteSectionContent.visibility =
            if (expandedSection == ExpandedSection.REMOTE) android.view.View.VISIBLE else android.view.View.GONE
        binding.catalogSectionContent.visibility =
            if (expandedSection == ExpandedSection.CATALOG) android.view.View.VISIBLE else android.view.View.GONE

        binding.quickSectionToggle.text = sectionTitle(R.string.section_quick_controls, expandedSection == ExpandedSection.QUICK)
        binding.remoteSectionToggle.text = sectionTitle(R.string.section_remote_mode, expandedSection == ExpandedSection.REMOTE)
        binding.catalogSectionToggle.text = sectionTitle(R.string.section_command_catalog, expandedSection == ExpandedSection.CATALOG)
    }

    private fun sectionTitle(@StringRes titleRes: Int, expanded: Boolean): String {
        val indicator = if (expanded) "▲" else "▼"
        return "${getString(titleRes)}  $indicator"
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
            setStatus(getString(R.string.status_enable_bluetooth), ConnectionState.ERROR)
            scheduleReconnect()
            return
        }

        bondedDevices = adapter.bondedDevices.orEmpty().sortedBy { it.name ?: it.address }
        selectPreferredDevice()

        setStatus(
            if (bondedDevices.isEmpty()) {
                getString(R.string.status_no_devices)
            } else {
                getString(R.string.status_devices_loaded, bondedDevices.size)
            },
            if (bondedDevices.isEmpty()) ConnectionState.ERROR else ConnectionState.IDLE,
        )

        if (autoReconnectEnabled && socket?.isConnected != true) {
            connectToSelectedDevice(preferAutoSelected = true)
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToSelectedDevice(
        deviceIndex: Int? = null,
        preferAutoSelected: Boolean = false,
    ) {
        if (isConnecting || socket?.isConnected == true) {
            return
        }

        if (bondedDevices.isEmpty()) {
            toast(getString(R.string.no_paired_devices))
            scheduleReconnect()
            return
        }

        if (preferAutoSelected) {
            selectPreferredDevice()
        }

        val selectedIndex = (deviceIndex ?: selectedBondedDeviceIndex)
            .takeIf { it in bondedDevices.indices } ?: 0
        selectedBondedDeviceIndex = selectedIndex
        val device = bondedDevices[selectedIndex]
        isConnecting = true
        setBusy(true)
        setStatus(
            if (preferAutoSelected) {
                getString(R.string.status_auto_connecting, device.name ?: device.address)
            } else {
                getString(R.string.status_connecting, device.name ?: device.address)
            },
            ConnectionState.CONNECTING,
        )

        Thread {
            try {
                closeSocket()
                bluetoothAdapter?.cancelDiscovery()
                socket = connectSocket(device)

                runOnUiThread {
                    isConnecting = false
                    setBusy(false)
                    setStatus(getString(R.string.status_connected, device.name ?: device.address), ConnectionState.CONNECTED)
                    scheduleReconnect(5000)
                }
            } catch (error: IOException) {
                closeSocket()
                runOnUiThread {
                    isConnecting = false
                    setBusy(false)
                    setStatus(getString(R.string.status_connection_failed, error.localizedMessage ?: "I/O error"), ConnectionState.ERROR)
                    scheduleReconnect()
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
                    setStatus(getString(R.string.status_sent, command.index, command.label), ConnectionState.CONNECTED)
                    scheduleReconnect(5000)
                }
            } catch (error: IOException) {
                closeSocket()
                runOnUiThread {
                    setStatus(getString(R.string.status_send_failed, error.localizedMessage ?: "I/O error"), ConnectionState.ERROR)
                    scheduleReconnect()
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
                    setStatus(successMessage, ConnectionState.CONNECTED)
                    scheduleReconnect(5000)
                }
            } catch (error: IOException) {
                closeSocket()
                runOnUiThread {
                    setStatus(getString(R.string.status_send_failed, error.localizedMessage ?: "I/O error"), ConnectionState.ERROR)
                    scheduleReconnect()
                }
            }
        }.start()
    }

    private fun sendDebugCommand(debugCommand: String, successMessage: String) {
        sendDebugSequence(listOf(debugCommand), successMessage)
    }

    private fun sendDebugSequence(debugCommands: List<String>, successMessage: String) {
        val currentSocket = socket
        if (currentSocket == null || !currentSocket.isConnected) {
            toast(getString(R.string.status_not_connected))
            return
        }

        Thread {
            try {
                debugCommands.forEachIndexed { index, debugCommand ->
                    currentSocket.outputStream.write("TPDBG:$debugCommand\n".toByteArray())
                    currentSocket.outputStream.flush()
                    if (index < debugCommands.lastIndex) {
                        Thread.sleep(450)
                    }
                }
                runOnUiThread {
                    setStatus(successMessage, ConnectionState.CONNECTED)
                    scheduleReconnect(5000)
                }
            } catch (error: IOException) {
                closeSocket()
                runOnUiThread {
                    setStatus(getString(R.string.status_send_failed, error.localizedMessage ?: "I/O error"), ConnectionState.ERROR)
                    scheduleReconnect()
                }
            }
        }.start()
    }

    private fun activateRemoteControlMode() {
        sendDebugSequence(
            listOf(selectedControlChannel.remoteControlDebug),
            getString(R.string.remote_mode_active, getString(selectedControlChannel.labelRes)),
        )
    }

    private fun closeSocket() {
        try {
            socket?.close()
        } catch (_: IOException) {
        } finally {
            socket = null
        }
    }

    private fun selectPreferredDevice() {
        val preferredIndex = bondedDevices.indexOfFirst { device ->
            (device.name ?: "").contains(preferredDeviceName, ignoreCase = true)
        }
        if (preferredIndex >= 0) {
            selectedBondedDeviceIndex = preferredIndex
        }
    }

    private fun scheduleReconnect(delayMillis: Long = 2000L) {
        reconnectHandler.removeCallbacks(reconnectRunnable)
        if (autoReconnectEnabled) {
            reconnectHandler.postDelayed(reconnectRunnable, delayMillis)
        }
    }

    private fun setBusy(isBusy: Boolean) {
        binding.searchField.isEnabled = !isBusy
        binding.quickSectionToggle.isEnabled = !isBusy
        binding.remoteSectionToggle.isEnabled = !isBusy
        binding.catalogSectionToggle.isEnabled = !isBusy
    }

    private fun setStatus(message: String, state: ConnectionState = connectionState) {
        connectionState = state
        binding.statusText.text = message
        updateConnectionStatusUi()
    }

    private fun updateConnectionStatusUi() {
        binding.connectionStatusText.text = getString(connectionState.labelRes)
        val indicator = binding.connectionIndicator.background.mutate()
        val color = ContextCompat.getColor(this, connectionState.colorRes)
        if (indicator is android.graphics.drawable.GradientDrawable) {
            indicator.setShape(android.graphics.drawable.GradientDrawable.OVAL)
            indicator.setColor(color)
        } else {
            binding.connectionIndicator.setBackgroundColor(color)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
