package com.foxstoncold.youralarm.alarmsUI

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.drawable.VectorDrawable
import android.os.Build
import android.text.Editable
import android.text.InputFilter
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.RelativeSizeSpan
import android.text.style.TypefaceSpan
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.BaseExpandableListAdapter
import android.widget.CompoundButton
import android.widget.ExpandableListView
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.Group
import androidx.core.content.res.ResourcesCompat
import com.foxstoncold.youralarm.R
import com.foxstoncold.youralarm.alarmsUI.DrawableUtils.Companion.paintSelectors
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.skydoves.balloon.Balloon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ExpandableListFactory(private val expandableListView: ExpandableListView,
                            private val context: Context){

    private val Number.toPx get() = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        this.toFloat(),
        Resources.getSystem().displayMetrics)

    //this backing array provided to maintain strict order of groups contained
    private val groupContainers = ArrayList<GroupItemContainer>()
    private val childContainersStorage = HashMap<GroupItemContainer, ArrayList<ChildItemContainer>>()
    private val childContainersBunch = ArrayList<ChildItemContainer>()
    private val childViewsStorage = HashMap<GroupItemContainer, ArrayList<View>>()

    var onGroupChanged: (BooleanArray)->Unit = {slU.i("on group changed action expected")}
    var initialGroupStates = BooleanArray(1)
    private val groupStates = ArrayList<Boolean>()

    inner class GroupItemContainer{
        var titleDrawable = AppCompatResources.getDrawable(context, R.drawable.ic_clock_alarm)!!
        var titleText = "Some text\nin some string"
        var enabled = true

        fun setTitleDrawable(@DrawableRes resID: Int){
            titleDrawable = AppCompatResources.getDrawable(context, resID)!!
        }

        fun addToGroupStorage(){
            groupContainers.add(this)
            childContainersStorage[this] = ArrayList()
            childViewsStorage[this] = ArrayList()
            groupStates.add(false)
        }

        fun expand() = expandableListView.expandGroup(groupContainers.indexOf(this))
        fun collapse() = expandableListView.collapseGroup(groupContainers.indexOf(this))
    }

    inner class ChildItemContainer (val parent: GroupItemContainer) {
        var type = 0f
        var groupPosition: Int = -1
        var childPosition: Int = -1

        var titleText = "Some text"
        var showHint = false
        fun showHint(){ showHint = true }
        var hintText =
            "this text will be shown in the hint bubble\nmultiple lines are the common occurrence and you have to make sure they're displayed correctly"
        var subSwitchVisible = false
        private val toast = Toast.makeText(context, "some behaviour expected", Toast.LENGTH_SHORT)

        var switchStartStateGetter: (View)->Boolean = { false }
        var switchWarningStartStateGetter: ()->Boolean = { false }

        var switchOnCheckedListener: (CompoundButton, ImageView, Boolean) -> Unit = { _, _, _ -> toast.show() }
        var switchWarningAvailable = false
        var switchUseCheckedListenerOnStart = false

        var testPlayButtonCallback: (View, View)->Unit = { _,_ -> toast.show() }
        var testStopButtonCallback: (View)->Unit = { _ -> toast.show() }
        var testStopButtonVisibilityChecker: ()->Boolean = { false }

        var editTextStartTextGetter: ()->String = { "-1" }
        var editTextMaxLength = 2
        val editTextStartValue: String
            get() = editTextStartTextGetter()
        var editTextOnChangedListener: (CharSequence?) -> Unit = { toast.show() }
        var unitText = "some text"

        var dropDownStartTextGetter: ()->String = { "dropDown" }
        val dropDownStartText
            get() = dropDownStartTextGetter()
        var dropDownItems = listOf("itemOne", "itemTwo")
        var dropDownChangedListener: (String) -> Unit = { toast.show() }

        var seekbarStartValueGetter: ()->Float = { 50f }
        var seekbarOnReleasedListener: (Float)->Unit = { toast.show() }
        val seekbarStartValue: Float
            get() = seekbarStartValueGetter()
        var seekbarFloatValues = false
        var seekbarMinValue = 0f
        var seekbarMaxValue = 100f
        var seekbarStep = 0f
        var seekbarUnitText = "unit"

        fun addToStorage() {
            childContainersStorage[parent]!!.add(this)
            childContainersBunch.add(this)
        }

        fun refreshView(){
            val child = childViewsStorage[groupContainers[groupPosition]]!![childPosition]
            child.alterChildView(this)
        }
    }

    fun AsyncBuild(onBuilt: (ExpandableListView)->Unit) {
        slU.fr("start building exp. listView")
        val adapter = BuildableExpandableListAdapter()

        CoroutineScope(Dispatchers.Default).launch {
            for (childContainer in childContainersBunch){
                val view = createChildView()
                withContext(Dispatchers.Main) {
                    view.alterChildView(childContainer)
                }
                childViewsStorage[childContainer.parent]!!.add(view)
            }
            slU.fr("child views cache created")

            withContext(Dispatchers.Main){
                with(expandableListView) {
                    setAdapter(adapter)
                    setGroupIndicator(VectorDrawable())
                    setOnGroupClickListener { parent, v, groupPosition, id ->

                        val container = groupContainers[groupPosition]
                        if (container.enabled) {
                            if (parent.isGroupExpanded(groupPosition))
                                parent.collapseGroup(groupPosition)
                            else parent.expandGroup(groupPosition)
                        }
                        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                            v.performHapticFeedback(HapticFeedbackConstants.REJECT)

                        groupStates[groupPosition] = parent.isGroupExpanded(groupPosition)
                        onGroupChanged(groupStates.toBooleanArray())
                        true
                    }

                    for ((i, expanded) in initialGroupStates.withIndex()){
                        if (expanded) expandGroup(i)
                        groupStates[i] = expanded
                    }
                    slU.fr("exp. listView passed")
                    onBuilt(this)
                }
            }
        }


    }

    private fun createChildView(): View{
        val layoutInflater =
            context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        return layoutInflater.inflate(R.layout.settings_child_item, null)
    }

    private fun View.alterChildView(container: ChildItemContainer){
        val imm = context.getSystemService(InputMethodManager::class.java)

        with(findViewById<TextView>(R.id.settings_child_title)) {
            text = container.titleText

            if (container.type == 0f) setPaddingRelative(
                0,
                10.5.toPx.toInt(),
                0,
                8.toPx.toInt()
            )
            else if (container.type == 1.1f) {
                val lp = layoutParams as ConstraintLayout.LayoutParams
                lp.topToTop = R.id.settings_child_editText_one
                lp.bottomToBottom = R.id.settings_child_editText_one
                layoutParams = lp
            }
        }

        if (container.showHint) {
            val balloon = Balloon.Builder(this.context)
                .setTextIsHtml(true)
                .setText(container.hintText)
                .setTextLineSpacing(4.4f)
                .setTextSize(18f)
                .setTextColorResource(R.color.black)
                .setBackgroundColorResource(R.color.mild_surface)

                .setArrowDrawable(VectorDrawable())
                .setMarginTop(-2)

                .setPaddingVertical(8)
                .setPaddingHorizontal(4)
                .setWidthRatio(0.85f)
                .setElevation(10)
                .setCornerRadius(23f)
                .build()
            with(findViewById<ImageView>(R.id.settings_child_hint)) {
                visibility = View.VISIBLE
                setOnClickListener { balloon.showAlignBottom(it) }
            }
        }

        val switchWarning = findViewById<ImageView>(R.id.settings_child_switch_m2_warning).apply {
            if (container.switchWarningAvailable) visibility = if (container.switchWarningStartStateGetter()) View.VISIBLE else View.INVISIBLE

            val drawable = ResourcesCompat.getDrawable(context.resources, R.drawable.ic_warning_rhombus, context.theme)!!
            drawable.setTint(context.getColor(R.color.mild_pitchRegular))
            setImageDrawable(drawable)
        }

        when (container.type) {

            //little switch
            0f -> {
                findViewById<Group>(R.id.settings_child_group_zero).visibility =
                    View.VISIBLE
                val switch = findViewById<SwitchMaterial>(R.id.settings_child_switch_m2)

                var states = arrayOf(
                    intArrayOf(-android.R.attr.state_checked),
                    intArrayOf(android.R.attr.state_checked)
                )
                var colors = arrayOf(
                    context.getColor(R.color.mild_greyscaleLight),
                    context.getColor(R.color.mild_presenceRegular)
                ).toIntArray()
                switch.trackTintList = ColorStateList(states, colors)

                states = arrayOf(
                    intArrayOf(-android.R.attr.state_checked),
                    intArrayOf(android.R.attr.state_checked)
                )
                colors = arrayOf(
                    context.getColor(R.color.white),
                    context.getColor(R.color.mild_pitchShadow)
                ).toIntArray()
                switch.thumbTintList = ColorStateList(states, colors)

                if (container.switchUseCheckedListenerOnStart) container.switchOnCheckedListener(switch, switchWarning, container.switchStartStateGetter(switch))
                switch.isChecked = container.switchStartStateGetter(switch)
                switch.setOnCheckedChangeListener { buttonView, isChecked ->
//                    slU.s("state changed")
                    buttonView.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE)
                    container.switchOnCheckedListener(buttonView, switchWarning, isChecked)
                }
            }

            //big switch
            0.1f -> {
                val switch = findViewById<MaterialSwitch>(R.id.settings_child_switch_m3).apply{
                    visibility = View.VISIBLE
                }

                var states = arrayOf(
                    intArrayOf(-android.R.attr.state_checked),
                    intArrayOf(android.R.attr.state_checked)
                )
                var colors = arrayOf(
                    context.getColor(R.color.mild_greyscaleLight),
                    context.getColor(R.color.mild_presenceRegular)
                ).toIntArray()
                switch.trackTintList = ColorStateList(states, colors)

                states = arrayOf(
                    intArrayOf(-android.R.attr.state_checked),
                    intArrayOf(android.R.attr.state_checked)
                )
                colors = arrayOf(
                    context.getColor(R.color.mild_neutral),
                    context.getColor(R.color.mild_pitchShadow)
                ).toIntArray()
                switch.thumbTintList = ColorStateList(states, colors)

                switch.isChecked = container.switchStartStateGetter(switch)
                switch.setOnCheckedChangeListener { buttonView, isChecked ->
                    buttonView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    container.switchOnCheckedListener(buttonView, switchWarning, isChecked)
                }
            }

            //start and stop testing
            0.2f -> {
                findViewById<ImageView>(R.id.settings_child_indicator).visibility = View.INVISIBLE

                val stopButton = findViewById<ImageView>(R.id.settings_child_stop).apply {
                    visibility = View.INVISIBLE
                    setOnClickListener {
//                        it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        container.testStopButtonCallback(it) }
                }
                val playButton = findViewById<ImageView>(R.id.settings_child_play).apply {
                    visibility = View.VISIBLE
                    setOnClickListener {
//                        it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        container.testPlayButtonCallback(it, stopButton) }
                }

                CoroutineScope(Dispatchers.Default).launch {
                    while (true) {
                        withContext(Dispatchers.Main) {
                            if (container.testStopButtonVisibilityChecker()) stopButton.visibility = View.VISIBLE
                            else stopButton.visibility = View.INVISIBLE
                        }

                        delay(800)
                    }
                }
            }

            //simple dropDown
            1.0f -> {
                findViewById<Group>(R.id.settings_child_group_two).visibility = View.VISIBLE

                val states = arrayOf(
                    intArrayOf(-android.R.attr.state_focused),
                    intArrayOf(android.R.attr.state_focused)
                )
                val colors = arrayOf(
//                                context.getColor(R.color.mild_greyscaleLight),
                    context.getColor(R.color.mild_presenceBright),
                    context.getColor(R.color.mild_pitchSub)
                ).toIntArray()
                val colorsTwo = arrayOf(
                    context.getColor(R.color.mild_presenceBright),
                    context.getColor(R.color.mild_pitchShadow)
                ).toIntArray()

                findViewById<TextInputLayout>(R.id.settings_child_dropdown_one).setEndIconTintList(
                    ColorStateList(states, colorsTwo)
                )

                val adapter = ArrayAdapter(
                    context,
                    R.layout.settings_dropdown_list_item,
                    container.dropDownItems
                )
                findViewById<AutoCompleteTextView>(R.id.settings_child_dropdown_one_item).apply {
                    backgroundTintList = ColorStateList(states, colors)

                    setText(container.dropDownStartText)
                    setAdapter(adapter)

                    setOnItemClickListener { parent, view, position, id ->
                        view.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE)
                        container.dropDownChangedListener(
                            parent.adapter.getItem(
                                position
                            ) as String
                        )
                    }
                    setOnClickListener {
                        imm.hideSoftInputFromWindow(it.windowToken, 0)
                    }
                }


            }

            //simple editText
            1.1f -> {
                findViewById<Group>(R.id.settings_child_group_three).visibility =
                    View.VISIBLE

                var states = arrayOf(
                    intArrayOf(-android.R.attr.state_focused),
                    intArrayOf(android.R.attr.state_focused)
                )
                var colors = arrayOf(
                    context.getColor(R.color.mild_presenceBright),
                    context.getColor(R.color.mild_pitchShadow)
                ).toIntArray()

                findViewById<TextInputEditText>(R.id.settings_child_editText_item_one).apply {
                    backgroundTintList = ColorStateList(states, colors)
                    highlightColor = context.getColor(R.color.mild_presenceSoft)
                    filters = arrayOf(InputFilter.LengthFilter(container.editTextMaxLength))

                    setText(container.editTextStartValue)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        paintSelectors(context.getColor(R.color.mild_neutral))
                    }
                }

                val switch =
                    findViewById<SwitchMaterial>(R.id.settings_child_switch_editText).apply {
                        if (container.subSwitchVisible) visibility =
                            View.VISIBLE
                        isChecked = container.switchStartStateGetter(this)
                    }

                val editText =
                    findViewById<TextInputLayout>(R.id.settings_child_editText_one).apply {
                        setBoxStrokeColorStateList(ColorStateList(states, colors))
                        editText?.addTextChangedListener(object : TextWatcher {
                            override fun beforeTextChanged(
                                s: CharSequence?,
                                start: Int,
                                count: Int,
                                after: Int
                            ) = Unit

                            override fun onTextChanged(
                                s: CharSequence?,
                                start: Int,
                                before: Int,
                                count: Int
                            ) {
                                container.editTextOnChangedListener(s)
                            }

                            override fun afterTextChanged(s: Editable?) = Unit
                        })
                        editText?.setOnEditorActionListener { v, actionId, event ->
                            if (actionId == 5) imm.hideSoftInputFromWindow(v.windowToken, 0)
                            true
                        }
                        if (container.subSwitchVisible) isEnabled =
                            container.switchStartStateGetter(switch)

                        if (container.showHint){
                            val lp = layoutParams as ConstraintLayout.LayoutParams
                            lp.startToEnd = R.id.settings_child_hint
                            layoutParams = lp
                        }
                    }
                findViewById<TextView>(R.id.settings_child_editText_unit).text = container.unitText

                switch.setOnCheckedChangeListener { buttonView, isChecked ->
                    container.switchOnCheckedListener(
                        buttonView,
                        switchWarning,
                        isChecked,
                    )
                    buttonView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    editText.isEnabled = isChecked
                }

                states = arrayOf(
                    intArrayOf(-android.R.attr.state_checked),
                    intArrayOf(android.R.attr.state_checked)
                )
                colors = arrayOf(
                    context.getColor(R.color.mild_greyscaleLight),
                    context.getColor(R.color.mild_presenceRegular)
                ).toIntArray()
                switch.trackTintList = ColorStateList(states, colors)

                states = arrayOf(
                    intArrayOf(-android.R.attr.state_checked),
                    intArrayOf(android.R.attr.state_checked)
                )
                colors = arrayOf(
                    context.getColor(R.color.white),
                    context.getColor(R.color.mild_pitchShadow)
                ).toIntArray()
                switch.thumbTintList = ColorStateList(states, colors)
            }

            //dropDown and editText
            //INCOMPLETE!!
            1.2f -> {
                findViewById<Group>(R.id.settings_child_group_four).visibility =
                    View.VISIBLE

                val states = arrayOf(
                    intArrayOf(-android.R.attr.state_focused),
                    intArrayOf(android.R.attr.state_focused)
                )
                val colors = arrayOf(
                    context.getColor(R.color.mild_presenceBright),
                    context.getColor(R.color.mild_pitchSub)
                ).toIntArray()
                val colorsTwo = arrayOf(
                    context.getColor(R.color.mild_presenceBright),
                    context.getColor(R.color.mild_pitchShadow)
                ).toIntArray()


                findViewById<TextInputLayout>(R.id.settings_child_dropdown_two).setEndIconTintList(ColorStateList(states, colorsTwo))
                findViewById<TextInputLayout>(R.id.settings_child_editText_two).setBoxStrokeColorStateList(
                    ColorStateList(states, colorsTwo)
                )

                //This variable needed if we want dropdown to be collapsed on repeated click
                var editTextTravelled = false
                val editText =
                    findViewById<TextInputEditText>(R.id.settings_child_editText_item_two).apply {
                        backgroundTintList = ColorStateList(states, colors)
                        highlightColor = context.getColor(R.color.mild_presenceSoft)

                        setText(container.editTextStartValue)
                        setOnFocusChangeListener { v, hasFocus -> editTextTravelled = true }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            paintSelectors(context.getColor(R.color.mild_neutral))
                        }
                    }

                val adapter = ArrayAdapter(
                    context,
                    R.layout.settings_dropdown_list_item,
                    container.dropDownItems
                )
                with(findViewById<AutoCompleteTextView>(R.id.settings_child_dropdown_two_item)) {
                    backgroundTintList = ColorStateList(states, colors)

                    setText(adapter.getItem(0))
                    setAdapter(adapter)

                    /*
                    The key thing is that we can't painfully hide keyboard
                    after we take focus from EditText, because the focus is gonna be
                    taken back (for no reason to me). So, manually forcing focus to stay at Dropdown
                    is the case, but only with delays included
                     */
                    setOnClickListener {
                        sl.i("dropdown click")

                        CoroutineScope(Dispatchers.Default).launch {
                            val mainDelay = 300L

                            delay(mainDelay)
                            imm.hideSoftInputFromWindow(it.windowToken, 0)

                            delay((mainDelay * 1.6).toLong())
                            withContext(CoroutineScope(Dispatchers.Main).coroutineContext) {
                                editText.clearFocus()
                                if (!isPopupShowing && editTextTravelled) {
                                    requestFocus()
                                    showDropDown()
                                    editTextTravelled = false
                                }
                            }
                        }
                    }
                }
            }

            //slider
            2f -> {
                findViewById<Group>(R.id.settings_child_group_five).visibility =
                    View.VISIBLE

                var states = arrayOf(
                    intArrayOf(-android.R.attr.state_enabled),
                    intArrayOf(android.R.attr.state_enabled)
                )
                var colors = arrayOf(
                    context.getColor(R.color.mild_greyscaleLight),
                    context.getColor(R.color.mild_pitchShadow)
                ).toIntArray()
                val colorsTwo = arrayOf(
                    context.getColor(R.color.mild_greyscaleLight),
                    context.getColor(R.color.mild_presenceSoft)
                ).toIntArray()
                val colorsThree = arrayOf(
                    context.getColor(R.color.mild_greyscaleLight),
                    context.getColor(R.color.mild_pitchSub)
                ).toIntArray()
                val colorsFour = arrayOf(
                    context.getColor(R.color.mild_greyscaleLight),
                    context.getColor(R.color.mild_pitchRegular)
                ).toIntArray()
                val colorsFive = arrayOf(
                    context.getColor(R.color.mild_greyscaleLight),
                    context.getColor(R.color.mild_greyscalePresence)
                ).toIntArray()

                val slider = findViewById<Slider>(R.id.settings_child_slider).apply {
                    valueFrom = container.seekbarMinValue
                    valueTo = container.seekbarMaxValue
                    value = container.seekbarStartValue
                    if (container.seekbarStep > 0)
                        stepSize = container.seekbarStep

                    thumbTintList = ColorStateList(states, colors)
                    trackInactiveTintList = ColorStateList(states, colorsTwo)
                    trackActiveTintList = ColorStateList(states, colorsThree)
                    tickInactiveTintList = ColorStateList(states, colorsFour)
                    tickActiveTintList = ColorStateList(states, colorsFive)

                    addOnSliderTouchListener(object: Slider.OnSliderTouchListener{
                        override fun onStartTrackingTouch(slider: Slider) {}

                        override fun onStopTrackingTouch(slider: Slider) {
                            container.seekbarOnReleasedListener(slider.value)
                        }

                    })
                }
                findViewById<TextView>(R.id.settings_child_slider_label).apply {
                    val spannable = SpannableStringBuilder()
                    val valueText = if (container.seekbarFloatValues) container.seekbarStartValue.toString() else container.seekbarStartValue.toInt().toString()

                    val setSpannableText: (String)->Unit = {
                        spannable.clear()
                        spannable.clearSpans()

                        spannable.append(it)
                        spannable.append(" ")
                        spannable.append(container.seekbarUnitText)

                        spannable.setSpan(
                            RelativeSizeSpan(1.3f),
                            0,
                            it.length,
                            Spanned.SPAN_INCLUSIVE_EXCLUSIVE
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                            spannable.setSpan(
                                TypefaceSpan(context.resources.getFont(R.font.montserrat_light_italic)),
                                it.length + 1,
                                it.length + 1 + container.seekbarUnitText.length,
                                Spanned.SPAN_INCLUSIVE_INCLUSIVE
                            )

                        text = spannable
                    }

                    setSpannableText(valueText)

                    slider.addOnChangeListener { view, value, fromUser ->
                        view.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE)
                        setSpannableText(if (container.seekbarFloatValues) value.toString().substring(0 .. 2) else value.toInt().toString())
                    }
                }

                val switch = findViewById<SwitchMaterial>(R.id.settings_child_switch_slider)
                if (container.subSwitchVisible) switch.visibility =
                    View.VISIBLE

                states = arrayOf(
                    intArrayOf(-android.R.attr.state_checked),
                    intArrayOf(android.R.attr.state_checked)
                )
                colors = arrayOf(
                    context.getColor(R.color.mild_greyscaleLight),
                    context.getColor(R.color.mild_presenceRegular)
                ).toIntArray()
                switch.trackTintList = ColorStateList(states, colors)

                states = arrayOf(
                    intArrayOf(-android.R.attr.state_checked),
                    intArrayOf(android.R.attr.state_checked)
                )
                colors = arrayOf(
                    context.getColor(R.color.white),
                    context.getColor(R.color.mild_pitchShadow)
                ).toIntArray()
                switch.thumbTintList = ColorStateList(states, colors)
            }

            else -> View(context)
        }
    }



    private inner class BuildableExpandableListAdapter : BaseExpandableListAdapter() {

        override fun hasStableIds(): Boolean = true

        override fun getGroupView(
            groupPosition: Int,
            isExpanded: Boolean,
            convertView: View?,
            parent: ViewGroup?,
        ): View {
            val container = groupContainers[groupPosition]

            val view = if (convertView == null) {
                val layoutInflater =
                    context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
                val view = layoutInflater.inflate(R.layout.settings_group_item, null).apply {
                    findViewById<ImageView>(R.id.settings_group_icon).setImageDrawable(container.titleDrawable)

                    val first = container.titleText.substringBefore("\n")
                    val sec = container.titleText.substringAfter("\n", "")
                    findViewById<TextView>(R.id.settings_group_title_one).text = first
                    findViewById<TextView>(R.id.settings_group_title_two).text = sec
                }

                view
            } else convertView

            val indicator = view.findViewById<ImageView>(R.id.settings_group_indicator)
            if (isExpanded) indicator.rotation = 180f
            else indicator.rotation = 0f

            return view
        }

        //Realtime inflation is not our way to accomplish smooth group expanding
        override fun getChildView(
            groupPosition: Int,
            childPosition: Int,
            isLastChild: Boolean,
            convertView: View?,
            parent: ViewGroup?,
        ): View {
            childContainersStorage[getGroup(groupPosition)]!![childPosition].apply {
                this.groupPosition = groupPosition
                this.childPosition = childPosition
            }

            return getChild(groupPosition, childPosition) as View
        }
        override fun getGroupCount(): Int = groupContainers.size

        override fun getChildrenCount(groupPosition: Int): Int =
            childViewsStorage[getGroup(groupPosition)]!!.size

        override fun getGroup(groupPosition: Int): Any = groupContainers[groupPosition]

        override fun getChild(groupPosition: Int, childPosition: Int): Any =
            childViewsStorage[getGroup(groupPosition)]!![childPosition]

        override fun getGroupId(groupPosition: Int): Long = groupPosition.toLong()

        override fun getChildId(groupPosition: Int, childPosition: Int): Long =
            childPosition.toLong()

        override fun isChildSelectable(groupPosition: Int, childPosition: Int): Boolean = true
    }
}