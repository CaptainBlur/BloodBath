package com.foxstoncold.youralarm

import android.animation.Animator
import android.animation.Animator.AnimatorListener
import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.text.toSpanned
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.LifecycleOwner
import com.foxstoncold.youralarm.SplitLogger.Companion.ex
import com.foxstoncold.youralarm.alarmScreenBackground.FiringControlService
import com.foxstoncold.youralarm.alarmsUI.AdjustableImageView
import com.foxstoncold.youralarm.alarmsUI.AdjustableRecyclerView
import com.foxstoncold.youralarm.alarmsUI.ErrorHandlerImpl
import com.foxstoncold.youralarm.alarmsUI.FA_to_M_Callback
import com.foxstoncold.youralarm.alarmsUI.InterfaceUtils
import com.foxstoncold.youralarm.alarmsUI.InterfaceUtils.Companion.toPx
import com.foxstoncold.youralarm.alarmsUI.SettingsFragment
import com.foxstoncold.youralarm.alarmsUI.UISupervisor
import com.foxstoncold.youralarm.alarmsUI.slU
import com.foxstoncold.youralarm.databinding.ActivityMainBinding
import io.reactivex.rxjava3.observers.DisposableObserver
import io.reactivex.rxjava3.subjects.AsyncSubject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date
import java.util.Timer
import java.util.TimerTask
import kotlin.math.abs

typealias Contractor = InterfaceUtils.Contractor


class MainActivity : AppCompatActivity(), M_to_FA_Callback, M_to_SF_Callback {
    private val sl = SplitLogger()
    private lateinit var binding: ActivityMainBinding
    private lateinit var _vm: MainViewModel
    private lateinit var supervisor: UISupervisor
    private lateinit var faCallback: FA_to_M_Callback

    //Because Android will most likely use onResume method multiple times during initialization
    private var onResumePermitted = false
    private val displayClock = DisplayClockController()

    private lateinit var ringtonePickerResultLauncher: ActivityResultLauncher<Int>
    private lateinit var ringtonePickerResultContract: ActivityResultContract<Int, Uri?>
    private lateinit var ringtonePickerRequestData: RPRequestData
    private lateinit var ringtonePickerResultCallback: ActivityResultCallback<Uri?>

    data class RPRequestData(val requestingAlarmID: String, val settledUri: Uri?, val callback: RPResultCallback)
    interface RPResultCallback{
        /**
         * If there weren't a valid result, and empty Uri will be returned. Null is for 'None' option chosen
         */
        fun onResultReceived(path: Uri?)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        slU.f("MA created")

//        Date firstDate = new Date();
//        Date secDate = firstDate;
//        secDate.setTime(secDate.getTime() + 10000);
//        firstDate = null;
//        SplitLogger.i(secDate);
//        SplitLogger.i(firstDate);

        val vm: MainViewModel by viewModels()
        _vm = vm
        supervisor = _vm.supervisor

        val rootView = this@MainActivity.window.decorView.rootView.findViewById<ConstraintLayout>(R.id.main_constraint_layout)
        LayoutInflater.from(this@MainActivity).inflate(R.layout.recycler_view, rootView, true)
        slU.f("RV inflated")

        val fragment = SettingsFragment().apply {
            maCallback = this@MainActivity
        }
        fun addOrHideFragment() = kotlin.run {
            supportFragmentManager.commit {
                if (fragment.isAdded){
                    remove(fragment)
                    binding.mainSettingsButtonBack.apply{
                        visibility = View.INVISIBLE
                    }
                    binding.mainSettingsButtonIcon.apply{
                        visibility = View.INVISIBLE
                    }
                }
                else add(R.id.main_fragment_container, fragment)
            }
        }
        fun replaceFragment() = run {
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                replace(R.id.main_fragment_container, fragment)
            }
        }

        //creating oneShot listener for MainActivity view's layout
        val targetWaiter: AsyncSubject<View> = AsyncSubject.create()
        targetWaiter.subscribe(object : DisposableObserver<View>() {
            override fun onNext(t: View) = run {
                supervisor.onMainActivityReady(this@MainActivity, t)

                val rect= Rect()
                t.getWindowVisibleDisplayFrame(rect)
                prepareRV(rect)
                replaceFragment()

                displayClock.setupDigits(false)
            }
            override fun onError(e: Throwable) = Unit
            override fun onComplete() = this.dispose()
        })
        fun onTargetReady(target: View) = with(targetWaiter){
            onNext(target); onComplete()
        }
        val view = this.window.decorView
        view.viewTreeObserver.addOnGlobalLayoutListener(object: OnGlobalLayoutListener{
            override fun onGlobalLayout() {
                view.viewTreeObserver.removeOnGlobalLayoutListener(this)
                onTargetReady(view)
            }
        })


        prepareReceiver()
        prepareViews()
        prepareRingtonePickerLauncher()
    }


    private fun prepareRV(rect: Rect){
        val recyclerView = findViewById<AdjustableRecyclerView>(R.id.recyclerview)
        val topCap = findViewById<AdjustableImageView>(R.id.rv_top_cap)

        recyclerView.makeAdjustments(rect)
        topCap.makeAdjustments(rect)

        val lp = topCap.layoutParams as ConstraintLayout.LayoutParams
        lp.leftToLeft = R.id.recyclerview
        lp.topToTop = R.id.recyclerview
        topCap.layoutParams = lp

        supervisor.onRecyclerViewReady(recyclerView)

        val fatomCallback: FA_to_M_Callback by supervisor.callbacks
        faCallback = fatomCallback
    }

    private fun prepareReceiver(){
        val receiver = object: BroadcastReceiver(){
            override fun onReceive(context: Context?, intent: Intent?) {
                slU.fr("broadcast received")
                intent!!

                if(intent.action.equals(RELOAD_RV_REQUEST)){
                    _vm.dropRVReloadRequest()
                    if (this@MainActivity::faCallback.isInitialized){
//                        faCallback.createBrandNewRecycler(false).also { slU.f("reloading pref") }
                        restartActivity()
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(RELOAD_RV_REQUEST)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) registerReceiver(receiver, filter, RECEIVER_EXPORTED)
        else registerReceiver(receiver, filter)
    }

    private fun prepareViews(){
        val drawer = binding.mainDrawer.apply {
            this.addDrawerListener(object: DrawerLayout.DrawerListener{
                var oneShot = false
                var wasOpened = false

                override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                    if (slideOffset < 0.085f && !oneShot && wasOpened) {
                        drawerView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_RELEASE)
                        oneShot = true
                    }
                }
                override fun onDrawerOpened(drawerView: View){
                    wasOpened = true
                }

                override fun onDrawerClosed(drawerView: View) {
                    oneShot = false
                    wasOpened = false
                }

                override fun onDrawerStateChanged(newState: Int) = Unit
            })
        }

        binding.buttonFill.apply {
            setOnClickListener { faCallback.clearOrFill(true) }
            setOnLongClickListener { faCallback.clearOrFill(false); true }
        }
        binding.mainSettingsButtonBack.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS)
            drawer.open()
        }

        binding.mainDisplayClockSubTextInfoStringOne.movementMethod = LinkMovementMethod.getInstance()
        binding.mainDisplayClockSubTextInfoStringTwo.movementMethod = LinkMovementMethod.getInstance()

//        binding.testView.setOnClickListener {
//            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_RELEASE)
//        }
//        binding.testView2.setOnClickListener {
//            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY_RELEASE)
//        }
//        binding.testView3.setOnClickListener {
//            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
//        }
//        binding.testView4.setOnClickListener {
//            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
//        }
//        binding.testView5.setOnClickListener {
//            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
//        }


        binding.fab.setOnClickListener{
            val intent = Intent(this@MainActivity, FiringControlService::class.java).apply {
                action = FiringControlService.ACTION_TEST
            }
            CoroutineScope(Dispatchers.Default).launch {
//                delay(2000)
                applicationContext.startForegroundService(intent)
            }
        }

        binding.testView.setOnClickListener{
            CoroutineScope(Dispatchers.Main).launch {
                displayClock.animateSubstitution(
                    binding.mainDisplayClockDigitOne,
                    binding.mainDisplayClockDigitOneSubstitute,
                    "0"
                )
            }
        }
        binding.testView.setOnLongClickListener{

//            activityResult.launch(intent)

            true
        }
        val date = Date()
        date.time += (1000 * 60 * 5) - (1000 * 60 * 30)

        binding.testView2.setOnClickListener{
//            val actives = supervisor.repo.actives
//            if (actives!=null){
//                displayClock.setupInfoStrings(actives[0].triggerTime!!)
//                binding.mainDisplayClockTextInfo.alpha = 1f
                displayClock.firstActiveDate = date
                date.time += 1000 * 60 * 30
//            }
        }
        binding.testView2.setOnLongClickListener {
            displayClock.firstActiveDate = null
            return@setOnLongClickListener true
        }

        val id = "1"

        binding.testView3.setOnClickListener {
            displayClock.showSubInfoStrings(id, true, Date())
        }
        binding.testView3.setOnLongClickListener {
//            displayClock.showSubInfoStrings(id, false)
            displayClock.hideSubInfoStrings()
            return@setOnLongClickListener true
        }
    }

    private fun prepareRingtonePickerLauncher(){
        ringtonePickerResultContract = object: ActivityResultContract<Int, Uri?>(){

            override fun createIntent(context: Context, input: Int): Intent {
                slU.i("requesting alarm sound picker for: ${ringtonePickerRequestData.requestingAlarmID}")

                return Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Alarm sound")
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, ringtonePickerRequestData.settledUri)
                }
            }

            override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
                if (resultCode != RESULT_OK || intent == null) {
                    return Uri.parse("")
                }
                return intent.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }

        }

        ringtonePickerResultCallback = ActivityResultCallback {
            ringtonePickerRequestData.callback.onResultReceived(it)
        }

        ringtonePickerResultLauncher = registerForActivityResult(ringtonePickerResultContract, ringtonePickerResultCallback)
    }

    override fun launchRingtonePicker(data: RPRequestData) {
        ringtonePickerRequestData = data
        ringtonePickerResultLauncher.launch(0)
    }

    private inner class DisplayClockController{
        private var digitsTimer: Timer? = null

        private val stringStartToEndMargin = -(14f.toPx())
        private val stringTopToBottomMargin = -(7f.toPx())

        private val refreshPeriod = 2000L
        private val separatorAnimationDuration = 200
        private val animatorLag = 80

        /**
         * Preparing background timer for periodic changes. Must be cancelled on stop it order to be set up again
         */
        fun prepareDisplayClock(){

            val cal = Calendar.getInstance()
            val separator = binding.mainDisplayClockDigitSeparator.apply { alpha = 0f }
            var separatorVisible = false

            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val minuteStart = cal.time

            cal.add(Calendar.MINUTE, 1)
            val minuteEnd = cal.time

            cal.timeInMillis = System.currentTimeMillis()
            var startDate = Date()
            val timeStampsArray = LongArray(31)
            for ((i, timeStamp) in (minuteStart.time .. minuteEnd.time step refreshPeriod).withIndex()) {
                timeStampsArray[i] = timeStamp
                if (i>0)
                    if (cal.timeInMillis in timeStampsArray[i-1] .. timeStamp)
                        startDate = if (timeStamp - cal.timeInMillis >= (separatorAnimationDuration + animatorLag)) Date(timeStamp)
                        else Date(timeStamp + refreshPeriod)
            }
            cal.time = startDate
            cal.add(Calendar.MILLISECOND, -(separatorAnimationDuration + animatorLag))
            startDate = cal.time

            fun animateSeparatorVisibility(){
                CoroutineScope(Dispatchers.Main).launch {
                    separatorVisible = if (separatorVisible){
                        InterfaceUtils.startAlphaAnimation(separator, 0f, separatorAnimationDuration.toLong())
                        false
                    } else{
                        InterfaceUtils.startAlphaAnimation(separator, 1f, separatorAnimationDuration.toLong())
                        true
                    }
                }
            }


            showInfoStrings(firstActiveDate)
            if (digitsTimer!=null) return
            //timer executes in background Thread
            digitsTimer = Timer("digitsTimer").apply {
                scheduleAtFixedRate(object : TimerTask() {
                    override fun run() {
                        try {
                            animateSeparatorVisibility()
                            if (checkDigitChange())
                                showInfoStrings(firstActiveDate)
                        } catch (e: Exception){
                            slU.s("periodical animation error", e)
                        }
                    }
                }, startDate, refreshPeriod)
            }


            val listener = View.OnClickListener {
                setupDigits(true)
            }
            binding.mainDisplayClockDigitOneSubstitute.setOnClickListener(listener)
            binding.mainDisplayClockDigitTwoSubstitute.setOnClickListener(listener)
            binding.mainDisplayClockDigitThreeSubstitute.setOnClickListener(listener)
            binding.mainDisplayClockDigitFourSubstitute.setOnClickListener(listener)
        }

        fun stopDigitsTimer(){
            digitsTimer?.cancel()
            digitsTimer = null
        }

        /**
         * Setting up initial static digits for hours and minutes. Animating single change on resume
         */
        fun setupDigits(onResume: Boolean){
            CoroutineScope(Dispatchers.Default).launch {
                val cal = Calendar.getInstance()
                val hour = String.format("%02d", cal.get(Calendar.HOUR_OF_DAY))
                val minute = String.format("%02d", cal.get(Calendar.MINUTE))

                if (!onResume) {
                    binding.mainDisplayClockDigitOne.text = (hour).substring(0, 1)
                    binding.mainDisplayClockDigitTwo.text = (hour).substring(1)
                    binding.mainDisplayClockDigitThree.text = (minute).substring(0, 1)
                    binding.mainDisplayClockDigitFour.text = (minute).substring(1)
                } else {
                    val digitOne = (hour).substring(0, 1)
                    val digitTwo = (hour).substring(1)
                    val digitThree = (minute).substring(0, 1)
                    val digitFour = (minute).substring(1)

                    if (binding.mainDisplayClockDigitOne.text != digitOne) animateSubstitution(
                        binding.mainDisplayClockDigitOne,
                        binding.mainDisplayClockDigitOneSubstitute,
                        digitOne
                    )
                    if (binding.mainDisplayClockDigitTwo.text != digitTwo) animateSubstitution(
                        binding.mainDisplayClockDigitTwo,
                        binding.mainDisplayClockDigitTwoSubstitute,
                        digitTwo
                    )
                    if (binding.mainDisplayClockDigitThree.text != digitThree) animateSubstitution(
                        binding.mainDisplayClockDigitThree,
                        binding.mainDisplayClockDigitThreeSubstitute,
                        digitThree
                    )
                    if (binding.mainDisplayClockDigitFour.text != digitFour) animateSubstitution(
                        binding.mainDisplayClockDigitFour,
                        binding.mainDisplayClockDigitFourSubstitute,
                        digitFour
                    )
                }
            }
        }

        private val digitsCal: Calendar = Calendar.getInstance()

        /**
         * Appointing method for animateSubstitution, only for periodic use. We've already checked minute change in this sequence.
         * Better use this result to not to check time info strings every period
         */
        private fun checkDigitChange(): Boolean{
            digitsCal.timeInMillis = System.currentTimeMillis()
            digitsCal.set(Calendar.SECOND, 0)
            digitsCal.set(Calendar.MILLISECOND, 0)
            digitsCal.add(Calendar.MINUTE,1)
            digitsCal.add(Calendar.MILLISECOND, -(separatorAnimationDuration + animatorLag))

            return if (digitsCal.time.before(Date())){
                digitsCal.add(Calendar.MILLISECOND, (separatorAnimationDuration + animatorLag))
                val hour = String.format("%02d", digitsCal.get(Calendar.HOUR_OF_DAY))
                val minute = String.format("%02d", digitsCal.get(Calendar.MINUTE))

                val digitOne = (hour).substring(0,1)
                val digitTwo = (hour).substring(1)
                val digitThree = (minute).substring(0,1)
                val digitFour = (minute).substring(1)

                if (binding.mainDisplayClockDigitOne.text != digitOne) animateSubstitution(
                    binding.mainDisplayClockDigitOne,
                    binding.mainDisplayClockDigitOneSubstitute,
                    digitOne
                )
                if (binding.mainDisplayClockDigitTwo.text != digitTwo) animateSubstitution(
                    binding.mainDisplayClockDigitTwo,
                    binding.mainDisplayClockDigitTwoSubstitute,
                    digitTwo
                )
                if (binding.mainDisplayClockDigitThree.text != digitThree) animateSubstitution(
                    binding.mainDisplayClockDigitThree,
                    binding.mainDisplayClockDigitThreeSubstitute,
                    digitThree
                )
                if (binding.mainDisplayClockDigitFour.text != digitFour) animateSubstitution(
                    binding.mainDisplayClockDigitFour,
                    binding.mainDisplayClockDigitFourSubstitute,
                    digitFour
                )

                true
            }
            else false
        }

        private var infoAnimationLaunched = false

        /**
         * Animate single digit pair with a given value. No content check
         */
        fun animateSubstitution(oldView: TextView, newView: TextView, newVal: String){
            val translationDistance = oldView.context.resources.getDimension(R.dimen.main_digitClock_translationDistance)

            val fadingStart = 0.75f
            val fadingEnd = 0.4f
            val fadingC = Contractor(fadingEnd, fadingStart)

            val showingStart = 0.2f
            val showingEnd = 0.45f
            val showingC = Contractor(showingStart, showingEnd)

            //bound to the old's fading animation
            val infoMutingStart = 0.85f
            val infoMutingEnd = 0.7f
            val infoMutingC = Contractor(infoMutingEnd, infoMutingStart)

            //and to the new's showing animation
            val infoRaisingStart = 0.1f
            val infoRaisingEnd = 0.7f
            val infoRaisingC = Contractor(infoRaisingStart, infoRaisingEnd)

            val infoBlock = binding.mainDisplayClockTextInfo
            val infoMutingCutoff = 0.4f
            val infoAnimation = !infoAnimationLaunched
            infoAnimationLaunched = true

            val oldAnimator = oldView.animate().apply{
                duration = 2100
                try{
                    translationY(translationDistance)
                } catch (_: Exception) {}

                setUpdateListener {
                    oldView.alpha = fadingC.contractReversed(it.animatedFraction)
                    if(infoAnimation) {
                        val muting = infoMutingC.contractReversed(it.animatedFraction)
                        if (muting > infoMutingCutoff) infoBlock.alpha = muting
                    }
                }
            }

            val newAnimator = newView.animate().apply{
                duration = 2400
                try{
                    translationY(translationDistance)
                } catch (_: Exception) {}
                interpolator = FastOutSlowInInterpolator()

                setUpdateListener {
                    newView.alpha = showingC.contract(it.animatedFraction)
                    if(infoAnimation) {
                        val raising = infoRaisingC.contract(it.animatedFraction)
                        if (raising > infoMutingCutoff) infoBlock.alpha = raising
                    }
                }

                setListener(object: AnimatorListener{
                    override fun onAnimationStart(animation: Animator) = Unit

                    override fun onAnimationEnd(animation: Animator){
                        with(oldView){
                            text = newVal
                            translationY = 0f
                            alpha = 1f
                        }
                        with(newView){
                            translationY = 0f
                            alpha = 0f
                        }
                        infoAnimationLaunched = false
                    }

                    override fun onAnimationCancel(animation: Animator){
                        with(oldView){
                            text = newVal
                            translationY = 0f
                            alpha = 1f
                        }
                        with(newView){
                            translationY = 0f
                            alpha = 0f
                        }
                        infoAnimationLaunched = false
                    }

                    override fun onAnimationRepeat(animation: Animator) = Unit

                })

                startDelay = 500
            }

            CoroutineScope(Dispatchers.Main).launch {
                try {
                    newView.text = newVal
                    oldAnimator.start()
                    newAnimator.start()
                } catch (e: Exception){
                    slU.s("animation launching exception", e)
                }
            }
        }

        var firstActiveDate: Date? = null
            set(value) {
                field = value
                slU.f("setting first active")
                showInfoStrings(value)
            }
        /**
         * Memorizing Date and launches block update animation if there is no-null Date; hiding the block otherwise
         */


        private var showingSubInfo = false
        private lateinit var infoStringsShowingJob: Job
        /**
         * Returning if sub info is shown
         * When the first string is empty by arrival, shows 'initial' animation.
         * When argument date resulted to the same info text, shows no difference
         * Starting replacing animation, in the last case
         */
        private fun showInfoStrings(faDate: Date?, pronto: Boolean = false){
            //delay to differentiate animation time strings animation from separator and digits
            val courtesyDelay = 2900L

            if (this::infoStringsShowingJob.isInitialized &&
                    infoStringsShowingJob.isActive) infoStringsShowingJob.cancel()

            infoStringsShowingJob = CoroutineScope(Dispatchers.Default).launch {
                //in case of just hidden subInfo
                if (!pronto) delay(courtesyDelay)

                if (showingSubInfo) return@launch
                if (faDate == null){
                    hideInfoStrings(600L)
                    return@launch
                }

                val secInMilli = 1000
                val minInMilli = secInMilli * 60
                val hInMilli = minInMilli * 60

                val date = Date()
                val time = faDate.time - date.time

                val hour = time / hInMilli
                val min = (time % hInMilli) / minInMilli

                val cutOff = (hInMilli * 10).toLong() + (minInMilli * 30)
                if (time > cutOff || date.before(Date())) showInfoStrings(null, pronto)
//                slU.i("$hour  $min")

                val comprised = compriseStrings(hour.toInt(), min.toInt())
                val showingSecond = comprised[1].isNotEmpty()

                val stringOne = binding.mainDisplayClockTimeTextInfoStringOne
                val stringTwo = binding.mainDisplayClockTimeTextInfoStringTwo
                val stringThree = binding.mainDisplayClockTimeTextInfoStringThree

                if (stringOne.text.isNotEmpty()) {
                    forkUsualInfoStringAnimations(stringOne, stringTwo, stringThree, comprised)
                    return@launch
                }

                fun launchAnimations() {
                    val alphaDuration = 680L
                    val alphaAdvancing = 150L
                    val alphaStart = 0.35f
                    val alphaEnd = 0.95f
                    val alphaC = Contractor(alphaStart, alphaEnd)

                    stringTwo.x =
                        if (showingSecond) stringOne.right.toFloat() + stringStartToEndMargin else stringOne.right.toFloat()
                    stringTwo.y = 0f
                    val stringTwoToY = stringOne.bottom.toFloat() + stringTopToBottomMargin

                    stringThree.x = stringTwo.right.toFloat() + stringTwo.x + stringStartToEndMargin
                    stringThree.y =
                        if (showingSecond) stringOne.bottom.toFloat() + stringTopToBottomMargin else 0f
                    val stringThreeToY =
                        if (showingSecond) stringTwo.bottom.toFloat() + (stringTopToBottomMargin * 2) else stringTwoToY

                    stringOne.animate().apply {
                        alpha(1f)
                        duration = alphaDuration

                        setListener(object: AnimatorListener{
                            override fun onAnimationStart(animation: Animator) = Unit

                            override fun onAnimationEnd(animation: Animator) = Unit

                            override fun onAnimationCancel(animation: Animator){
                                stringOne.alpha = 1f
                            }

                            override fun onAnimationRepeat(animation: Animator) = Unit
                        })

                        start()
                    }

                    if (showingSecond) {
                        stringTwo.animate().apply {
                            y(stringTwoToY)
                            duration = alphaDuration
                            startDelay = alphaDuration - alphaAdvancing

                            setUpdateListener {
                                stringTwo.alpha = alphaC.contract(it.animatedFraction)
                            }

                            setListener(object: AnimatorListener{
                                override fun onAnimationStart(animation: Animator) = Unit

                                override fun onAnimationEnd(animation: Animator) = Unit

                                override fun onAnimationCancel(animation: Animator){
                                    stringTwo.alpha = 1f
                                    stringTwo.y = stringTwoToY
                                }

                                override fun onAnimationRepeat(animation: Animator) = Unit
                            })

                            start()
                        }
                    }

                    stringThree.animate().apply {
                        y(stringThreeToY)
                        duration = alphaDuration
                        startDelay =
                            if (showingSecond) alphaDuration * 2 - alphaAdvancing else alphaDuration - alphaAdvancing

                        setUpdateListener {
                            stringThree.alpha = alphaC.contract(it.animatedFraction)
                        }

                        setListener(object: AnimatorListener{
                            override fun onAnimationStart(animation: Animator) = Unit

                            override fun onAnimationEnd(animation: Animator) = Unit

                            override fun onAnimationCancel(animation: Animator){
                                stringThree.alpha = 1f
                                stringThree.y = stringThreeToY
                            }

                            override fun onAnimationRepeat(animation: Animator) = Unit
                        })

                        start()
                    }
                }

                withContext(Dispatchers.Main) {
                    stringOne.text = comprised[0]
                    stringTwo.text = comprised[1]
                    stringThree.text = "to sleep"

                    waitForStringsLayout(
                        stringOne,
                        stringTwo,
                        stringThree,
                        method = ::launchAnimations
                    )
                }
            }
        }

        private var infoStringHeight = 0
        private var infoStringHeightMeasured = false
        private suspend fun forkUsualInfoStringAnimations(stringOne: TextView, stringTwo: TextView, stringThree: TextView, comprised: Array<Spanned>){
            val height =
                if (!infoStringHeightMeasured) stringOne.height.also {
                    infoStringHeight = it
                    infoStringHeightMeasured = true
                }
                else infoStringHeight

            val shrinkDuration = 980L
            val expandOverlap = 280L
            val expandDuration = 1650L
            val c = Contractor(0.48f, 0.95f)

            //casting to String is mandatory
            if (stringOne.text.toString() == comprised[0].toString() &&
                stringTwo.text.toString() == comprised[1].toString()) return


            //first, shrinking all the lines
            ValueAnimator.ofInt(height, 0).apply {
                duration = shrinkDuration
                addUpdateListener {
                    val value = it.animatedValue as Int
                    stringOne.bottom = value
                    stringTwo.bottom = value + stringTwo.top
                    stringThree.bottom = value + stringThree.top

                    val result = c.contractReversed(it.animatedFraction)
                    stringOne.alpha = result
                    stringTwo.alpha = result
                    stringThree.alpha = result
                }


                addListener(object: AnimatorListener{
                    override fun onAnimationStart(animation: Animator) = Unit

                    override fun onAnimationEnd(animation: Animator) = Unit

                    override fun onAnimationCancel(animation: Animator){
                        stringOne.alpha = 1f
                        stringTwo.alpha = 1f
                        stringThree.alpha = 1f

                        stringOne.height = height
                        stringTwo.height = height
                        stringThree.height = height
                    }

                    override fun onAnimationRepeat(animation: Animator) = Unit
                })

                withContext(Dispatchers.Main) {
                    start()
                }
            }

            //for final animations
            fun launchAnimations(){
                val showingSecond = comprised[1].isNotEmpty()

                stringTwo.x = if (showingSecond) stringOne.right.toFloat() + stringStartToEndMargin else stringOne.right.toFloat()
                stringTwo.y = if (showingSecond) stringOne.bottom.toFloat() + stringTopToBottomMargin else 0f

                stringThree.x = stringTwo.right.toFloat() + stringTwo.x + stringStartToEndMargin
                stringThree.y = if (showingSecond) stringTwo.bottom.toFloat() + (stringTopToBottomMargin * 2) else stringTwo.top.toFloat() + stringTopToBottomMargin

                ValueAnimator.ofInt(0, height).apply {
                    duration = expandDuration

                    addUpdateListener {
                        val value = it.animatedValue as Int
                        stringOne.bottom = value
                        stringTwo.bottom = value + stringTwo.top
                        stringThree.bottom = value + stringThree.top

                        val result = c.contract(it.animatedFraction)
                        stringOne.alpha = result
                        stringTwo.alpha = result
                        stringThree.alpha = result
                    }


                    addListener(object: AnimatorListener{
                        override fun onAnimationStart(animation: Animator) = Unit

                        override fun onAnimationEnd(animation: Animator) = Unit

                        override fun onAnimationCancel(animation: Animator){
                            stringOne.alpha = 1f
                            stringTwo.alpha = 1f
                            stringThree.alpha = 1f

                            stringOne.height = height
                            stringTwo.height = height
                            stringThree.height = height
                        }

                        override fun onAnimationRepeat(animation: Animator) = Unit
                    })

                    start()
                }
            }

            //incorporates delay
            CoroutineScope(Dispatchers.Default).launch {
                delay(shrinkDuration - expandOverlap)

                withContext(Dispatchers.Main){
                    stringOne.apply {
                        alpha = 1f
                        text = comprised[0]
                    }
                    stringTwo.apply {
                        alpha = 1f
                        text = comprised[1]
                    }
                    stringThree.apply {
                        alpha = 1f
                        text = "to sleep"
                    }

                    waitForStringsLayout(
                        stringOne,
                        stringTwo,
                        stringThree,
                        method = ::launchAnimations
                    )
                }
            }
        }
        private fun compriseStrings(hour: Int, min: Int): Array<Spanned>{

            val firstStringText = SpannableStringBuilder(when (hour){
                1-> "one"
                2-> "two"
                3-> "three"
                4-> "four"
                5-> "five"
                6-> "six"
                7-> "seven"
                8-> "eight"
                9-> "nine"
                10-> "ten"
                else ->{
                    if (min < 30) "not a much time"
                    else "half"
                }
            }).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                    setSpan(
                        TypefaceSpan(this@MainActivity.resources.getFont(R.font.lato_regular_italic)),
                        0,
                        length,
                        Spanned.SPAN_INCLUSIVE_EXCLUSIVE
                    )

                if ((hour==0 && min >= 30) || hour > 0) {
                    setSpan(
                        ForegroundColorSpan(this@MainActivity.getColor(R.color.mild_pitchShadow)),
                        0,
                        1,
                        Spanned.SPAN_INCLUSIVE_INCLUSIVE
                    )
                    if (hour==0) append(" of a hour")
                }
            }

            val secStringText = SpannableStringBuilder(if (min in 30 .. 59 && hour >= 1) "a half" else "").apply {
                if (isNotEmpty()){
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                        setSpan(
                            TypefaceSpan(this@MainActivity.resources.getFont(R.font.lato_regular_italic)),
                            2,
                            length,
                            Spanned.SPAN_INCLUSIVE_EXCLUSIVE
                        )

                    if ((hour==0 && min >= 30) || hour > 0) setSpan(
                        ForegroundColorSpan(this@MainActivity.getColor(R.color.mild_pitchShadow)),
                        2,
                        3,
                        Spanned.SPAN_INCLUSIVE_INCLUSIVE
                    )
                }
            }

            val showingSecond = secStringText.isNotEmpty()
            if (showingSecond){
                firstStringText.append(" and")
                secStringText.addHourNoun(hour, true)
            }
            else{
                firstStringText.addHourNoun(hour, false)
            }

            return arrayOf(firstStringText.toSpanned(), secStringText.toSpanned())
        }
        private fun SpannableStringBuilder.addHourNoun(hour: Int, half: Boolean){
            val hourSingular = " hour"
            val hoursAccusative = " hours"
            val hoursGenitive = " hours"

            this.append(when(hour){
                1 -> if (half) hoursAccusative else hourSingular
                2 -> hoursAccusative
                3 -> hoursAccusative
                4 -> hoursAccusative
                5 -> hoursGenitive
                6 -> hoursGenitive
                7 -> hoursGenitive
                8 -> hoursGenitive
                9 -> hoursGenitive
                10 -> hoursGenitive
                else -> ""
            })
        }
        private fun waitForStringsLayout(vararg strings: TextView, method: ()-> Unit){
            val stringsReady = BooleanArray(strings.size)
            for ((i, string) in strings.withIndex())
                string.viewTreeObserver.addOnGlobalLayoutListener(object: OnGlobalLayoutListener{
                override fun onGlobalLayout() {
                    string.viewTreeObserver.removeOnGlobalLayoutListener(this)

                    stringsReady[i] = true
                    if (stringsReady.count { it } == strings.size) method()
                }
            })
        }
        private suspend fun hideInfoStrings(dur: Long, onEnd: () -> Unit = {}){
            val stringOne = binding.mainDisplayClockTimeTextInfoStringOne
            val stringTwo = binding.mainDisplayClockTimeTextInfoStringTwo
            val stringThree = binding.mainDisplayClockTimeTextInfoStringThree

            withContext(Dispatchers.Main) {
                if (stringOne.text.toString() == ""){
                    onEnd()
                    return@withContext
                }
                ValueAnimator.ofFloat(1f, 0f).apply {
                    this.duration = dur
                    addUpdateListener {
                        val value = it.animatedValue as Float

                        stringOne.alpha = value
                        stringTwo.alpha = value
                        stringThree.alpha = value
                    }


                    addListener(object: AnimatorListener{
                        override fun onAnimationStart(animation: Animator) = Unit

                        override fun onAnimationEnd(animation: Animator){
                            stringOne.alpha = 0f
                            stringTwo.alpha = 0f
                            stringThree.alpha = 0f

                            stringOne.text = ""
                            stringTwo.text = ""
                            stringThree.text = ""

                            onEnd()
                        }

                        override fun onAnimationCancel(animation: Animator){
                            stringOne.alpha = 0f
                            stringTwo.alpha = 0f
                            stringThree.alpha = 0f

                            stringOne.text = ""
                            stringTwo.text = ""
                            stringThree.text = ""
                        }

                        override fun onAnimationRepeat(animation: Animator) = Unit
                    })

                    start()
                }
            }
        }


        private var subInfoID = ""
        private var subInfoPre = false
        /**
         * Storing ID for callbacks on *dismiss* actions and flags for change animations
         */
        fun showSubInfoStrings(alarmIdReference: String, preliminaryActive: Boolean, firingDate: Date){
            val infoDuration = 400L
            val subDuration = 420L

            val secInMilli = 1000
            val minInMilli = secInMilli * 60
            val hInMilli = minInMilli * 60

            val date = Date()
            val time = firingDate.time - date.time

            val cutOff = (hInMilli * 10).toLong() + (minInMilli * 30)
            if (time > cutOff || date.before(Date())){
                hideSubInfoStrings()
                return
            }

            fun showStrings() {
                val preliminaryStringTopMargin = 8f.toPx()
                val preliminaryMoveDuration = 195L
                val preliminaryMoveAlphaC = Contractor(0.55f, 0.95f)

                val stringOne = binding.mainDisplayClockSubTextInfoStringOne
                val stringTwo = binding.mainDisplayClockSubTextInfoStringTwo
                val stringOneDub = binding.mainDisplayClockSubTextInfoStringOneDub
                val stringTwoDub = binding.mainDisplayClockSubTextInfoStringTwoDub
                val dismissText = "Dismiss\t\t\t\t\t\t"

                val secondTopY = stringOne.y
                val secondBottomY = stringOne.y + stringOne.height + preliminaryStringTopMargin

                val firstSpannable = SpannableString(dismissText + "Main")
                val secondSpannable = SpannableString(dismissText + "Preliminary")
                val dubSpannable = SpannableString(dismissText)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    firstSpannable.setSpan(
                        TypefaceSpan(this@MainActivity.resources.getFont(R.font.lato_regular_italic)),
                        dismissText.length,
                        firstSpannable.length,
                        Spanned.SPAN_INCLUSIVE_EXCLUSIVE
                    )
                    secondSpannable.setSpan(
                        TypefaceSpan(this@MainActivity.resources.getFont(R.font.lato_regular_italic)),
                        dismissText.length,
                        secondSpannable.length,
                        Spanned.SPAN_INCLUSIVE_EXCLUSIVE
                    )
                }

                firstSpannable.setSpan(
                    HumbleClickableSpan {faCallback.externalDismiss(subInfoID, false)},
                    0,
                    dismissText.length,
                    Spanned.SPAN_INCLUSIVE_EXCLUSIVE
                )
                secondSpannable.setSpan(
                    HumbleClickableSpan {faCallback.externalDismiss(subInfoID, true)},
                    0,
                    dismissText.length,
                    Spanned.SPAN_INCLUSIVE_EXCLUSIVE
                )

                dubSpannable.setSpan(
                    UnderlineSpan(),
                    0,
                    dismissText.length,
                    Spanned.SPAN_INCLUSIVE_EXCLUSIVE
                )
                dubSpannable.setSpan(
                    ForegroundColorSpan(this@MainActivity.getColor(R.color.mild_pitchRegular)),
                    0,
                    dismissText.length,
                    Spanned.SPAN_INCLUSIVE_EXCLUSIVE
                )

                //If they were hidden
                if (!showingSubInfo) {
                    stringOne.text = firstSpannable
                    stringOneDub.text = dubSpannable
                    stringTwo.text = secondSpannable
                    stringTwoDub.text = dubSpannable

                    if (preliminaryActive){
                        stringTwo.y = secondBottomY
                        stringTwoDub.y = secondBottomY

                        stringTwo.visibility = View.VISIBLE
                        stringTwoDub.visibility = View.VISIBLE
                    }


                    ValueAnimator.ofFloat(0f, 1f).apply {
                        this.duration = subDuration

                        addUpdateListener {
                            val value = it.animatedValue as Float

                            stringOne.alpha = value
                            stringOneDub.alpha = value
                            if (preliminaryActive) {
                                stringTwo.alpha = value
                                stringTwoDub.alpha = value
                            }
                        }

                        addListener(object : AnimatorListener {
                            override fun onAnimationStart(animation: Animator) = Unit

                            override fun onAnimationEnd(animation: Animator) = Unit

                            override fun onAnimationCancel(animation: Animator) {
                                stringOne.alpha = 1f
                                stringOneDub.alpha = 1f
                                if (preliminaryActive) {
                                    stringTwo.alpha = 1f
                                    stringTwoDub.alpha = 1f
                                }
                            }

                            override fun onAnimationRepeat(animation: Animator) = Unit
                        })

                        start()

                    }
                }

                //If strings are being shown
                else if (preliminaryActive != subInfoPre){
                    if (!subInfoPre){

                        stringTwo.text = secondSpannable
                        stringTwoDub.text = dubSpannable
                        stringTwo.visibility = View.VISIBLE
                        stringTwoDub.visibility = View.VISIBLE

                        ValueAnimator.ofFloat(secondTopY, secondBottomY).apply {
                            duration = preliminaryMoveDuration

                            addUpdateListener {
                                stringTwo.y = it.animatedValue as Float
                                stringTwoDub.y = it.animatedValue as Float

                                stringTwo.alpha = preliminaryMoveAlphaC.contract(it.animatedFraction)
                                stringTwoDub.alpha = preliminaryMoveAlphaC.contract(it.animatedFraction)
                            }

                            addListener(object : AnimatorListener {
                                override fun onAnimationStart(animation: Animator) = Unit

                                override fun onAnimationEnd(animation: Animator) = Unit

                                override fun onAnimationCancel(animation: Animator) {
                                    stringTwo.y = secondBottomY
                                    stringTwoDub.y = secondBottomY

                                    stringTwo.alpha = 0f
                                    stringTwoDub.alpha = 0f
                                }

                                override fun onAnimationRepeat(animation: Animator) = Unit
                            })

                            start()
                        }
                    }
                    else{
                        stringTwo.text = secondSpannable
                        stringTwoDub.text = dubSpannable

                        ValueAnimator.ofFloat(secondBottomY, secondTopY).apply {
                            duration = preliminaryMoveDuration

                            addUpdateListener {
                                stringTwo.y = it.animatedValue as Float
                                stringTwoDub.y = it.animatedValue as Float

                                stringTwo.alpha = preliminaryMoveAlphaC.contractReversed(it.animatedFraction)
                                stringTwoDub.alpha = preliminaryMoveAlphaC.contractReversed(it.animatedFraction)
                            }

                            addListener(object : AnimatorListener {
                                override fun onAnimationStart(animation: Animator) = Unit

                                override fun onAnimationEnd(animation: Animator){
                                    stringTwo.text = ""
                                    stringTwoDub.text = ""

                                    stringTwo.visibility = View.INVISIBLE
                                    stringTwoDub.visibility = View.INVISIBLE
                                }

                                override fun onAnimationCancel(animation: Animator){
                                    stringTwo.text = ""
                                    stringTwoDub.text = ""

                                    stringTwo.alpha = 0f
                                    stringTwoDub.alpha = 0f

                                    stringTwo.y = secondTopY
                                    stringTwoDub.y = secondTopY

                                    stringTwo.visibility = View.INVISIBLE
                                    stringTwoDub.visibility = View.INVISIBLE
                                }

                                override fun onAnimationRepeat(animation: Animator) = Unit
                            })

                            start()
                        }
                    }
                }

                //If view is basically didn't change
                else{
                    val fadingStart = 0.65f
                    val fadingEnd = 1f
                    val fadingC = Contractor(fadingStart, fadingEnd)
                    val eachDuration = 440L
                    val secondAdvancing = 20L

                    ValueAnimator.ofFloat(0f, 1f).apply{
                        duration = eachDuration

                        addUpdateListener {
                            stringOneDub.alpha = fadingC.contractReversed(it.animatedFraction)
                            if (preliminaryActive) {
                                stringTwoDub.alpha = fadingC.contractReversed(it.animatedFraction)
                            }
                        }

                        start()
                    }

                    ValueAnimator.ofFloat(0f,1f).apply {
                        duration = eachDuration
                        startDelay = eachDuration - secondAdvancing

                        addUpdateListener {
                            stringOneDub.alpha = fadingC.contract(it.animatedFraction)
                            if (preliminaryActive) {
                                stringTwoDub.alpha = fadingC.contract(it.animatedFraction)
                            }
                        }

                        start()
                    }
                }

                showingSubInfo = true
                subInfoID = alarmIdReference
                subInfoPre = preliminaryActive
            }

            CoroutineScope(Dispatchers.Default).launch {
                hideInfoStrings(infoDuration, ::showStrings)
            }

        }

        fun hideSubInfoStrings(){
            val subDuration = 400L

            val stringOne = binding.mainDisplayClockSubTextInfoStringOne
            val stringTwo = binding.mainDisplayClockSubTextInfoStringTwo
            val stringOneDub = binding.mainDisplayClockSubTextInfoStringOneDub
            val stringTwoDub = binding.mainDisplayClockSubTextInfoStringTwoDub

            if (!showingSubInfo){
                showInfoStrings(firstActiveDate, true)
                return
            }

            binding.mainDisplayClockTimeTextInfoStringOne.alpha = 0f
            binding.mainDisplayClockTimeTextInfoStringTwo.alpha = 0f
            binding.mainDisplayClockTimeTextInfoStringThree.alpha = 0f

            ValueAnimator.ofFloat(0f, 1f).apply {
                this.duration = subDuration
                addUpdateListener {
                    val value = abs(1 - it.animatedValue as Float)

                    stringOne.alpha = value
                    stringOneDub.alpha = value
                    if (subInfoPre) {
                        stringTwo.alpha = value
                        stringTwoDub.alpha = value
                    }
                }


                addListener(object : AnimatorListener {
                    override fun onAnimationStart(animation: Animator) = Unit

                    override fun onAnimationCancel(animation: Animator){
                        stringOne.alpha = 0f
                        stringOneDub.alpha = 0f
                        if (subInfoPre) {
                            stringTwo.alpha = 0f
                            stringTwoDub.alpha = 0f
                        }

                        stringOne.text = ""
                        stringOneDub.text = ""
                        stringTwo.text = ""
                        stringTwoDub.text = ""

                        showingSubInfo = false
                        showInfoStrings(firstActiveDate, true)
                    }

                    override fun onAnimationEnd(animation: Animator){
                        stringOne.alpha = 0f
                        stringOneDub.alpha = 0f
                        if (subInfoPre) {
                            stringTwo.alpha = 0f
                            stringTwoDub.alpha = 0f
                        }

                        stringOne.text = ""
                        stringOneDub.text = ""
                        stringTwo.text = ""
                        stringTwoDub.text = ""

                        showingSubInfo = false
                        showInfoStrings(firstActiveDate, true)
                    }

                    override fun onAnimationRepeat(animation: Animator) = Unit
                })

                start()
            }
        }

        private inner class HumbleClickableSpan(private val onViewClick: ()-> Unit): ClickableSpan(){
            override fun onClick(widget: View) = onViewClick()

            override fun updateDrawState(ds: TextPaint) {
                super.updateDrawState(ds)
                ds.color = this@MainActivity.getColor(R.color.mild_shady)
                ds.isUnderlineText = false
            }
        }
    }

    override fun onResume() {
        if (onResumePermitted) {
            slU.f("MA onResume")
            displayClock.setupDigits(true)
            displayClock.prepareDisplayClock()

            if (_vm.checkRVReloadRequest() && this::faCallback.isInitialized) {
                restartActivity()
//                faCallback.createBrandNewRecycler(false).also { slU.f("reloading pref") }
            }

            if (_vm.checkFiringError()) transmitError(ErrorHandlerImpl.FIRING_ERROR_CODE)
        }

        super.onResume()
    }

    override fun onStop() {
        ex()

        displayClock.stopDigitsTimer()
        super.onStop()
    }

    override fun restartActivity(){
        finish()
        startActivity(Intent(applicationContext, this::class.java))
    }

    override fun getLifecycleOwner(): LifecycleOwner = this

    /*
        All below methods belong to intercom connections
         */
    override fun onSettingsReady(){
        binding.mainSettingsButtonBack.apply{
            visibility = View.VISIBLE
            alpha = 0f
            InterfaceUtils.startAlphaAnimation(this, 1f, 2200, FastOutSlowInInterpolator())
        }
        binding.mainSettingsButtonIcon.apply{
            visibility = View.VISIBLE
            alpha = 0f
            InterfaceUtils.startAlphaAnimation(this, 1f, 2200, FastOutSlowInInterpolator(), object: AnimatorListener{
                override fun onAnimationStart(animation: Animator)= Unit

                override fun onAnimationEnd(animation: Animator) {
                    //additionally checking digits
                    with(displayClock){
                        setupDigits(true)
                        prepareDisplayClock()
//                        binding.fab.callOnClick()
                    }

                }

                override fun onAnimationCancel(animation: Animator)= Unit

                override fun onAnimationRepeat(animation: Animator)= Unit

            })
        }

        onResumePermitted = true

    }
    override fun getStateSaver(): StateSaver = provideStateSaver()

    fun transmitError(code: Int){
        when(code){
            ErrorHandlerImpl.RV_ERROR_CODE -> Toast.makeText(this, "RV ERROR!!!", Toast.LENGTH_LONG).show()
            ErrorHandlerImpl.FIRING_ERROR_CODE -> Toast.makeText(this, "FIRING ERROR!!!", Toast.LENGTH_LONG).show()
        }
        slU.i("showing error message")
    }

    //Additionally calling this method in Supervisor when MainActivity is indicated ready,
    //because it's not distributed in a way that other callback functions does
    fun provideStateSaver(): StateSaver = _vm

    override fun pingAddButtonVisibility() {
        TODO("Not yet implemented")
    }

    override fun getFM(): FragmentManager = supportFragmentManager

    override fun getParentVG(): ViewGroup = findViewById(R.id.main_constraint_layout)

    override fun passNewFirstActiveDate(date: Date?){
        displayClock.firstActiveDate = date
    }
    override fun showSubInfo(alarmIdReference: String, preliminaryActive: Boolean, firingDate: Date) = displayClock.showSubInfoStrings(
        alarmIdReference,
        preliminaryActive,
        firingDate
    )
    override fun hideSubInfo() = displayClock.hideSubInfoStrings()

    companion object {
        const val RELOAD_RV_REQUEST = "rv_reload"
        const val FILL_DB = 3
        const val CLEAR_DB = 4
    }
}

interface M_to_FA_Callback{
    fun pingAddButtonVisibility()
    fun getFM(): FragmentManager
    fun getParentVG(): ViewGroup

    fun passNewFirstActiveDate(date: Date?)
    fun showSubInfo(alarmIdReference: String, preliminaryActive: Boolean, firingDate: Date)
    fun hideSubInfo()
    fun launchRingtonePicker(data: MainActivity.RPRequestData)
    fun restartActivity()
    fun getLifecycleOwner(): LifecycleOwner
}
interface M_to_SF_Callback{
    fun onSettingsReady()
    fun getStateSaver(): StateSaver
}