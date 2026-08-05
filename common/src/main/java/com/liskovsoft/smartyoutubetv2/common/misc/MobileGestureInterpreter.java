package com.liskovsoft.smartyoutubetv2.common.misc;

/**
 * Android-independent gesture state machine used by the mobile compatibility
 * layer. Keeping the decision logic free of Android classes makes it possible
 * to test navigation behavior with a plain JVM before building the APK.
 */
public final class MobileGestureInterpreter {
    public enum Command {
        UP,
        DOWN,
        LEFT,
        RIGHT,
        TAP,
        BACK,
        MENU
    }

    public interface Listener {
        void onCommand(Command command, float x, float y);
    }

    private enum Axis {
        NONE,
        HORIZONTAL,
        VERTICAL
    }

    private static final int MAX_COMMANDS_PER_MOVE = 4;
    private static final int MAX_COMMANDS_PER_GESTURE = 12;

    private final float mTouchSlop;
    private final float mNavigationStep;
    private final float mEdgeWidth;
    private final float mEdgeBackTrigger;
    private final long mLongPressTimeout;
    private final Listener mListener;

    private boolean mTracking;
    private boolean mSuppressCommands;
    private boolean mEdgeCandidate;
    private Axis mAxis = Axis.NONE;
    private float mDownX;
    private float mDownY;
    private float mLastStepX;
    private float mLastStepY;
    private long mDownTime;
    private int mCommandCount;

    public MobileGestureInterpreter(
            float touchSlop,
            float navigationStep,
            float edgeWidth,
            float edgeBackTrigger,
            long longPressTimeout,
            Listener listener) {
        if (touchSlop < 0 || navigationStep <= 0 || edgeWidth < 0 || edgeBackTrigger <= 0) {
            throw new IllegalArgumentException("Gesture distances must be positive");
        }
        if (longPressTimeout < 0 || listener == null) {
            throw new IllegalArgumentException("Invalid gesture configuration");
        }

        mTouchSlop = touchSlop;
        mNavigationStep = navigationStep;
        mEdgeWidth = edgeWidth;
        mEdgeBackTrigger = edgeBackTrigger;
        mLongPressTimeout = longPressTimeout;
        mListener = listener;
    }

    public boolean onDown(float x, float y, long eventTime, int pointerCount) {
        reset();
        if (pointerCount != 1) {
            return false;
        }

        mTracking = true;
        mDownX = x;
        mDownY = y;
        mLastStepX = x;
        mLastStepY = y;
        mDownTime = eventTime;
        mEdgeCandidate = x <= mEdgeWidth;
        return true;
    }

    public boolean onMove(float x, float y, long eventTime, int pointerCount) {
        if (!mTracking) {
            return false;
        }

        if (pointerCount != 1) {
            // A second finger must never accidentally generate DPAD commands.
            // The rest of the already-consumed sequence remains consumed.
            mSuppressCommands = true;
            return true;
        }

        if (mSuppressCommands) {
            return true;
        }

        float dx = x - mDownX;
        float dy = y - mDownY;
        float absX = Math.abs(dx);
        float absY = Math.abs(dy);
        float activationDistance = Math.max(mTouchSlop * 2f, mNavigationStep * 0.35f);

        if (mEdgeCandidate) {
            // Reserve a primarily horizontal right swipe for system-like back.
            // A clearly vertical movement leaves edge mode and scrolls normally.
            if (absY >= activationDistance && absY > absX * 1.25f) {
                mEdgeCandidate = false;
            } else {
                return true;
            }
        }

        if (mAxis == Axis.NONE) {
            if (Math.max(absX, absY) < activationDistance) {
                return true;
            }

            mAxis = absX > absY ? Axis.HORIZONTAL : Axis.VERTICAL;
            emitDirection(dx, dy, x, y);
            mLastStepX = x;
            mLastStepY = y;
            return true;
        }

        emitIncrementalSteps(x, y);
        return true;
    }

    public boolean onUp(float x, float y, long eventTime, int pointerCount) {
        if (!mTracking) {
            return false;
        }

        if (pointerCount != 1) {
            mSuppressCommands = true;
        }

        if (!mSuppressCommands) {
            float dx = x - mDownX;
            float dy = y - mDownY;
            float absX = Math.abs(dx);
            float absY = Math.abs(dy);
            float activationDistance = Math.max(mTouchSlop * 2f, mNavigationStep * 0.35f);

            if (mEdgeCandidate && dx >= mEdgeBackTrigger && absX > absY * 1.15f) {
                emit(Command.BACK, x, y);
            } else if (mCommandCount == 0 && Math.max(absX, absY) >= activationDistance) {
                // Some devices deliver almost no MOVE events during a fast fling.
                emitDirection(dx, dy, x, y);
            } else if (mCommandCount == 0 && eventTime - mDownTime >= mLongPressTimeout) {
                emit(Command.MENU, x, y);
            } else if (mCommandCount == 0) {
                emit(Command.TAP, x, y);
            }
        }

        reset();
        return true;
    }

    public boolean onCancel() {
        boolean wasTracking = mTracking;
        reset();
        return wasTracking;
    }

    private void emitIncrementalSteps(float x, float y) {
        float delta = mAxis == Axis.HORIZONTAL ? x - mLastStepX : y - mLastStepY;
        int commandsThisMove = 0;

        while (Math.abs(delta) >= mNavigationStep
                && commandsThisMove < MAX_COMMANDS_PER_MOVE
                && mCommandCount < MAX_COMMANDS_PER_GESTURE) {
            if (mAxis == Axis.HORIZONTAL) {
                emit(delta > 0 ? Command.RIGHT : Command.LEFT, x, y);
                mLastStepX += delta > 0 ? mNavigationStep : -mNavigationStep;
                delta = x - mLastStepX;
            } else {
                emit(delta > 0 ? Command.DOWN : Command.UP, x, y);
                mLastStepY += delta > 0 ? mNavigationStep : -mNavigationStep;
                delta = y - mLastStepY;
            }
            commandsThisMove++;
        }
    }

    private void emitDirection(float dx, float dy, float x, float y) {
        if (Math.abs(dx) > Math.abs(dy)) {
            emit(dx > 0 ? Command.RIGHT : Command.LEFT, x, y);
        } else {
            emit(dy > 0 ? Command.DOWN : Command.UP, x, y);
        }
    }

    private void emit(Command command, float x, float y) {
        mListener.onCommand(command, x, y);
        if (command == Command.UP || command == Command.DOWN
                || command == Command.LEFT || command == Command.RIGHT) {
            mCommandCount++;
        }
    }

    private void reset() {
        mTracking = false;
        mSuppressCommands = false;
        mEdgeCandidate = false;
        mAxis = Axis.NONE;
        mDownX = 0;
        mDownY = 0;
        mLastStepX = 0;
        mLastStepY = 0;
        mDownTime = 0;
        mCommandCount = 0;
    }
}
