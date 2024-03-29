package com.foxstoncold.youralarm.alarmsUI.recyclerView;

import android.util.SparseArray;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView;

import com.foxstoncold.youralarm.alarmsUI.AideCallback;
import com.foxstoncold.youralarm.alarmsUI.MeasurementsAide;
import com.foxstoncold.youralarm.alarmsUI.ErrorHandlerImpl;
import com.foxstoncold.youralarm.alarmsUI.RLMCallback;
import com.foxstoncold.youralarm.SplitLoggerUI;
import com.foxstoncold.youralarm.alarmsUI.RLMReturnData;

import org.jetbrains.annotations.NotNull;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;


public class RowLayoutManager extends RecyclerView.LayoutManager implements RLMCallback {
    private final ErrorHandlerImpl er;
    private final AideCallback aideCallback;
    private MeasurementsAide aide;
    private RecyclerView.Recycler recycler;
    private int[] savedState = new int[] {-1,0,0};
    private boolean initialPassed = false;
    private final RowLayoutManagerAnimator animator;
    private int mVisibleRows;//Значение отрисованных строк всгда на 1 больше
    private int mExtendedVisibleRows;//Сама строка настроек в счёт не входит
    //Available rows of items including addAlarm, but not pref
    private int mAvailableRows;
    private int mAnchorRowPos;//У первой строки всегда как минимум виден нижний отступ
    private int mLastVisibleRow;

    /*
    Bounds represent edge positions of edge views in the layout.
    So, when we scroll and reach their borders, our scroll handler could define
    that it's a time for layout, and tell it's proxies where to layout
     */
    private int mBottomBound;
    /**
     * When topOffset starts counting from topPadding, topBound starts from zero
     */
    private int mTopBound;

    /*
    Baselines represent current position of the scroll,
    so only scrolling methods can rewrite them
     */
    private int mBottomBaseline;
    private int mTopBaseline;
    private int savedOffsetPosition;

    private final short DIR_DOWN = 0;
    private final short DIR_UP = 1;
    private final short DIR_BOTH = 2;

    private int FLAG_NOTIFY;
    private static final int NOTIFY_NONE = 0;
    public static final int LAYOUT_PREF = 1;
    public static final int HIDE_PREF = 2;
    public static final int HIDE_N_LAYOUT_PREF = 3;
    //Called when items count is changed
    public static final int UPDATE_DATASET = 4;
    //Just to update one invisible parent
    public static final int UPDATE_PARENT = 5;

    private boolean prefVisibility = false;
    private View prefView;
    private int prefParentPos = 666;
    private int oldPrefParentPos;
    private int oldPrefRowPos;
    private int prefRowPos;
    private int prefPos;
    private RecyclerView.ViewHolder savedPrefVH;
    private boolean prefScrapped = false;//Переменная означает, что настройки отскрапаны, но требуют выкладки при скролле

    public boolean getPrefVisibility(){
        return prefVisibility;
    }
    public int getPrefParentPos(){ return prefParentPos; }

    public int getPrefRowPos(){
        return prefRowPos;
    }

    private final SparseArray<View> mViewCache = new SparseArray<>();

    private final int SCROLL_MODE = 3;
    /*
     Interim values are set TRUE if number of rows was changed (like btw mVisible and mExtendedVisible) at the last scroll pass,
     and there's a need to either hide pref row or bring back rows count to normal at the next pass
     */
    private boolean STSBottom = false;
    private boolean STSTop = false;

    private static SplitLoggerUI slU;
    private boolean busy = false;
    @Override
    public boolean isBusy() {
        return busy;
    }

    @Override
    public boolean isPrefPresented() { return prefVisibility; }

    private void setBusy(boolean busy) {
        this.busy = busy;
    }


    public RowLayoutManager(@NonNull AideCallback cb, ErrorHandlerImpl er){
        super();
        SplitLoggerUI.en();
        this.er = er;
        this.aideCallback = cb;
        aide = aideCallback.getMeasurements();
        animator = new RowLayoutManagerAnimator();
    }

    public RecyclerView.ItemAnimator getItemAnimator(){
        return animator;
    }

    @Override
    public RecyclerView.LayoutParams generateDefaultLayoutParams() {
        return new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT);
    }

    @Override
    public void onLayoutChildren (RecyclerView.Recycler recycler, RecyclerView.State state) {
        this.recycler = recycler;
        if (FLAG_NOTIFY!=UPDATE_PARENT) SplitLoggerUI.i("Layout time");
        if (aide==null){
            aideCallback.createMeasurements(recycler, this);
            aide = aideCallback.getMeasurements();
        }
        if (initialPassed) setBusy(true);


        if (getChildCount()==0 && 0 != state.getItemCount()) {
            mAvailableRows = getItemCount() / 3; if (getItemCount() % 3 !=0) mAvailableRows++;
            //Рассчитать максимальное количество строк, основываясь на высоте RV
            mVisibleRows = getHeight() / aide.getDecoratedTimeHeight() + 1;
            mExtendedVisibleRows = (getHeight() - aide.getMeasuredPrefHeight() + aide.getHorizontalPadding()) / aide.getDecoratedTimeHeight() + 1;

            slU.f( "Visible rows: " + mVisibleRows + " (Extended: " + mExtendedVisibleRows + "), Available: " + mAvailableRows);
            //Asserting values depending only on RV's size
            assert (mVisibleRows>1 || mExtendedVisibleRows>1) : "RV's size is too small, cannot contain enough rows";
        }

        if (0 != state.getItemCount()){ //Выкладывать, если есть что выкладывать
            try {
                fillRows (recycler, state);
            }catch (Exception e){ er.transmitError(e); }

        }
        else if (getItemCount()==0) removeAndRecycleAllViews(recycler);//Если адаптер пустой, то очищаем разметку

    }

    private void layoutStraightByRow(int rowPos){
        assert rowPos > 0 : "rows always start from the 1st";
        assert rowPos <= mAvailableRows : "it can't be more than presented";

        int baseline = (rowPos-1) * aide.getDecoratedTimeHeight();
        layoutStraight(baseline);
    }
    /**
     * This method takes actual (of saved) topBaseline and automatically detects a row which is
     * hypothetically would be on it's place if none of all above rows would contain pref
     * @param savedBaseline - starting offset value saved from previous layout
     */
    private void layoutStraight(int savedBaseline){
        int startRowPos = savedBaseline / aide.getDecoratedTimeHeight() + 1;
        int endRowPos = startRowPos + mVisibleRows;
        short topOverflow = ((endRowPos-mAvailableRows) > 0) ? (short) (endRowPos-mAvailableRows) : 0;
        startRowPos-=topOverflow;
        endRowPos-=topOverflow;
        if (startRowPos < 1) startRowPos = 1;
        //this offset can't be far away from startRow, because it still has to be visible
        //for us to tap on it and close pref
        int startOffset = savedBaseline - (startRowPos-1) * aide.getDecoratedTimeHeight();
        if (startOffset < 0) startOffset = 0;

        /*
        In every each layout method this value relatively counts from the start of RV
         */
        int topOffset = getRealPaddingTop() - startOffset;
        int leftOffset = aide.getHorizontalPadding();
        mTopBound = (startRowPos-1) * aide.getDecoratedTimeHeight();
        mTopBaseline = mTopBound + startOffset;
        mBottomBound = (endRowPos) * aide.getDecoratedTimeHeight() + getPaddingBottom() +
                ((startRowPos==1) ? getRealPaddingTop() : getPaddingTop());

        if (startRowPos > 1){
            mTopBound+=aide.getPrefTopIndent();
            mBottomBound+=aide.getPrefTopIndent();
        }
        if (endRowPos==mAvailableRows){
            mBottomBound -= aide.getVerticalPadding() - aide.getPrefBottomPadding();
        }

        int rowCount = startRowPos;
        int p = 1;
        for (int i = (startRowPos - 1) * 3; i < getItemCount() && rowCount <= endRowPos; i++) {
            slU.fst( "adding: " + i + ", row count: " + rowCount);

            View child = recycler.getViewForPosition(i);
            mViewCache.put(i, child);

            addView(child);
            measureChild(child, 0, 0);
            layoutDecorated(child, leftOffset, topOffset,
                    leftOffset + aide.getMeasuredTimeWidth(),
                    topOffset + aide.getDecoratedTimeHeight());

            if (p < 3) {//Выкладываем вдоль, добавляем отступ слева
                leftOffset += aide.getDecoratedTimeWidth();
                p++;
            }
            else {//Строка кончилась, делаем вертикальный отступ и сбрасываем счёт
                topOffset += aide.getDecoratedTimeHeight();
                leftOffset = aide.getHorizontalPadding();
                rowCount++;

                p = 1;
            }
        }

        mAnchorRowPos = startRowPos;
        mLastVisibleRow = endRowPos;

        if (mTopBaseline <= mTopBound){
            int offset = mTopBound - mTopBaseline + 1;
            slU.fst("shifting for: " + offset);

            offsetChildrenVertical(-offset);
            mTopBaseline += offset;
            mBottomBaseline += offset;
        }
    }

    /**
     * Use this method to define distribution of surrounding by sides of pref,
     * and lay them out, returning topOffset for further layout of pref and motherRow.
     * Also it calculates Bounds and topBaseline
     * */
    private int layoutSurroundingRows(){
        /*
        We assume that layout should contain at least one row besides mother's,
        and it should be laid out below pref. Others will be equally distributed to the top and bottom.
         */
        int motherRow = prefRowPos-1;

        /*
        ExtendedVisible value is always a one digit less than we prefer to layout,
        so that's why we only rely on that when counting rows around [mother]
        */
        //this amount is meant to be equally distributed around mother
        short noRemainder = (short) ((mExtendedVisibleRows) /2);
        //this one will be added below pref
        short remainder = (short) ((mExtendedVisibleRows) %2);

        /*
        Strict rules about edge row positions:
        - only valid numbers in range of amount of available rows
        - can be the same with motherRow if there's not enough to layout above or below
         */
        int startRowPos = motherRow - noRemainder;
        int endRowPos = motherRow + noRemainder + remainder;

        short botOverflow = ((startRowPos-1) < 0) ? (short) Math.abs(startRowPos-1) : 0;
        short topOverflow = ((endRowPos-mAvailableRows) > 0) ? (short) (endRowPos-mAvailableRows) : 0;
        startRowPos += botOverflow - topOverflow;
        endRowPos += botOverflow - topOverflow;

        startRowPos = Math.max(startRowPos, 1);
        endRowPos = Math.min(endRowPos, mAvailableRows);

        int leftOffset = aide.getHorizontalPadding();
        //All above rows should be hidden, and topOffset will be relative to the current scroll position
        //and be greater than zero
        int topOffset = getRealPaddingTop() - (motherRow - startRowPos) * aide.getDecoratedTimeHeight();
        if (mAvailableRows < 2){
            slU.fr("not laying out surrounding. Insufficient amount of rows");
            return topOffset;
        }
        /*
        Since topOffset is relative to scroll position, our bounds and baselines are bound to rows heights
         */
        mTopBound = (startRowPos-1) * aide.getDecoratedTimeHeight();
        mTopBaseline = (motherRow - 1) * aide.getDecoratedTimeHeight();
        //Pref can actually overlap with timeWindows, but Bounds should be placed on the edges
        mBottomBound = (endRowPos-1) * aide.getDecoratedTimeHeight() + aide.getPrefTopOffsetShift() + getPaddingBottom() +
                ((startRowPos==1 || motherRow==1) ? getRealPaddingTop() : getPaddingTop());

        //small alteration to make an end bottom padding shorter
        if (endRowPos==mAvailableRows && endRowPos!=motherRow){
            mBottomBound -= aide.getVerticalPadding() - aide.getPrefBottomPadding();
        }
        int rowCount = startRowPos;
        detachAndScrapAttachedViews(recycler);
        mViewCache.clear();

        int p = 1;
        for (int i = (startRowPos-1) * 3; i < getItemCount() && rowCount < motherRow; i++){
            slU.fst( "adding above mother's: " + i + ", row count: " + rowCount);
            View child = recycler.getViewForPosition(i);
            mViewCache.put(i, child);

            addView(child);
            measureChild(child, 0,0);
            layoutDecorated(child, leftOffset, topOffset,
                    leftOffset + aide.getMeasuredTimeWidth(),
                    topOffset + aide.getDecoratedTimeHeight());

            if (p < 3) {//Выкладываем вдоль, добавляем отступ слева
                leftOffset += aide.getDecoratedTimeWidth();
                p++;
            }
            else {//Строка кончилась, делаем вертикальный отступ и сбрасываем счёт
                topOffset += aide.getDecoratedTimeHeight();
                leftOffset = aide.getHorizontalPadding();
                rowCount++;
                //Добавляем к счёту и отступу уже добавленную строку

                p = 1;
            }
        }

        int returned = topOffset;
        topOffset += aide.getPrefTopOffsetShift();

        for (int i = (motherRow) * 3 + 1; i < getItemCount() && rowCount < endRowPos; i++){
            slU.fst( "adding below pref: " + i + ", row count: " + rowCount);
            View child = recycler.getViewForPosition(i);
            mViewCache.put(i, child);

            addView(child);
            measureChild(child, 0,0);
            layoutDecorated(child, leftOffset, topOffset,
                    leftOffset + aide.getMeasuredTimeWidth(),
                    topOffset + aide.getDecoratedTimeHeight());

            if (p < 3) {//Выкладываем вдоль, добавляем отступ слева
                leftOffset += aide.getDecoratedTimeWidth();
                p++;
            }
            else {//Строка кончилась, делаем вертикальный отступ и сбрасываем счёт
                topOffset += aide.getDecoratedTimeHeight();
                leftOffset = aide.getHorizontalPadding();
                rowCount++;
                //Добавляем к счёту и отступу уже добавленную строку

                p = 1;
            }
        }

        mAnchorRowPos = startRowPos;
        mLastVisibleRow = endRowPos;

        return returned;
    }

    private void layoutMotherRow(RecyclerView.Recycler recycler, int topOffset){
        int motherRowPos = prefRowPos-1;
        int leftOffset = aide.getHorizontalPadding((motherRowPos-1) * 3);

        int p = 1;
        for (int i = (motherRowPos-1) * 3; i < getItemCount()-1 && i < (motherRowPos-1) * 3 + 3; i++) {
            slU.fstp(i + ", row pos: " + motherRowPos);
            View child = recycler.getViewForPosition(i);
            mViewCache.put(i, child);

            addView(child);
            measureChild(child, 0,0);
            layoutDecorated(child, leftOffset, topOffset,
                    leftOffset + aide.getMeasuredTimeWidth(),
                    topOffset + aide.getMeasuredTimeHeight());

            if (p < 3) {//Выкладываем вдоль, добавляем отступ слева
                leftOffset += aide.getDecoratedTimeWidth(i);
                p++;
            }
            else {//Строка кончилась, делаем вертикальный отступ и сбрасываем счёт
                leftOffset = aide.getHorizontalPadding();
            }
        }
    }

    private void layoutPref(int topOffset) throws NullPointerException{
        int alternateTopOffset = topOffset - aide.getPrefTopIndent();
        int alternateLeftOffset = aide.getPrefLeftIndent();

        mViewCache.put(prefPos, prefView);//Вьюшку настроек тоже добавляем в кэш согласно её индексу
        addView(prefView);
        measureChild(prefView, 0, 0);
        layoutDecorated(prefView, alternateLeftOffset, alternateTopOffset,
                alternateLeftOffset + aide.getMeasuredPrefWidth(),
                alternateTopOffset + aide.getMeasuredPrefHeight());
    }

    /**
     * Using this to re-layout parent view in two different cases:
     * 1.
     */
    private void updateParent(){

        detachView(mViewCache.get(prefParentPos));
        recycler.recycleView(mViewCache.get(prefParentPos));

        int motherRowPos = prefRowPos-1;
        int leftOffset = aide.getHorizontalPadding((motherRowPos-1) * 3);
        int topOffset = getTopOffset(motherRowPos, false);

        int p = 1;
        for (int i = (motherRowPos-1) * 3; i < getItemCount()-1 && i < (motherRowPos-1) * 3 + 3; i++) {
            if (i==prefParentPos) {
                slU.fr("updating parent: " + i);
                View child = recycler.getViewForPosition(i);
                mViewCache.put(i, child);

                int envoyIndent = (int) (aide.getPrefTopIndent() * 0.8);

                addView(child);
                measureChild(child, 0, 0);
                layoutDecorated(child, leftOffset, topOffset,
                        leftOffset + aide.getMeasuredTimeWidth(),
                        topOffset + aide.getMeasuredTimeHeight());
            }

            if (p < 3) {//Выкладываем вдоль, добавляем отступ слева
                leftOffset += aide.getDecoratedTimeWidth(i);
                p++;
            }
            else {//Строка кончилась, делаем вертикальный отступ и сбрасываем счёт
                leftOffset = aide.getHorizontalPadding();
            }
        }
    }

    private int getRealPaddingTop(){ return getPaddingTop() + aide.getPrefTopIndent(); }

    private void fillRows (RecyclerView.Recycler recycler,
                           RecyclerView.State state){

        int topOffset;


        if (getChildCount() == 0 && FLAG_NOTIFY == NOTIFY_NONE){
            slU.fr( "Empty layout detected. Views to be laid out: " + state.getItemCount());
            setBusy(false);
            if (!initialPassed) initialPassed=true;

            //in case of hidden pref
            if (savedState[0] != -1) layoutStraight(savedState[0]);
            else layoutStraightByRow(1);

        }

        else if (FLAG_NOTIFY == NOTIFY_NONE){
            slU.fr( "False call");
            setBusy(false);
        }


        else if (FLAG_NOTIFY == LAYOUT_PREF){
            rearrangeChildren();
            prefView = recycler.getViewForPosition(prefPos);

            topOffset = layoutSurroundingRows();
            layoutPref(topOffset);
            layoutMotherRow(recycler, topOffset);
        }


        else if (FLAG_NOTIFY == HIDE_PREF | FLAG_NOTIFY == UPDATE_DATASET ) {

            animator.addItemDeleteChanges();

            if (prefVisibility) {
                slU.fr("Removing visible pref");
                prefVisibility = prefScrapped = STSTop = STSBottom = false;
                removeAndRecycleView(prefView, recycler);
            }

            detachAndScrapAttachedViews(recycler);
            mViewCache.clear();

            //Recalculating mAvailableRows
            if (FLAG_NOTIFY == UPDATE_DATASET ){ mAvailableRows = getItemCount() / 3; if (getItemCount() % 3 !=0) mAvailableRows++; }

            layoutStraight(mTopBaseline);

            recycler.clear();//старую кучу отходов нужно чистить, иначе адаптер не будет байндить новые вьюшки
        }

        /*
          The key here is just to scrap all presented views and lay them out again,
          figuring out new positions (if required) on the fly
         */
        else if (FLAG_NOTIFY == HIDE_N_LAYOUT_PREF){

            prefScrapped = STSTop = STSBottom = false;

            detachAndScrapAttachedViews(recycler);
            mViewCache.clear();
            prefView = recycler.getViewForPosition(prefPos);

            topOffset = layoutSurroundingRows();
            layoutPref(topOffset);
            layoutMotherRow(recycler, topOffset);
        }


        else if (FLAG_NOTIFY == UPDATE_PARENT){
            updateParent();
            setBusy(false);
        }

        FLAG_NOTIFY = NOTIFY_NONE;

        mBottomBaseline = getHeight() + mTopBaseline;//Базовую линию всегда считаем относительно топовой

        if (mBottomBaseline >= mBottomBound){
            int offset = mBottomBaseline - mBottomBound + 1;
            //if there's not enough space to shift by bottom, we shift top to zero
            if ((mTopBaseline - offset) < 0) offset = mTopBaseline;
            slU.fst("shifting for: " + offset);

            offsetChildrenVertical(offset);
            mTopBaseline -= offset;
            mBottomBaseline -= offset;
        }

        if (!initialPassed) initialPassed=true;

        saveRVState();

        int prp = (prefRowPos==0) ? -1 : prefRowPos;
        slU.f("Anchor row: " + mAnchorRowPos + " , top baseline: " + mTopBaseline + " , top bound: " + mTopBound +
                ", \n\tLast row: " + mLastVisibleRow + ", bottom baseline: " + mBottomBaseline + ", bottom bound: " + mBottomBound +
                ", \n\tAvailable rows: " + mAvailableRows + ", Pref row pos: " + prp);
    }


    @Override
    public void setNotifyUpdate(int flag) {
        FLAG_NOTIFY=flag;
    }

    @Override
    public boolean canScrollVertically() {//проверка всегда производится уже после выкладки
        return !busy;
    }

    /**
     * Supervisor method for scrolling process.
     * Here we allowing ot scroll, changing offset and starting execution method for laying out rows
     *
     * @param dy            distance to scroll in pixels. Y increases as scroll position
     *                      approaches the bottom.
     * @param recycler      Recycler to use for fetching potentially cached views for a
     *                      position
     * @param state         Transient state of RecyclerView
     */

    @Override
    public int scrollVerticallyBy (int dy,
                                   RecyclerView.Recycler recycler,
                                   RecyclerView.State state) {
        if (getChildCount() == 0) return 0;

        int delta;
        int offset = 0;

        if (busy) return offset;

        if (dy>0){//Сколлинг вверх, оффсет - вниз
            boolean bottomBoundReached = mBottomBaseline >= mBottomBound;

            if (!bottomBoundReached){
                delta = mBottomBound - mBottomBaseline;

                //Если дельта больше запрашиваемого скролла, то мы подтверждаем запрашиваемый оффсет и обновляем значение нижней базовой линии
                if (delta > dy) {
                    offset = dy;
                    mBottomBaseline += dy; mTopBaseline += dy;
                }
                //Если дельта меньше или равна скроллу и есть ещё как минимум строка для выкладывания,
                //то мы допускаем скролл на ряд, который будет выложен сейчас, и обновляем значение нижней границы,
                //однако скролл не будет больше дельты, которая считается в самом начале.
                //Мы обновляем координаты нижней границы и верхней (если за ней есть хоть ещё одна)
                else if (mLastVisibleRow < mAvailableRows && (SCROLL_MODE == 1 || SCROLL_MODE == 3))  {
                    //Но при выложенной строке настроек, её видимость определяется в другом методе, где мы дополнительно изменяем значение границ, влияя на дельту
                    offset = dy; mBottomBaseline += dy; mTopBaseline += dy;
                    try {
                        addNRecycle(recycler, DIR_DOWN);
                        slU.fr( "AddNRecycle DOWN, new pos: " + mAnchorRowPos + " " + mLastVisibleRow);
                    }catch (Exception e){ er.transmitError(e); }
                }
                //Если дельта меньше или равна оффсету и выкладывать уже нечего
                else {
                    offset = delta;
                    mBottomBaseline += delta; mTopBaseline += delta;
                }
            }

            offsetChildrenVertical(-offset);
        }
        if (dy<0){//Скроллинг вниз, оффсет - вверх
            boolean topBoundReached = mTopBaseline <= mTopBound;

            if (!topBoundReached){
                delta = mTopBound - mTopBaseline;//Дельта отрицательная

                if (delta < dy) {
                    offset = dy;
                    mBottomBaseline += dy; mTopBaseline += dy;
                }

                else if (mAnchorRowPos > 1 && (SCROLL_MODE == 2 || SCROLL_MODE == 3)) { //Меньше первой строки у нас нет
                    offset = dy; mBottomBaseline += dy; mTopBaseline += dy;

                    try {
                        addNRecycle(recycler, DIR_UP);
                    }catch (Exception e){ er.transmitError(e); }
                    slU.fr( "AddNRecycle UP, new pos: " + mAnchorRowPos + " " + mLastVisibleRow);
                }

                else if (mAnchorRowPos > 0) {
                    offset = delta;
                    mBottomBaseline += delta; mTopBaseline += delta;
                }

                else {
                    offset = delta;
                    mBottomBaseline += delta; mTopBaseline += delta;
                }
            }

            offsetChildrenVertical(-offset);
        }

        return offset;
    }

    /**
     * For proper recycling and rows layout,
     * we have to reassign previously laid out child view's indices for RV.
     * If pref is scrapped by scroll, it's still takes it's index, that's why we need
     * to use RCShift val
     */
    private void rearrangeChildren() {
        int count = getChildCount();
        int RCShift = (prefScrapped && mAnchorRowPos >= prefRowPos) ? 1 : 0;
        int v = (mAnchorRowPos - 1) * 3;
        slU.fstp("starting from: " + v);

        for (int i = RCShift; i < count + RCShift; i++) {//Для правельной переработки и добавления строк,
            //сначала нам нужно переназначить индексы дочерних вьюшек, которые уже выложены,
            //и заодно обновить кэш
            View view;
            view = mViewCache.get(v + i);//Нужно взять из кэша все выложенные вьюшки по одной, согласно их нормальным индексам, которые у них в адаптере
            detachView(view);
            attachView(view, i);
        }
    }

    /**
     * 1. The most complicated thing is when we have to deal with Shifted Transition State (STS).
     * This can only happen when we have laid out pref and scrolling requires this method (which is basically happens quite a lot):
     * a. Right after pref layout we have {@link RowLayoutManager#mExtendedVisibleRows} laid out;
     * b. Let's say, when recycling UP (by moving our finger down) we've reached the point when with the next
     * addNRecycle's run we have to recycle pref's mother row;
     * c. It means that now we have to bring back our rows count to the value of {@link RowLayoutManager#mVisibleRows} plus one
     * and save a clue that with the next run we have to eventually recycle pref
     * along with it's mother row (it we're gonna scroll in the same direction)
     * or bring back rows count to {@link RowLayoutManager#mExtendedVisibleRows} (if we're going backwards);
     */

    private void scrapFirstRow(boolean afterPref){

        int one = (afterPref) ? 1 : 0;
        for (int i = (mAnchorRowPos - 1) * 3 + one; i < mAnchorRowPos * 3 + one && i < getItemCount(); i++) {
            slU.fst( i + " scrapping, row: " + mAnchorRowPos);

            detachAndScrapViewAt(0, recycler);
            mViewCache.remove(i);
        }
    }

    private void scrapLastRow(boolean afterPref){

        int one = (afterPref) ? 1 : 0;
        for (int i = (mLastVisibleRow - 1) * 3 + one; i < mLastVisibleRow * 3 + one && i < getItemCount(); i++) {
            slU.fst( i + " scrapping, row: " + mLastVisibleRow);

            detachAndScrapViewAt(getChildCount() - 1, recycler);
            mViewCache.remove(i);
        }
    }

    private int getTopOffset(int rowPos, boolean afterPref) {
        //topBaseline is like our universal guide through all layout methods
        int baselineRowPos = mTopBaseline / aide.getDecoratedTimeHeight();
        int rowDif = rowPos - baselineRowPos;
        int startOffset = mTopBaseline - baselineRowPos * aide.getDecoratedTimeHeight() - rowDif * aide.getDecoratedTimeHeight();
        int alternate = (afterPref) ? aide.getPrefTopOffsetShift() - aide.getDecoratedTimeHeight() : 0;

        /*
        In every each layout method this value relatively counts from the start of RV
         */
        return getRealPaddingTop() - startOffset - aide.getDecoratedTimeHeight() + alternate;
    }

    /**
     * Default topOffset points to the start of a row to be laid out
     */
    private void layoutRow(int rowPos, boolean afterPref){

        int topOffset = getTopOffset(rowPos, afterPref);
        int leftOffset = aide.getHorizontalPadding();

        int one = (afterPref) ? 1 : 0;
        for (int i = (rowPos - 1) * 3 + one; i < rowPos * 3 + one && i < getItemCount(); i++) {
            slU.fst( i + " adding, row: " + rowPos);

            View view = recycler.getViewForPosition(i);
            addView(view);
            measureChild(view, 0, 0);
            layoutDecorated(view, leftOffset, topOffset,
                    leftOffset + aide.getMeasuredTimeWidth(),
                    topOffset + aide.getDecoratedTimeHeight());

            leftOffset += aide.getDecoratedTimeWidth();

            mViewCache.put(i, view);
        }

    }

    private void shift (short dir, int amount){
        switch (dir){
            case(DIR_DOWN):
                mLastVisibleRow += amount;
                mBottomBound += aide.getDecoratedTimeHeight() * amount;
            break;

            case(DIR_UP):
                mAnchorRowPos += amount;
                mTopBound += aide.getDecoratedTimeHeight() * amount;
            break;

            case(DIR_BOTH):
                shift(DIR_DOWN, amount);
                shift(DIR_UP, amount);
        }
    }

    /**
     * This method has to define proper layout for incoming rows shifting event.
     * After that it takes several highly-automated executive methods to scrap/layout rows
     * and shift global pointers responsible for scroll positioning.
     * Such conditions as Shift Transition States can be entered or exited,
     * but it assumed that none of external layout methods can intervene their workflow,
     * except of just resetting them (continued by further layout).
     *
     * @param recycler  attached recycler
     * @param direction direction, must be one of {@link RowLayoutManager#DIR_DOWN} or {@link RowLayoutManager#DIR_UP}
     */
    private void addNRecycle (RecyclerView.Recycler recycler, int direction){

        final int dif = mVisibleRows - mExtendedVisibleRows;
        int realDif;
        int motherRow = prefRowPos-1;

        rearrangeChildren();

        boolean flagAnchorReachedMother;
        boolean flagLastReachedMother;

        switch(direction){
            case (DIR_DOWN):

                flagAnchorReachedMother = mAnchorRowPos == motherRow;
                flagLastReachedMother = mLastVisibleRow+1 == motherRow;


                if(!prefVisibility){
                    slU.fr("D0. Simple scroll");

                    scrapFirstRow(false);
                    layoutRow(mLastVisibleRow+1, false);

                    if (mAnchorRowPos==1) mTopBound += aide.getPrefTopIndent();
                    if (mLastVisibleRow+1 == mAvailableRows) mBottomBound -= aide.getVerticalPadding() - aide.getPrefBottomPadding();

                    shift(DIR_BOTH, 1);

                    break;
                }

                //If we already passed transition, but pref is still [visible],
                //we have to be aware of it's index in view cache
                if (
                        !flagAnchorReachedMother &&
                        !flagLastReachedMother &&
                        !STSBottom &&
                        !STSTop){
                    slU.fr("D1. Simple scroll with pref presented");

                    scrapFirstRow(mAnchorRowPos > motherRow);
                    layoutRow(mLastVisibleRow+1, mLastVisibleRow+1 > motherRow);

                    //in this two cases we're about to leave anchorRow and
                    //have to cut indent in topBound
                    if (mAnchorRowPos==1) mTopBound+= aide.getPrefTopIndent();
                    if (mLastVisibleRow+1 == mAvailableRows) mBottomBound -= aide.getVerticalPadding() - aide.getPrefBottomPadding();

                    shift(DIR_BOTH, 1);

                    break;
                }

                //In normal conditions we'd have to scrap motherRow
                if (!prefScrapped &&
                        flagAnchorReachedMother &&
                        !flagLastReachedMother &&
                        !STSBottom &&
                        !STSTop){
                    slU.fr("D2. Entering STS");
                    prefScrapped = false; STSBottom = true;

                    //We definitely need some special behavior when number of still hidden rows is less than we need
                    //(remember that mLastVisibleRow didn't shrink yet)
                    realDif = Math.min((mAvailableRows - mLastVisibleRow), dif);
                    //It's not actually a row pos, but just a row to set up iterator
                    int endRowPos = mLastVisibleRow + realDif;
                    SplitLoggerUI.fr("real dif is: " + realDif);

                    //It's safe because we take rows positions just to define views' numbers for layout
                    for (int rowPos = mLastVisibleRow+1; rowPos < endRowPos+1; rowPos++)
                        layoutRow(rowPos, true);

                    shift(DIR_DOWN, realDif);

                    if (mLastVisibleRow == mAvailableRows) mBottomBound -= aide.getVerticalPadding() - aide.getPrefBottomPadding();

                    break;
                }

                if (!prefScrapped &&
                        flagAnchorReachedMother &&
                        !flagLastReachedMother &&
                        STSBottom &&
                        !STSTop){
                    slU.fr("D3. Closing STS");
                    prefScrapped = true; STSBottom = false;

                    detachAndScrapView(prefView, recycler);
                    mViewCache.remove(prefPos);

                    scrapFirstRow(false);
                    layoutRow(mLastVisibleRow+1, true);

                    if (mAnchorRowPos==1) mTopBound+= aide.getPrefTopIndent();
                    if (mLastVisibleRow+1 == mAvailableRows) mBottomBound -= aide.getVerticalPadding() - aide.getPrefBottomPadding();

                    shift(DIR_BOTH, 1);
                    mTopBound += -aide.getDecoratedTimeHeight() + aide.getPrefTopOffsetShift();

                    break;
                }

                //Nothing more than restoring motherRow and pref
                if (prefScrapped &&
                        !flagAnchorReachedMother &&
                        flagLastReachedMother &&
                        !STSBottom &&
                        !STSTop){
                    slU.fr("D4. Restoring pref (entering opposite STS)");
                    prefScrapped = false; STSTop = true;

                    scrapFirstRow(false);

                    prefView = recycler.getViewForPosition(prefPos);
                    int topOffset = getTopOffset(motherRow, false);

                    layoutPref(topOffset);
                    layoutMotherRow(recycler, topOffset);

                    if (mAnchorRowPos==1) mTopBound+= aide.getPrefTopIndent();

                    shift(DIR_BOTH, 1);

                    mBottomBound += -aide.getDecoratedTimeHeight() + aide.getPrefTopOffsetShift();

                    break;
                }

                //Have to restore [dif] amount of rows (laying above) plus one of a casual shift process
                if (!prefScrapped &&
                        !flagAnchorReachedMother &&
                        !flagLastReachedMother &&
                        !STSBottom
                        ){
                    STSTop = false;
                    slU.fr("D5. Closing opposite STS");

                    int endRowPos = mLastVisibleRow+1;
                    int startRowPos = endRowPos - mExtendedVisibleRows;

                    for (int rowPos = mAnchorRowPos; rowPos < startRowPos; rowPos++){
                        scrapFirstRow(false);
                        //We have to iterate this pointer to sustain proper [scrap] cycling
                        mAnchorRowPos++;
                    }

                    layoutRow(mLastVisibleRow+1, true);

                    //Don't forget about indent in the very start of RV
                    mTopBound = (startRowPos-1) * aide.getDecoratedTimeHeight() + aide.getPrefTopIndent();
                    mBottomBound += aide.getDecoratedTimeHeight();

                    mLastVisibleRow = endRowPos;

                    if (mLastVisibleRow == mAvailableRows) mBottomBound -= aide.getVerticalPadding() - aide.getPrefBottomPadding();

                    break;
                }


            case (DIR_UP):

                flagLastReachedMother = mLastVisibleRow == motherRow;
                flagAnchorReachedMother = mAnchorRowPos-1 == motherRow;


                if(!prefVisibility) {
                    slU.fr("U0. Simple scroll");

                    scrapLastRow(false);
                    layoutRow(mAnchorRowPos-1, false);

                    shift(DIR_BOTH, -1);

                    if (mAnchorRowPos == 1) mTopBound-= aide.getPrefTopIndent();
                    if (mLastVisibleRow+1 == mAvailableRows) mBottomBound += aide.getVerticalPadding() - aide.getPrefBottomPadding();

                    break;
                }

                if (
                        !flagAnchorReachedMother &&
                        !flagLastReachedMother &&
                        !STSBottom &&
                        !STSTop
                ){
                    slU.fr("U1. Simple scroll with pref presented");

                    scrapLastRow(mLastVisibleRow > motherRow);
                    layoutRow(mAnchorRowPos-1, mAnchorRowPos-1 > motherRow);

                    shift(DIR_BOTH, -1);

                    if (mAnchorRowPos==1) mTopBound-= aide.getPrefTopIndent();
                    if (mLastVisibleRow+1 == mAvailableRows) mBottomBound += aide.getVerticalPadding() - aide.getPrefBottomPadding();

                    break;
                }

                if (!prefScrapped &&
                        flagLastReachedMother &&
                        !flagAnchorReachedMother &&
                        !STSBottom &&
                        !STSTop){
                    slU.fr("U2. Entering STS");
                    prefScrapped = false; STSTop = true;

                    //We definitely need some special behavior when number of still hidden rows is less than we need
                    //(remember that mAnchorRowPos didn't shrink yet)
                    realDif = (mAnchorRowPos-1 < dif) ? dif - (mAnchorRowPos-1) : dif;//Checking if there even enough rows to lay out
                    //It's not actually a row pos, because this value can be equals zero, when mAnchorRowPos don't
                    int startRowPos = (mAnchorRowPos - 1 - realDif);
                    SplitLoggerUI.fr("real dif is: " + realDif);


                    for (int rowPos = mAnchorRowPos-1; rowPos > startRowPos; rowPos--) layoutRow(rowPos, false);

                    shift(DIR_UP, -realDif);

                    if (mAnchorRowPos == 1) mTopBound-= aide.getPrefTopIndent();

                    break;
                }

                if (!prefScrapped &&
                        flagLastReachedMother &&
                        !flagAnchorReachedMother &&
                        !STSBottom
                        ){
                    slU.fr("U3. Closing STS");
                    prefScrapped = true; STSTop = false;

                    detachAndScrapView(prefView, recycler);
                    mViewCache.remove(prefPos);

                    //Condition as a second argument is not exactly as *afterPref* means,
                    //but if last row is actually mother, we still have to include the pref presence
                    //and rely on [childCount]
                    scrapLastRow(mLastVisibleRow==motherRow);
                    layoutRow(mAnchorRowPos-1, false);

                    shift(DIR_BOTH, -1);
                    mBottomBound -= -aide.getDecoratedTimeHeight() + aide.getPrefTopOffsetShift();

                    //Here we have no need to adjust bottomBound, because if pref on the last row,
                    //it's already fine
                    if (mAnchorRowPos == 1) mTopBound-= aide.getPrefTopIndent();

                    break;
                }

                if (prefScrapped &&
                        !flagLastReachedMother &&
                        flagAnchorReachedMother &&
                        !STSBottom &&
                        !STSTop){
                    slU.fr("U4. Restoring pref (entering opposite STS)");
                    prefScrapped = false; STSBottom = true;

                    scrapLastRow(true);

                    prefView = recycler.getViewForPosition(prefPos);
                    int topOffset = getTopOffset(motherRow, false);

                    layoutPref(topOffset);
                    layoutMotherRow(recycler, topOffset);

                    shift(DIR_BOTH, -1);
                    mTopBound-= -aide.getDecoratedTimeHeight() + aide.getPrefTopOffsetShift();

                    if (mAnchorRowPos == 1) mTopBound-= aide.getPrefTopIndent();
                    if (mLastVisibleRow+1 == mAvailableRows) mBottomBound += aide.getVerticalPadding() - aide.getPrefBottomPadding();

                    break;
                }

                if (!prefScrapped &&
                        !flagLastReachedMother &&
                        !flagAnchorReachedMother &&
                        STSBottom &&
                        !STSTop
                ){
                    STSBottom = false;
                    slU.fr("U5. Closing opposite STS");

                    int startRowPos = mAnchorRowPos-1;
                    int endRowPos = startRowPos + mExtendedVisibleRows;

                    for (int rowPos = mLastVisibleRow; rowPos > endRowPos; rowPos--){
                        scrapLastRow(true);
                        //We have to iterate this pointer to sustain proper [scrap] cycling
                        mLastVisibleRow--;
                    }

                    layoutRow(mAnchorRowPos-1, false);

                    //Don't forget about indent in the very start of RV
                    mTopBound -= aide.getDecoratedTimeHeight();
                    mBottomBound = (endRowPos-1) * aide.getDecoratedTimeHeight() + aide.getPrefTopOffsetShift() + getRealPaddingTop() + getPaddingBottom();

                    mAnchorRowPos = startRowPos;

                    //We don't need to adjust bottomBound, because it's already recalculated
                    //and endRowPos is always less than last existing
                    if (mAnchorRowPos == 1) mTopBound-= aide.getPrefTopIndent();

                    break;
                }
            }

            saveRVState();
    }

    @Override
    public void onItemsAdded (@NonNull RecyclerView recyclerView, int positionStart, int itemCount){
        slU.f( "Items added: " + positionStart + ", " + itemCount);
    }

    @Override
    public void onItemsUpdated(@NonNull RecyclerView recyclerView, int positionStart, int itemCount) {
        super.onItemsUpdated(recyclerView, positionStart, itemCount);
        slU.f( "Item updated: " + positionStart);
    }

    @Override
    public void onAdapterChanged(@Nullable RecyclerView.Adapter oldAdapter, @Nullable RecyclerView.Adapter newAdapter) {
        super.onAdapterChanged(oldAdapter, newAdapter);
        slU.f( "Adapter changed!");
    }

    @Override
    public void onScrollStateChanged (int state){
        saveRVState();
        if (state == RecyclerView.SCROLL_STATE_IDLE && mViewCache.size()!=0){//Чисто лог выводим
            slU.f("Row " + mAnchorRowPos + ", Top baseline: " + mTopBaseline + ", Top bound:" + mTopBound + ", Bottom baseline: " + mBottomBaseline + ", Bottom bound:" + mBottomBound + ", first cache: " + mViewCache.keyAt(0) + ", last cache: " + mViewCache.keyAt(mViewCache.size() - 1));
        }
    }

    @Override
    public void onItemsMoved(@NonNull RecyclerView recyclerView, int from, int to, int itemCount) {
        super.onItemsMoved(recyclerView, from, to, itemCount);
        slU.f( "Items Moved: " + from + " " + to + ", count: " + itemCount);
    }

    @Override
    public void onItemsRemoved(@NonNull RecyclerView recyclerView, int positionStart, int itemCount) {
        super.onItemsRemoved(recyclerView, positionStart, itemCount);
        slU.f( "Items removed: " + positionStart + ", " + itemCount);
    }

    @NonNull
    @Override
    public RLMReturnData defineBaseAction(int currentPrefParentPos, boolean changeTime) {
        slU.fp( "Old values. |" + "pref parent pos: " + this.prefParentPos + "|pref row pos: " + prefRowPos + "|pref pos: " + prefPos + "|pref visibility: " + prefVisibility + "|");
        setBusy(true);

        if (this.prefParentPos == 666 & !changeTime || this.prefParentPos == currentPrefParentPos & !prefVisibility || this.prefParentPos != currentPrefParentPos & !prefVisibility) {//Либо настройки ещё не выкладывались, либо матерниские позиции соответствуют и строки не видно
            slU.f( "LAYING OUT PREF");

            this.prefParentPos = currentPrefParentPos;
            //Если строка с материнским элементом не полная, либо элементов в раскладке всего не больше трёх, то добавляем одну строку к счётчику
            prefRowPos = ((currentPrefParentPos+1) / 3) + 1; if ((currentPrefParentPos+1) % 3 !=0 || (currentPrefParentPos+1) < 3) prefRowPos++;//Строка после материнской вьюшки. На которой будем выкладывать настройки
            prefPos = ((prefRowPos-1) * 3); if (prefPos>getItemCount()) prefPos = getItemCount();//Позиция вьюшки настроек в адаптере
            slU.f( "prefParentPos:" + this.prefParentPos + ", prefRowPos:" + prefRowPos + ", prefPos:" + prefPos);

            //We have to recycle the view that was previously taking place of pref just in case RV will want to reuse it and put in place of pref
            if (prefRowPos <= mAvailableRows) removeAndRecycleView(recycler.getViewForPosition(prefPos), recycler);

            FLAG_NOTIFY = LAYOUT_PREF;
            prefVisibility = true;

            animator.addPrefLayoutChanges();
            return new RLMReturnData(false, this.prefParentPos, prefPos);
        }
        else if (this.prefParentPos != 666 && this.prefParentPos == currentPrefParentPos && prefVisibility && !changeTime){//Настройки уже выкладывались, старая материнская позиция, строку видно
            FLAG_NOTIFY = HIDE_PREF;
            slU.f( "HIDING PREF");

            /* RV don't actually throws an order to animate pref's removal,
            so it's for us to launch an animation before pref's still in the Adapter,
            and only then tell the Handler to proceed
            */
            animator.addPrefHideChanges();

            /*
            It's crucial to make it re-bind previous pref view
            and clear built-in cache because notifications heading to adapter
            doesn't affect scrapped views, so some of them become corrupted
            */
            if (mViewCache.get(currentPrefParentPos)!=null) detachAndScrapView(mViewCache.get(currentPrefParentPos), recycler);
            recycler.clear();
        }
        else if (this.prefParentPos != 666 && this.prefParentPos != currentPrefParentPos && prefVisibility || changeTime){//Настройки уже выкладывались, новая материнская позиция, строку уже видно
            slU.f( "HIDING and LAYING OUT PREF");

            int oldPrefPos = this.prefPos;
            oldPrefRowPos = prefRowPos;
            oldPrefParentPos = this.prefParentPos;

            //Here, because global prefParentPos didn't change yet
            View parent = mViewCache.get(this.prefParentPos);
            try {
                //Sometimes ti can be scrapped
                if (parent!=null) detachAndScrapView(parent, recycler);
            } catch (Exception ignored) {}
            recycler.clear();

            this.prefParentPos = currentPrefParentPos;
            /*
            If new parentPos is greater than old prefPos, we have to decrease it
            keeping in mind that position of new pref will not affect
            indices of items below new parent.
            But only if Handler didn't figure it out yet
            */
            if (!changeTime && (currentPrefParentPos >= oldPrefPos)) this.prefParentPos--;

            prefRowPos = ((this.prefParentPos+1) / 3) + 1;//Строка после материнской вьюшки. Имея в виду плюс один элемент в адаптере, добавляем один
            //Если строка с материнским элементом не полная, либо элементов в раскладке всего не больше трёх, то добавляем одну строку к счётчику
            if ((this.prefParentPos+1) % 3 !=0 || (this.prefParentPos+1) < 3) prefRowPos++;

            prefPos = ((prefRowPos-1) * 3); if (prefPos>=getItemCount()) prefPos = getItemCount() - 1;//Позиция вьюшки настроек в адаптере
            slU.f( "prefParentPos:" + this.prefParentPos + " prefRowPos:" + prefRowPos + " prefPos:" + prefPos);

            //We have to recycle this one just in case RV will want to reuse it and put in place of pref
            if (prefRowPos <= mAvailableRows) removeAndRecycleView(recycler.getViewForPosition(prefPos), recycler);


            FLAG_NOTIFY = HIDE_N_LAYOUT_PREF;
            prefVisibility = true;

            oldPrefParentPos += (oldPrefRowPos > prefRowPos) ? 1 : 0;
            if (!changeTime) animator.addPrefHideLayoutChanges();
            else animator.addTimeChangeChanges();

            return new RLMReturnData(true, prefParentPos, prefPos);
        }
        return new RLMReturnData();
    }

    //Externally called method to set values in a decided moment
    @Override
    public void setRVState(@NotNull int[] state) {
        slU.frp(state);
        savedState = state;
    }

    //Internal method to save complex state when any action is over
    private void saveRVState() {
        int[] s = new int[3];
        s[0] = mTopBaseline;
        //Effective pref visibility
        s[1] = (prefVisibility && !prefScrapped && !STSTop && !STSBottom) ? 1 : 0;
        s[2] = prefParentPos;

        aideCallback.saveRVState(s);
    }

    @Override
    public int manipulatePrefPowerState(boolean enabled) {
        if (!prefVisibility){
            slU.s("Cannot manipulate pref since it's not presented");
            return -1;
        }

        ChildViewHolder holder = (ChildViewHolder) aideCallback.getItemViewHolder(prefPos);
        if (holder==null){
            slU.w("CVH is not found");
            return -1;
        }
        holder.animatePowerChange(enabled);
        return prefParentPos;
    }

    /**
     * FUCKING RECYCLERVIEW DOES EVERYTHING IT WANTS FOR IT'S OWN REASONS.
     * indicates of starts and ends of different animation groups due to weather on the Moon;
     * sometimes even gives me no access to the ViewHolder after animation is ended;
     * leaving duplicate views sometimes
     */
    // TODO: 9/17/23 make your own fucking recyclerView with transparent work process
    private class RowLayoutManagerAnimator extends HomieDefaultItemAnimator {
        private final boolean LOG_CALLS = false;
        private LinkedList<ChangesExecutor> factory;
        private ChildViewHolder interceptedVH1;
        private List<ChildViewHolder> interceptedVHList1;

        private int FLAG_ANIMATE;
        public static final int TIME_CHANGE_ANIMATION = 885;

        private final long defaultRemoveDuration = getRemoveDuration();
        private final long defaultMoveDuration = getMoveDuration();
        private final long defaultAddDuration = getAddDuration();
        private final long defaultChangeDuration = getChangeDuration();


        /*
        Default animator sequence:
            remove (unused) ->
            move (entire parent row and the rest) ->
            add (pref)
         */
        public void addPrefLayoutChanges(){
            factory = new LinkedList<>();
            FLAG_ANIMATE = LAYOUT_PREF;

            int newAddDuration = 400;
            int newMoveDuration = 300;
            int fadeDuration = (int) (newMoveDuration * 0.9f);


            setMoveDuration(newMoveDuration);
            //whole Add group is only pref
            setAddDuration(newAddDuration);

            AtomicBoolean fadingStarted = new AtomicBoolean(false);
            //parent's fading during Move
            factory.add(new ChangesExecutor(ChangesExecutor.FLAG_MOVE, false, prefParentPos, (vh) ->{
                ((ChildViewHolder) vh).startTimeWindowAlphaAnimation(fadeDuration, 0f);
                fadingStarted.set(true);
            }));
            factory.add(new ChangesExecutor(ChangesExecutor.FLAG_MOVE, true, (vh) ->{
                if (!fadingStarted.get()) ((ChildViewHolder) vh).startTimeWindowAlphaAnimation(fadeDuration, 0f);
                fadingStarted.set(true);
            }));
            factory.add(new ChangesExecutor(ChangesExecutor.FLAG_ADD, true, (vh) ->{
                ChildViewHolder viewHold = (ChildViewHolder) aideCallback.getItemViewHolder(prefParentPos);
                if (viewHold != null){
                    viewHold.setTimeWindowVisibility(0f);
                    viewHold.requestParentUpdate();
                }
            }));

            slU.fr("a.b.c. are set up for ^layoutPref^");
        }

        /*
        Default animator sequence:
            {missing ^remove^ call for pref, like it didn't exist in the first place}
            move (the rest)
            add (difference and pref's replacement)
        */
        public void addPrefHideChanges(){
            factory = new LinkedList<>();
            interceptedVH1=null;

            FLAG_ANIMATE = HIDE_PREF;

            //Add duration for parent view. Can't be longer than Move
            int prefAppDuration = 300;
            setAddDuration(prefAppDuration);
            setMoveDuration(prefAppDuration);

            //launching pref's fading before new dataset is passed
            ChildViewHolder prefView = (ChildViewHolder) aideCallback.getItemViewHolder(prefPos);
            if (prefView != null) prefView.hidePrefNLaunchHandler(150);

            AtomicBoolean alphaChanged = new AtomicBoolean(false);

            //starting ex-parent's Add animation along with Move group
            factory.add(new ChangesExecutor(ChangesExecutor.FLAG_MOVE, false, vh->{
                if (interceptedVH1!=null) animateAddImpl(interceptedVH1);
                //making the rest's appearance instant
                setAddDuration(0);

                //In case of adding previously blanked add parent,
                //we need to pull it back to normal
                if (interceptedVH1!=null) interceptedVH1.setTimeWindowVisibility(1f);
                alphaChanged.set(true);
            }));
            factory.add(new ChangesExecutor(ChangesExecutor.FLAG_MOVE, true, vh->{
                if (!alphaChanged.get() && interceptedVH1!=null) interceptedVH1.setTimeWindowVisibility(1f);
                alphaChanged.set(true);
            }));

            slU.fr("a.b.c. are set up for ^hidePref^");
        }

        public void addPrefHideLayoutChanges(){
            factory = new LinkedList<>();
            interceptedVH1=null;

            FLAG_ANIMATE = HIDE_N_LAYOUT_PREF;

            setRemoveDuration(200);
            setMoveDuration(400);
            setAddDuration(getRemoveDuration());

            if (oldPrefRowPos == prefRowPos) {

                //new parent's fading during Move
                factory.add(new ChangesExecutor(ChangesExecutor.FLAG_MOVE, false, prefParentPos, (vh) ->
                        ((ChildViewHolder) vh).startTimeWindowAlphaAnimation(getAddDuration(), 0f)));

                //starting ex-parent's Add animation along with Move group
                factory.add(new ChangesExecutor(ChangesExecutor.FLAG_MOVE, false, vh -> {
                    if (interceptedVH1 != null) animateAddImpl(interceptedVH1);
                }));

                slU.fr("a.b.c. are set up for ^HLP^, same row");
            }

            else if (!prefScrapped) {
                factory.add(new ChangesExecutor(ChangesExecutor.FLAG_MOVE, false, vh -> {
                    ChildViewHolder newParentHolder = (ChildViewHolder) aideCallback.getItemViewHolder(prefParentPos);
                    if (newParentHolder!=null) newParentHolder.startTimeWindowAlphaAnimation(getMoveDuration(), 0f);

                    /*
                    This gets a little tricky since Animator will start Add animation
                    in the time when it supposed to start, even when we start it manually before
                    (which should 'expel' it from Animator's sequence, but it will not)
                    */
                    ChildViewHolder oldParentHolder = (ChildViewHolder) aideCallback.getItemViewHolder(oldPrefParentPos);

                    /*
                    It's apparently implicit, but animator won't get an order
                    to animate a view that's not visible on the screen,
                    so we can just null-check this parent holder for not to be caught
                    */
                    if (oldParentHolder != null) {

                        endAnimation(oldParentHolder);
                        oldParentHolder.setTimeWindowVisibility(0f);
                        oldParentHolder.startTimeWindowAlphaAnimation(getMoveDuration() + 100, 1f);
                    }
                    else slU.fst("a.b.c. missing ex-parent animation");
                }));

                slU.fr("a.b.c. are set up for ^HLP^, different row");
            }

            else addPrefLayoutChanges();
        }

        public void addTimeChangeChanges() {
            factory = new LinkedList<>();
            interceptedVHList1 = new LinkedList<>();

            FLAG_ANIMATE = TIME_CHANGE_ANIMATION;

            setRemoveDuration(400);
            setMoveDuration(600);
            setAddDuration(getRemoveDuration());

            factory.add(new ChangesExecutor(ChangesExecutor.FLAG_MOVE, false, prefParentPos, vh ->{
                ((ChildViewHolder) vh).startTimeWindowAlphaAnimation(getMoveDuration(), 0f);

                if (!interceptedVHList1.isEmpty()) for (ChildViewHolder holder : interceptedVHList1) animateAddImpl(holder);
            }));
        }

        public void addItemDeleteChanges(){
            factory = new LinkedList<>();
            interceptedVH1=null;

            factory.add(new ChangesExecutor(ChangesExecutor.FLAG_MOVE, true, vh ->{
                setBusy(false);
            }));
        }

        public void clearChanges(){
            factory = null;
            setRemoveDuration(defaultRemoveDuration);
            setMoveDuration(defaultMoveDuration);
            setAddDuration(defaultAddDuration);
            setChangeDuration(defaultChangeDuration);

            slU.fr("a.b.c. cleared");
        }


        @Override
        public void onRemoveStarting(RecyclerView.ViewHolder item) {
            ChangesExecutor.runThrough(factory, ChangesExecutor.FLAG_REMOVE, false, item);
            if (LOG_CALLS) slU.w("remove starting" + item.getAdapterPosition());
        }
        @Override
        public void onRemoveFinished(RecyclerView.ViewHolder item) {
            ChangesExecutor.runThrough(factory, ChangesExecutor.FLAG_REMOVE, true, item);
            if (LOG_CALLS) slU.s("remove finished" + item.getAdapterPosition());
        }

        @Override
        public void onMoveStarting(RecyclerView.ViewHolder item) {
            ChangesExecutor.runThrough(factory, ChangesExecutor.FLAG_MOVE, false, item);
            if (LOG_CALLS) slU.w("move starting" + item.getAdapterPosition());
        }
        @Override
        public void onMoveFinished(RecyclerView.ViewHolder item) {
            ChangesExecutor.runThrough(factory, ChangesExecutor.FLAG_MOVE, true, item);
            if (LOG_CALLS) slU.s("move finished" + item.getAdapterPosition());
        }

        @Override
        public void onAddStarting(RecyclerView.ViewHolder item) {
            if (LOG_CALLS) slU.w("add starting" + item.getAdapterPosition());
            ChangesExecutor.runThrough(factory, ChangesExecutor.FLAG_ADD, false, item);
        }
        @Override
        public void onAddFinished(RecyclerView.ViewHolder item) {
            if (LOG_CALLS) slU.s("add finished" + item.getAdapterPosition());
            ChangesExecutor.runThrough(factory, ChangesExecutor.FLAG_ADD, true, item);
        }

        @Override
        public void onChangeStarting(RecyclerView.ViewHolder item, boolean oldItem) {
            if (LOG_CALLS) slU.w("change starting" + item.getAdapterPosition());
            ChangesExecutor.runThrough(factory, ChangesExecutor.FLAG_CHANGE, false, item);
        }
        @Override
        public void onChangeFinished(RecyclerView.ViewHolder item, boolean oldItem) {
            if (LOG_CALLS) slU.s("change finished" + item.getAdapterPosition());
            ChangesExecutor.runThrough(factory, ChangesExecutor.FLAG_CHANGE, true, item);
        }

        @Override
        public boolean animateAdd(RecyclerView.ViewHolder holder) {
            if (holder!=null) {

                if ((FLAG_ANIMATE == HIDE_PREF && holder.getAdapterPosition() == prefParentPos) ||
                    (FLAG_ANIMATE == HIDE_N_LAYOUT_PREF && holder.getAdapterPosition() == oldPrefParentPos))
                {
                    interceptedVH1 = (ChildViewHolder) holder;
                }
                else if (FLAG_ANIMATE == TIME_CHANGE_ANIMATION && (holder.getAdapterPosition()!=prefParentPos && holder.getAdapterPosition()!=prefPos)){
                    interceptedVHList1.add((ChildViewHolder) holder);
                }

            }
            return super.animateAdd(holder);
        }

        //Effectively the last group in the entire sequence
        @Override
        public void onAddGroupFinished(RecyclerView.ViewHolder holder) {
            clearChanges();
            if (FLAG_ANIMATE==TIME_CHANGE_ANIMATION) ((ChildViewHolder) holder).requestParentUpdate();

            setBusy(false);
        }

        @Override
        public boolean canReuseUpdatedViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, @NonNull List<Object> payloads) {
            return true;
        }

    }


    /**
     * This class is built to provide convenience process of
     * setting up changes applicable to the various stages of
     * {@link DefaultItemAnimator}
     */
    private static class ChangesExecutor {
        public static final int FLAG_REMOVE = 11;
        public static final int FLAG_MOVE = 22;
        public static final int FLAG_ADD = 33;
        public static final int FLAG_CHANGE = 44;

        private final int flag;
        private final boolean onFinish;
        private final int targetPosition;
        private final ChangesCallback callback;

        private boolean firedAlready = false;

        ChangesExecutor(int flag, boolean onFinish, int targetPosition, ChangesCallback callback) {
            this.flag = flag;
            this.onFinish = onFinish;
            this.targetPosition = targetPosition;
            this.callback = callback;
        }

        //If position didn't mentioned, we just run it once
        ChangesExecutor(int flag, boolean onFinish, ChangesCallback callback) {
            this.flag = flag;
            this.onFinish = onFinish;
            this.targetPosition = -1;
            this.callback = callback;
        }


        public static void runThrough(List<ChangesExecutor> list, int animationType, boolean alliance, RecyclerView.ViewHolder vH){
            if (list==null) return;

            for (ChangesExecutor executor : list){
                if (executor.flag==animationType && executor.onFinish==alliance){
                    if (executor.targetPosition ==-1 && !executor.firedAlready){
                        executor.firedAlready = true;
                        executor.callback.changes(vH);
                    }
                    else if (vH.getAdapterPosition() == executor.targetPosition){
                        executor.callback.changes(vH);
                    }
                }
            }
        }

    }

    private interface ChangesCallback{
        void changes(RecyclerView.ViewHolder item);
    }

}
