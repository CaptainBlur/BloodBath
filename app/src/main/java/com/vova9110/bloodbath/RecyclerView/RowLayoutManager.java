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

import com.vova9110.bloodbath.AlarmRepo;
import com.vova9110.bloodbath.Database.Alarm;
import com.vova9110.bloodbath.R;
import com.vova9110.bloodbath.RLMCallback;
import com.vova9110.bloodbath.RepoCallback;


public class RowLayoutManager extends RecyclerView.LayoutManager implements RLMCallback {
    private final String TAG = "TAG_RLM";
    private final RepoCallback repoCallback;
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
    private int mTopShift;//Значение сдвига относительно начала первой видимой строки, для сохнанения позиции скролинга при выкладке.

    private final int DIR_DOWN = 0;
    private final int DIR_UP = 1;
    private int FLAG_NOTIFY;
    private final int NOTIFY_NONE = 0;
    private final int LAYOUT_PREF = 1;
    private final int HIDE_PREF = 2;
    private final int HIDE_N_LAYOUT_PREF = 3;


    private boolean prefVisibility = false;
    private View prefView;
    private int prefParentPos = 666;
    private int prefRowPos;
    private int prefPos;
    private int oldPrefParentPos;
    private int oldPrefRowPos;
    private int oldPrefPos;
    /*Кэш, который полностью дублирует выложенный сет вьюшек,
    наполняется вместе с первой выкладкой и обновляется при добавлении и переработке строк,
    служит как референс для индексов при переприсоединении,
    и используется как устаревший вариант раскладки в предиктивных анимациях
    */
    private SparseArray<View> mViewCache = new SparseArray<>();


    public RowLayoutManager (AlarmRepo repo){
        super();
        repoCallback = repo.pullRepoCallback();
        repo.passRLMCallback(this);
    }

    @Override
    public RecyclerView.LayoutParams generateDefaultLayoutParams() {
        return new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT);
    }

    @Override
    public void onLayoutChildren (RecyclerView.Recycler recycler, RecyclerView.State state) {
//        Log.d (TAG, "Adapter size: " + getItemCount());

        if (getChildCount()==0 && 0 != state.getItemCount()){//Первоначальное измерение, если есть что измерять и ничего ещё не выложено
            //Здесь необходимо высчитать и задать стандартные размеры боковых и вертикальных отступов для всех дочерних вьюшек,
            View sample = recycler.getViewForPosition(0);
            mBaseHorizontalPadding = 130;
            mBaseVerticalPadding = 150;
            sample.setPadding(0, 0, mBaseHorizontalPadding, mBaseVerticalPadding);

            //Высчитать размеры
            //Сначала для окошка со временем
            measureChild(sample, 0,0);
            mDecoratedTimeWidth = getDecoratedMeasuredWidth(sample);
            mDecoratedTimeHeight = getDecoratedMeasuredHeight(sample);
            sample.setPadding(0,0,0,0);

            //Потом для прямоугольника настроек
            View timeView = sample.findViewById(R.id.timeWindow);
            View hourPicker = sample.findViewById(R.id.picker_h);
            View minutePicker = sample.findViewById(R.id.picker_m);
            View switcher = sample.findViewById(R.id.switcher);
            timeView.setVisibility(View.GONE);
            hourPicker.setVisibility(View.VISIBLE);
            minutePicker.setVisibility(View.VISIBLE);
            switcher.setVisibility(View.VISIBLE);

            measureChild(sample, 0, 0);
            mDecoratedPreferencesWidth = getDecoratedMeasuredWidth(sample);
            mDecoratedPreferencesHeight = getDecoratedMeasuredHeight(sample);

            timeView.setVisibility(View.VISIBLE);
            hourPicker.setVisibility(View.GONE);
            minutePicker.setVisibility(View.GONE);
            switcher.setVisibility(View.GONE);


            //Рассчитать максимальное количество строк, основываясь на высоте RV
            mVisibleRows = getHeight() / mDecoratedTimeHeight + 1;
            mExtendedVisibleRows = (getHeight() - mDecoratedPreferencesHeight + mBaseVerticalPadding) / mDecoratedTimeHeight + 1;
            mAvailableRows = getItemCount() / 3; if (getItemCount() % 3 !=0 || mAvailableRows < 3) mAvailableRows++;//Я реально уже хз, как оно работает, но оно работает
            Log.d(TAG, "Visible rows: " + mVisibleRows + " (Extended: " + mExtendedVisibleRows + "), Available rows: " + mAvailableRows +
                    "\n Time Height: " + mDecoratedTimeHeight + ", Pref Weight: " + mDecoratedTimeWidth +
                    "\n Pref Height: " + mDecoratedPreferencesHeight + ", Pref Weight: " + mDecoratedPreferencesWidth);
        }

        if (0 != state.getItemCount()){ //Выкладывать, если есть что выкладывать
                    fillRows (recycler, state);
        }
        else if (getItemCount()==0) removeAndRecycleAllViews(recycler);//Если адаптер пустой, то очищаем разметку

    }
    /*
    Метод выкладывает и кэширует дочерние вьюшки. Если в разметке уже есть - использует кэшированные заново
    Он сам определяет требуемое количество строк и стартовую позицию,
    Производит выкладку на пустую, уже заполненную и проскроленную разметку (определяет сам),
    При первоначальной выкладке устанавливает стартовые значения границ и оффсетов для скроллинга
     */
    private void fillRows (RecyclerView.Recycler recycler,
                           RecyclerView.State state){

        int paddingLeft = getPaddingLeft();
        int paddingTop = getPaddingTop();
        int leftOffset = paddingLeft;
        int topOffset = paddingTop;
        int rowCount = 1;//Это относительный счётчик строк, находится в пределах 1<=...>=VisibleRows + 1

        if (getChildCount() == 0 && FLAG_NOTIFY == NOTIFY_NONE){
            Log.d(TAG, "Empty layout detected. Views to be laid out: " + state.getItemCount());
            mAnchorRowPos = 1; mTopBound = mTopBaseline = mTopShift = 0;

            for (int index = 0; index < getItemCount() && rowCount <= mVisibleRows + 1; index++) { //Главный цикл. Выкладываемых строк больше, чем видимых
                int p = index + 1;//Переменная позиции для высчитывания оффсетов
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
        }
        /*
        Тут мы скрапаем какую-нибудь крайнюю вьюшку и строки, которые нужно сместить,
        затем добавляем вьюшку настроек в кэш и наполняем его недостающими вьюшками,
        и только в конце устанавливаем флаг видимости окна настроек
         */
        else if (FLAG_NOTIFY == LAYOUT_PREF){

            rearrangeChildren();
            prefView = recycler.getViewForPosition(prefPos);

            if ((mTopBaseline - mTopBound) >= mDecoratedTimeHeight){//При наличии одной полностью невидимой строки сверху, она ресайклится
                for (int i = (mAnchorRowPos - 1) * 3; i < mAnchorRowPos * 3; i++) {
                    Log.d(TAG, i + " scrapping, row: " + mAnchorRowPos);
                    detachAndScrapViewAt(0, recycler);//Метод берёт индекс вьюшки из разметки, а не из адаптера
                    mViewCache.remove(i);
                }
                mAnchorRowPos++; mTopBound += mDecoratedTimeHeight;
                mBottomBound += mDecoratedPreferencesHeight - mBaseVerticalPadding;
            }
            else if (mAvailableRows * mDecoratedTimeHeight > getHeight()) {//Во всех остальных случаях, кроме того, когда строк не хватает ресайклим последнюю строку
                Log.d (TAG, "Recycling last row");
                for (int i = (mLastVisibleRow - 1) * 3; i < mLastVisibleRow * 3 && i != getItemCount(); i++) {
                    Log.d(TAG, i + " scrapping , row: " + mLastVisibleRow);
                    detachAndScrapViewAt(getChildCount() - 1, recycler);//Берём индекс последней выложенной вьюшки
                    mViewCache.remove(i);
                }
                mBottomBound += mDecoratedPreferencesHeight - mBaseVerticalPadding - ((mVisibleRows - mExtendedVisibleRows) * mDecoratedTimeHeight);
            }

            int count = getChildCount();
            for (int i = (prefRowPos - mAnchorRowPos) * 3; i < count; i++){//Инкремент у нас в относительных значениях, а позиция вьюшки в абсолютных
                int v = (mAnchorRowPos - 1) * 3 + i;
                Log.d (TAG, "" + i + " " + v);

                View scrap = mViewCache.get(v);//Вытаскивам также по примеру из кэша, потому что в адаптере может быть хрен знает что
                detachAndScrapView(scrap, recycler);
                mViewCache.remove(v);
            }

            topOffset += ((mDecoratedTimeHeight * (prefRowPos - 1)) - mBaseVerticalPadding) - mTopBaseline;

            mViewCache.put(prefPos, prefView);
            addView(prefView);
            measureChild(prefView, 0, 0);
            layoutDecorated(prefView, leftOffset, topOffset,
                    leftOffset + mDecoratedPreferencesWidth,
                    topOffset + mDecoratedPreferencesHeight);

            topOffset += mDecoratedPreferencesHeight;
            rowCount = prefRowPos - mAnchorRowPos - 1;//Тут он как-то сдвинут вниз, хрен его знает, работает
            mLastVisibleRow = prefRowPos - 1;

            int p = 1;
            for (int i = (prefRowPos - 1) * 3 + 1; i < getItemCount() && rowCount < mExtendedVisibleRows; i++) {//Наполнять уже нужно привычным способом, не таким, которым отсоединяли
                Log.d (TAG, "adding shifted: " + i);
                View child = recycler.getViewForPosition(i);
                mViewCache.put(i, child);

                addView(child);
                measureChild(child, 0,0);
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
                    p = 1;
                }
            }
            prefVisibility = true;
        }

        else if (FLAG_NOTIFY == (HIDE_PREF | HIDE_N_LAYOUT_PREF)) {

            boolean firstWasRecycled = false;//todo в отличи от предыдущего случая, тут всегда добавляется последняя строка. Методы скролла от этого не обидятся, обещаю. Кэш повторно юзать нельзя
            for (int i = (mAnchorRowPos - 1) * 3; i < getItemCount() && rowCount <= mVisibleRows + 1; i++) {
                int p = i + 1;//Переменная позиции для высчитывания оффсетов
                if (i < 0 || i >= state.getItemCount()) { //Метод из класса State возвращает количество оставшихся Вьюшек, доступных для выкладки
                    //С его помощью будем выкладывать, пока не кончатся
                    continue;
                }
                Log.d (TAG, "getting " + i);
                View child = mViewCache.get(i);
                if (child == null){
                    child =  recycler.getViewForPosition(i);
                    mViewCache.put(i, child);

                    firstWasRecycled = true;
                }

                addView(child);
                measureChild(child, 0, 0);
                layoutDecorated(child, leftOffset, topOffset,
                        leftOffset + mDecoratedTimeWidth,
                        topOffset + mDecoratedTimeHeight);

                if (p < 3 || p % 3 != 0) leftOffset += mDecoratedTimeWidth;
                else if (p % 3 == 0) {
                    topOffset += mDecoratedTimeHeight;
                    leftOffset = paddingLeft;
                    rowCount++;
                }
            }

            detachAndScrapView(prefView, recycler);

            if (!firstWasRecycled) {//Если не ресайклили первую строку, значит, ресайклили последнюю
                offsetChildrenVertical(-(mTopBaseline - ((mAnchorRowPos - 1) * mDecoratedTimeHeight)));//Топовую базовую линию пересчитываем только после скролла
                mBottomBound = topOffset + getPaddingBottom();//Берём сумму всех сдвигов в процессе выкладки плюс нижний отступ так, чтобы получалось вплотную до следующей строки
                mLastVisibleRow++;
            }
            else {

            }


        }
        mBottomBaseline = getHeight() + mTopBaseline;//Базовую линию всегда считаем относительно топовой
        Log.d(TAG, "Anchor row: " + mAnchorRowPos + ", Last row: " + mLastVisibleRow + ", bottom baseline: " + mBottomBaseline + ", bottom bound: " + mBottomBound);
    }

    @Override
    public boolean canScrollVertically() {
        return mAvailableRows * mDecoratedTimeHeight > getHeight();
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
                else if (delta <= dy && mLastVisibleRow < mAvailableRows)  {
                    mBottomBound += mDecoratedTimeHeight; mTopBound += mDecoratedTimeHeight;//Даём первой строке стать частично невидимой и держим границу по ней
                    offset = dy; mBottomBaseline += dy; mTopBaseline += dy;
                    Log.d (TAG, "AddNRecycle DOWN, former pos: " + mAnchorRowPos + " " + mLastVisibleRow);
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

                else if (delta >= dy && mAnchorRowPos > 1) { //Меньше первой строки у нас нет
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
        mTopShift += offset;
        return offset;
    }

    private void rearrangeChildren() {
        for (int i = 0; i < getChildCount(); i++) {//Для правельной переработки и добавления строк,
            //сначала нам нужно переназначить индексы дочерних вьюшек, которые уже выложены,
            //и заодно обновить кэш
            int v = (mAnchorRowPos - 1) * 3 + i;
            Log.d(TAG, "Row " + mAnchorRowPos + ", taking " + v + ", setting " + i);

            View view;
            if (v == prefPos && prefVisibility) view = prefView;//Берём отдельно вьшку настроек, когда до неё доходит
            else view = mViewCache.get(v);//Нужно взять из кэша все выложенные вьюшки по одной, согласно их нормальным индексам, которые у них в адаптере
            detachView(view);
            attachView(view, i);
        }
    }
/*
 */
    void addNRecycle (RecyclerView.Recycler recycler, int direction, int joint){//TODO при переходе со статичной выкладки на скроллинг, происходит короткое зависание

        int leftOffset = getPaddingLeft();
        int topOffset;

        rearrangeChildren();

        switch (direction){
            case (DIR_DOWN):

                topOffset = joint;
                mLastVisibleRow++;

                for (int i = (mAnchorRowPos - 1) * 3; i < mAnchorRowPos * 3; i++) {
                    Log.d(TAG, i + " scrapping, row: " + mAnchorRowPos);
                    detachAndScrapViewAt(0, recycler);//Метод берёт индекс вьюшки из разметки, а не из адаптера
                    mViewCache.remove(i);
                }

                for (int i = (mLastVisibleRow - 1) * 3; i < mLastVisibleRow * 3 && i != getItemCount(); i++){
                    Log.d (TAG, i + " adding row: " + mLastVisibleRow);

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
                        Log.d(TAG, i + " scrapping , row: " + mLastVisibleRow);
                        detachAndScrapViewAt(getChildCount() - 1, recycler);//Берём индекс последней выложенной вьюшки
                        mViewCache.remove(i);
                    }

                for (int i = (mAnchorRowPos - 1) * 3; i < mAnchorRowPos * 3; i++){
                    Log.d (TAG, i + " adding row: " + mAnchorRowPos);

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
        Log.d (TAG, "Cache filled: " + mViewCache.size());
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
            //int count = getChildCount() - 1; if (prefVisibility) count--;
            Log.d (TAG, "Row " + mAnchorRowPos + ", Top shift: " + mTopShift + ", first cache index: " + mViewCache.keyAt(0) + ", last cache index: " + mViewCache.keyAt(getChildCount()-1));
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
                repoCallback.passPrefToAdapter(prefParentPos, prefPos);
                Log.d (TAG, "Laying out pref");
            }
            else if (this.prefParentPos != 666 && this.prefParentPos == prefParentPos && prefVisibility){//Настройки уже выкладывались, старая материнская позиция, строку видно
                FLAG_NOTIFY = HIDE_PREF;
                repoCallback.removePref();
                Log.d (TAG, "Hiding pref");

                prefVisibility = false;
            }
            else if (this.prefParentPos != 666 && this.prefParentPos != prefParentPos && prefVisibility){//Настройки уже выкладывались, новая материнская позиция, строку уже видно
                oldPrefParentPos = this.prefParentPos;
                oldPrefRowPos = this.prefRowPos;
                oldPrefPos = this.prefPos;

                FLAG_NOTIFY = HIDE_N_LAYOUT_PREF;
                repoCallback.removePref();
                Log.d (TAG, "Hiding and laying out pref");

                this.prefParentPos = prefParentPos;
                if (prefParentPos >= oldPrefPos) this.prefParentPos--;//Если мы тыкаем на элемент, который идёт после строки с настройками, то нужно бы откорректировать позицию на 1 вниз
                //Если строка с материнским элементом не полная, либо элементов в раскладке всего не больше трёх, то добавляем одну строку к счётчику
                prefRowPos = ((prefParentPos+1) / 3) + 1; if ((prefParentPos+1) % 3 !=0 || (prefParentPos+1) < 3) prefRowPos++;//Строка после материнской вьюшки. На которой будем выкладывать настройки
                prefPos = ((prefRowPos-1) * 3); if (prefPos>getItemCount()) prefPos = getItemCount();//Позиция вьюшки настроек в адаптере
                Log.d (TAG, "___prefParentPos:" + this.prefParentPos + " prefRowPos:" + prefRowPos + " prefPos:" + prefPos);

                repoCallback.passPrefToAdapter(prefParentPos, prefPos);

                prefVisibility = true;
            }
    }

}
