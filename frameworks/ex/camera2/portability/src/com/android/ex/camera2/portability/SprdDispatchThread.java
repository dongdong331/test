package com.android.ex.camera2.portability;

import android.os.Handler;
import android.os.HandlerThread;

import com.android.ex.camera2.portability.debug.Log;

import java.util.LinkedList;
import java.util.Queue;

public class SprdDispatchThread  extends DispatchThread{
    private static final Log.Tag TAG = new Log.Tag("SprdDispThread");
    private final Queue<Runnable> mJobQueue;

    public SprdDispatchThread(Handler cameraHandler, HandlerThread cameraHandlerThread) {
        super(cameraHandler, cameraHandlerThread);
        mJobQueue = new LinkedList<Runnable>();
    }

    public boolean removeJob(Runnable job){
        Log.i(TAG,"removeJob");
        if (mJobQueue.contains(job)){
            return mJobQueue.remove(job);
        }
        return false;
    }
}
