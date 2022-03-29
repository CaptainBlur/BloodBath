package com.vova9110.bloodbath;

import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;


public class RowLayoutManager extends RecyclerView.LayoutManager {

    private final TaskViewModel ViewModel;
    private int mDecoratedChildWidth;
    private int mDecoratedChildHeight;

    private int mBaseHorizontalPadding;// in pixels
    private int mBaseVerticalPadding;
    private int mVisibleRows;//Реальное значение отрисованных строк всгда на 1 больше
    private int mAvailableRows;

    private int mAnchorRowPos;//У первой строки всегда как минимум виден нижний отступ
    private int mLastVisibleRow;//При первоначальном заполнении выходит, что эта строка отрисовывается невидимой
    private int mBottomBound;//Значение нижней выложенной границы
    private int mTopBound;
    private int mBottomBaseline;//Значение нижней видимой линии
    private int mTopBaseline;
    /*Значение сдвига относительно начала первой видимой строки, для сохнанения позиции скролинга при выкладке.
    При отрицательном значении, верхняя базовая линия находится на позиции topEdgeRow, то есть строки, которая уже была переработана при скроллинге вниз*/
    private int mTopShift;
    /*Используется для определения направления последней выкладки при скроллинге,
    для выбора правильной строки для переработки при скроллинге вверх*/
    private boolean mLayoutDown;
    private final int DIR_DOWN = 0;
    private final int DIR_UP = 1;
    private ArrayList<View> mAdapterCache;
    private SparseArray<View> mViewCache;


    public RowLayoutManager (TaskViewModel VM){
        ViewModel = VM;
    }

    @Override
    public RecyclerView.LayoutParams generateDefaultLayoutParams() {
        return new RecyclerView.LayoutParams(RecyclerView.LayoutParams.WRAP_CONTENT, RecyclerView.LayoutParams.WRAP_CONTENT);
    }

    @Override
    public void onLayoutChildren (RecyclerView.Recycler recycler, RecyclerView.State state) {

        mAdapterCache = new ArrayList<>();
        for (int i = 0; i < state.getItemCount(); i++) mAdapterCache.add(recycler.getViewForPosition(i));
        Log.d ("TAG", "Adapter cache created, size: " + mAdapterCache.size());

        if (mViewCache == null) mViewCache = new SparseArray<>(getChildCount());

        if (getChildCount()==0 && 0 != state.getItemCount()){//Первоначальное измерение, если есть что измерять
            //Здесь необходимо высчитать и задать стандартные размеры боковых и вертикальных отступов для всех дочерних вьюшек,
            //Рассчитать максимальное количество строк, основываясь на высоте RV
            View sample = recycler.getViewForPosition(0);
            mBaseHorizontalPadding = 130;
            mBaseVerticalPadding = 150;
            sample.setPadding(0, 0, mBaseHorizontalPadding, mBaseVerticalPadding);
            addView(sample);
            measureChild(sample, 0,0);
            mDecoratedChildWidth = getDecoratedMeasuredWidth(sample);
            mDecoratedChildHeight = getDecoratedMeasuredHeight(sample);
            detachAndScrapView(sample, recycler);

            mVisibleRows = getHeight() / mDecoratedChildHeight + 1;
            mAvailableRows = getItemCount() / 3; if (getItemCount() % 3 !=0 || mAvailableRows < 3) mAvailableRows++;
            Log.d("TAG", "Visible rows: " + mVisibleRows + ", Available rows: " + mAvailableRows);
        }

        if (0 != state.getItemCount()){ //Выкладывать, если есть что выкладывать
            Log.d("TAG", "Simple layout started");
            fillRows (recycler, state);
        }
        else if (getChildCount()!=0) removeAndRecycleAllViews(recycler);

    }
    /*
    Метод выкладывает дочерние вьюшки. Если в разметке уже есть - кэширует и использует заново
    Он сам определяет требуемое количество строк и стартовую позицию,
    Производит выкладку на пустую, уже заполненную и проскроленную разметку,
    При первоначальной выкладке устанавливает значения границ и оффсетов для скроллинга
     */
    private void fillRows (RecyclerView.Recycler recycler,//TODO добавить инструменты выкладки на заполненный и проскроленный списки
                           RecyclerView.State state){

        int paddingLeft = getPaddingLeft();
        int paddingTop = getPaddingTop();
        int leftOffset = paddingLeft;
        int topOffset = paddingTop;
        int rowCount = 1;//Внутренний счётчик строк. Обнуляется каждый раз при заполнении разметки

        if (getChildCount() == 0){
            Log.d("TAG", "Empty layout detected. Views to be laid out: " + state.getItemCount());
            mAnchorRowPos = 1; mTopBound = mTopBaseline = mTopShift = 0; mLayoutDown = true;//Устанавливаем начальные значения на пустую выкладку

            for (int i = 0; i < getItemCount() && rowCount <= mVisibleRows + 1; i++) { //Главный цикл. Выкладываемых строк больше, чем видимых
                int p = i + 1;
                if (i < 0 || i >= state.getItemCount()) { //Метод из класса State возвращает количество оставшихся Вьюшек, доступных для выкладки
                    //С его помощью будем выкладывать, пока не кончатся
                    continue;
                }

                View view = mAdapterCache.get(i);
                mViewCache.put(i, view);//Наполняем кэш по пути

                addView(view);
                measureChild(view, 0, 0);
                layoutDecorated(view, leftOffset, topOffset,
                        leftOffset + mDecoratedChildWidth,
                        topOffset + mDecoratedChildHeight);

                if (p < 3 || p % 3 != 0) leftOffset += mDecoratedChildWidth;
                else if (p % 3 == 0) {
                    topOffset += mDecoratedChildHeight;
                    leftOffset = paddingLeft;
                    rowCount++;
                }
            }
            mBottomBound = topOffset + getPaddingBottom();//Берём сумму всех сдвигов в процессе выкладки плюс нижний отступ так, чтобы получалось вплотную до следующей строки
            mLastVisibleRow = rowCount - 1;
            mBottomBaseline = getHeight();//Для первого раза достаточно просто присвоить высоту RV. Это высота с учётом отступов
            Log.d("TAG", "Child height: " + mDecoratedChildHeight);
            Log.d("TAG", "Row count: " + mLastVisibleRow + ", bottom bound: " + mBottomBound + ", bottom baseline: " + mBottomBaseline);
        }
        /*
        После первой выкладки или скролла уже в любом случае будет кэш. Вопрос в том, изменился ли список
         */
        else{
            removeAndRecycleAllViews(recycler);
        }
//        detachAndScrapAttachedViews(recycler);//Отстраняем прикреплённые вьюшки
//        Log.d("TAG", "Cached children: " + mViewCache.size() + ", Views to be laid out: " + state.getItemCount());


    }

    @Override
    public boolean canScrollVertically() {return true;}

    @Override
    public int scrollVerticallyBy (int dy,
                                   RecyclerView.Recycler recycler,
                                   RecyclerView.State state) {
        if (getChildCount() == 0) return 0;

        if (mAvailableRows * mDecoratedChildHeight <= getHeight()) return 0;

        int delta = 0;
        int offset = 0;
        int joint;

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
                else if (delta <= dy && mLastVisibleRow < mAvailableRows)  {
                    mBottomBound += mDecoratedChildHeight; mTopBound += mDecoratedChildHeight;//Даём первой строке стать частично невидимой и держим границу по ней
                    offset = dy; mBottomBaseline += dy; mTopBaseline += dy;
                    Log.d ("TAG", "AddNRecycle DOWN, former pos: " + mAnchorRowPos + " " + mLastVisibleRow);
                    /*
                    Переменная Стыка введена, потому что метод layoutDecorated выкладывает дочерние вьюшки в координатах, относительно начала RV.
                    Мы передаём это смещение для выкладки, когда нужно выложить новую строку,
                    При этом координаты нижней границы всё ещё считаются как обсолютные относительно начала первой строки,
                    И нужны для скроллинга
                    */
                    joint = getHeight() - getPaddingBottom() + delta;
                    addNRecycle (recycler, DIR_DOWN, joint);
                }
                //Если дельта меньше или равна оффсету и выкладывать уже нечего
                else {
                    offset = delta;
                    mBottomBaseline += delta; mTopBaseline += delta;
                }
            }
            else offset = 0;//Если базовая линия не обновилась, то выкладывать уже нечего, условие не выполняется

            offsetChildrenVertical(-offset);
        }
        if (dy<0){//Скроллинг вниз, оффсет - вверх
            boolean topBoundReached = mTopBaseline <= mTopBound;

            if (!topBoundReached){
                delta = mTopBound - mTopBaseline;//Дельта отрицательная
                //Log.d ("TAG", " " + delta);

                if (delta < dy) {
                    offset = dy;
                    mBottomBaseline += dy; mTopBaseline += dy;
                    //Log.d ("TAG", "Baseline down");
                }

                else if (delta >= dy && mAnchorRowPos > 1) { //Меньше первой строки у нас нет
                    mBottomBound -= mDecoratedChildHeight; mTopBound -= mDecoratedChildHeight;//Даём последней строке стать частично невидимой и держим границу по ней
                    offset = dy; mBottomBaseline += dy; mTopBaseline += dy;

                    joint = getPaddingTop() + delta;//Берём нижнюю границу RV (0), прибавляем отступ разметки и вычитаем дельту
                    addNRecycle (recycler, DIR_UP, joint);
                    if (mLayoutDown) mLayoutDown = false;
                    Log.d ("TAG", "AddNRecycle UP, new pos: " + mAnchorRowPos + " " + mLastVisibleRow);
                }

                else if (delta >= dy && mAnchorRowPos > 0) {
                    offset = delta;
                    mBottomBaseline += delta; mTopBaseline += delta;
                }

                else {
                    offset = delta;
                    mBottomBaseline += delta; mTopBaseline += delta;
                }
            }

            else offset = 0;

            offsetChildrenVertical(-offset);
        }

        //Log.d("TAG", dy + " " + delta + " " + offset + " ");
        //Log.d("TAG", "Bottom: " + mBottomBaseline + " Top: " + mTopBaseline + ", Bottom bound: " + mBottomBound + ", Top bound: " + mTopBound);
        return offset;
    }
/*

 */
    void addNRecycle (RecyclerView.Recycler recycler, int direction, int joint){

        int leftOffset = getPaddingLeft();
        int topOffset;

        switch (direction){
            case (DIR_DOWN):

                topOffset = joint;
                mLastVisibleRow++;

                for (int i = (mAnchorRowPos - 1) * 3; i < mAnchorRowPos * 3; i++) {
                    Log.d("TAG", i + " recycling, row: " + mAnchorRowPos);
                    removeAndRecycleViewAt(0, recycler);//Метод берёт индекс вьюшки из разметки, а не из адаптера
                }

                for (int i = (mLastVisibleRow - 1) * 3; i < mLastVisibleRow * 3 && i != getItemCount(); i++){
                    Log.d ("TAG", i + " adding row: " + mLastVisibleRow);

                    View view  = mAdapterCache.get(i);
                    addView (view);
                    measureChild (view, 0, 0);
                    layoutDecorated (view, leftOffset, topOffset,
                            leftOffset + mDecoratedChildWidth,
                            topOffset + mDecoratedChildHeight);

                    leftOffset += mDecoratedChildWidth;
                }
                mAnchorRowPos++;
                break;

            case (DIR_UP):

                for (int i = 0; i < getChildCount(); i++){//Для правельной переработки и добавления строк,
                    //сначала нам нужно переназначить индексы дочерних вьюшек внутри разметки
                    int v = (mAnchorRowPos - 1) * 3 + i;
                    Log.d ("TAG", "Row " + mAnchorRowPos +", taking " + v + ", setting " + i);

                    View view = mAdapterCache.get(v);//Нужно взять из кэша все выложенные вьюшки по одной
                    detachView(view); attachView(view, i);
                }

                topOffset = joint - mDecoratedChildHeight;
                mAnchorRowPos--;

                    for (int i = (mLastVisibleRow - 1) * 3; i < mLastVisibleRow * 3 && i != getItemCount(); i++) {
                        Log.d("TAG", i + " recycling, row: " + mLastVisibleRow);
                        removeAndRecycleViewAt(getChildCount() - 1, recycler);//Берём индекс последней выложенной вьюшки
                    }

                for (int i = (mAnchorRowPos - 1) * 3; i < mAnchorRowPos * 3; i++){
                    Log.d ("TAG", i + " adding row: " + mAnchorRowPos);

                    View view  = mAdapterCache.get(i);
                    addView (view);
                    measureChild (view, 0, 0);
                    layoutDecorated (view, leftOffset, topOffset,
                            leftOffset + mDecoratedChildWidth,
                            topOffset + mDecoratedChildHeight);

                    leftOffset += mDecoratedChildWidth;
                }
                mLastVisibleRow--;
                    break;
        }
    }
    @Override
    public void onItemsAdded (RecyclerView recyclerView,
                                          int positionStart,
                                          int itemCount){
        Log.d("TAG", "Adapter changed, clear cache");
        mViewCache.clear();
    }

    @Override
    public void onScrollStateChanged (int state){
        if (state == RecyclerView.SCROLL_STATE_IDLE){//Заполняем кэш при остановке скроллинга и считаем верхний сдвиг

            //Log.d ("TAG", " " + mTopShift);
        }
    }
}
