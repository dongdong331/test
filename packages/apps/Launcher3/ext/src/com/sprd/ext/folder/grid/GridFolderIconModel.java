package com.sprd.ext.folder.grid;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.support.v4.graphics.ColorUtils;
import android.view.View;
import android.widget.LinearLayout;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAnimUtils;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.PropertyResetListener;
import com.android.launcher3.anim.RoundedRectRevealOutlineProvider;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.folder.ClippedFolderIconLayoutRule;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.folder.FolderAnimationManager;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.folder.PreviewBackground;
import com.android.launcher3.util.Themes;
import com.sprd.ext.folder.BaseFolderIconModel;

import java.util.List;

import static com.android.launcher3.BubbleTextView.TEXT_ALPHA_PROPERTY;
import static com.android.launcher3.LauncherAnimUtils.SCALE_PROPERTY;

public class GridFolderIconModel extends BaseFolderIconModel {
    private int mFolderIconRadius;
    private int mFolderPageRadius;

    public GridFolderIconModel(Context context, String model) {
        super(context, model);

        mFolderIconRadius = mContext.getResources().getDimensionPixelSize(R.dimen.grid_folder_icon_radius);
        mFolderPageRadius = mContext.getResources().getDimensionPixelSize(R.dimen.grid_folder_page_radius);
    }

    public String getIconModel() {
        return mIconModel;
    }

    public AnimatorSet getAnimator(FolderAnimationManager animationManager, Launcher launcher,
                                   Folder folder, FolderIcon icon, GradientDrawable folderBackground,
                                   TimeInterpolator mFolderInterpolator, boolean isOpening) {
        final DragLayer.LayoutParams lp = (DragLayer.LayoutParams) folder.getLayoutParams();
        ClippedFolderIconLayoutRule rule = icon.getLayoutRule();
        PreviewBackground previewBackground = icon.getFolderBackground();
        final List<BubbleTextView> itemsInPreview = icon.getPreviewItems();

        // Match position of the FolderIcon
        final Rect folderIconPos = new Rect();
        float scaleRelativeToDragLayer = launcher.getDragLayer()
                .getDescendantRectRelativeToSelf(icon, folderIconPos);
        float initialSize = previewBackground.getPreviewSize() * scaleRelativeToDragLayer;

        // Match size/scale of icons in the preview
        float previewScale = rule.scaleForItem(itemsInPreview.size());
        float previewSize = rule.getIconSize() * previewScale;
        float initialScale = previewSize / itemsInPreview.get(0).getIconSize()
               * scaleRelativeToDragLayer;

        final float finalScale = 1f;
        float scale = isOpening ? initialScale : finalScale;
        folder.setScaleX(scale);
        folder.setScaleY(scale);
        folder.setPivotX(0);
        folder.setPivotY(0);

        // We want to create a small X offset for the preview items, so that they follow their
        // expected path to their final locations. ie. an icon should not move right, if it's final
        // location is to its left. This value is arbitrarily defined.
        int previewItemOffsetX = 0;
        if (Utilities.isRtl(launcher.getResources())) {
            previewItemOffsetX = (int) (lp.width * initialScale - initialSize - previewItemOffsetX);
        }

        final int paddingOffsetX = (int) ((folder.getPaddingLeft() + folder.mContent.getPaddingLeft())
                * initialScale);
        final int paddingOffsetY = (int) ((folder.getPaddingTop() + folder.mContent.getPaddingTop())
                * initialScale);

        LinearLayout.LayoutParams footerLp = (LinearLayout.LayoutParams) folder.mFooter.getLayoutParams();
        final int unUsedOffsetY = (int) ((folder.mFooterHeight + footerLp.bottomMargin) * initialScale);

        int initialX = folderIconPos.left + previewBackground.getOffsetX() - paddingOffsetX
                - previewItemOffsetX;
        int initialY = folderIconPos.top + previewBackground.getOffsetY() - paddingOffsetY;
        final float xDistance = initialX - lp.x;
        final float yDistance = initialY - lp.y - unUsedOffsetY;

        // Set up the reveal animation that clips the Folder.
        int totalOffsetX = paddingOffsetX + previewItemOffsetX;
        int startLeft = Math.round(totalOffsetX / initialScale);
        int startTop = Math.round(paddingOffsetY / initialScale);
        int startRight = Math.round((totalOffsetX + initialSize) / initialScale);
        int startBottom = Math.round((paddingOffsetY + initialSize) / initialScale);
        Rect startRect = new Rect(startLeft, startTop, startRight, startBottom);
        Rect endRect = new Rect(0, 0, lp.width, lp.height);
        int initialRadius = mFolderPageRadius * 2;
        int finalRadius = mFolderIconRadius;

        // Create the animators.
        AnimatorSet a = LauncherAnimUtils.createAnimatorSet();

        // Initialize the Folder items' text.
        PropertyResetListener colorResetListener =
                new PropertyResetListener<>(TEXT_ALPHA_PROPERTY, 1f);
        for (BubbleTextView textView : folder.getItemsOnPage(folder.mContent.getCurrentPage())) {
            if (isOpening) {
                textView.setTextVisibility(false);
                folder.mFolderName.setVisibility(View.VISIBLE);
            } else {
                folder.mFolderName.setVisibility(View.GONE);
            }
            ObjectAnimator anim = textView.createTextAlphaAnimator(isOpening);
            anim.addListener(colorResetListener);
            animationManager.play(a, anim);
        }

        int pageCount = folder.mContent.getPageCount();
        folder.mPageIndicator.setVisibility((isOpening && pageCount > 1) ? View.VISIBLE : View.GONE);

        // Set up the Folder background.
        final int finalColor = Themes.getAttrColor(folder.getContext(), android.R.attr.colorPrimary);
        final int initialColor =
                ColorUtils.setAlphaComponent(finalColor, previewBackground.getBackgroundAlpha());
        folderBackground = (GradientDrawable) folder.mContent.getBackground();
        folderBackground.setColor(isOpening ? initialColor : finalColor);

        animationManager.play(a, animationManager.getAnimator(folder, View.TRANSLATION_X, xDistance, 0f));
        animationManager.play(a, animationManager.getAnimator(folder, View.TRANSLATION_Y, yDistance, 0f));
        animationManager.play(a, animationManager.getAnimator(folder, SCALE_PROPERTY, initialScale, finalScale));
        animationManager.play(a, animationManager.getAnimator(folderBackground, "color", initialColor, finalColor));
        animationManager.play(a, icon.getFolderName().createTextAlphaAnimator(!isOpening));
        RoundedRectRevealOutlineProvider outlineProvider = new RoundedRectRevealOutlineProvider(
                initialRadius, finalRadius, startRect, endRect) {
            @Override
            public boolean shouldRemoveElevationDuringAnimation() {
                return true;
            }
        };
        animationManager.play(a, outlineProvider.createRevealAnimator(folder.mContent, !isOpening));

        a.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                folder.setTranslationX(0.0f);
                folder.setTranslationY(0.0f);
                folder.setTranslationZ(0.0f);
                folder.setScaleX(1f);
                folder.setScaleY(1f);
            }
        });

        // We set the interpolator on all current child animators here, because the preview item
        // animators may use a different interpolator.
        for (Animator animator : a.getChildAnimations()) {
            animator.setInterpolator(mFolderInterpolator);
        }

        animationManager.addPreviewItemAnimators(a, initialScale / scaleRelativeToDragLayer,
                // Background can have a scaled radius in drag and drop mode, so we need to add the
                // difference to keep the preview items centered.
                previewItemOffsetX, 0);
        return a;
    }
}
