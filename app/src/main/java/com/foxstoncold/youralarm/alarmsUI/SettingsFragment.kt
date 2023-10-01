package com.foxstoncold.youralarm.alarmsUI

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.ColorStateList
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.ExpandableListView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.foxstoncold.youralarm.M_to_SF_Callback
import com.foxstoncold.youralarm.MainViewModel
import com.foxstoncold.youralarm.MyApp
import com.foxstoncold.youralarm.R
import com.foxstoncold.youralarm.StateSaver
import com.foxstoncold.youralarm.alarmScreenBackground.ActivenessDetectionService
import com.foxstoncold.youralarm.database.Alarm
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class SettingsFragment: Fragment(R.layout.fragment_settings) {
    lateinit var maCallback: M_to_SF_Callback
    lateinit var stateSaver: StateSaver

    private fun checkMACallback(): Boolean = this::maCallback.isInitialized

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupSettingsList(view)
    }

    private fun setupSettingsList(view: View){
        if (!checkMACallback()) return
        slU.i("fragment created")

        val settingsFactory = ExpandableListFactory(view.findViewById(R.id.settings_list) as ExpandableListView, view.context).apply {
            stateSaver = maCallback.getStateSaver()

            initialGroupStates = stateSaver.state.value.SFSet
            onGroupChanged = {
                stateSaver.updateSFSavedState(it)
            }
        }
        val interlayer = InterfaceUtils.SPInterlayer(view.context, MainViewModel.USER_SETTINGS_SP)

        val mainGroup = settingsFactory.GroupItemContainer().apply{
            titleText = "Main\nsettings"
            setTitleDrawable(R.drawable.ic_settings)
            addToGroupStorage()
        }

        with(settingsFactory.ChildItemContainer(mainGroup)){
            titleText = "Volume lock"
            showHint()
            hintText = "After this switch is activated, the minimal possible firing volume will be set and displayed with the volume slider.<br>Adjust your desired volume and it will be saved <u>in 7 seconds</u>"

            switchStartStateGetter = { interlayer.sp.getBoolean("volumeLock", false) }
            switchOnCheckedListener = { _, _, isChecked ->
                interlayer.setElement("volumeLock", isChecked)

                if (isChecked){
                    CoroutineScope(Dispatchers.Default).launch {
                        val streamType = AudioManager.STREAM_ALARM
                        val audioManager = view.context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

                        audioManager.setStreamVolume(streamType, 0, AudioManager.FLAG_SHOW_UI)
                        delay(7000)

                        val maxVol = audioManager.getStreamMaxVolume(streamType).toFloat()
                        val vol = audioManager.getStreamVolume(streamType).toFloat()

                        interlayer.setElement("volume", vol.toInt())
                        withContext(Dispatchers.Main){
                            Toast.makeText(view.context, "Volume locked: ${(vol/maxVol * 100).toInt()}%", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            addToStorage()
        }

        with(settingsFactory.ChildItemContainer(mainGroup)){
            type = 1.1f
            titleText = "Volume\n   ramp up"
            subSwitchVisible = true
            unitText = "sec"

            switchStartStateGetter = { interlayer.sp.getBoolean("rampUpVolume", false) }
            switchOnCheckedListener = { _, _, isChecked ->
                interlayer.setElement("rampUpVolume", isChecked)
            }
            editTextStartTextGetter = { interlayer.sp.getInt("rampUpVolumeTime", 0).toString() }
            editTextOnChangedListener = {
                if (it!=null && it.toString()!="") interlayer.setElement("rampUpVolumeTime", it.toString().toInt())
            }
            addToStorage()
        }

        with(settingsFactory.ChildItemContainer(mainGroup)){
            type = 1f
            titleText = "First day of week"

            dropDownStartTextGetter = {
                if (interlayer.sp.getBoolean("shiftedWeekdaysOrder", false))
                    "Sunday"
                else "Monday"
            }
            dropDownItems = listOf("Sunday", "Monday")
            dropDownChangedListener = {
                interlayer.setElement("shiftedWeekdaysOrder", it=="Sunday")
            }
            addToStorage()
        }

        with(settingsFactory.ChildItemContainer(mainGroup)){
            type = 1.1f
            titleText = "Snooze for"
            unitText = "min"

            editTextStartTextGetter = { interlayer.sp.getInt("globalSnoozed", 8).toString() }
            editTextOnChangedListener = {
                if (it!=null && it.toString()!="") interlayer.setElement("globalSnoozed", it.toString().toInt())
            }
            addToStorage()
        }

        with(settingsFactory.ChildItemContainer(mainGroup)){
            type = 1.1f
            titleText = "Silence after"
            unitText = "min"

            editTextStartTextGetter = { interlayer.sp.getInt("globalLost", 10).toString() }
            editTextOnChangedListener = {
                if (it!=null && it.toString()!="") interlayer.setElement("globalLost", it.toString().toInt())
            }
            addToStorage()
        }


        val preliminaryGroup = settingsFactory.GroupItemContainer().apply{
            titleText = "Preliminary\nalarm"
            setTitleDrawable(R.drawable.ic_preliminary)
            addToGroupStorage()
        }

        with(settingsFactory.ChildItemContainer(preliminaryGroup)){
            type = 0.1f
            titleText = "Enabled by\n   default"

            switchStartStateGetter = { interlayer.sp.getBoolean("preliminaryByDefault", false) }
            switchOnCheckedListener = { _, _, isChecked ->
                interlayer.setElement("preliminaryByDefault", isChecked)
            }
            addToStorage()
        }

        with(settingsFactory.ChildItemContainer(preliminaryGroup)){
            type = 2f
            titleText = "Preliminary time"

            seekbarStartValueGetter = { interlayer.sp.getInt("preliminaryTime", 30).toFloat() }
            seekbarOnReleasedListener = {
                interlayer.setElement("preliminaryTime", it.toInt())

                slU.i("Global preliminary time changed")
                CoroutineScope(Dispatchers.Default).launch {
                    (view.context.applicationContext as MyApp).component.repo.updateActives(view.context)
                }
            }
            seekbarUnitText = "min"
            seekbarMinValue = 5f
            seekbarMaxValue = 60f
            seekbarStep = 5f
            addToStorage()
        }


        val activenessGroup = settingsFactory.GroupItemContainer().apply{
            titleText = "Activeness\ndetection"
            setTitleDrawable(R.drawable.ic_activeness_bold)
            addToGroupStorage()
        }

        with(settingsFactory.ChildItemContainer(mainGroup)){
            type = 0.1f
            titleText = "Activeness\n   detection"
            showHint()
            hintText = "Activeness detection requires the <i>Activity Recognition permission</i> on devices running Android 10 and above.<br>" +
                    "Also, on some rare occasions, you <u>may not have</u> a specific <i>Step counter sensor</i> on your device, and in that case there's nothing I can do, yet. My apologies"

            val resultLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
                refreshView()
            }

            switchStartStateGetter = {
                (InterfaceUtils.checkActivityPermission(view.context)
                        && InterfaceUtils.checkStepSensors(view.context)
                        ).also { activenessGroup.enabled = it }
            }
            switchOnCheckedListener = { switch, _, isChecked ->
                if (InterfaceUtils.checkStepSensors(view.context)) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                        if (isChecked && !InterfaceUtils.checkActivityPermission(view.context)) resultLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                    activenessGroup.enabled = InterfaceUtils.checkActivityPermission(view.context)

                    //Switch should remain checked when permission is granted
                    if (!isChecked && InterfaceUtils.checkActivityPermission(view.context)) switch.isChecked = true
                    if (!isChecked) activenessGroup.collapse()
                }

                else switch.isChecked = false
            }
            addToStorage()
        }

        var serviceBound = false
        var ads: ActivenessDetectionService? = null
        val connection = object: ServiceConnection{
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                serviceBound = true
                ads = (service as ActivenessDetectionService.AEDBinder).getService()
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                serviceBound = false
            }
        }
        val intent = Intent(context, ActivenessDetectionService::class.java)
        requireActivity().bindService(intent, connection, 0)

        with(settingsFactory.ChildItemContainer(activenessGroup)){
            type = 0.2f
            titleText = "Info\n   and test"
            showHint()
            hintText = "1. <i>Activeness Detection</i> prevents you from falling asleep after you dismissed an alarm.<br>" +
                    "2. It counts your steps when you stand up and take a walk around.<br>" +
                    "3. Adjust your target amount of steps by launching this test version of the Detector.<br>." +
                    "4. <i>Shower detection</i> can be used instead of <i>Steps</i>.<br>" +
                    "You don't have to switch anything, just go to the bathroom, lay down the phone aside a running shower. The sound will be detected in 5 seconds<br>" +
                    "(don't forget to enable the feature in the setting below)"

            var info = Alarm(-1,-1).getInfo(view.context)

            testPlayButtonCallback = { _,_ ->
                val i = Intent(context, ActivenessDetectionService::class.java)
                info = Alarm(-1,-1).getInfo(view.context)

                i.putExtra("info", info)
                i.putExtra("testMode", true)
                view.context.startForegroundService(i)
                requireActivity().bindService(i, connection, 0)
            }
            testStopButtonCallback = {
                ads?.controller?.stopAltogether()
            }
            testStopButtonVisibilityChecker = {
                var valid = false
                if (serviceBound) valid = ads!!.checkIdValidity(info.id)
                valid
            }
            addToStorage()
        }

        with(settingsFactory.ChildItemContainer(activenessGroup)){
            type = 0.1f
            titleText = "Enabled by\n   default"

            switchStartStateGetter = { interlayer.sp.getBoolean("activenessByDefault", false) }
            switchOnCheckedListener = { _, _, isChecked ->
                interlayer.setElement("activenessByDefault", isChecked)
            }
            addToStorage()
        }

        with(settingsFactory.ChildItemContainer(activenessGroup)){
            type = 0f
            titleText = "Enable shower\n   detection"
            showHint()
            hintText = "This function requires microphone access. Warning sign means it's <u>not granted</u><br>Keep in mind that app do not store any of the recorded data, but just immediately use it in a transformed form to detect presence of a <i>falling water sound</i>"
            switchWarningAvailable = true

            var states = arrayOf(
                intArrayOf(-android.R.attr.state_checked),
                intArrayOf(android.R.attr.state_checked)
            )
            var colors = arrayOf(
                view.context.getColor(R.color.mild_greyscaleLight),
                view.context.getColor(R.color.mild_presenceRegular)
            ).toIntArray()
            val enabledTrackTintList = ColorStateList(states, colors)

            states = arrayOf(
                intArrayOf(-android.R.attr.state_checked),
                intArrayOf(android.R.attr.state_checked)
            )
            colors = arrayOf(
                view.context.getColor(R.color.white),
                view.context.getColor(R.color.mild_pitchShadow)
            ).toIntArray()
            val enabledThumbTintList = ColorStateList(states, colors)

            states = arrayOf(
                intArrayOf(-android.R.attr.state_checked),
                intArrayOf(android.R.attr.state_checked)
            )
            colors = arrayOf(
                view.context.getColor(R.color.mild_greyscaleLight),
                view.context.getColor(R.color.mild_greyscalePresence)
            ).toIntArray()
            val disabledTrackTintList = ColorStateList(states, colors)

            states = arrayOf(
                intArrayOf(-android.R.attr.state_checked),
                intArrayOf(android.R.attr.state_checked)
            )
            colors = arrayOf(
                view.context.getColor(R.color.white),
                view.context.getColor(R.color.mild_greyscaleLight)
            ).toIntArray()
            val disabledThumbTintList = ColorStateList(states, colors)

            val noiseGet: ()->Boolean = { interlayer.sp.getBoolean("noiseDetection", false) }
            val noiseSet: (Boolean)->Unit = { interlayer.setElement("noiseDetection", it) }
            var permission = InterfaceUtils.checkNoisePermission(view.context)
            val resultLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted->
                if (isGranted) {
                    permission = InterfaceUtils.checkNoisePermission(view.context)
                    refreshView()
                }
            }
            switchStartStateGetter = {
                val switch = it as SwitchMaterial

                if (permission){
                    switch.trackTintList = enabledTrackTintList
                    switch.thumbTintList = enabledThumbTintList
                }
                else{
                    switch.trackTintList = disabledTrackTintList
                    switch.thumbTintList = disabledThumbTintList
                }

                noiseGet()
            }
            switchWarningStartStateGetter = {
                noiseGet() && !permission
            }

            switchOnCheckedListener = { switch, warning, isChecked ->
                noiseSet(isChecked)
                permission = InterfaceUtils.checkNoisePermission(view.context)

                if (isChecked && !permission){
                    resultLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    warning.visibility = View.VISIBLE
                }
                else if (isChecked) warning.visibility = View.INVISIBLE

                with (switch as SwitchMaterial){
                    if (permission){
                        trackTintList = enabledTrackTintList
                        thumbTintList = enabledThumbTintList
                    }
                    else {
                        trackTintList = disabledTrackTintList
                        thumbTintList = disabledThumbTintList
                    }
                }
            }
            addToStorage()
        }

        with(settingsFactory.ChildItemContainer(activenessGroup)){
            type = 2f
            titleText = "Steps\n   required"
            showHint()
            hintText = "Default value is 30<br>Two steps per second is a normal pace for our species"

            seekbarStartValueGetter = { interlayer.sp.getInt("steps", 30).toFloat() }
            seekbarOnReleasedListener = {
                interlayer.setElement("steps", it.toInt())
            }
            seekbarUnitText = "steps"
            seekbarMinValue = 6f
            seekbarMaxValue = 122f
            seekbarStep = 4f
            addToStorage()
        }

        with(settingsFactory.ChildItemContainer(activenessGroup)){
            type = 1.1f
            titleText = "Snoozed\n   time"
            editTextMaxLength = 3
            unitText = "sec"
            showHint()
            hintText = "After this time, if you were not active, the alarming sound will be on again.<br>" +
                    "1. By the half of that time you will hear a Warning notification popped up.<br>" +
                    "2. For the first time, you will be given an option to remain silence for another minute (maybe, you'll have to expand 'Snoozed' notification).<br>" +
                    "3. The effective amount of <i>Snoozed time</i> will be halved every time you push it to the state of alarming.<br>" +
                    "4. Value of <u>90 sec</u> is recommended"

            editTextStartTextGetter = { interlayer.sp.getInt("timeOut", 90).toString() }
            editTextOnChangedListener = {
                if (it!=null && it.toString()!="") interlayer.setElement("timeOut", it.toString().toInt())
            }
            addToStorage()
        }

        settingsFactory.AsyncBuild {
            if (checkMACallback()) maCallback.onSettingsReady()
        }
        
//        val testableGroup = settingsFactory.GroupItemContainer().apply{
//            titleText = "Testable\nelements"
//            setTitleDrawable(R.drawable.ic_chevron)
//            addToGroupStorage()
//        }
//        with(settingsFactory.ChildItemContainer(testableGroup)){
//            titleText = "Type zero child"
//            showHint()
//            addToStorage()
//        }
//        with(settingsFactory.ChildItemContainer(testableGroup)){
//            type = 0.1f
//            titleText = "Type zero.\n   one child"
//            showHint()
//            addToStorage()
//        }
//        with(settingsFactory.ChildItemContainer(testableGroup)){
//            type = 1f
//            titleText = "Type one child"
//            showHint()
//            addToStorage()
//        }
//        with(settingsFactory.ChildItemContainer(testableGroup)){
//            type = 1.1f
//            titleText = "Type one.\n   one child"
//            addToStorage()
//        }
//        with(settingsFactory.ChildItemContainer(testableGroup)){
//            type = 1.2f
//            titleText = "Type one.two child"
//            showHint()
//            addToStorage()
//        }
//        with(settingsFactory.ChildItemContainer(testableGroup)){
//            type = 2f
//            titleText = "Type two child"
//            showHint()
//
//            subSwitchVisible = true
//            seekbarStep = 10f
//            addToStorage()
//        }
    }
}
