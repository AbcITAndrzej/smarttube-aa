package com.liskovsoft.smartyoutubetv2.common.misc;

import android.app.Activity;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.TextView;

import com.liskovsoft.smartyoutubetv2.common.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Touch compatibility layer for the mobile SmartTube flavor.
 *
 * The retained TV interface is focus/DPAD driven. This class translates phone
 * gestures into focus commands, but taps are resolved against an expanded
 * logical touch target so small Leanback controls remain usable by finger.
 */
public final class MobileTouchController implements MobileGestureInterpreter.Listener {
    private final Activity mActivity;
    private final boolean mHapticFeedbackEnabled;
    private final MobileGestureInterpreter mGestureInterpreter;
    private final int mMinimumTouchTargetPx;
    private boolean mHandlingSequence;
    private boolean mPassThroughSequence;

    public MobileTouchController(Activity activity) {
        mActivity = activity;
        MobileDiagnostics.lifecycle(activity, "touch-controller-create", null);

        float density = activity.getResources().getDisplayMetrics().density;
        ViewConfiguration configuration = ViewConfiguration.get(activity);
        float touchSlop = configuration.getScaledTouchSlop();
        float navigationStep = dp(
                activity.getResources().getInteger(R.integer.mobile_navigation_step_dp), density);
        float edgeWidth = dp(
                activity.getResources().getInteger(R.integer.mobile_edge_width_dp), density);
        float edgeBackTrigger = dp(
                activity.getResources().getInteger(R.integer.mobile_edge_back_trigger_dp), density);

        mMinimumTouchTargetPx = Math.round(dp(
                activity.getResources().getInteger(R.integer.mobile_min_touch_target_dp), density));
        mHapticFeedbackEnabled = activity.getResources()
                .getBoolean(R.bool.mobile_haptic_feedback_enabled);
        mGestureInterpreter = new MobileGestureInterpreter(
                touchSlop,
                navigationStep,
                edgeWidth,
                edgeBackTrigger,
                ViewConfiguration.getLongPressTimeout(),
                this);
    }

    /**
     * @return true when the touch sequence belongs to the mobile compatibility
     * navigation layer; false lets Android deliver it to a native touch control.
     */
    public boolean onTouchEvent(MotionEvent event) {
        if (event == null) {
            return false;
        }

        int action = event.getActionMasked();
        int pointerCount = event.getPointerCount();
        long eventTime = event.getEventTime();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mPassThroughSequence = shouldUseNativeTouch(event.getRawX(), event.getRawY());
                if (mPassThroughSequence) {
                    mHandlingSequence = false;
                    return false;
                }
                mHandlingSequence = mGestureInterpreter.onDown(
                        event.getRawX(), event.getRawY(), eventTime, pointerCount);
                return mHandlingSequence;
            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_POINTER_UP:
                if (mPassThroughSequence) {
                    return false;
                }
                if (mHandlingSequence) {
                    mGestureInterpreter.onMove(
                            event.getRawX(), event.getRawY(), eventTime, pointerCount);
                }
                return mHandlingSequence;
            case MotionEvent.ACTION_UP:
                if (mPassThroughSequence) {
                    mPassThroughSequence = false;
                    return false;
                }
                if (mHandlingSequence) {
                    mGestureInterpreter.onUp(
                            event.getRawX(), event.getRawY(), eventTime, pointerCount);
                    mHandlingSequence = false;
                    return true;
                }
                return false;
            case MotionEvent.ACTION_CANCEL:
                if (mPassThroughSequence) {
                    mPassThroughSequence = false;
                    return false;
                }
                boolean handled = mHandlingSequence;
                mGestureInterpreter.onCancel();
                mHandlingSequence = false;
                return handled;
            default:
                return mHandlingSequence && !mPassThroughSequence;
        }
    }

    public void cancel() {
        MobileDiagnostics.lifecycle(mActivity, "touch-controller-cancel", null);
        mGestureInterpreter.onCancel();
        mHandlingSequence = false;
        mPassThroughSequence = false;
    }

    @Override
    public void onCommand(MobileGestureInterpreter.Command command, float x, float y) {
        MobileDiagnostics.debug("Touch", command + " @ " + Math.round(x) + "," + Math.round(y));
        switch (command) {
            case UP:
                sendKey(KeyEvent.KEYCODE_DPAD_UP);
                break;
            case DOWN:
                sendKey(KeyEvent.KEYCODE_DPAD_DOWN);
                break;
            case LEFT:
                sendKey(KeyEvent.KEYCODE_DPAD_LEFT);
                break;
            case RIGHT:
                sendKey(KeyEvent.KEYCODE_DPAD_RIGHT);
                break;
            case BACK:
                hapticFeedback();
                mActivity.onBackPressed();
                break;
            case MENU:
                sendKey(KeyEvent.KEYCODE_MENU);
                break;
            case TAP:
                performTap(x, y);
                break;
            default:
                throw new IllegalStateException("Unknown mobile command: " + command);
        }
    }

    private void performTap(float rawX, float rawY) {
        View root = getRootView();
        View target = findBestTarget(root, rawX, rawY, true);
        target = promoteNestedPreferenceWidget(target);

        if (target != null) {
            if (!target.isFocused() && target.isFocusable()) {
                target.requestFocus();
            }

            if (target.isClickable() && target.performClick()) {
                hapticFeedback();
                return;
            }
        }

        sendKey(KeyEvent.KEYCODE_DPAD_CENTER);
    }

    private View promoteNestedPreferenceWidget(View target) {
        if (target == null) {
            return null;
        }

        String className = target.getClass().getName();
        if (!MobilePreferenceTouchPolicy.shouldPromoteToClickableParent(className)) {
            return target;
        }

        ViewParent parent = target.getParent();
        while (parent instanceof View) {
            View parentView = (View) parent;
            if (parentView.isShown() && parentView.isEnabled() && parentView.isClickable()) {
                return parentView;
            }
            parent = parentView.getParent();
        }
        return target;
    }

    private boolean shouldUseNativeTouch(float rawX, float rawY) {
        View root = getRootView();
        View target = findDeepestViewAt(root, rawX, rawY);

        if (target == null) {
            target = findBestTarget(root, rawX, rawY, false);
        }
        if (target == null) {
            return false;
        }

        // P11: Leanback lists are RecyclerViews. Part9 consumed their complete
        // DOWN/MOVE/UP sequence and translated movement to DPAD presses, which
        // made real one-finger scrolling impossible. Let the native scrollable
        // ancestor receive the original gesture instead. Leanback item views
        // retain their own click listeners, so a normal tap still opens a card.
        if (isInsideNativeScrollableView(target)) {
            return true;
        }

        // These controls need the original down/move/up sequence for cursor,
        // dragging, text selection, or pinch/scroll behavior. Class-name checks
        // avoid introducing extra module dependencies into :common.
        if (target instanceof TextView && ((TextView) target).getMovementMethod() != null) {
            return true;
        }

        String className = target.getClass().getName();
        return className.contains("EditText")
                || className.contains("SeekBar")
                || className.contains("NumberPicker")
                || className.contains("WebView")
                || className.contains("SurfaceView")
                || className.contains("TextureView");
    }

    private boolean isInsideNativeScrollableView(View target) {
        View current = target;

        while (current != null) {
            String className = current.getClass().getName();

            if (className.contains("RecyclerView")
                    || className.contains("VerticalGridView")
                    || className.contains("HorizontalGridView")
                    || className.contains("BaseGridView")
                    || className.contains("NestedScrollView")
                    || className.contains("ScrollView")
                    || className.contains("ListView")
                    || className.contains("GridView")
                    || className.contains("ViewPager")) {
                tuneNativeScrollableView(current, className);
                return true;
            }

            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }

        return false;
    }

    private void tuneNativeScrollableView(View view, String className) {
        // Android 12+ stretch overscroll can pull a short Leanback grid far
        // enough that the complete History/Settings content appears to vanish.
        view.setOverScrollMode(View.OVER_SCROLL_NEVER);
        view.setVerticalFadingEdgeEnabled(false);
        view.setHorizontalFadingEdgeEnabled(false);

        if (!className.contains("GridView")) {
            return;
        }

        // BaseGridView.WINDOW_ALIGN_BOTH_EDGE == 3. Reflection keeps :common
        // independent from the Leanback module while clamping first and last
        // items to the real viewport edges.
        try {
            view.getClass().getMethod("setWindowAlignment", int.class).invoke(view, 3);
        } catch (ReflectiveOperationException ignored) {
            // Non-Leanback GridView or an older implementation.
        }
    }

    private View findDeepestViewAt(View view, float rawX, float rawY) {
        if (view == null || !view.isShown() || view.getWidth() <= 0 || view.getHeight() <= 0) {
            return null;
        }

        int[] location = new int[2];
        view.getLocationOnScreen(location);

        if (rawX < location[0]
                || rawY < location[1]
                || rawX >= location[0] + view.getWidth()
                || rawY >= location[1] + view.getHeight()) {
            return null;
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;

            // Iterate from the visually topmost child.
            for (int i = group.getChildCount() - 1; i >= 0; i--) {
                View child = findDeepestViewAt(group.getChildAt(i), rawX, rawY);
                if (child != null) {
                    return child;
                }
            }
        }

        return view;
    }

    private View findBestTarget(View root, float rawX, float rawY, boolean expandTouchArea) {
        if (root == null || !root.isShown()) {
            return null;
        }

        List<View> views = new ArrayList<>();
        List<MobileTouchTargetResolver.Candidate> candidates = new ArrayList<>();
        collectCandidates(root, views, candidates, 0, new int[]{0});

        int minimum = expandTouchArea ? mMinimumTouchTargetPx : 1;
        int index = MobileTouchTargetResolver.findBest(candidates, rawX, rawY, minimum);
        return index >= 0 && index < views.size() ? views.get(index) : null;
    }

    private void collectCandidates(
            View view,
            List<View> views,
            List<MobileTouchTargetResolver.Candidate> candidates,
            int depth,
            int[] drawingOrder) {
        if (view == null || !view.isShown() || view.getWidth() <= 0 || view.getHeight() <= 0) {
            return;
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectCandidates(
                        group.getChildAt(i), views, candidates, depth + 1, drawingOrder);
            }
        }

        if (!view.isClickable() && !view.isFocusable()) {
            return;
        }

        int[] location = new int[2];
        view.getLocationOnScreen(location);
        views.add(view);
        candidates.add(new MobileTouchTargetResolver.Candidate(
                location[0],
                location[1],
                location[0] + view.getWidth(),
                location[1] + view.getHeight(),
                view.isClickable(),
                view.isFocusable(),
                view.isEnabled(),
                view.isShown(),
                depth,
                drawingOrder[0]++));
    }

    private View getRootView() {
        return mActivity.getWindow() != null ? mActivity.getWindow().getDecorView() : null;
    }

    private void sendKey(int keyCode) {
        hapticFeedback();
        long now = SystemClock.uptimeMillis();
        mActivity.dispatchKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0));
        mActivity.dispatchKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0));
    }

    private void hapticFeedback() {
        if (!mHapticFeedbackEnabled || mActivity.getWindow() == null) {
            return;
        }

        View decorView = mActivity.getWindow().getDecorView();
        if (decorView != null) {
            decorView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
    }

    private static float dp(float value, float density) {
        return value * density;
    }
}
