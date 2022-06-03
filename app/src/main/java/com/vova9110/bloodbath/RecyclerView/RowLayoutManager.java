package com.vova9110.bloodbath.RecyclerView;

import android.content.Context;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.vova9110.bloodbath.HandlerCallback;
import com.vova9110.bloodbath.R;
import com.vova9110.bloodbath.RLMCallback;
import com.vova9110.bloodbath.UIHandler;


public class RowLayoutManager extends RecyclerView.LayoutManager implements RLMCallback {
    private final String TAG = "TAG_RLM";
    private final Context parentContext;
    private RecyclerView.Recycler recycler;
    private RecyclerView.State state;
    private final HandlerCallback handlerCallback;
    //Размеры окошка с временем в пикселях
    private int mDecoratedTimeWidth;
    private int mDecoratedTimeHeight;
    //Размеры прямоугольника с пикерами времени и кнопкой
    private int mDecoratedPreferencesWidth;
    private int mDecoratedPreferencesHeight;

    private int mBaseHorizontalPadding;// in pixels
    private int mBaseVerticalPadding;
    private int mVisibleRows;//Значение отрисованных строк всгда на 1 больше
    private int mExtendedVisibleRows;//Сама строка настроек в счёт не входит
    private int mAvailableRows;
    private int mAnchorRowPos;//У первой строки всегда как минимум виден нижний отступ
    private int mLastVisibleRow;
    private int mBottomBound;//Значение нижней выложенной границы
    private int mTopBound;
    private int mBottomBaseline;//Значение нижней видимой линии
    private int mTopBaseline;

    private final int DIR_DOWN = 0;
    private final int DIR_UP = 1;

    private int FLAG_NOTIFY;
    private final int NOTIFY_NONE = 0;
    private final int LAYOUT_PREF = 1;
    private final int HIDE_PREF = 2;
    private final int HIDE_N_LAYOUT_PREF = 3;
    private final int UPDATE_DATASET = 4;

    public static final int MODE_DELETE = 0;
    public static final int MODE_ADD = 1;
    public static final int MODE_CHANGE = 2;

    private boolean prefVisibility = false;
    private View prefView;
    private int prefParentPos = 666;
    private int prefRowPos;
    private int prefPos;
    private int oldPrefParentPos;
    private int oldPrefRowPos;
    private int oldPrefPos;
    private boolean prefScrapped = false;//Переменная означает, что настройки отскрапаны, но требуют выкладки при скролле


    private SparseArray<View> mViewCache = new SparseArray<>();

    private int SCROLL_MODE = 3;
    private boolean STSBottom = false;
    private boolean STSTop = false;//Переменные означают, что в предыдущем проходе скролла было изменено кол-во строк,
    //а в следующим за ним проходе требуется либо скрыть настройки, либо вернуть кол-во строк



    public RowLayoutManager(Context parentContext, UIHandler handler){
        super();
        this.parentContext = parentContext;
        handlerCallback = handler.pullHandlerCallback();
        handler.passRLMCallback(this);
    }

    @Override
    public RecyclerView.LayoutParams generateDefaultLayoutParams() {
        return new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT);
    }

    @Override
    public void onLayoutChildren (RecyclerView.Recycler recycler, RecyclerView.State state) {
        this.recycler = recycler;
        this.state = state;
        Log.d (TAG, "TIME TO LAYOUT!");

        if (getChildCount()==0 && 0 != state.getItemCount()) {//Первоначальное измерение, если есть что измерять и ничего ещё не выложено
            //Здесь необходимо высчитать и задать стандартные размеры боковых и вертикальных отступов для всех дочерних вьюшек,
            View sample = recycler.getViewForPosition(0);
            mBaseHorizontalPadding = 130;
            mBaseVerticalPadding = 120;
            sample.setPadding(0, 0, mBaseHorizontalPadding, mBaseVerticalPadding);

            //Высчитать размеры
            //Сначала для окошка со временем
            measureChild(sample, 0, 0);
            mDecoratedTimeWidth = getDecoratedMeasuredWidth(sample);
            mDecoratedTimeHeight = getDecoratedMeasuredHeight(sample);
            sample.setPadding(0, 0, 0, 0);

            //Потом для прямоугольника настроек
            View timeView = sample.findViewById(R.id.timeWindow);
            View hourPicker = sample.findViewById(R.id.picker_h);
            View minutePicker = sample.findViewById(R.id.picker_m);
            View switcher = sample.findViewById(R.id.switcher);
            View FAB = sample.findViewById(R.id.floatingActionButton);
            timeView.setVisibility(View.GONE);
            hourPicker.setVisibility(View.VISIBLE);
            minutePicker.setVisibility(View.VISIBLE);
            switcher.setVisibility(View.VISIBLE);
            FAB.setVisibility(View.VISIBLE);

            measureChild(sample, 0, 0);
            mDecoratedPreferencesWidth = getDecoratedMeasuredWidth(sample);
            mDecoratedPreferencesHeight = getDecoratedMeasuredHeight(sample);

            timeView.setVisibility(View.VISIBLE);
            hourPicker.setVisibility(View.GONE);
            minutePicker.setVisibility(View.GONE);
            switcher.setVisibility(View.GONE);
            FAB.setVisibility(View.GONE);

            //Рассчитать максимальное количество строк, основываясь на высоте RV
            mAvailableRows = getItemCount() / 3; if (getItemCount() % 3 !=0 || mAvailableRows < 3) mAvailableRows++;//Я реально уже хз, как оно работает, но оно работает
            mVisibleRows = getHeight() / mDecoratedTimeHeight + 1;
            mExtendedVisibleRows = (getHeight() - mDecoratedPreferencesHeight + mBaseVerticalPadding) / mDecoratedTimeHeight + 1;
            Log.d(TAG, "Visible rows: " + mVisibleRows + " (Extended: " + mExtendedVisibleRows + "), Available: " + mAvailableRows +
                    "\nTime Height: " + mDecoratedTimeHeight + ", Pref Weight: " + mDecoratedTimeWidth +
                    "\n Pref Height: " + mDecoratedPreferencesHeight + ", Pref Weight: " + mDecoratedPreferencesWidth);

        }

        if (0 != state.getItemCount()){ //Выкладывать, если есть что выкладывать
                    fillRows (recycler, state);
        }
        else if (getItemCount()==0) removeAndRecycleAllViews(recycler);//Если адаптер пустой, то очищаем разметку

    }



    private void fillRows (RecyclerView.Recycler recycler,
                           RecyclerView.State state){

        int paddingLeft = getPaddingLeft();
        int paddingTop = getPaddingTop();
        int leftOffset = paddingLeft;
        int topOffset = paddingTop;
        int rowCount = 1;//Это относительный счётчик строк, находится в пределах 1<= ... >=VisibleRows + 1


        if (getChildCount() == 0 && FLAG_NOTIFY == NOTIFY_NONE){
            Log.d(TAG, "Empty layout detected. Views to be laid out: " + state.getItemCount());
            mAnchorRowPos = 1; mTopBound = mTopBaseline = 0;//Выходит, что нулевая линия у нас на уровне верхнего отступа всего RV

            int p = 1;
            for (int index = 0; index < getItemCount() && rowCount <= mVisibleRows + 1; index++) { //Главный цикл. Выкладываемых строк больше, чем видимых
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

                if (p < 3) {
                    leftOffset += mDecoratedTimeWidth;
                    p++;
                }
                else {
                    topOffset += mDecoratedTimeHeight;
                    leftOffset = paddingLeft;
                    rowCount++;
                    mLastVisibleRow++;
                    p = 1;
                }
            }
            if (p>1){
                topOffset += mDecoratedTimeHeight;
                rowCount++;
                mLastVisibleRow++;
            }
            mBottomBound = topOffset + getPaddingBottom();//Берём сумму всех сдвигов в процессе выкладки плюс нижний отступ так, чтобы получалось вплотную до следующей строки
            mLastVisibleRow = rowCount - 1;

        }


        else if (FLAG_NOTIFY == LAYOUT_PREF){

            rearrangeChildren();
            prefView = recycler.getViewForPosition(prefPos);
            int scrappedRows = 0;

            if ((mTopBaseline - mTopBound) >= mDecoratedTimeHeight){//При наличии хотя бы одной полностью невидимой строки, мы скрапаем первую
                //Это не связанно с выкладкой строки настроек, просто в процессе скролла может оказаться одна такая строка сверху
                Log.d (TAG, "Scrapping first row");
                for (int i = (mAnchorRowPos - 1) * 3; i < mAnchorRowPos * 3; i++) {//Начинаем скрапать с первой вьюшки. Их всегда будет по трое в строке
                    Log.d(TAG, i + " scrapping, row: " + mAnchorRowPos);
                    detachAndScrapViewAt(0, recycler);//Метод берёт индекс вьюшки из разметки, а не из адаптера
                    mViewCache.remove(i);
                }
                mAnchorRowPos++; mTopBound += mDecoratedTimeHeight;
                scrappedRows++;
            }

            int count = getChildCount();
            int p = 1;
            Log.d (TAG, "Pref row pos: " + prefRowPos + ", first row: " + mAnchorRowPos);//Скрапаем всё, что выше (в разметке ниже) материнской строки настроек
            for (int i = (prefRowPos - mAnchorRowPos) * 3; i < count; i++){//Инкремент у нас в относительных значениях, а позиция вьюшки в абсолютных
                int v = (mAnchorRowPos - 1) * 3 + i;
                Log.d (TAG, "Scrapping pos: " + v);

                View scrap = mViewCache.get(v);//Вытаскивам также по примеру из кэша, потому что в адаптере может быть хрен знает что
                detachAndScrapView(scrap, recycler);
                mViewCache.remove(v);

                if (p<3) p++;
                else{
                    p = 1;
                    scrappedRows++;
                }
            }
            if (p>1) scrappedRows++;

            if (scrappedRows < (mVisibleRows - mExtendedVisibleRows) && mAvailableRows > mExtendedVisibleRows + 1){//если снизу не хватает строк для скрапа, берём сверху,
                int scrapRows = mVisibleRows - mExtendedVisibleRows - scrappedRows;//но только тогда, когда
                Log.d (TAG, "Additionally scrapping first row (rows)");
                for (int i = (mAnchorRowPos - 1) * 3; i < (mAnchorRowPos - 1 + scrapRows) * 3; i++) {//Начинаем скрапать с первой вьюшки. Их всегда будет по трое в строке
                    Log.d(TAG, i + " scrapping, row: " + mAnchorRowPos + scrapRows);
                    detachAndScrapViewAt(0, recycler);//Метод берёт индекс вьюшки из разметки, а не из адаптера
                    mViewCache.remove(i);
                }
                mAnchorRowPos += scrapRows; mTopBound += (mDecoratedTimeHeight * scrapRows);
            }

            //Смесь первоначальных установок и высчитывания границы по нижнему краю самого окна (без отступа)
            mBottomBound = (prefRowPos - 1) * mDecoratedTimeHeight - mBaseVerticalPadding + getPaddingBottom() + getPaddingTop();
            topOffset += ((mDecoratedTimeHeight * (prefRowPos - 1)) - mBaseVerticalPadding) - mTopBaseline;//Мы выкладываем уже по правильному сдвигу, оффсет в конце не нужен

            mViewCache.put(prefPos, prefView);//Вьюшку настроек тоже добавляем в кэш согласно её индексу
            addView(prefView);
            measureChild(prefView, 0, 0);
            layoutDecorated(prefView, leftOffset, topOffset,
                    leftOffset + mDecoratedPreferencesWidth,
                    topOffset + mDecoratedPreferencesHeight);

            topOffset += mDecoratedPreferencesHeight;
            mBottomBound += mDecoratedPreferencesHeight;
            rowCount = prefRowPos - mAnchorRowPos - 1;//Тут он как-то сдвинут вниз, хрен его знает, работает
            mLastVisibleRow = prefRowPos - 1;

            p = 1;
            for (int i = (prefRowPos - 1) * 3 + 1; i < getItemCount() && rowCount < mExtendedVisibleRows; i++) {//Наполнять уже нужно привычным способом, не таким, которым отсоединяли
                Log.d (TAG, "adding shifted: " + i);
                View child = recycler.getViewForPosition(i);
                mViewCache.put(i, child);

                addView(child);
                measureChild(child, 0,0);
                layoutDecorated(child, leftOffset, topOffset,
                        leftOffset + mDecoratedTimeWidth,
                        topOffset + mDecoratedTimeHeight);

                if (p < 3) {//Выкладываем вдоль, добавляем отступ слева
                    leftOffset += mDecoratedTimeWidth;
                    p++;
                }
                else {//Строка кончилась, делаем вертикальный отступ и сбрасываем счёт
                    topOffset += mDecoratedTimeHeight;
                    leftOffset = paddingLeft;
                    rowCount++;
                    //Добавляем к счёту и отступу уже добавленную строку
                    mLastVisibleRow++;
                    mBottomBound += mDecoratedTimeHeight;

                    p = 1;
                }
            }
            //На тот редкий случай, когда под конец выкладки попадается неполная строка
            if (p > 1) {
                mLastVisibleRow++;
                mBottomBound += mDecoratedTimeHeight;
            }
            //Когда строка настроек выкладывается последней
            if (prefRowPos > mLastVisibleRow) {
                int offset = mBottomBound - mBottomBaseline + 1;

                offsetChildrenVertical(-offset);
                mTopBaseline += offset;

            }
            FLAG_NOTIFY = NOTIFY_NONE;
        }


        else if (FLAG_NOTIFY == HIDE_PREF | FLAG_NOTIFY == UPDATE_DATASET) {

            if (prefVisibility) {
                Log.d (TAG, "Removing pref N shifting if transition state exists");
                if (STSTop) mAnchorRowPos++;
                if (STSBottom) mLastVisibleRow--;
                prefScrapped = STSTop = STSBottom = false;
                removeAndRecycleView(prefView, recycler);
                prefVisibility = false;
            }
            detachAndScrapAttachedViews(recycler);
            mViewCache.clear();

            if (FLAG_NOTIFY == UPDATE_DATASET){ mAvailableRows = getItemCount() / 3; if (getItemCount() % 3 !=0 || mAvailableRows < 3) mAvailableRows++; }

            int shift = 0;//Переменная обозначает, на сколько нужно уменьшить mAnchorRowPos, чтобы в раскладке было нужное количество строк;
            if (mVisibleRows + mAnchorRowPos > mAvailableRows && mAvailableRows > mExtendedVisibleRows + 1)//если у нас в раскладке вообще есть лишние
                shift = (mVisibleRows + mAnchorRowPos) - mAvailableRows;
            Log.d(TAG, "Shifting rows up for :" + shift);
            mAnchorRowPos -= shift;

            mLastVisibleRow = mAnchorRowPos - 1;//Потому что строку плюсуем только после её выкладки

            //Верхняя граница у нас уже есть, она никуда не сдвигалась. Установим стартовые значения нижней границы и оффсета
            mBottomBound = (mAnchorRowPos - 1) * mDecoratedTimeHeight + getPaddingBottom() + getPaddingTop();
            mTopBound = mBottomBound - (getPaddingTop() + getPaddingBottom());
            topOffset += (mAnchorRowPos - 1) * mDecoratedTimeHeight - mTopBaseline;//Мы выкладываем уже по правильному сдвигу, оффсет в конце не нужен
            Log.d (TAG, "Start filling from row " + mAnchorRowPos);

            int p = 1;
            for (int i = (mAnchorRowPos - 1) * 3; i < getItemCount() && rowCount <= mVisibleRows + 1; i++) {
                if (i < 0 || i >= state.getItemCount()) { //Метод из класса State возвращает количество оставшихся Вьюшек, доступных для выкладки
                    //С его помощью будем выкладывать, пока не кончатся
                    continue;
                }
                //Log.d (TAG, "Laying out: " + i);
                View child = recycler.getViewForPosition(i);
                mViewCache.put(i, child);

                addView(child);
                measureChild(child, 0, 0);
                layoutDecorated(child, leftOffset, topOffset,
                        leftOffset + mDecoratedTimeWidth,
                        topOffset + mDecoratedTimeHeight);

                if (p < 3) {//Выкладываем вдоль, добавляем отступ слева
                    leftOffset += mDecoratedTimeWidth;
                    p++;
                }
                else {//Строка кончилась, делаем вертикальный отступ и сбрасываем счёт
                    topOffset += mDecoratedTimeHeight;
                    leftOffset = paddingLeft;
                    rowCount++;
                    //Добавляем к счёту и отступу уже добавленную строку
                    mLastVisibleRow++;
                    mBottomBound += mDecoratedTimeHeight;

                    p = 1;
                }
            }
            //На тот редкий случай, когда под конец выкладки попадается неполная строка
            if (p > 1) {
                mLastVisibleRow++;
                mBottomBound += mDecoratedTimeHeight;
                Log.d (TAG, "row incomplete. Adding one");
            }

            recycler.clear();//старую кучу отходов нужно чистить, иначе адаптер не будет байндить новые вьюшки
            FLAG_NOTIFY = NOTIFY_NONE;
        }


        else if (FLAG_NOTIFY == HIDE_N_LAYOUT_PREF){

            if (STSTop) mAnchorRowPos++;
            if (STSBottom) mLastVisibleRow--;
            prefScrapped = STSTop = STSBottom = false;

            mLastVisibleRow = mAnchorRowPos - 1;//Потому что строку плюсуем только после её выкладки
            mBottomBound = (mAnchorRowPos - 1) * mDecoratedTimeHeight + getPaddingBottom() + getPaddingTop();
            mTopBound = mBottomBound - (getPaddingTop() + getPaddingBottom());
            topOffset += (mAnchorRowPos - 1) * mDecoratedTimeHeight - mTopBaseline;//Мы выкладываем уже по правильному сдвигу, оффсет в конце не нужен

            detachAndScrapAttachedViews(recycler);
            mViewCache.clear();
            prefView = recycler.getViewForPosition(prefPos);

            int p = 1;
            for (int i = (mAnchorRowPos - 1) * 3; i < getItemCount() - 1 && mLastVisibleRow < prefRowPos - 1; i++) {
                if (i < 0 || i >= state.getItemCount()) { //Метод из класса State возвращает количество оставшихся Вьюшек, доступных для выкладки
                    //С его помощью будем выкладывать, пока не кончатся
                    continue;
                }

                Log.d (TAG, "Laying out: " + i + ", row count: " + rowCount);
                View child = recycler.getViewForPosition(i);
                mViewCache.put(i, child);

                addView(child);
                measureChild(child, 0, 0);
                layoutDecorated(child, leftOffset, topOffset,
                        leftOffset + mDecoratedTimeWidth,
                        topOffset + mDecoratedTimeHeight);

                if (p < 3) {
                    leftOffset += mDecoratedTimeWidth;
                    p++;
                }
                else {
                    topOffset += mDecoratedTimeHeight;
                    leftOffset = paddingLeft;
                    rowCount++;
                    mLastVisibleRow++;
                    mBottomBound += mDecoratedTimeHeight;

                    p = 1;
                }
            }
            //На тот случай, когда материнская строка не полная
            if (p > 1) {
                mLastVisibleRow++;
                mBottomBound += mDecoratedTimeHeight;
                topOffset += mDecoratedTimeHeight;
                leftOffset = paddingLeft;
            }


            topOffset -= mBaseVerticalPadding;
            mBottomBound -= mBaseVerticalPadding;

            mViewCache.put(prefPos, prefView);//Вьюшку настроек тоже добавляем в кэш согласно её индексу
            addView(prefView);
            measureChild(prefView, 0, 0);
            layoutDecorated(prefView, leftOffset, topOffset,
                    leftOffset + mDecoratedPreferencesWidth,
                    topOffset + mDecoratedPreferencesHeight);

            topOffset += mDecoratedPreferencesHeight;
            mBottomBound += mDecoratedPreferencesHeight;
            //rowCount одновременно равен строке после материнской и номеру строки настроек


            p = 1;
            for (int i = (prefRowPos - 1) * 3 + 1; i < getItemCount() && rowCount <= mExtendedVisibleRows + 1; i++) {
                if (i < 0 || i >= state.getItemCount()) { //Метод из класса State возвращает количество оставшихся Вьюшек, доступных для выкладки
                    //С его помощью будем выкладывать, пока не кончатся
                    continue;
                }

                Log.d (TAG, "Laying out: " + i + ", row count: " + rowCount);
                View child = recycler.getViewForPosition(i);
                mViewCache.put(i, child);//Засовываем на обновлённые позиции

                addView(child);
                measureChild(child, 0, 0);
                layoutDecorated(child, leftOffset, topOffset,
                        leftOffset + mDecoratedTimeWidth,
                        topOffset + mDecoratedTimeHeight);

                if (p < 3) {
                    leftOffset += mDecoratedTimeWidth;
                    p++;
                }
                else {
                    topOffset += mDecoratedTimeHeight;
                    leftOffset = paddingLeft;
                    rowCount++;
                    mLastVisibleRow++;
                    mBottomBound += mDecoratedTimeHeight;

                    p = 1;
                }
            }
            //На тот случай, когда материнская строка не полная
            if (p > 1) {
                mLastVisibleRow++;
                mBottomBound += mDecoratedTimeHeight;
            }

            FLAG_NOTIFY = NOTIFY_NONE;
        }


        mBottomBaseline = getHeight() + mTopBaseline;//Базовую линию всегда считаем относительно топовой
        int offset = 0;
        if (mBottomBaseline > mBottomBound) {
            offset = mBottomBaseline - mBottomBound + 1;
            mTopBaseline -= offset; mBottomBaseline -= offset;
            offsetChildrenVertical(offset);
            Log.d (TAG, "Shifting layout by bottom for: " + offset);
        }
        if (mTopBaseline < 0) {
            offset = mTopBaseline;
            mTopBaseline -= offset; mBottomBaseline -= offset;
            offsetChildrenVertical(offset);
            Log.d (TAG, "Shifting layout by top for: " + offset);
        }

        Log.d(TAG, "Anchor row: " + mAnchorRowPos + " , top baseline: " + mTopBaseline + " , top bound: " + mTopBound +
                ", \nLast row: " + mLastVisibleRow + ", bottom baseline: " + mBottomBaseline + ", bottom bound: " + mBottomBound +
                ", \nAvailable rows: " + mAvailableRows);
    }

    @Override
    public void updateDatasetEvent(int position, int mode, int newHour, int newMinute) {
        Log.d (TAG, "UDE, pos " + position + ", mode " + mode);

        FLAG_NOTIFY = UPDATE_DATASET;//todo всё нормально, только бы со сдвигами разобраться
        Log.d(TAG, "UPDATE DATASET");
        try {
            switch (mode) {
                case (MODE_DELETE):
                    handlerCallback.deleteItem(position);
                    break;
                case (MODE_ADD):
                    handlerCallback.addItem(newHour, newMinute);
                    break;
                case (MODE_CHANGE):
                    handlerCallback.changeItem(position, newHour, newMinute);
                    break;
            }
        }
        catch (UnsupportedOperationException e){
            e.printStackTrace();
            Toast.makeText(parentContext, "Такой будильник уже существует", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean canScrollVertically() {//проверка всегда производится уже после выкладки
        return true;
    }

    @Override
    public int scrollVerticallyBy (int dy,
                                   RecyclerView.Recycler recycler,
                                   RecyclerView.State state) {
        if (getChildCount() == 0) return 0;

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
                else if (delta <= dy && mLastVisibleRow < mAvailableRows && (SCROLL_MODE == 1 || SCROLL_MODE == 3))  {
                    //Log.d (TAG, "" + mLastVisibleRow + mAvailableRows);
                    mBottomBound += mDecoratedTimeHeight; mTopBound += mDecoratedTimeHeight;//В мирное время, мы управляем значениями границ только отсюда,
                    //Но при выложенной строке настроек, её видимость определяется в другом методе, где мы дополнительно изменяем значение границ, влияя на дельту
                    offset = dy; mBottomBaseline += dy; mTopBaseline += dy;
                    Log.d (TAG, "AddNRecycle DOWN, new pos: " + (mAnchorRowPos+1) + " " + (mLastVisibleRow+1));
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
                //Log.d (TAG, " " + delta);

                if (delta < dy) {
                    offset = dy;
                    mBottomBaseline += dy; mTopBaseline += dy;
                    //Log.d (TAG, "Baseline down");
                }

                else if (delta >= dy && mAnchorRowPos > 1 && (SCROLL_MODE == 2 || SCROLL_MODE == 3)) { //Меньше первой строки у нас нет
                    mBottomBound -= mDecoratedTimeHeight; mTopBound -= mDecoratedTimeHeight;//Даём последней строке стать частично невидимой и держим границу по ней
                    offset = dy; mBottomBaseline += dy; mTopBaseline += dy;

                    joint = getPaddingTop() + delta;//Берём нижнюю границу RV (0), прибавляем отступ разметки и вычитаем дельту
                    addNRecycle (recycler, DIR_UP, joint);
                    Log.d (TAG, "AddNRecycle UP, new pos: " + mAnchorRowPos + " " + mLastVisibleRow);
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

        //Log.d(TAG, dy + " " + delta + " " + offset + " ");
        //Log.d(TAG, "Bottom: " + mBottomBaseline + " Top: " + mTopBaseline + ", Bottom bound: " + mBottomBound + ", Top bound: " + mTopBound);
        return offset;
    }

    private void rearrangeChildren() {
        int count = getChildCount();
        int RCShift = 0;
        if (prefScrapped && mAnchorRowPos >= prefRowPos) RCShift++;
        //Log.d (TAG, "" + count + mViewCache.size() + RCShift);
        for (int i = RCShift; i < count + RCShift; i++) {//Для правельной переработки и добавления строк,
            //сначала нам нужно переназначить индексы дочерних вьюшек, которые уже выложены,
            //и заодно обновить кэш
            int v = (mAnchorRowPos - 1) * 3 + i;
            //Log.d(TAG, "Row " + mAnchorRowPos + ", taking " + v + ", setting " + i);
            View view;
            view = mViewCache.get(v);//Нужно взять из кэша все выложенные вьюшки по одной, согласно их нормальным индексам, которые у них в адаптере
            detachView(view);
            attachView(view, i);
        }
    }
/*
 */
    private void addNRecycle (RecyclerView.Recycler recycler, int direction, int joint){

        int leftOffset = getPaddingLeft();
        int topOffset;
        int dif = mVisibleRows - mExtendedVisibleRows;//У нас в любом случае есть строки, которые можно поставить на место строки настроек

        rearrangeChildren();

        //Окно настроек убирается и добавляется только вместе с прилегающей строкой (снизу или сверху)
        //todo на все скраповые варианты с окном настроек нужно добавить отдельный высчет строк, чтобы не зависеть от дифа
        switch (direction){
            case (DIR_DOWN):

                topOffset = joint;

                if (mAnchorRowPos == prefRowPos && !prefScrapped && prefVisibility && !STSTop){//Если дошло дело до ресайкла строки, которая вплотную снизу стоит к строке настроек,
                    //то нужно оставить эту строку вместе с настройками и выложить диф снизу
                    Log.d(TAG, "Adding dif rows on bottom. Need to scrap pref next time");
                    prefScrapped = false; STSTop = true;

                    mTopBound -= mDecoratedTimeHeight;

                    int p = 1;
                    for (int i = mLastVisibleRow * 3 + 1; i < (mLastVisibleRow + dif) * 3 + 1 && i < getItemCount(); i++){//С учётом того, что диф всегда равен минимум одному
                        Log.d (TAG, "Adding shifted: " + i);

                        View view = recycler.getViewForPosition(i);
                        mViewCache.put(i, view);
                        addView(view);
                        measureChild(view, 0, 0);
                        layoutDecorated(view, leftOffset, topOffset,
                                leftOffset + mDecoratedTimeWidth,
                                topOffset + mDecoratedTimeHeight);

                        if (p < 3) {
                            leftOffset += mDecoratedTimeWidth;
                            p++;
                        }
                        else {
                            topOffset += mDecoratedTimeHeight;
                            leftOffset = getPaddingLeft();
                            p = 1;
                        }
                    }

                    mLastVisibleRow += dif;
                    mBottomBound += (mDecoratedTimeHeight * dif) - mDecoratedTimeHeight;

                    break;//Корневая и последняя строки не добавятся. Просто switch завершится
                }
                else if (STSTop){//Тут нужно убрать настройки со следующей за ними строкой и выложить новую снизу
                    Log.d (TAG, "TS passed. Scrapping pref and rolling rows");
                    prefScrapped = true; STSTop = false;

                    mTopBound += mDecoratedPreferencesHeight;
                    mLastVisibleRow++;

                    detachAndScrapView(prefView, recycler);
                    mViewCache.remove(prefPos);

                    for (int i = (mAnchorRowPos - 1) * 3 + 1; i < mAnchorRowPos * 3 + 1; i++) {
                        Log.d(TAG, i + " scrapping shifted, row: " + mAnchorRowPos);

                        detachAndScrapViewAt(0, recycler);
                        mViewCache.remove(i);
                    }
                    for (int i = (mLastVisibleRow - 1) * 3 + 1; i < mLastVisibleRow * 3 + 1 && i < getItemCount(); i++) {
                        Log.d(TAG, i + " adding shifted, row: " + mLastVisibleRow);

                        View view = recycler.getViewForPosition(i);
                        addView(view);
                        measureChild(view, 0, 0);
                        layoutDecorated(view, leftOffset, topOffset,
                                leftOffset + mDecoratedTimeWidth,
                                topOffset + mDecoratedTimeHeight);

                        leftOffset += mDecoratedTimeWidth;

                        mViewCache.put(i, view);
                    }

                    mAnchorRowPos++;
                    break;
                }

                if (prefScrapped && prefVisibility && mLastVisibleRow == prefRowPos - 2 && !STSBottom){//В условии, когда доходит дело до выкладки материнской строки,
                    //мы её выложим со строкой настроек, сверху скрапнем старую, а диф скрапнем уже когда нужно будет выкладывать строку за окном настроек
                    Log.d(TAG, "Restoring pref with parent row. Need to scrap redundant next time");
                    prefScrapped = false; STSBottom = true;

                    mBottomBound += mDecoratedPreferencesHeight - mBaseVerticalPadding;
                    mLastVisibleRow++;

                    for (int i = (mAnchorRowPos - 1) * 3; i < mAnchorRowPos * 3; i++) {
                        Log.d(TAG, i + " scrapping, row: " + mAnchorRowPos);

                        detachAndScrapViewAt(0, recycler);
                        mViewCache.remove(i);
                    }

                    mAnchorRowPos++;

                    for (int i = (mLastVisibleRow - 1) * 3; i < mLastVisibleRow * 3 && i < getItemCount(); i++) {
                        Log.d(TAG, i + " adding row: " + mLastVisibleRow);

                        View view = recycler.getViewForPosition(i);
                        addView(view);
                        measureChild(view, 0, 0);
                        layoutDecorated(view, leftOffset, topOffset,
                                leftOffset + mDecoratedTimeWidth,
                                topOffset + mDecoratedTimeHeight);

                        leftOffset += mDecoratedTimeWidth;

                        mViewCache.put(i, view);
                    }

                    leftOffset = getPaddingLeft();
                    topOffset += mDecoratedTimeHeight - mBaseVerticalPadding;

                    prefView = recycler.getViewForPosition(prefPos);
                    mViewCache.put(prefPos, prefView);
                    addView (prefView);
                    measureChild(prefView,0,0);
                    layoutDecorated(prefView, leftOffset, topOffset,
                            leftOffset + mDecoratedPreferencesWidth, topOffset + mDecoratedPreferencesHeight);

                    break;
                }
                else if (STSBottom){//Помимо дифа нужно скрапнуть ещё одну старую сверху
                    Log.d (TAG, "TS passed. Scrapping rows on top and adding new on bottom");
                    STSBottom = false;

                    for (int i = (mAnchorRowPos - 1) * 3; i < (mAnchorRowPos + dif) * 3; i++) {
                        Log.d(TAG, i + " scrapping, row: " + mAnchorRowPos);

                        detachAndScrapViewAt(0, recycler);
                        mViewCache.remove(i);
                    }

                    mAnchorRowPos += dif + 1;
                    mTopBound += mDecoratedTimeHeight * (dif);//Не забываем, что один отступ уже сделал вызывающий метод
                    mLastVisibleRow++;

                    for (int i = (mLastVisibleRow - 1) * 3 + 1; i < mLastVisibleRow * 3 + 1 && i < getItemCount(); i++) {
                        Log.d(TAG, i + " adding row: " + mLastVisibleRow);

                        View view = recycler.getViewForPosition(i);
                        addView(view);
                        measureChild(view, 0, 0);
                        layoutDecorated(view, leftOffset, topOffset,
                                leftOffset + mDecoratedTimeWidth,
                                topOffset + mDecoratedTimeHeight);

                        leftOffset += mDecoratedTimeWidth;

                        mViewCache.put(i, view);
                    }

                    break;
                }

                mLastVisibleRow++;//Дефолтное выполнение варианта начинается отсюда

                //Обычный скрап первой строки происходит, если якорная строка выше (по раскладке) строки настроек,
                //либо же в условиях, когда окна с настройками не видно
                if (mAnchorRowPos < prefRowPos || !prefVisibility){
                    for (int i = (mAnchorRowPos - 1) * 3; i < mAnchorRowPos * 3; i++) {
                        Log.d(TAG, i + " scrapping, row: " + mAnchorRowPos);

                        detachAndScrapViewAt(0, recycler);
                        mViewCache.remove(i);
                    }
                    if (mAnchorRowPos == prefRowPos - 1 && !prefScrapped && prefVisibility) mTopBound -= mBaseVerticalPadding;//Отступ от материнской строки до настроек отсутствует


                }
                else {
                    for (int i = (mAnchorRowPos - 1) * 3 + 1; i < mAnchorRowPos * 3 + 1; i++) {//Так как мы скрапаем уже после строки настроек, нужно удалять верные вьюшки из кэша
                        Log.d(TAG, i + " scrapping shifted, row: " + mAnchorRowPos);

                        detachAndScrapViewAt(0, recycler);
                        mViewCache.remove(i);
                    }
                }


                if (!prefVisibility || mLastVisibleRow < prefRowPos) {
                    Log.d (TAG, "No pref detected");
                    for (int i = (mLastVisibleRow - 1) * 3; i < mLastVisibleRow * 3 && i < getItemCount(); i++) {
                        Log.d(TAG, i + " adding row: " + mLastVisibleRow);

                        View view = recycler.getViewForPosition(i);
                        addView(view);
                        measureChild(view, 0, 0);
                        layoutDecorated(view, leftOffset, topOffset,
                                leftOffset + mDecoratedTimeWidth,
                                topOffset + mDecoratedTimeHeight);

                        leftOffset += mDecoratedTimeWidth;

                        mViewCache.put(i, view);
                    }
                }
                else{
                    Log.d (TAG, "Pref in the Layout");
                    for (int i = (mLastVisibleRow - 1) * 3 + 1; i < mLastVisibleRow * 3 + 1 && i < getItemCount(); i++) {
                        Log.d(TAG, i + " adding row: " + mLastVisibleRow);

                        View view = recycler.getViewForPosition(i);
                        addView(view);
                        measureChild(view, 0, 0);
                        layoutDecorated(view, leftOffset, topOffset,
                                leftOffset + mDecoratedTimeWidth,
                                topOffset + mDecoratedTimeHeight);

                        leftOffset += mDecoratedTimeWidth;

                        mViewCache.put(i, view);
                    }
                }
                mAnchorRowPos++;
                break;


            case (DIR_UP):

                topOffset = joint - mDecoratedTimeHeight;//Стык у нас приходит как топовый отступ разметки + дельта (отрицательная)

                if (mLastVisibleRow == prefRowPos - 1 && !prefScrapped && prefVisibility && !STSBottom){//Если дошло дело до ресайкла материнской строки,
                    //с учётом уменьшенного кол-ва строк, то нужно оставить материнскую строку с настройками и выложить диф кол-во строк сверху
                    Log.d(TAG, "Adding row(s) on top. Need to scrap pref next time");
                    prefScrapped = false; STSBottom = true;

                    mBottomBound += mDecoratedTimeHeight;
                    mTopBound += mDecoratedTimeHeight;

                    int p = 1;
                    for (int i = (mAnchorRowPos - 1 - dif) * 3; i < (mAnchorRowPos - 1) * 3; i++){
                        Log.d (TAG, "Adding " + i);

                        View view = recycler.getViewForPosition(i);
                        mViewCache.put(i, view);
                        addView(view);
                        measureChild(view, 0, 0);
                        layoutDecorated(view, leftOffset, topOffset,
                                leftOffset + mDecoratedTimeWidth,
                                topOffset + mDecoratedTimeHeight);

                        if (p < 3) {
                            leftOffset += mDecoratedTimeWidth;
                            p++;
                        }
                        else {
                            topOffset += mDecoratedTimeHeight;
                            leftOffset = getPaddingLeft();
                            p = 1;
                        }
                    }
                    mAnchorRowPos -= dif;
                    mTopBound -= mDecoratedTimeHeight * dif;

                    break;//Корневая и последняя строки не добавятся. Просто switch завершится
                }
                else if (STSBottom){//На следующем проходе после выкладки диф строк, нам нужно скрапнуть настройки вместе с материнской строкой,
                    //а также выложить новую верхнюю строку
                    Log.d (TAG, "TS passed. Scrapping pref");
                    prefScrapped = true; STSBottom = false;

                    detachAndScrapView(prefView, recycler);
                    mViewCache.remove(prefPos);

                    mAnchorRowPos--;

                    for (int i = (mLastVisibleRow - 1) * 3; i < mLastVisibleRow * 3 && i < getItemCount(); i++) {
                        Log.d(TAG, i + " scrapping, row: " + mLastVisibleRow);
                        detachAndScrapViewAt(getChildCount() - 1, recycler);
                        mViewCache.remove(i);
                    }

                    mBottomBound -= mDecoratedPreferencesHeight - mBaseVerticalPadding;

                    for (int i = (mAnchorRowPos - 1) * 3; i < mAnchorRowPos * 3; i++) {
                        Log.d(TAG, i + " adding row: " + mAnchorRowPos);

                        View view = recycler.getViewForPosition(i);
                        addView(view);
                        measureChild(view, 0, 0);
                        layoutDecorated(view, leftOffset, topOffset,
                                leftOffset + mDecoratedTimeWidth,
                                topOffset + mDecoratedTimeHeight);

                        leftOffset += mDecoratedTimeWidth;

                        mViewCache.put(i, view);
                    }
                    mLastVisibleRow--;
                    break;
                }

                if (prefScrapped && prefVisibility && mAnchorRowPos == prefRowPos && !STSTop){//В условии, когда доходит дело до выкладки материнской строки (якорная позиция ещё не сместилась),
                    //мы просто выложим настройки
                    Log.d(TAG, "Restoring pref. Need to scrap redundant next time");
                    prefScrapped = false; STSTop = true;

                    mTopBound -= mDecoratedPreferencesHeight - mDecoratedTimeHeight;
                    mBottomBound += mDecoratedTimeHeight;

                    prefView = recycler.getViewForPosition(prefPos);
                    mViewCache.put(prefPos, prefView);
                    addView (prefView);
                    measureChild(prefView,0,0);
                    layoutDecorated(prefView, leftOffset, joint - mDecoratedPreferencesHeight,
                            leftOffset + mDecoratedPreferencesWidth, joint);

                    break;
                }
                else if (STSTop){
                    Log.d (TAG, "TS passed, scrapping redundant on bottom");
                    STSTop = false;

                    mTopBound += mBaseVerticalPadding;
                    mBottomBound += mDecoratedTimeHeight;
                    mAnchorRowPos--;
                    int rowCount = mAnchorRowPos + mExtendedVisibleRows;
                    topOffset += mBaseVerticalPadding;

                    for (int i = rowCount * 3 + 1; i < mLastVisibleRow * 3 + 1 && i < getItemCount(); i++) {
                        Log.d(TAG, i + " scrapping shifted, row: " + mLastVisibleRow);
                        detachAndScrapViewAt(getChildCount() - 1, recycler);
                        mViewCache.remove(i);
                    }
                    for (int i = (mAnchorRowPos - 1) * 3; i < mAnchorRowPos * 3; i++) {
                        Log.d(TAG, i + " adding row: " + mAnchorRowPos);

                        View view = recycler.getViewForPosition(i);
                        addView(view);
                        measureChild(view, 0, 0);
                        layoutDecorated(view, leftOffset, topOffset,
                                leftOffset + mDecoratedTimeWidth,
                                topOffset + mDecoratedTimeHeight);

                        leftOffset += mDecoratedTimeWidth;

                        mViewCache.put(i, view);
                    }
                    mBottomBound -= (mLastVisibleRow - rowCount) * mDecoratedTimeHeight;
                    mLastVisibleRow = rowCount;
                    break;
                }

                mAnchorRowPos--;

                //Обычный скрап последней строки происходит, если эта якорная строка ниже (по раскладке) строки настроек,
                //либо же в условиях, когда окна с настройками не видно
                if (mLastVisibleRow < prefRowPos || !prefVisibility){
                    for (int i = (mLastVisibleRow - 1) * 3; i < mLastVisibleRow * 3 && i < getItemCount(); i++) {
                        Log.d(TAG, i + " scrapping, row: " + mLastVisibleRow);
                        detachAndScrapViewAt(getChildCount() - 1, recycler);
                        mViewCache.remove(i);
                    }
                }
                else {
                    for (int i = (mLastVisibleRow - 1) * 3 + 1; i < mLastVisibleRow * 3 + 1 && i < getItemCount(); i++) {
                        Log.d(TAG, i + " scrapping shifted, row: " + mLastVisibleRow);
                        detachAndScrapViewAt(getChildCount() - 1, recycler);
                        mViewCache.remove(i);
                    }
                }

                if (!prefVisibility || mAnchorRowPos < prefRowPos) {
                    if (mAnchorRowPos == prefRowPos - 1 && !prefScrapped && prefVisibility) {
                        topOffset += mBaseVerticalPadding;
                        mTopBound += mBaseVerticalPadding;
                    }
                    for (int i = (mAnchorRowPos - 1) * 3; i < mAnchorRowPos * 3; i++) {
                        Log.d(TAG, i + " adding row: " + mAnchorRowPos);

                        View view = recycler.getViewForPosition(i);
                        addView(view);
                        measureChild(view, 0, 0);
                        layoutDecorated(view, leftOffset, topOffset,
                                leftOffset + mDecoratedTimeWidth,
                                topOffset + mDecoratedTimeHeight);

                        leftOffset += mDecoratedTimeWidth;

                        mViewCache.put(i, view);
                    }
                }
                else {
                    for (int i = (mAnchorRowPos - 1) * 3 + 1; i < mAnchorRowPos * 3 + 1; i++) {
                        Log.d(TAG, i + " adding row: " + mAnchorRowPos);

                        View view = recycler.getViewForPosition(i);
                        addView(view);
                        measureChild(view, 0, 0);
                        layoutDecorated(view, leftOffset, topOffset,
                                leftOffset + mDecoratedTimeWidth,
                                topOffset + mDecoratedTimeHeight);

                        leftOffset += mDecoratedTimeWidth;

                        mViewCache.put(i, view);
                    }
                }
                mLastVisibleRow--;
                    break;
        }
//        for (int i = 0; i<getChildCount(); i++){
//            Log.d (TAG, "" + mViewCache.keyAt(i));
//        }
        Log.d (TAG, "Cache filled: " + mViewCache.size() + ". from:" + mViewCache.keyAt(0) + " to:" + mViewCache.keyAt(mViewCache.size() - 1));
    }

    private void fillDrawer (RecyclerView.Recycler recycler){//todo проверить теорию о том, что именно выкладка, а не байндинг тормозит скролл

    }

    @Override
    public void onItemsAdded (RecyclerView recyclerView, int positionStart, int itemCount){
        Log.d(TAG, "Items added: " + positionStart + ", " + itemCount);
    }

    @Override
    public void onItemsUpdated(@NonNull RecyclerView recyclerView, int positionStart, int itemCount) {
        super.onItemsUpdated(recyclerView, positionStart, itemCount);
        Log.d (TAG, "Item updated: " + positionStart);
    }

    @Override
    public void onAdapterChanged(@Nullable RecyclerView.Adapter oldAdapter, @Nullable RecyclerView.Adapter newAdapter) {
        super.onAdapterChanged(oldAdapter, newAdapter);
        Log.d (TAG, "Adapter changed!");
    }

    @Override
    public void onScrollStateChanged (int state){
        if (state == RecyclerView.SCROLL_STATE_IDLE){//Чисто лог выводим
            Log.d (TAG, "Row " + mAnchorRowPos + ", Top baseline: " + mTopBaseline + ", first cache index: " + mViewCache.keyAt(0) + ", last cache index: " + mViewCache.keyAt(mViewCache.size() - 1));
        }
    }

    @Override
    public void onItemsRemoved(@NonNull RecyclerView recyclerView, int positionStart, int itemCount) {
        super.onItemsRemoved(recyclerView, positionStart, itemCount);
        Log.d (TAG, "Items removed: " + positionStart + ", " + itemCount);
    }

    @Override
    public void notifyBaseClick(int prefParentPos) {
        Log.d (TAG, "PREFPARENTPOS: " + this.prefParentPos + " VISIBILITY: " + prefVisibility);
        if (this.prefParentPos == 666 || this.prefParentPos == prefParentPos & !prefVisibility || this.prefParentPos != prefParentPos & !prefVisibility) {//Либо настройки ещё не выкладывались, либо матерниские позиции соответствуют и строки не видно

            this.prefParentPos = prefParentPos;
            //Если строка с материнским элементом не полная, либо элементов в раскладке всего не больше трёх, то добавляем одну строку к счётчику
            prefRowPos = ((prefParentPos+1) / 3) + 1; if ((prefParentPos+1) % 3 !=0 || (prefParentPos+1) < 3) prefRowPos++;//Строка после материнской вьюшки. На которой будем выкладывать настройки
            prefPos = ((prefRowPos-1) * 3); if (prefPos>getItemCount()) prefPos = getItemCount();//Позиция вьюшки настроек в адаптере
            Log.d (TAG, "___prefParentPos:" + this.prefParentPos + " prefRowPos:" + prefRowPos + " prefPos:" + prefPos);

            FLAG_NOTIFY = LAYOUT_PREF;
            handlerCallback.passPrefToAdapter(prefParentPos, prefPos);
            prefVisibility = true;
            Log.d (TAG, "LAYING OUT PREF");
        }
        else if (this.prefParentPos != 666 && this.prefParentPos == prefParentPos && prefVisibility){//Настройки уже выкладывались, старая материнская позиция, строку видно
            FLAG_NOTIFY = HIDE_PREF;
            handlerCallback.removePref(false);
            Log.d (TAG, "HIDING PREF");
        }
        else if (this.prefParentPos != 666 && this.prefParentPos != prefParentPos && prefVisibility){//Настройки уже выкладывались, новая материнская позиция, строку уже видно
            oldPrefParentPos = this.prefParentPos;
            oldPrefRowPos = this.prefRowPos;
            oldPrefPos = this.prefPos;


            this.prefParentPos = prefParentPos;
            if (prefParentPos >= oldPrefPos) this.prefParentPos--;//Если мы тыкаем на элемент, который идёт после строки с настройками, то нужно бы откорректировать позицию на 1 вниз

            prefRowPos = ((this.prefParentPos+1) / 3) + 1;//Строка после материнской вьюшки. Имея в виду плюс один элемент в адаптере, добавляем один
            //Если строка с материнским элементом не полная, либо элементов в раскладке всего не больше трёх, то добавляем одну строку к счётчику
            if ((this.prefParentPos+1) % 3 !=0 || (this.prefParentPos+1) < 3) prefRowPos++;

            prefPos = ((prefRowPos-1) * 3); if (prefPos>=getItemCount()) prefPos = getItemCount() - 1;//Позиция вьюшки настроек в адаптере
            Log.d (TAG, "___prefParentPos:" + this.prefParentPos + " prefRowPos:" + prefRowPos + " prefPos:" + prefPos);


            FLAG_NOTIFY = HIDE_N_LAYOUT_PREF;
            handlerCallback.removeNPassPrefToAdapter(this.prefParentPos, prefPos);
            prefVisibility = true;
            Log.d (TAG, "HIDING and LAYING OUT PREF");
        }
    }

    @Override
    public void hideOnResume() {
        if (prefVisibility){
            FLAG_NOTIFY = HIDE_PREF;
            handlerCallback.removePref(true);
        }
    }
}
