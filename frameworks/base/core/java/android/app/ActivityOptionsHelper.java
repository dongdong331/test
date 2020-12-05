/*
 * The Spreadtrum Communication Inc. 2016
 */

package android.app;

import android.content.res.Resources;
import android.os.Bundle;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.view.animation.ScaleAnimation;
import android.view.View;

import static com.android.internal.R.integer.config_overrideAppTransitionAnimationType;

/**
 * @hide
 */
public class ActivityOptionsHelper {
    public static final int TRANSIT_ANIM_TYPE_NORMAL = 0;
    public static final int TRANSIT_ANIM_TYPE_CUSTOM_SCALEUP = 1;
    private static final int TRANSIT_ANIM_TYPE_MAX = 2;

    private static final String KEY_ANIM_TYPE_OVERRIDE = "sprd:activity.animTypeOverride";

    // markdown the configuration has been loaded, avoid duplicated loading
    private static boolean sLoaded = false;

    // Vendor set override animation solution, default is 0, do not override
    private static int sOverrideAnimationType = 0;

    private static ThreadLocal<AnimationHelper> sAnimationHelperLocal = new ThreadLocal<AnimationHelper>();

    // Ensure to load configuration one time.
    private static void ensureConfigurations() {
        if (sLoaded) return;

        Resources res = Resources.getSystem();
        sOverrideAnimationType = res.getInteger(
                config_overrideAppTransitionAnimationType);

        if (sOverrideAnimationType < 0
                || sOverrideAnimationType >= TRANSIT_ANIM_TYPE_MAX) {
            sOverrideAnimationType = TRANSIT_ANIM_TYPE_NORMAL;
        }
        sLoaded = true;
    }

    // Do not use sOverrideAnimationType, must call this instead because intializing configuration.
    // This is the global configuration.
    public static int getOverrideAppTransitionAnimationType() {
        ensureConfigurations();
        return sOverrideAnimationType;
    }

    // Exported api in future, to override the app transition animation, currently used is in Launcher3
    public static Bundle overrideOptsBundle(View v, Bundle optsBundle) {
        if (optsBundle == null) return null;

        final int animTypeOverride = getOverrideAppTransitionAnimationType();
        switch (animTypeOverride) {
            case TRANSIT_ANIM_TYPE_CUSTOM_SCALEUP:
                if (v == null) return optsBundle;
                if (optsBundle.getInt(ActivityOptions.KEY_ANIM_TYPE) != ActivityOptions.ANIM_SCALE_UP) {
                    optsBundle = ActivityOptions.makeScaleUpAnimation(v, 0, 0,
                                    v.getMeasuredWidth(), v.getMeasuredHeight()).toBundle();
                }
                optsBundle.putInt(KEY_ANIM_TYPE_OVERRIDE, animTypeOverride);
                return optsBundle;
            default:
                return optsBundle;
        }
    }

    // Hidden interface, additional options into bundle to transfor through processes
    /*package*/ static void appendAdditionalOptions(ActivityOptions opts, Bundle optsBundle) {
        if (opts != null && optsBundle != null) {
            opts.animTypeOverride = optsBundle.getInt(KEY_ANIM_TYPE_OVERRIDE,
                    TRANSIT_ANIM_TYPE_NORMAL);
        }
    }

    // Hidden api for ActivityRecord, generate AnimationHelper from ActivityOptions,
    // set to static thread local instance and use getAnimationHelper to get it.
    public static void setAnimationHelper(ActivityOptions opts) {
        if (opts == null) {
            sAnimationHelperLocal.set(null);
            return;
        }
        ActivityOptionsHelper.AnimationHelper animationHelper =
                new ActivityOptionsHelper.AnimationHelper();
        animationHelper.setOverrideAnimationType(opts.animTypeOverride);
        sAnimationHelperLocal.set(animationHelper);
    }

    // In AppTransition, take the animation helper from ActivityRecord
    public static AnimationHelper getAnimationHelper() {
        return sAnimationHelperLocal != null ? sAnimationHelperLocal.get() : null;
    }

    // The parameters helper for building custom animation which can not be
    // accessed by android.app package
    public static class AnimationParameters {
        public int nextAppTransitionStartWidth;
        public int nextAppTransitionStartHeight;
        public int nextAppTransitionStartX;
        public int nextAppTransitionStartY;
        public Interpolator decelerateInterpolator;
        public Interpolator linearOutSlowInInterpolator;
    }

    /**
     * @hide the animation builder, to build custom animation
     * used in AppTransition
     */
    public static class AnimationHelper {
        private static AnimationHelper sPending;

        private int mOverrideAnimationType = ActivityOptionsHelper.TRANSIT_ANIM_TYPE_NORMAL;
        private int mNextTransitionType = -1;

        public static AnimationHelper ensureHelper(AnimationHelper helper) {
            if (helper == null) {
                helper = new AnimationHelper();
                helper.clear();
            }
            return helper;
        }

        public void clear() {
            mOverrideAnimationType = ActivityOptionsHelper.TRANSIT_ANIM_TYPE_NORMAL;
            mNextTransitionType = -1;
        }

        void setOverrideAnimationType(int type) {
            mOverrideAnimationType = type;
        }

        public int getOverrideAnimationType() {
            return mOverrideAnimationType;
        }

        public void setNextTransitionType(int type) {
            mNextTransitionType = type;
        }

        public int getNextTransitionType() {
            return mNextTransitionType;
        }

        // validate the override animation type is valid
        public boolean shouldOverrideAnimation() {
            return mOverrideAnimationType > ActivityOptionsHelper.TRANSIT_ANIM_TYPE_NORMAL
                    && mOverrideAnimationType < ActivityOptionsHelper.TRANSIT_ANIM_TYPE_MAX;
        }

        // raw building animation here
        public static Animation createOverrideAnimation(AnimationHelper helper,
                int transit, boolean enter, int appWidth, int appHeight,
                AnimationParameters params) {
            Animation a = null;
            if (helper == null) return null;
            switch (helper.getOverrideAnimationType()) {
                case ActivityOptionsHelper.TRANSIT_ANIM_TYPE_CUSTOM_SCALEUP:
                    android.util.Log.d("AnimationHelper", "createOverrideAnimation enter " + enter);
                    if (enter) {
                        // Entering app zooms out from the center of the initial rect.
                        float scaleW = params.nextAppTransitionStartWidth / (float) appWidth;
                        float scaleH = params.nextAppTransitionStartHeight / (float) appHeight;
                        Animation scale = new ScaleAnimation(0.75f, 1, 0.75f, 1,
                                computePivot(params.nextAppTransitionStartX, scaleW),
                                computePivot(params.nextAppTransitionStartY, scaleH));
                        scale.setInterpolator(params.decelerateInterpolator);

                        Animation alpha = new AlphaAnimation(0, 1);

                        alpha.setInterpolator(params.linearOutSlowInInterpolator);

                        AnimationSet set = new AnimationSet(false);
                        set.addAnimation(scale);
                        set.addAnimation(alpha);
                        set.setDetachWallpaper(true);
                        a = set;
                    } else if (!enter) {
                        Animation scale = new ScaleAnimation(1, 1.08f, 1, 1.08f,
                                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
                        scale.setInterpolator(params.decelerateInterpolator);

                        Animation alpha = new AlphaAnimation(1.0f, 1.0f);
                        alpha.setInterpolator(params.decelerateInterpolator);

                        AnimationSet set = new AnimationSet(false);
                        //set.addAnimation(scale);
                        set.addAnimation(alpha);
                        set.setDetachWallpaper(true);
                        a = set;
                    } else {
                        // For normal animations, the exiting element just holds in place.
                        a = new AlphaAnimation(1, 1);
                    }

                    a.setDuration(150);
                    a.setFillAfter(true);
                    a.setInterpolator(params.decelerateInterpolator);
                    a.initialize(appWidth, appHeight, appWidth, appHeight);
                    return a;
                default:
                    android.util.Log.d("AnimationHelper", "Not developed yet");
            }
            return a;
        }

        // copy from AppTransition
        private static float computePivot(int startPos, float finalScale) {
            final float denom = finalScale-1;
            if (Math.abs(denom) < .0001f) {
                return startPos;
            }
            return -startPos / denom;
        }
    }


}
