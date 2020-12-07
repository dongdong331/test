package com.sprd.ext.folder;

import android.animation.AnimatorSet;
import android.animation.TimeInterpolator;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;

import com.android.launcher3.Launcher;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.folder.FolderAnimationManager;
import com.android.launcher3.folder.FolderIcon;

public class BaseFolderIconModel {
    protected Context mContext;
    protected String mIconModel;

    public BaseFolderIconModel(Context context, String model) {
        mContext = context;
        mIconModel = model;
    }

    public String getIconModel() {
        return mIconModel;
    }

    /**
     * Prepares the Folder for animating between open / closed states.
     */
    public AnimatorSet getAnimator(FolderAnimationManager animationManager, Launcher launcher,
                                   Folder folder, FolderIcon icon, GradientDrawable folderBackground,
                                   TimeInterpolator mFolderInterpolator,
                               boolean isOpening) {
        return null;
    }
}
