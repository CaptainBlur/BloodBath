package com.vova9110.bloodbath.alarmsUI

import android.app.Application
import android.graphics.Rect
import android.view.View
import com.vova9110.bloodbath.MainActivity
import com.vova9110.bloodbath.MyApp
import com.vova9110.bloodbath.SplitLoggerUI
import com.vova9110.bloodbath.StateSaver
import com.vova9110.bloodbath.alarmScreenBackground.AlarmRepo
import java.lang.Exception

typealias slU = SplitLoggerUI.UILogger

class UISupervisor(val app: Application) {
    val repo: AlarmRepo = (app as MyApp).component.repo
    lateinit var ratios: RatiosResolver
        private set
    lateinit var drawables: MainDrawables
        private set
    lateinit var stateSaver: StateSaver
        private set

    private lateinit var mainActivity: MainActivity
    private lateinit var fAHandler: FreeAlarmsHandler
    val callbacks: HashMap<String, Any?> = HashMap()

    fun onMainActivityReady(ma: MainActivity, view: View){
        slU.i("MA is ready. Start distributing")
        mainActivity = ma
        callbacks["mtofaCallback"] = mainActivity
        stateSaver = mainActivity.provideStateSaver()

        val rect = Rect()
        view.getWindowVisibleDisplayFrame(rect)

        ratios = RatiosResolver(app, rect)
        drawables = MainDrawables(app, ratios)
    }
    fun onRecyclerViewReady(rv: AdjustableView){
        slU.i("RV is ready. Start distributing")
        fAHandler = FreeAlarmsHandler(this, rv, mainActivity::transmitError)
        callbacks["fatomCallback"] = fAHandler
    }

    //TODO GET RID OF THIS!!!
    fun clearOrFill(fill: Boolean){
        if (fill) fAHandler.fill()
        else fAHandler.clear()
    }
}

interface ErrorHandlerImpl{
    val errorNotifierMethod: (code: Int)->Unit
    val errorCode: Int
    fun transmitError(ex: Exception) {
        errorNotifierMethod(errorCode)
        internalErrorHandling(ex)
    }
    fun internalErrorHandling(ex: Exception)
    companion object{
        const val RV_ERROR_CODE = 289
        const val FIRING_ERROR_CODE = 715
    }
}


