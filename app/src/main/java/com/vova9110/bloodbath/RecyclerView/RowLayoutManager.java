package com.vova9110.bloodbath.RecyclerView;

import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.widget.NumberPicker;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.vova9110.bloodbath.R;


public class RowLayoutManager extends RecyclerView.LayoutManager {
    private TextView mTimeView;
    private NumberPicker mHourPicker;
    private NumberPicker mMinutePicker;
    private Switch mSwitcher;
    //Размеры окошка с временем в пикселях
    private int mDecoratedTimeWidth;
    private int mDecoratedTimeHeight;
    //Размеры прямоугольника с пикерами времени и кнопкой
    private int mDecoratedPreferencesWidth;
    private int mDecoratedPreferencesHeight;

    private int mBaseHorizontalPadding;// in pixels
    private int mBaseVerticalPadding;
    private int mVisibleRows;//Значение отрисованных строк всгда на 1 больше
    private int mAvailableRows;
    private int mAnchorRowPos;//У первой строки всегда как минимум виден нижний отступ
    private int mLastVisibleRow;//При первоначальном заполнении выходит, что эта строка отрисовывается невидимой
    private int mBottomBound;//Значение нижней выложенной границы
    private int mTopBound;
    private int mBottomBaseline;//Значение нижней видимой линии
    private int mTopBaseline;
    //Значение сдвига относительно начала первой видимой строки, для сохнанения позиции скролинга при выкладке.
    private int mTopShift;
    private final int DIR_DOWN = 0;
    private final int DIR_UP = 1;
    /*Кэш, который полностью дублирует выложенный сет вьюшек,
    наполняется вместе с первой выкладкой и обновляется при добавлении и переработке строк,
    служит как референс для индексов при переприсоединении,
    и используется как устаревший вариант раскладки в предиктивных анимациях
    */
    private SparseArray<View> mViewCache = new SparseArray<>();


    public RowLayoutManager (){
        super();
    }

    @Override
    public RecyclerView.LayoutParams generateDefaultLayoutParams() {
        return new RecyclerView.LayoutParams(RecyclerView.LayoutParams.WRAP_CONTENT, RecyclerView.LayoutParams.WRAP_CONTENT);
    }

    @Override
    public void onLayoutChildren (RecyclerView.Recycler recycler, RecyclerView.State state) {
        Log.d ("TAG", "Adapter size: " + getItemCount());

        if (getChildCount()==0 && 0 != state.getItemCount()){//Первоначальное измерение, если есть что измерять и ничего ещё не выложено
            //Здесь необходимо высчитать и задать стандартные размеры боковых и вертикальных отступов для всех дочерних вьюшек,
            View sample = recycler.getViewForPosition(0);
            mTimeView = sample.findViewById(R.id.textView);
            mHourPicker = sample.findViewById(R.id.picker_h);
            mMinutePicker = sample.findViewById(R.id.picker_m);
            mSwitcher = sample.findViewById(R.id.switcher);
            mBaseHorizontalPadding = 130;
            mBaseVerticalPadding = 150;
            sample.setPadding(0, 0, mBaseHorizontalPadding, mBaseVerticalPadding);
            addView(sample);

            //Сначала для окошка со временем
            measureChild(sample, 0,0);
            mDecoratedTimeWidth = getDecoratedMeasuredWidth(sample);
            mDecoratedTimeHeight = getDecoratedMeasuredHeight(sample);
            //Потом для прямоугольника настроек
            mTimeView.setVisibility(View.GONE);
            mHourPicker.setVisibility(View.VISIBLE);
            mMinutePicker.setVisibility(View.VISIBLE);
            mSwitcher.setVisibility(View.VISIBLE);
            measureChild(sample, 0, 0);
            mDecoratedPreferencesWidth = getDecoratedMeasuredWidth(sample);
            mDecoratedPreferencesHeight = getDecoratedMeasuredHeight(sample);
            mTimeView.setVisibility(View.VISIBLE);
            detachAndScrapView(sample, recycler);

            //Рассчитать максимальное количество строк, основываясь на высоте RV
            mVisibleRows = getHeight() / mDecoratedTimeHeight + 1;
            mAvailableRows = getItemCount() / 3; if (getItemCount() % 3 !=0 || mAvailableRows < 3) mAvailableRows++;
            Log.d("TAG", "Visible rows: " + mVisibleRows + ", Available rows: " + mAvailableRows +
                    "\n Time Height: " + mDecoratedTimeHeight + ", Pref Weight: " + mDecoratedTimeWidth +
                    "\n Pref Height: " + mDecoratedPreferencesHeight + ", Pref Weight: " + mDecoratedPreferencesWidth);
        }

        if (0 != state.getItemCount()){ //Выкладывать, если есть что выкладывать
            Log.d("TAG", "Simple layout started");
            fillRows (recycler, state);
        }
        else if (getItemCount()==0) removeAndRecycleAllViews(recycler);//Если адаптер пустой, то очищаем разметку

    }
    /*
    Метод выкладывает и кэширует дочерние вьюшки. Если в разметке уже есть - использует кэшированные заново
    Он сам определяет требуемое количество строк и стартовую позицию,
    Производит выкладку на пустую, уже заполненную и проскроленную разметку,
    При первоначальной выкладке устанавливает стартовые значения границ и оффсетов для скроллинга
     */
    private void fillRows (RecyclerView.Recycler recycler,
                           RecyclerView.State state){

        int paddingLeft = getPaddingLeft();
        int paddingTop = getPaddingTop();
        int leftOffset = paddingLeft;
        int topOffset = paddingTop;
        int rowCount = 1;//Внутренний счётчик строк. Обнуляется каждый раз при заполнении разметки

        if (getChildCount() == 0){
            Log.d("TAG", "Empty layout detected. Views to be laid out: " + state.getItemCount());
            mAnchorRowPos = 1; mTopBound = mTopBaseline = mTopShift = 0;

            for (int index = 0; index < getItemCount() && rowCount <= mVisibleRows + 1; index++) { //Главный цикл. Выкладываемых строк больше, чем видимых
                int p = index + 1;
                if (index < 0 || index >= state.getItemCount()) { //Метод из класса State возвращает количество оставшихся Вьюшек, доступных для выкладки
                    //С его помощью будем выкладывать, пока не кончатся
                    continue;
                }

                View view = recycler.getViewForPosition(index);
                mViewCache.put(index, view);//Наполняем кэш по пути

                addView(view);
                measureChild(view, 0, 0);
                layoutDecorated(view, leftOffset, topOffset,
                        leftOffset + mDecoratedTimeWidth,
                        topOffset + mDecoratedTimeHeight);

                if (p < 3 || p % 3 != 0) leftOffset += mDecoratedTimeWidth;
                else if (p % 3 == 0) {
                    topOffset += mDecoratedTimeHeight;
                    leftOffset = paddingLeft;
                    rowCount++;
                }
            }
            mBottomBound = topOffset + getPaddingBottom();//Берём сумму всех сдвигов в процессе выкладки плюс нижний отступ так, чтобы получалось вплотную до следующей строки
            mLastVisibleRow = rowCount - 1;
            mBottomBaseline = getHeight();//Для первого раза достаточно просто присвоить высоту RV. Это высота с учётом отступов
            Log.d("TAG", "Child height: " + mDecoratedTimeHeight);
            Log.d("TAG", "Row count: " + mLastVisibleRow + ", bottom bound: " + mBottomBound + ", bottom baseline: " + mBottomBaseline);
        }
        /*
        После первой выкладки или скролла уже в любом случае будет кэш. Если видимый сет не изменился, то мы не выкладываем заново
        Выкладываем только при удалении, добавлении или изменении элементов
         */
        else {
            removeAndRecycleAllViews(recycler);//Во избежание крашей, пока что просто переработаем все вьюшки, когда доходит до повторной выкладки
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

        if (mAvailableRows * mDecoratedTimeHeight <= getHeight()) return 0;

        int delta;
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
                    mBottomBound += mDecoratedTimeHeight; mTopBound += mDecoratedTimeHeight;//Даём первой строке стать частично невидимой и держим границу по ней
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
                    mBottomBound -= mDecoratedTimeHeight; mTopBound -= mDecoratedTimeHeight;//Даём последней строке стать частично невидимой и держим границу по ней
                    offset = dy; mBottomBaseline += dy; mTopBaseline += dy;

                    joint = getPaddingTop() + delta;//Берём нижнюю границу RV (0), прибавляем отступ разметки и вычитаем дельту
                    addNRecycle (recycler, DIR_UP, joint);
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
        mTopShift += offset;
        return offset;
    }
/*

 */
    void addNRecycle (RecyclerView.Recycler recycler, int direction, int joint){//TODO при переходе со статичной выкладки на скроллинг, происходит короткое зависание

        int leftOffset = getPaddingLeft();
        int topOffset;

        for (int i = 0; i < getChildCount(); i++){//Для правельной переработки и добавления строк,
            //сначала нам нужно переназначить индексы дочерних вьюшек, которые уже выложены,
            //и заодно обновить кэш
            int v = (mAnchorRowPos - 1) * 3 + i;
            Log.d ("TAG", "Row " + mAnchorRowPos +", taking " + v + ", setting " + i);

            View view = mViewCache.get(v);//Нужно взять из кэша все выложенные вьюшки по одной
            detachView(view); attachView(view, i);
        }

        switch (direction){
            case (DIR_DOWN):

                topOffset = joint;
                mLastVisibleRow++;

                for (int i = (mAnchorRowPos - 1) * 3; i < mAnchorRowPos * 3; i++) {
                    Log.d("TAG", i + " recycling, row: " + mAnchorRowPos);
                    removeAndRecycleViewAt(0, recycler);//Метод берёт индекс вьюшки из разметки, а не из адаптера
                    mViewCache.remove(i);
                }

                for (int i = (mLastVisibleRow - 1) * 3; i < mLastVisibleRow * 3 && i != getItemCount(); i++){
                    Log.d ("TAG", i + " adding row: " + mLastVisibleRow);

                    View view  = recycler.getViewForPosition(i);
                    addView (view);
                    measureChild (view, 0, 0);
                    layoutDecorated (view, leftOffset, topOffset,
                            leftOffset + mDecoratedTimeWidth,
                            topOffset + mDecoratedTimeHeight);

                    leftOffset += mDecoratedTimeWidth;

                    mViewCache.put(i, view);
                }
                mAnchorRowPos++;
                break;

            case (DIR_UP):

                topOffset = joint - mDecoratedTimeHeight;
                mAnchorRowPos--;

                    for (int i = (mLastVisibleRow - 1) * 3; i < mLastVisibleRow * 3 && i != getItemCount(); i++) {
                        Log.d("TAG", i + " recycling, row: " + mLastVisibleRow);
                        removeAndRecycleViewAt(getChildCount() - 1, recycler);//Берём индекс последней выложенной вьюшки
                        mViewCache.remove(i);
                    }

                for (int i = (mAnchorRowPos - 1) * 3; i < mAnchorRowPos * 3; i++){
                    Log.d ("TAG", i + " adding row: " + mAnchorRowPos);

                    View view  = recycler.getViewForPosition(i);
                    addView (view);
                    measureChild (view, 0, 0);
                    layoutDecorated (view, leftOffset, topOffset,
                            leftOffset + mDecoratedTimeWidth,
                            topOffset + mDecoratedTimeHeight);

                    leftOffset += mDecoratedTimeWidth;

                    mViewCache.put(i, view);
                }
                mLastVisibleRow--;
                    break;
        }
        Log.d ("TAG", "Cache filled: " + mViewCache.size());
    }
    @Override
    public void onItemsAdded (RecyclerView recyclerView,
                                          int positionStart,
                                          int itemCount){
        Log.d("TAG", "Items added, clear cache");
        mViewCache.clear();
    }

    @Override
    public void onItemsUpdated(@NonNull RecyclerView recyclerView, int positionStart, int itemCount) {
        super.onItemsUpdated(recyclerView, positionStart, itemCount);
        Log.d ("TAG", "Items updated!");
    }

    @Override
    public void onAdapterChanged(@Nullable RecyclerView.Adapter oldAdapter, @Nullable RecyclerView.Adapter newAdapter) {
        super.onAdapterChanged(oldAdapter, newAdapter);
        Log.d ("TAG", "Adapter changed!");
    }

    @Override
    public void onScrollStateChanged (int state){
        if (state == RecyclerView.SCROLL_STATE_IDLE){//Чисто лог выводим
            Log.d ("TAG", "Row " + mAnchorRowPos + ", Top shift: " + mTopShift + ", first cache index: " + mViewCache.keyAt(0) + ", last cache index: " + mViewCache.keyAt(getChildCount() - 1));
        }
    }

    @Override
    public void onItemsRemoved(@NonNull RecyclerView recyclerView, int positionStart, int itemCount) {
        super.onItemsRemoved(recyclerView, positionStart, itemCount);
        Log.d ("TAG", "Items removed!");
    }
}
