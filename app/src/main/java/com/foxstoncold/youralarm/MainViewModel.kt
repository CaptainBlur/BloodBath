package com.foxstoncold.youralarm

import android.annotation.SuppressLint
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.google.gson.Gson
import com.foxstoncold.youralarm.SplitLogger.Companion.fpc
import com.foxstoncold.youralarm.SplitLoggerUI.UILogger.initialize
import com.foxstoncold.youralarm.alarmsUI.UISupervisor
import com.foxstoncold.youralarm.alarmsUI.slU
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

interface StateSaver{
    val state: StateFlow<SavedUiState>

    fun checkRVReloadRequest(): Boolean
    fun dropRVReloadRequest()
    fun updateRVSavedState(state: IntArray)
}

class MainViewModel(private val app: Application) : AndroidViewModel(
    app
), StateSaver {

    private val sl: SplitLogger? = null
    val supervisor: UISupervisor

    private val _savedState: MutableStateFlow<SavedUiState>
    override val state: StateFlow<SavedUiState>

//    val errorHandler = object : ErrorHandlerImpl{
//        override val errorNotifierMethod: (code: Int) -> Unit
//            get() = supervisor.ma
//        override val errorCode: Int
//            get() = TODO("Not yet implemented")
//
//        override fun internalErrorHandling(ex: Exception) {
//            TODO("Not yet implemented")
//        }
//
//    }

    init {
        initialize(app)
        supervisor = UISupervisor(app)
        reassureRepo()
        checkLaunchPreferences()
        checkSomeDefaults()


        val RVReload = false
        val RVSetJson = getPrefs().getString("rv_saved", Gson().toJson(listOf(-1,0,-1).toIntArray()))
        val RVSet = Gson().fromJson(RVSetJson, IntArray::class.java)

        _savedState = MutableStateFlow(SavedUiState(RVReload, RVSet))
        state = _savedState.asStateFlow()
        dropRVReloadRequest()
        slU.i(state.value)
    }

    private fun getPrefs(): SharedPreferences = app.getSharedPreferences(PREFERENCES_SP, Context.MODE_PRIVATE)

    override fun checkRVReloadRequest(): Boolean{
        var reloadRV = false
        _savedState.update { current->
            val editor = getPrefs().edit()
            reloadRV = getPrefs().getBoolean("rv_reload", false)
            editor.putBoolean("rv_reload", false)

            editor.apply()
            current.copy(reloadRV = reloadRV)
        }

        slU.f("RVReload: $reloadRV")
        return reloadRV
    }

    /**
     * Request is useful only when resuming activity
     * In case of creating a new one (along with MVM), it's useless
     */
    override fun dropRVReloadRequest() {
            _savedState.update { current->
                val editor = getPrefs().edit()
                editor.putBoolean("rv_reload", false)

                editor.apply()
                current.copy(reloadRV = false)
            }
    }

    @SuppressLint("ApplySharedPref")
    override fun updateRVSavedState(state: IntArray){
            _savedState.update { current ->
                val editor = getPrefs().edit()
                val rvSetJson = Gson().toJson(state)

                editor.putString("rv_saved", rvSetJson)
                //Executing in async mode may cause data didn't saved before app's killed
                editor.commit()
                current.copy(RVSet = state)
            }
    }


    private fun reassureRepo() = (app as MyApp).component.repo.reassureAll(app)

    fun checkFiringError(): Boolean{
        val editor = getPrefs().edit()
        val error = getPrefs().getBoolean("firing_error", false)
        editor.putBoolean("firing_error", false)

        editor.apply()
        return error
    }

    private fun checkLaunchPreferences() {
        val editor = getPrefs().edit()
        if (!getPrefs().getBoolean("notificationChannelsSet", false)) {
            fpc("Setting up notification channels")
            val manager =
                app.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel1 = NotificationChannel(
                FIRING_CH_ID,
                app.getString(R.string.firing_notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            )


            channel1.importance = NotificationManager.IMPORTANCE_HIGH
            channel1.setSound(null, null)
            channel1.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            channel1.enableVibration(false)
            manager.createNotificationChannel(channel1)


            val channel2 = NotificationChannel(
                DETECTOR_CH_ID,
                app.getString(R.string.activeness_detection_notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            )
            channel2.importance = NotificationManager.IMPORTANCE_HIGH
            val attr = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()
            channel2.setSound(
                Uri.parse("android.resource://" + app.packageName + "/" + R.raw.ariel),
                attr
            )
            channel2.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            channel2.enableVibration(true)
            manager.createNotificationChannel(channel2)


            val channel3 = NotificationChannel(
                INFO_CH_ID,
                app.getString(R.string.firing_info_notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            channel3.importance = NotificationManager.IMPORTANCE_LOW
            channel3.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            channel3.enableVibration(true)
            manager.createNotificationChannel(channel3)
            val channel4 = NotificationChannel(
                DETECTOR_INFO_CH_ID,
                app.getString(R.string.activeness_detection_notification_info_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )


            channel4.importance = NotificationManager.IMPORTANCE_HIGH
            channel4.setSound(
                Uri.parse("android.resource://" + app.packageName + "/" + R.raw.high_intensity_alert),
                attr
            )
            channel4.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            channel4.enableVibration(true)
            manager.createNotificationChannel(channel4)
            editor.putBoolean("notificationChannelsSet", true)
        }

//        if (!prefs.getBoolean("appExitedProperly", false) || !prefs.getBoolean("firstLaunch", true)){
//            sl.i("###urgent app exit detected###");
//        }

//        editor.putBoolean("appExitedProperly", false);
//        editor.putBoolean("firstLaunch", false);
//        editor.putBoolean("notificationChannelsSet", false);
        editor.apply()
    }

    /**
     * Sometimes, there's a need to mandatory set some default values
     */
    private fun checkSomeDefaults() {
        val prefs = app.getSharedPreferences(USER_SETTINGS_SP, Context.MODE_PRIVATE)
        val editor = prefs.edit()

        if (!prefs.contains("shiftedWeekdaysOrder")) {
            editor.putBoolean(
                "shiftedWeekdaysOrder",
                Locale.getDefault().country == "US" || Locale.getDefault().country == "CA" || Locale.getDefault().country == "JP"
            )
        }
        editor.apply()
    }

    companion object {
        const val PREFERENCES_SP = "prefs"
        const val USER_SETTINGS_SP = "user_settings"
        const val FIRING_CH_ID = "firing"
        const val INFO_CH_ID = "info"
        const val DETECTOR_CH_ID = "detector"
        const val DETECTOR_INFO_CH_ID = "detector_warning"
    }

}

/**
 * This container i like a 'all you need to know' class for
 * all UI elements to setup saved state.
 * It even contains the info about neediness of applying saved state
 */
data class SavedUiState(
    var reloadRV: Boolean,
    var RVSet: IntArray)