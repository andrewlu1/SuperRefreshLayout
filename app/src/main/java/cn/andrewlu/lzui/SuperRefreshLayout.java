package cn.andrewlu.lzui;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.AbsListView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.Scroller;

import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.List;

/**
 * 实现通用的上拉下拉布局控件。 设计原则，其中只能有一个子控件。否则将抛出异常。可以使用ScrollView包起来。
 * 目前测试可用的子控件有：ScrollVew.普通控件如ImageView,TextView,...ListView,GridView.
 *
 * @author andrewlu 2015.12.27.
 */
public final class SuperRefreshLayout extends FrameLayout {
    private Scroller mScroller = null;

    // private PointF mLastTipPoint = new PointF(0,0);
    private float mLastStartY = 0;
    // 控制手指在界面上的灵敏度。目前不提供公共访问方法。0.3f使拖拽的距离不会太长。
    private float scrollFactor = 0.4f;// 值越大，界面偏离的距离会越长。
    // 是否是向上拖拽，即上拉加载。
    private boolean isUpRefresh = false;
    // 生成事件的最终距离，也是头或尾的高度。
    private int MIN_DISTANCE = 200;
    // 当前刷新状态，控制正在刷新时，不能再次刷新。
    boolean isRefreshing = false;
    // 唯一子控件的引用。在附加到界面上时会自动赋值。不能再调用addView等函数。
    private View mChildView = null;
    // 当已经按上去的时候不能再改变lastMovePointY的值。
    private int mEvents = 0;

    // 构造函数======================================
    public SuperRefreshLayout(Context context) {
        super(context);
        init();
    }

    public SuperRefreshLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SuperRefreshLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    // 构造函数======================================

    private void init() {
        // 主要用于控制放手后的反弹效果。
        mScroller = new Scroller(getContext(), new DecelerateInterpolator(2));
        initMinDistance();
    }

    private boolean isAttachedToWindow = false;

    // 开始初始化此控件时的调用。此时可以引用其中的子控件了。
    @Override
    protected void onAttachedToWindow() {
        // TODO Auto-generated method stub
        super.onAttachedToWindow();

        // 如果没有子控件还刷新个鬼啊。
        if (getChildCount() != 1) {
            throw new RuntimeException("SuperRefreshLayout cannot contain more than one child!");
        }
        // if (getChildCount() > 0) {
        mChildView = getChildAt(0);
        initCallbacks();

        // 初始化头和尾。
        initHeaderAndFooter();

        isAttachedToWindow = true;
        if (isNeedDelayStartRefreshing) {
            startRealRefreshing();
            isNeedDelayStartRefreshing = false;
        }
    }

    private void initMinDistance() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        if (metrics != null && metrics.heightPixels > 0) {
            MIN_DISTANCE = metrics.heightPixels / 8;
        }
    }

    // computerScroll和scrollTo形成完美的相互调用，以此来让界面进行平滑的滚动。
    // 隐含的是要求有invalidate触发。
    @Override
    public final void computeScroll() {
        // 还没滚动完成。
        if (mScroller.computeScrollOffset()) {
            scrollTo(mScroller.getCurrX(), mScroller.getCurrY());
        }
    }

    // 根据手指移动的Y轴距离去滚动自动。
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        //Log.e("OnTouchEvent", event.toString());
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                // 记录上一次手指按下的距离。
                // 此应该是在interceptTouch中操作，因为有可能会被子控件消耗掉此事件。但目前没有测出问题。
                mLastStartY = event.getY();
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                // 如果当前已经在刷新了，不管手指怎么滑动都不再滚动屏幕了。
                if (!isRefreshing) {
                    // 限制拉到多少距离就不能再拉了。
//                    if (getScrollY() >= MIN_DISTANCE * 2 || getScrollY() <= -MIN_DISTANCE * 2) {
//                        break;
//                    }
                    // 计算一个差值。并滚动这么多距离。
                    int dy = (int) ((mLastStartY - event.getY()) * scrollFactor);
                    if (MIN_DISTANCE <= Math.abs(dy)) // dy太大，有可能是跳跃性移动。因此忽略此值。
                        break;
                    // if (canMove(dy))
                    // {//由于intercept已经处理了是否该向子控件发送此事件。因此这里不再处理。
                    scrollBy(0, dy);
                    // 生成拖拽事件，用于头或尾的动画
                    onDragging(getScrollY() > 0, Math.abs(getScrollY() * 1.0f / MIN_DISTANCE));
                    mLastStartY = event.getY();
                }
                break;
            }
            case MotionEvent.ACTION_UP: {
                //当这里初始抬起时才触发刷新动作。
                if (!isRefreshing) {
                    startRealRefreshing();
                }
                isDragging = false;
                break;
            }

        }
        return false;
    }

    // 通过intercept拦截发向子控件的事件。
    // 并在canMove中判断子控件是否需要滑动事件。
    private float lastMovePointY = 0;
    private boolean isDragging = false;
    private final int MIN_SLOP = 10;

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        Log.e("onInterceptTouchEvent", ev.toString());
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                lastMovePointY = ev.getY();
                mLastStartY = ev.getY();
                mEvents = 0;
                break;
            }
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_POINTER_UP: {
                mEvents = -1;
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                if (mEvents == 0) {
                    float dy = ev.getY() - lastMovePointY;
                    if (Math.abs(dy) < MIN_SLOP) {
                        return false;
                    } else {
                        isDragging = true;
                        lastMovePointY = ev.getY();
                        return canMove(dy);
                    }
                } else {
                    mEvents = 0;
                }
                break;
            }
            case MotionEvent.ACTION_UP: {
                //当正在刷新时，up事件需要被消费掉。
                if (isRefreshing || isDragging) {
                    isDragging = false;
                    return true;
                }
            }
        }
        return super.onInterceptTouchEvent(ev);
    }

    private void scrollBack() {
        smothScrollBy(0, -getScrollY());
    }

    private void smothScrollBy(int dx, int dy) {
        mScroller.startScroll(getScrollX(), getScrollY(), dx, dy, 300);
        postInvalidate();// 这一句保证computerScroll一定执行。
    }

    // 直接调用进行上拉加载
    public final void startUpRefreshing() {
        if (isRefreshing)
            return;
        isRefreshing = true;
        mScroller.forceFinished(true);
        // 直接滚动到某一距离。
        scrollBy(0, MIN_DISTANCE - (int) getScrollY());
        startRealRefreshing();
    }

    private boolean isNeedDelayStartRefreshing = false;

    // 直接调用进行刷新。
    public final void startDownRefreshing() {
        if (isRefreshing)
            return;
        isRefreshing = true;
        mScroller.forceFinished(true);
        // 直接滚动到某一距离。
        scrollBy(0, -MIN_DISTANCE - (int) getScrollY());
        startRealRefreshing();
    }

    // 控制可以向上滚动。
    private boolean mUpEnable = true;

    public void setUpEnable(boolean enable) {
        mUpEnable = enable;
    }

    // 控制可以向下滚动。
    private boolean mDownEnable = true;

    public void setDownEnable(boolean enable) {
        mDownEnable = enable;
    }

    // 开始操作界面进行UI动画。
    private void startRealRefreshing() {
        if (!isAttachedToWindow) {
            isNeedDelayStartRefreshing = true;
            return;
        } else {
            isNeedDelayStartRefreshing = false;
        }
        if (!checkIsUpOrDown()) {
            scrollBack();
            return;
        }
        if (isUpRefresh) {
            Log.i("SuperRefreshLayout", "需要向下滚动:" + (MIN_DISTANCE - getScrollY()));
            smothScrollBy(0, (MIN_DISTANCE - getScrollY()));
        } else {
            Log.i("SuperRefreshLayout", "需要向上滚动:" + (-getScrollY() - MIN_DISTANCE));
            smothScrollBy(0, (-getScrollY() - MIN_DISTANCE));
        }
        startTimeMills = System.currentTimeMillis();
        if (isUpRefresh) {
            if (footerAnimator != null) {
                footerAnimator.start();
            }
        } else {
            if (headerAnimator != null) {
                headerAnimator.start();
            }
        }
        if (mOnRefreshListener != null) {
            mOnRefreshListener.OnRefresh(SuperRefreshLayout.this, isUpRefresh);
        }
    }

    // 判断自己是否能够滑动。根据子控件的类型进行判断。
    private boolean canMove(float dy) {
        if (mChildView == null)
            return true;

        // 总控制。
        boolean result = false;
        for (MovableCallback callback : mCallbacks) {
            Boolean ret = callback.canMove(mChildView, (int) dy);
            if (ret == null)
                continue;
            result = ret;
            break;
        }
        //只有当子控件允许继续滚动时才进行禁止控制。
        if (result == true) {
            if (dy < 0) {
                result = mUpEnable;
            } else if (dy > 0) {
                result = mDownEnable;
            }
        }
        return result;
    }

    // 用来添加其他支持的控件的移动判断。
    // 实现canMove方法，在其中根据child的当前状态判断父控件是否能够移动。
    // direction>0表示要向下继续拖动，<0表示要向上继续上拉。
    // 返回true表示父控件可以继续滚动，false表示不可以，null表示未处理，需要交给其他callback处理。
    public interface MovableCallback {
        public Boolean canMove(View child, int direction);
    }

    private static List<MovableCallback> mCallbacks = new LinkedList<MovableCallback>();

    public final static void registerMovableCallback(MovableCallback callback) {
        if (callback != null)
            mCallbacks.add(callback);
    }

    private void initCallbacks() {
        MovableCallback absListViewCallback = new MovableCallback() {
            @Override
            public Boolean canMove(View child, int direction) {
                // TODO Auto-generated method stub
                if (child instanceof AbsListView) {
                    AbsListView list = (AbsListView) child;
                    if (list.getChildCount() == 0)
                        return true;
                    else if (direction > 0 && list.getFirstVisiblePosition() == 0 && list.getChildAt(0).getTop() >= 0) {
                        return true;
                    } else if (direction < 0 && list.getLastVisiblePosition() == (list.getCount() - 1)) {
                        if (list.getChildAt(list.getLastVisiblePosition() - list.getFirstVisiblePosition()) != null
                                && list.getChildAt(list.getLastVisiblePosition() - list.getFirstVisiblePosition())
                                .getBottom() <= list.getMeasuredHeight())
                            return true;
                    } else {
                        return false;
                    }
                }
                return null;
            }
        };
        registerMovableCallback(absListViewCallback);
        MovableCallback generalCallback = new MovableCallback() {
            @Override
            public Boolean canMove(View child, int direction) {
                return ((direction > 0) && !canChildScrollVertically(mChildView, -1))
                        || ((direction < 0) && !canChildScrollVertically(mChildView, 1));
            }
        };
        registerMovableCallback(generalCallback);
    }

    // 判断滚动距离是否达到标准。
    private boolean checkIsNeedUpdate() {
        return getScrollY() >= MIN_DISTANCE || getScrollY() <= -MIN_DISTANCE;
    }

    // 判断是否是向下拖动，还是向上拖动。
    private boolean checkIsUpOrDown() {
        //if (checkIsNeedUpdate()) {
        if (getScrollY() > MIN_DISTANCE) {
            isUpRefresh = true;
        } else if (getScrollY() < -MIN_DISTANCE) {
            isUpRefresh = false;
        } else {
            return false;
        }
        isRefreshing = true;
        return true;
    }

    private long startTimeMills = 0;

    // 当在OnRefresh中刷新完成时，调用此函数即可完成刷新动作。
    public void finishRefresh() {
        long duration = 1000 - (System.currentTimeMillis() - startTimeMills);
        if (duration < 0) duration = 0;
        postDelayed(new Runnable() {

            @Override
            public void run() {
                // TODO Auto-generated method stub
                finishRefreshOnMain();
            }
        }, duration);
    }

    private void finishRefreshOnMain() {
        isUpRefresh = false;
        isRefreshing = false;
        scrollBack();
        if (headerAnimator != null) {
            headerAnimator.cancel();
        }
        if (footerAnimator != null) {
            footerAnimator.cancel();
        }
    }

    // 回调接口。不能做耗时操作。异步回调中调用finishRefreshAPI.
    public interface OnRefreshListener {
        void OnRefresh(SuperRefreshLayout refresher, boolean up);
    }

    private OnRefreshListener mOnRefreshListener = null;

    public final void setOnRefreshListener(OnRefreshListener listener) {
        this.mOnRefreshListener = listener;
    }

    // 在这里边做拖动时的动画。
    private final void onDragging(boolean isUp, float dragDegree) {
        // Log.i("onDragging", dragDistance + "");
        if (mDraggingListener != null) {
            mDraggingListener.onDragging(isUp, isUp ? footerView : headerView, dragDegree);
        }
    }

    public interface OnDraggingListener {
        // isUp表示向上还是向下滑。headerOrFooter表示当前显示的头或尾的引用，percent>0<1,表示滑动距离比例。
        void onDragging(boolean isUp, View headerOrFooter, float dragPercent);
    }

    private OnDraggingListener mDraggingListener = new OnDraggingListener() {

        @Override
        public void onDragging(boolean isUp, View headerOrFooter, float dragDegree) {
            // TODO Auto-generated method stub
            if (!isUp && headerOrFooter != null && headerOrFooter instanceof LevelImageView) {
                ((LevelImageView) headerOrFooter).setImageLevel((int) (dragDegree * 10) % 11);
            }
            if (isUp && headerOrFooter != null) {
                headerOrFooter.setRotation(dragDegree * 360);
            }
            headerOrFooter.setAlpha(dragDegree);
        }
    };

    public final void setOnDraggingListener(OnDraggingListener listener) {
        this.mDraggingListener = listener;
    }

//    public final void setHeaderView(int layoutId) {
//
//        if (layoutId > 0) {
//            if (headerView != null) {
//                mHeaderLaout.removeView(headerView);
//            }
//            headerView = View.inflate(getContext(), layoutId, null);
//            LayoutParams p = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
//            p.gravity = Gravity.CENTER_HORIZONTAL|Gravity.BOTTOM;
//            p.bottomMargin = 20;
//            mHeaderLaout.addView(headerView);
//        }
//    }

//    public final void setFooterView(int footerId) {
//        if (footerId > 0) {
//            if (footerView != null) {
//                mFootLayout.removeView(footerView);
//            }
//            footerView = View.inflate(getContext(), footerId, null);
//            LayoutParams p = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
//            p.gravity = Gravity.CENTER_HORIZONTAL|Gravity.TOP;
//            p.topMargin  = 20;
//            mFootLayout.addView(footerView, p);
//        }
//    }

    private FrameLayout mHeaderLaout = null;
    private FrameLayout mFootLayout = null;
    private View headerView;
    private View footerView;

    // 给头或尾设置动画。
    private ObjectAnimator headerAnimator = null;

    public final ObjectAnimator getHeaderAnimator() {
        return headerAnimator;
    }

    public final void setHeaderAnimator(ObjectAnimator headerAnimator) {
        this.headerAnimator = headerAnimator;
    }

    public final ObjectAnimator getFooterAnimator() {
        return footerAnimator;
    }

    public final void setFooterAnimator(ObjectAnimator footerAnimator) {
        this.footerAnimator = footerAnimator;
    }

    private ObjectAnimator footerAnimator = null;

    // 初始化头和尾。只能调用一次。
    private void initHeaderAndFooter() {
        createDefaultHeader();
        LayoutParams headerParam = new LayoutParams(LayoutParams.MATCH_PARENT,
                MIN_DISTANCE);
        headerParam.gravity = Gravity.TOP;
        headerParam.topMargin = -MIN_DISTANCE;
        addView(mHeaderLaout, headerParam);

        createDefaultFooter();
        LayoutParams footerParam = new LayoutParams(LayoutParams.MATCH_PARENT,
                MIN_DISTANCE);
        footerParam.gravity = Gravity.BOTTOM;
        footerParam.bottomMargin = -MIN_DISTANCE;
        addView(mFootLayout, footerParam);
    }

    // 实现默认的头视图，用FrameLayout包起来。
    private void createDefaultHeader() {
        mHeaderLaout = new FrameLayout(getContext());
        LayoutParams p = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (MIN_DISTANCE * 0.8f));
        p.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
        p.bottomMargin = 10;

        LevelImageView imageView = new LevelImageView(getContext());
        imageView.setImageResource(R.drawable.drawable_refresh_image);
        imageView.setScaleType(ScaleType.CENTER_INSIDE);
        mHeaderLaout.addView(imageView, p);

        headerAnimator = ObjectAnimator.ofInt(imageView, "imageLevel", 1, 11);
        headerAnimator.setRepeatCount(ObjectAnimator.INFINITE);
        headerAnimator.setInterpolator(new LinearInterpolator());
        headerAnimator.setRepeatMode(ObjectAnimator.RESTART);
        headerAnimator.setDuration(600);
        headerView = imageView;
    }

    // 添加默认的尾视图。用FrameLayout包起来。
    private void createDefaultFooter() {
        mFootLayout = new FrameLayout(getContext());
        LayoutParams p = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (MIN_DISTANCE * 0.5f));
        p.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
        p.topMargin = 10;

        ImageView imageView = new ImageView(getContext());
        imageView.setImageResource(R.drawable.drawable_progress);
        imageView.setScaleType(ScaleType.CENTER_INSIDE);
        mFootLayout.addView(imageView, p);

        footerAnimator = ObjectAnimator.ofFloat(imageView, "rotation", 0, 360f);
        footerAnimator.setRepeatCount(ObjectAnimator.INFINITE);
        footerAnimator.setInterpolator(new LinearInterpolator());
        footerAnimator.setRepeatMode(ObjectAnimator.RESTART);
        footerAnimator.setDuration(700);
        footerView = imageView;
    }

    // ====================================================
    // 以下是反射调用得出子控件是否能滚动的方法。主要是scrollView重写了些方法。
    // ===================================================
    private boolean canChildScrollVertically(View child, int direction) {
        final int offset = child.getScrollY();
        final int range = getVerticalScrollRange(child) - getVerticalScrollExtent(child);
        if (range == 0)
            return false;
        if (direction < 0) {
            return offset > 0;
        } else {
            return offset < range - 1;
        }
    }

    // 这个方法用于View计算垂直滚动的额外距离。但属于protect方法，因此使用反射调用。
    private int getVerticalScrollExtent(View view) {
        if (computeVerticalScrollExtent == null) {
            computeVerticalScrollExtent = computeVerticalScrollExtent(view);
            if (computeVerticalScrollExtent != null) {
                computeVerticalScrollExtent.setAccessible(true);
            }
        }
        if (computeVerticalScrollExtent != null) {
            try {
                Object o = computeVerticalScrollExtent.invoke(view, (Object[]) null);
                return (Integer) o;
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        return 0;
    }

    // 这个方法用于View计算垂直滚动的额外距离。但属于protect方法，因此使用反射调用。
    private int getVerticalScrollRange(View view) {
        if (computeVerticalScrollRange == null) {
            computeVerticalScrollRange = computeVerticalScrollRange(view);
            if (computeVerticalScrollRange != null) {
                computeVerticalScrollRange.setAccessible(true);
            }
        }
        if (computeVerticalScrollRange != null) {
            try {
                Object o = computeVerticalScrollRange.invoke(view, (Object[]) null);
                return (Integer) o;
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        return 0;
    }

    private static Method computeVerticalScrollRange(View view) {
        return findMethodWithView(view, "computeVerticalScrollRange", null);
    }

    // 这个方法用于View计算垂直滚动的额外距离。但属于protect方法，因此使用反射调用。
    private static Method findMethodWithView(View view, String methodName, Class[] params) {
        try {
            Method[] methods = view.getClass().getDeclaredMethods();
            _nextMethod:
            for (Method method : methods) {
                if (methodName.equals(method.getName())) {
                    Class[] dParams = method.getParameterTypes();
                    if ((params == null || params.length == 0) && dParams.length == 0)
                        return method;
                    if (params.length != dParams.length)
                        continue;
                    for (int i = 0; i < dParams.length; i++) {
                        if (!dParams[i].equals(params[i]))
                            continue _nextMethod;
                    }
                    return method;
                }
            }
            if (view.getClass().getName().equals(View.class.getName()))
                return null;
            methods = View.class.getDeclaredMethods();
            _nextMethod1:
            for (Method method : methods) {
                if (methodName.equals(method.getName())) {
                    Class[] dParams = method.getParameterTypes();
                    if ((params == null || params.length == 0) && dParams.length == 0)
                        return method;
                    if (params.length != dParams.length)
                        continue;
                    for (int i = 0; i < dParams.length; i++) {
                        if (!dParams[i].getName().equals(params[i].getName()))
                            continue _nextMethod1;
                    }
                    return method;
                }
            }
        } catch (Exception e) {
        }
        return null;
    }

    // 这个方法用于View计算垂直滚动的额外距离。但属于protect方法，因此使用反射调用。
    private static Method computeVerticalScrollExtent(View view) {
        return findMethodWithView(view, "computeVerticalScrollExtent", null);
    }

    // 在什么时候初始化呢。子控件一旦初始化，类型就固定了，因些这个数据也固定起来。只初始化一次。
    private Method computeVerticalScrollRange = null;
    private Method computeVerticalScrollExtent = null;

    // 提供一个用于属性动画操作的图片类。imageLevel不属于ImageView的属性，因此只能自己定义此属性。
    // 并通过属性动画进行修改。
    public static class LevelImageView extends ImageView {

        public LevelImageView(Context context) {
            super(context);
            // TODO Auto-generated constructor stub
        }

        private int imageLevel = 0;

        public void setImageLevel(int level) {
            if (this.imageLevel == level)
                return;
            super.setImageLevel(level);
            this.imageLevel = level;
        }

        public int getImageLevel() {
            return imageLevel;
        }

        // 下一level接口。
        public void nextLevel() {
            setImageLevel(imageLevel++ % maxLevel);
        }

        private int maxLevel = 10;

        public void setMaxLevel(int maxLevel) {
            this.maxLevel = maxLevel;
        }
    }
}
