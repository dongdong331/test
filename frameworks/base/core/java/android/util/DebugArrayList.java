package android.util;

import java.util.ArrayList;
import java.util.Collection;

import android.util.Slog;

/**
 * @hide
 */
public class DebugArrayList<T> extends ArrayList<T> {
    private static final String TAG = "DebugArrayList";
    Throwable mThrowable;

    public DebugArrayList() {
        super();
    }

    public DebugArrayList(int initialCapacity) {
        super(initialCapacity);
    }

    public DebugArrayList(Collection<? extends T> c) {
        super(c);
    }

    public T get(int index) {
        T result = null;
        try {
            result = super.get(index);
        } catch (IndexOutOfBoundsException e) {
            Slog.d(TAG, "Getting index " + index + " failed", mThrowable);
            throw e;
        }
        return result;
    }

    public T remove(int index) {
        mThrowable = new Throwable("Instance " + hashCode()
                + " removing index=" + index);
        return super.remove(index);
    }

    public boolean remove(Object t) {
        mThrowable = new Throwable("Instance " + hashCode() + " removing t=" + t);
        return super.remove(t);
    }

    protected void removeRange(int fromIndex, int toIndex) {
        mThrowable = new Throwable("Instance " + hashCode()
                + " removing fromIndex=" + fromIndex + " toIndex=" + toIndex);
        super.removeRange(fromIndex, toIndex);
    }

    public void clear() {
        mThrowable = new Throwable("Instance " + hashCode() + " clear");
        super.clear();
    }

    public boolean removeAll(Collection<?> c) {
        mThrowable = new Throwable("Instance " + hashCode() + " removeAll c=" + c);
        return super.removeAll(c);
    }
}

