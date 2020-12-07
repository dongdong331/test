package com.sprd.ext.gestures;

import android.graphics.PointF;
import android.view.MotionEvent;

import com.android.launcher3.Launcher;

import java.util.ArrayList;

public class LauncherRootViewGestures {

    private final Launcher mLauncher;
    private static final float MIN_DISTANCE = 200f;
    private enum FingerMode {NONE, ONE_FINGER_MODE}
    private FingerMode fingerMode = FingerMode.NONE;
    //You can add different gestures here
    public enum Gesture {NONE,
        ONE_FINGER_SLIDE_UP, ONE_FINGER_SLIDE_DOWN, ONE_FINGER_SLIDE_LEFT, ONE_FINGER_SLIDE_RIGHT}

    private PointF startPoint = new PointF();
    private PointF curPoint = new PointF();
    private ArrayList<OnGestureListener> mOnGestureListeners = new ArrayList<>();

    public interface OnGestureListener{
        boolean onGesture(Gesture gesture);
    }

    public LauncherRootViewGestures(Launcher launcher) {
        mLauncher = launcher;
    }

    public boolean onTouchEvent(MotionEvent event) {
        //Do nothing
        if(!mLauncher.isEnableGestures() || mOnGestureListeners.isEmpty()){
            fingerMode = FingerMode.NONE;
            return false;
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                startPoint.set(event.getX(), event.getY());
                fingerMode = FingerMode.ONE_FINGER_MODE;
                break;
            case MotionEvent.ACTION_UP:
                fingerMode = FingerMode.NONE;
                break;
            case MotionEvent.ACTION_MOVE:
                return notifyListeners(eventAnalysis(event));
        }
        return false;
    }

    private Gesture eventAnalysis(MotionEvent event){
        Gesture gesture = Gesture.NONE;
        switch (fingerMode) {
            case ONE_FINGER_MODE:
                curPoint.set(event.getX(), event.getY());
                float fingerDist = distance(startPoint, curPoint);
                if (fingerDist > MIN_DISTANCE) {
                    if (Math.abs(startPoint.x - curPoint.x) > Math.abs(startPoint.y - curPoint.y)) {
                        gesture = startPoint.x > curPoint.x ?
                                Gesture.ONE_FINGER_SLIDE_LEFT : Gesture.ONE_FINGER_SLIDE_RIGHT;
                    } else {
                        gesture = startPoint.y > curPoint.y ?
                                Gesture.ONE_FINGER_SLIDE_UP : Gesture.ONE_FINGER_SLIDE_DOWN;
                    }
                }
                break;
            default:
                break;
        }
        return gesture;
    }

    private boolean notifyListeners(Gesture gesture) {
        boolean result = false;
        for (OnGestureListener listener : mOnGestureListeners) {
            if (listener != null) {
                result |= listener.onGesture(gesture);
            }
        }
        return result;
    }

    void registerOnGestureListener(OnGestureListener listener) {
        if (mOnGestureListeners.contains(listener)) {
            return;
        }
        mOnGestureListeners.add(listener);
    }

    void unregisterOnGestureListener(OnGestureListener listener) {
        if (!mOnGestureListeners.isEmpty()) {
            mOnGestureListeners.remove(listener);
        }
    }

    private float distance(PointF point1, PointF point2) {
        float x = point1.x - point2.x;
        float y = point1.y - point2.y;
        return (float)Math.sqrt(x * x + y * y);
    }
}
