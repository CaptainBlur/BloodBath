package com.vova9110.bloodbath.alarmsUI.recyclerView;

import android.util.SparseArray;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.vova9110.bloodbath.R;
import com.vova9110.bloodbath.alarmsUI.AdjustableImageView;
import com.vova9110.bloodbath.alarmsUI.AideCallback;
import com.vova9110.bloodbath.alarmsUI.MeasurementsAide;
import com.vova9110.bloodbath.alarmsUI.ErrorReceiver;
import com.vova9110.bloodbath.alarmsUI.RLMCallback;
import com.vova9110.bloodbath.SplitLoggerUI;
import com.vova9110.bloodbath.alarmsUI.RLMReturnData;


public class RowLayoutManager extends RecyclerView.LayoutManager implements RLMCallback {
    private final ErrorReceiver er;
    private RecyclerView.Recycler recycler;
    private int mVisibleRows;//Значение отрисованных строк всгда на 1 больше
    private int mExtendedVisibleRows;//Сама строка настроек в счёт не входит
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
    public static final int NOTIFY_NONE = 0;
    public static final int LAYOUT_PREF = 1;
    public static final int HIDE_PREF = 2;
    public static final int HIDE_N_LAYOUT_PREF = 3;
    public static final int UPDATE_DATASET = 4;

    private boolean prefVisibility = false;
    private View prefView;
    private int prefParentPos = 666;
    private int prefRowPos;
    private int prefPos;
    private boolean prefScrapped = false;//Переменная означает, что настройки отскрапаны, но требуют выкладки при скролле
    public int getPrefParentPos(){
        return prefParentPos;
    }
    public int getPrefRowPos(){
        return prefRowPos;
    }


    private final SparseArray<View> mViewCache = new SparseArray<>();

    private int SCROLL_MODE = 3;
    /*
     Interim values are set TRUE if number of rows was changed (like btw mVisible and mExtendedVisible) at the last scroll pass,
     and there's a need to either hide pref row or bring back rows count to normal at the next pass
     */
    private boolean STSBottom = false;
    private boolean STSTop = false;

    private SplitLoggerUI slU;

    private final AideCallback aideCallback;
    private MeasurementsAide aide;

    public RowLayoutManager(AideCallback cb, ErrorReceiver er){
        super();
        SplitLoggerUI.en();
        this.er = er;
        this.aideCallback = cb;
        aide = aideCallback.getMeasurements();
    }

    @Override
    public RecyclerView.LayoutParams generateDefaultLayoutParams() {
        return new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT);
    }

    @Override
    public void onLayoutChildren (RecyclerView.Recycler recycler, RecyclerView.State state) {
        this.recycler = recycler;
        SplitLoggerUI.i("Layout time");
        if (aide==null) aide = aideCallback.createMeasurements(recycler, this);


        if (getChildCount()==0 && 0 != state.getItemCount()) {
            //Рассчитать максимальное количество строк, основываясь на высоте RV
            mAvailableRows = getItemCount() / 3; if (getItemCount() % 3 !=0 || mAvailableRows < 3) mAvailableRows++;
            mVisibleRows = getHeight() / aide.getDecoratedTimeHeight() + 1;
            mExtendedVisibleRows = (getHeight() - aide.getMeasuredPrefHeight() + aide.getHorizontalPadding()) / aide.getDecoratedTimeHeight() + 1;

            slU.f( "Visible rows: " + mVisibleRows + " (Extended: " + mExtendedVisibleRows + "), Available: " + mAvailableRows);
            if (mVisibleRows<=1 || mExtendedVisibleRows<=1) throw new IllegalArgumentException("RV's size is too small, cannot contain enough rows");
        }

        if (0 != state.getItemCount()){ //Выкладывать, если есть что выкладывать
            try {
                fillRows (recycler, state);
            }catch (Exception e){ er.receiveError(e); }

        }
        else if (getItemCount()==0) removeAndRecycleAllViews(recycler);//Если адаптер пустой, то очищаем разметку

    }

    private void layoutStraightByRow(int rowPos){
        assert rowPos > 0 : "rows start from the 1st";
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
        //this offset can't be far away from startRow, because it still has to be visible
        //for us to tap on it and close pref
        int startOffset = savedBaseline - (startRowPos-1) * aide.getDecoratedTimeHeight();

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
     * Also it calculates Bounds
     * */
    private int layoutSurroundingRows(){
        /*
        We assume that layout should contain at least one row besides mother's,
        and it should be laid out below pref. Others will be equally distributed to the top and bottom.
         */
        int motherRow = prefRowPos-1;

        //ExtendedVisible value is always a one digit less than we prefer to layout,
        //so that's why we only rely on that when counting rows around [mother]
        short noRemainder = (short) ((mExtendedVisibleRows) /2);
        short remainder = (short) ((mExtendedVisibleRows) %2);

        int startRowPos = motherRow - noRemainder;
        int endRowPos = motherRow + noRemainder + remainder;

        short botOverflow = ((startRowPos-1) < 0) ? (short) Math.abs(startRowPos-1) : 0;
        short topOverflow = ((endRowPos-mAvailableRows) > 0) ? (short) (endRowPos-mAvailableRows) : 0;
        startRowPos += botOverflow - topOverflow;
        endRowPos += botOverflow - topOverflow;


        int leftOffset = aide.getHorizontalPadding();
        //All above rows should be hidden, and topOffset will be relative to the current scroll position
        int topOffset = getRealPaddingTop() - (noRemainder - botOverflow) * aide.getDecoratedTimeHeight();
        mTopBound = (startRowPos-1) * aide.getDecoratedTimeHeight();
        mTopBaseline = (motherRow-1) * aide.getDecoratedTimeHeight();
        //Pref can actually overlap with timeWindows, but Bounds should be placed on the edges
        mBottomBound = (endRowPos-1) * aide.getDecoratedTimeHeight() + aide.getPrefTopOffsetShift() + getPaddingBottom() +
                ((startRowPos==1 || motherRow==1) ? getRealPaddingTop() : getPaddingTop());

        if (startRowPos > 1){
            mTopBound+=aide.getPrefTopIndent();
            mBottomBound+=aide.getPrefTopIndent();
        }

        if (endRowPos==mAvailableRows && endRowPos!=motherRow){
            mBottomBound -= aide.getVerticalPadding() - aide.getPrefBottomPadding();
        }
        //In that condition we have small problems calculating proper offset
        if (endRowPos==motherRow) topOffset -= aide.getDecoratedTimeHeight();

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
        AdjustableImageView frame = prefView.findViewById(R.id.rv_pref_frame);

        int parentRef = (prefRowPos-2) * 3 + 1;
        assert aide.getParentInRow(parentRef)!=null;

        //noinspection ConstantConditions
        switch (aide.getParentInRow(parentRef)){
            case (1):
                frame.setImageResource(R.drawable.rv_pref_frame_right_unlit);
                frame.setRotationY(180);
                break;

            case(0):
                frame.setImageResource(R.drawable.rv_pref_frame_center_unlit);
                break;

            case(-1):
                frame.setImageResource(R.drawable.rv_pref_frame_right_unlit);
                frame.setRotationY(0);
                break;
        }

        int alternateTopOffset = topOffset - aide.getPrefTopIndent();
        int alternateLeftOffset = aide.getPrefLeftIndent();

        mViewCache.put(prefPos, prefView);//Вьюшку настроек тоже добавляем в кэш согласно её индексу
        addView(prefView);
        measureChild(prefView, 0, 0);
        layoutDecorated(prefView, alternateLeftOffset, alternateTopOffset,
                alternateLeftOffset + aide.getMeasuredPrefWidth(),
                alternateTopOffset + aide.getMeasuredPrefHeight());
    }

    private int getRealPaddingTop(){
        return getPaddingTop() + aide.getPrefTopIndent();
    }

    private void fillRows (RecyclerView.Recycler recycler,
                           RecyclerView.State state){

        int topOffset;


        if (getChildCount() == 0 && FLAG_NOTIFY == NOTIFY_NONE){
            slU.fr( "Empty layout detected. Views to be laid out: " + state.getItemCount());

            layoutStraightByRow(1);
        }


        else if (FLAG_NOTIFY == LAYOUT_PREF){

            rearrangeChildren();
            prefView = recycler.getViewForPosition(prefPos);

            topOffset = layoutSurroundingRows();
            layoutPref(topOffset);
            layoutMotherRow(recycler, topOffset);

            FLAG_NOTIFY = NOTIFY_NONE;
        }


        else if (FLAG_NOTIFY == HIDE_PREF | FLAG_NOTIFY == UPDATE_DATASET ) {

            if (prefVisibility) {
                slU.fr("Removing visible pref");
                prefVisibility = prefScrapped = STSTop = STSBottom = false;
                removeAndRecycleView(prefView, recycler);
            }

            detachAndScrapAttachedViews(recycler);
            mViewCache.clear();

            //Recalculating mAvailableRows
            if (FLAG_NOTIFY == UPDATE_DATASET ){ mAvailableRows = getItemCount() / 3; if (getItemCount() % 3 !=0 || mAvailableRows < 3) mAvailableRows++; }

            layoutStraight(mTopBaseline);

            recycler.clear();//старую кучу отходов нужно чистить, иначе адаптер не будет байндить новые вьюшки
            FLAG_NOTIFY = NOTIFY_NONE;
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

            FLAG_NOTIFY = NOTIFY_NONE;
        }


        mBottomBaseline = getHeight() + mTopBaseline;//Базовую линию всегда считаем относительно топовой

        if (mBottomBaseline >= mBottomBound){
            int offset = mBottomBaseline - mBottomBound + 1;
            slU.fst("shifting for: " + offset);

            offsetChildrenVertical(offset);
            mTopBaseline -= offset;
            mBottomBaseline -= offset;
        }

        int prp = (prefRowPos==0) ? -1 : prefRowPos;
        slU.f("Anchor row: " + mAnchorRowPos + " , top baseline: " + mTopBaseline + " , top bound: " + mTopBound +
                ", \n\tLast row: " + mLastVisibleRow + ", bottom baseline: " + mBottomBaseline + ", bottom bound: " + mBottomBound +
                ", \n\tAvailable rows: " + mAvailableRows + ", Pref row pos: " + prp);
    }


    @Override
    public void setUpdateDataset(int flag) {
        FLAG_NOTIFY=flag;
    }

    @Override
    public boolean canScrollVertically() {//проверка всегда производится уже после выкладки
        return true;
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
                    /*
                    Переменная Стыка введена, потому что метод layoutDecorated выкладывает дочерние вьюшки в координатах, относительно начала RV.
                    Мы передаём это смещение для выкладки, когда нужно выложить новую строку,
                    При этом координаты нижней границы всё ещё считаются как обсолютные относительно начала первой строки,
                    И нужны для скроллинга
                    */
                    try {
                        addNRecycle(recycler, DIR_DOWN);
                        slU.fr( "AddNRecycle DOWN, new pos: " + mAnchorRowPos + " " + mLastVisibleRow);
                    }catch (Exception e){ er.receiveError(e); }
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
                    }catch (Exception e){ er.receiveError(e); }
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
        if (state == RecyclerView.SCROLL_STATE_IDLE && mViewCache.size()!=0){//Чисто лог выводим
            slU.f("Row " + mAnchorRowPos + ", Top baseline: " + mTopBaseline + ", Top bound:" + mTopBound + ", Bottom baseline: " + mBottomBaseline + ", Bottom bound:" + mBottomBound + ", first cache: " + mViewCache.keyAt(0) + ", last cache: " + mViewCache.keyAt(mViewCache.size() - 1));
        }
    }

    @Override
    public void onItemsMoved(@NonNull RecyclerView recyclerView, int from, int to, int itemCount) {
        super.onItemsMoved(recyclerView, from, to, itemCount);
        slU.f( "onItemsMoved: " + from + " " + to);
    }

    @Override
    public void onItemsRemoved(@NonNull RecyclerView recyclerView, int positionStart, int itemCount) {
        super.onItemsRemoved(recyclerView, positionStart, itemCount);
        slU.f( "Items removed: " + positionStart + ", " + itemCount);
    }

    @NonNull
    @Override
    public RLMReturnData defineBaseAction(int prefParentPos) {
        slU.fp( "Old values. " + "pref parent pos: " + this.prefParentPos + " pref visibility: " + prefVisibility);
        if (this.prefParentPos == 666 || this.prefParentPos == prefParentPos & !prefVisibility || this.prefParentPos != prefParentPos & !prefVisibility) {//Либо настройки ещё не выкладывались, либо матерниские позиции соответствуют и строки не видно

            this.prefParentPos = prefParentPos;
            //Если строка с материнским элементом не полная, либо элементов в раскладке всего не больше трёх, то добавляем одну строку к счётчику
            prefRowPos = ((prefParentPos+1) / 3) + 1; if ((prefParentPos+1) % 3 !=0 || (prefParentPos+1) < 3) prefRowPos++;//Строка после материнской вьюшки. На которой будем выкладывать настройки
            prefPos = ((prefRowPos-1) * 3); if (prefPos>getItemCount()) prefPos = getItemCount();//Позиция вьюшки настроек в адаптере
            slU.f( "prefParentPos:" + this.prefParentPos + " prefRowPos:" + prefRowPos + " prefPos:" + prefPos);

            //We have to recycle this one just in case RV will want to reuse it and put in place of pref
            if (prefRowPos <= mAvailableRows) removeAndRecycleView(recycler.getViewForPosition(prefPos), recycler);

            FLAG_NOTIFY = LAYOUT_PREF;
            prefVisibility = true;
            slU.f( "LAYING OUT PREF");

            return new RLMReturnData(LAYOUT_PREF, this.prefParentPos, prefPos);
        }
        else if (this.prefParentPos != 666 && this.prefParentPos == prefParentPos && prefVisibility){//Настройки уже выкладывались, старая материнская позиция, строку видно
            FLAG_NOTIFY = HIDE_PREF;
            slU.f( "HIDING PREF");

            //It's crucial to make it re-bind previous pref view
            //and clear built-in cache because notifications heading to adapter
            //doesn't affect scrapped views, so some of them become corrupted
            if (mViewCache.get(prefParentPos)!=null) detachAndScrapView(mViewCache.get(prefParentPos), recycler);
            recycler.clear();

            return new RLMReturnData(HIDE_PREF, false);
        }
        else if (this.prefParentPos != 666 && this.prefParentPos != prefParentPos && prefVisibility){//Настройки уже выкладывались, новая материнская позиция, строку уже видно
            int oldPrefPos = this.prefPos;

            //Here, because global prefParentPos didn't change yet
            if (mViewCache.get(this.prefParentPos)!=null) detachAndScrapView(mViewCache.get(this.prefParentPos), recycler);
            recycler.clear();

            this.prefParentPos = prefParentPos;
            if (prefParentPos >= oldPrefPos) this.prefParentPos--;//Если мы тыкаем на элемент, который идёт после строки с настройками, то нужно бы откорректировать позицию на 1 вниз

            prefRowPos = ((this.prefParentPos+1) / 3) + 1;//Строка после материнской вьюшки. Имея в виду плюс один элемент в адаптере, добавляем один
            //Если строка с материнским элементом не полная, либо элементов в раскладке всего не больше трёх, то добавляем одну строку к счётчику
            if ((this.prefParentPos+1) % 3 !=0 || (this.prefParentPos+1) < 3) prefRowPos++;

            prefPos = ((prefRowPos-1) * 3); if (prefPos>=getItemCount()) prefPos = getItemCount() - 1;//Позиция вьюшки настроек в адаптере
            slU.f( "prefParentPos:" + this.prefParentPos + " prefRowPos:" + prefRowPos + " prefPos:" + prefPos);

            //We have to recycle this one just in case RV will want to reuse it and put in place of pref
            if (prefRowPos <= mAvailableRows) removeAndRecycleView(recycler.getViewForPosition(prefPos), recycler);

            FLAG_NOTIFY = HIDE_N_LAYOUT_PREF;
            prefVisibility = true;
            slU.f( "HIDING and LAYING OUT PREF");

            return new RLMReturnData(HIDE_N_LAYOUT_PREF, this.prefParentPos, prefPos);
        }
        return null;
    }

    public void hideOnResume() {
        if (prefVisibility){
            FLAG_NOTIFY = HIDE_PREF;
//            handlerCallback.removePref(true);
//            return new RLMReturnData(HIDE_PREF, true);

        }
    }
}
