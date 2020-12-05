package com.android.server.performance.policy.oomadj;

import android.util.Slog;

import java.io.PrintWriter;
import java.util.HashMap;

import android.app.ProcessInfo;

import com.android.server.performance.PolicyConfig;


/**
 * Created by SPREADTRUM\joe.yu on 9/30/18.
 */

public class OomAdjConstants {

    public static final String TAG = "OomAdjConstants";
    public static final String CONFIG_TYPE_NORMAL = "OomAdjConstansNormal";
    public static final String CONFIG_TYPE_1G = "OomAdjConstans1G";
    public static final String CONFIG_TYPE_512M = "OomAdjConstans512M";

    public static final String ITEM_COMMON_CONFIGS = "commonConfig";
    public static final String ITEM_MEMPRESSURE = "memPressure";
    public static final String TAG_MEMPRESSURE_NORMAL = "normal";
    public static final String TAG_MEMPRESSURE_MORDERATE = "moderate";
    public static final String TAG_MEMPRESSURE_LOW = "low";
    public static final String TAG_MEMPRESSURE_CRITICAL = "critical";

    public static final String TAG_PERCEPTIBLE_PSS = "perceptible_pss";
    public static final String TAG_PERCEPTIBLE_COUNT = "perceptible_count";
    public static final String TAG_SERVICE_PSS = "service_pss";
    public static final String TAG_SERVICE_COUNT = "service_count";
    public static final String TAG_SERVICEB_PSS = "serviceb_pss";
    public static final String TAG_SERVICEB_COUNT = "service_count";
    public static final String TAG_HOTAPP_LAUNCHCOUNT = "hotapp_launchcount";
    public static final String TAG_HOTAPP_ADJ = "hotapp_adj";
    public static final String TAG_HOTAPP_COUNT = "hotapp_count";
    public static final String TAG_RECENTAPP_ADJ = "recentapp_adj";
    public static final String TAG_RELEVANCEAPP_ADJ = "relevanceapp_adj";
    public static final String TAG_RELEVANCEAPP_COUNT = "relevanceapp_count";
    public static final String TAG_RELEVANCEAPP_LAUNCHCOUNT = "relevanceapp_launchcount";

    public static final String TAG_VISIBLE_DROP_TO_ADJ = "visible_adj";
    public static final String TAG_PERCEPTIBLE_DROP_TO_ADJ = "perceptible_adj";
    public static final String TAG_SERVICE_DROP_TO_ADJ = "service_adj";
    public static final String TAG_SERVICEB_DROP_TO_ADJ = "serviceb_adj";
    public static final String TAG_APP_IDLETIME_LIMIT = "idletime";
    public static final String TAG_RECENTAPP_PROTECTED_TIME = "recentprotect";
    public static final String TAG_NOTIFICATION_PROTECTED_TIME = "notificationtime";

    public static final String OOM_ADJ_VISIBLE = "visible";
    public static final String OOM_ADJ_PERCEPTIBLE = "perceptible";
    public static final String OOM_ADJ_SERVICE = "service";
    public static final String OOM_ADJ_SERVICEB = "serviceb";
    public static final String OOM_ADJ_CACHED = "cached";

    public static final int MEMPRESSURE_NORMAL = 0;
    public static final int MEMPRESSURE_MORDERATE = 1;
    public static final int MEMPRESSURE_LOW = 2;
    public static final int MEMPRESSURE_CRITICAL = 3;
    public static final int MEMPRESSURE_COUNT = 4;

    private MemPressureTunning[] mTunnings = new MemPressureTunning[MEMPRESSURE_COUNT];
    private int mHotAppMinLaunchCount;
    private int mHotAppAdj;
    private int mHotAppCount;
    private int mRecentAppAdj;
    private int mRelevanceAppAdj;
    private int mRelevanceAppCount;
    private int mRelevanceAppLaunchCount;

    public static OomAdjConstants loadFromConfig(PolicyConfig config) {

        PolicyConfig.ConfigItem commonConfig = config.getConfigItem(ITEM_COMMON_CONFIGS);
        PolicyConfig.ConfigItem mempressureNormal = config.getConfigItem(TAG_MEMPRESSURE_NORMAL);
        PolicyConfig.ConfigItem mempressureModerate = config.getConfigItem(TAG_MEMPRESSURE_MORDERATE);
        PolicyConfig.ConfigItem mempressureLow = config.getConfigItem(TAG_MEMPRESSURE_LOW);
        PolicyConfig.ConfigItem mempressureCritical = config.getConfigItem(TAG_MEMPRESSURE_CRITICAL);
        if (commonConfig != null && mempressureNormal != null &&
                mempressureModerate != null && mempressureLow != null &&
                mempressureCritical != null) {
            OomAdjConstants constants = new OomAdjConstants();
            constants.mTunnings[MEMPRESSURE_NORMAL] = new MemPressureTunning(mempressureNormal, TAG_MEMPRESSURE_NORMAL);
            constants.mTunnings[MEMPRESSURE_MORDERATE] = new MemPressureTunning(mempressureModerate, TAG_MEMPRESSURE_MORDERATE);
            constants.mTunnings[MEMPRESSURE_LOW] = new MemPressureTunning(mempressureLow, TAG_MEMPRESSURE_LOW);
            constants.mTunnings[MEMPRESSURE_CRITICAL] = new MemPressureTunning(mempressureCritical, TAG_MEMPRESSURE_CRITICAL);

            constants.mHotAppMinLaunchCount = Integer.valueOf(commonConfig.getString(TAG_HOTAPP_LAUNCHCOUNT));
            constants.mHotAppAdj = StringToOomAdj(commonConfig.getString(TAG_HOTAPP_ADJ));
            constants.mHotAppCount = Integer.valueOf(commonConfig.getString(TAG_HOTAPP_COUNT));
            constants.mRecentAppAdj = StringToOomAdj(commonConfig.getString(TAG_RECENTAPP_ADJ));
            constants.mRelevanceAppAdj = StringToOomAdj(commonConfig.getString(TAG_RELEVANCEAPP_ADJ));
            constants.mRelevanceAppCount = Integer.valueOf(commonConfig.getString(TAG_RELEVANCEAPP_COUNT));
            constants.mRelevanceAppLaunchCount = Integer.valueOf(commonConfig.getString(TAG_RELEVANCEAPP_LAUNCHCOUNT));
            return constants;
        }

        return null;
    }

    private static long minToms(String min) {
        return Long.valueOf(min) * 60 * 1000;
    }

    private static int StringToOomAdj(String adj) {
        switch (adj) {
            case OOM_ADJ_VISIBLE:
                return ProcessInfo.VISIBLE_APP_ADJ;
            case OOM_ADJ_PERCEPTIBLE:
                return ProcessInfo.PERCEPTIBLE_APP_ADJ;
            case OOM_ADJ_SERVICE:
                return ProcessInfo.SERVICE_ADJ;
            case OOM_ADJ_SERVICEB:
                return ProcessInfo.SERVICE_B_ADJ;
            case OOM_ADJ_CACHED:
                return ProcessInfo.CACHED_APP_MIN_ADJ;
            default:
                return -1;
        }
    }

    public int getAdjTunningValue(int adj, int mempressure) {
        return mTunnings[mempressure].tunningAdj(adj);
    }

    public long getAppIdleTimeLimit(int mempressure) {
        return mTunnings[mempressure].getAppIdleTimeLimit();
    }

    public long getRecentAppProtectedTimeLimit(int mempressure) {
        return mTunnings[mempressure].getRecentAppProtectedTimeLimit();
    }

    public long getNotifcationProtectedTimeLimit(int mempressure) {
        return mTunnings[mempressure].getNotifcationProtectedTimeLimit();
    }

    public int getHotAppMinLaunchCount() {
        return mHotAppMinLaunchCount;
    }

    public int getHotAppAdj() {
        return mHotAppAdj;
    }

    public int getHotAppCount() {
        return mHotAppCount;
    }

    public int getRecentAppAdj() {
        return mRecentAppAdj;
    }

    public int getRelevanceAppAdj() {
        return mRelevanceAppAdj;
    }

    public int getRelevanceAppCount() {
        return mRelevanceAppCount;
    }

    public int getRelevanceAppMinLaunchCount() {
        return mRelevanceAppLaunchCount;
    }

    public static class MemPressureTunning {

        private int mVisibleTunnedAdj;
        private int mPerceptibleTunnedAdj;
        private int mServiceTunnedAdj;
        private int mServiceBTunnedAdj;
        private long mRecentAppProtectedTimeLimit;
        private long mAppIdleTimeLimit;
        private long mNotifcationProtectedTimeLimit;
        private String mMemPressure;

        public MemPressureTunning(PolicyConfig.ConfigItem item, String memPressure) {
            mVisibleTunnedAdj = StringToOomAdj(item.getString(TAG_VISIBLE_DROP_TO_ADJ));
            mPerceptibleTunnedAdj = StringToOomAdj(item.getString(TAG_PERCEPTIBLE_DROP_TO_ADJ));
            mServiceTunnedAdj = StringToOomAdj(item.getString(TAG_SERVICE_DROP_TO_ADJ));
            mServiceBTunnedAdj = StringToOomAdj(item.getString(TAG_SERVICEB_DROP_TO_ADJ));
            mAppIdleTimeLimit = minToms(item.getString(TAG_APP_IDLETIME_LIMIT));
            mRecentAppProtectedTimeLimit = minToms(item.getString(TAG_RECENTAPP_PROTECTED_TIME));
            mNotifcationProtectedTimeLimit = minToms(item.getString(TAG_NOTIFICATION_PROTECTED_TIME));
            mMemPressure = memPressure;
        }

        public int getVisibleTunnedAdj() {
            return mVisibleTunnedAdj;
        }

        public int getPerceptibleTunnedAdj() {
            return mPerceptibleTunnedAdj;
        }

        public int getServiceTunnedAdj() {
            return mServiceTunnedAdj;
        }

        public int getServiceBTunnedAdj() {
            return mServiceBTunnedAdj;
        }

        public long getRecentAppProtectedTimeLimit() {
            return mRecentAppProtectedTimeLimit;
        }

        public long getAppIdleTimeLimit() {
            return mAppIdleTimeLimit;
        }

        public long getNotifcationProtectedTimeLimit() {
            return mNotifcationProtectedTimeLimit;
        }

        public int tunningAdj(int adj) {
            switch (adj) {
                case ProcessInfo.VISIBLE_APP_ADJ:
                    return getVisibleTunnedAdj();
                case ProcessInfo.PERCEPTIBLE_APP_ADJ:
                    return getPerceptibleTunnedAdj();
                case ProcessInfo.SERVICE_ADJ:
                    return getServiceTunnedAdj();
                case ProcessInfo.SERVICE_B_ADJ:
                    return getServiceBTunnedAdj();
                default:
                    return adj;
            }
        }

        public void dump(PrintWriter pw) {
            pw.println("Dump MemPressureTunning: "+mMemPressure);
            pw.println("---------------------------");
            pw.println("mVisibleTunnedAdj: "+ mVisibleTunnedAdj);
            pw.println("mPerceptibleTunnedAdj: "+ mPerceptibleTunnedAdj);
            pw.println("mServiceTunnedAdj: "+ mServiceTunnedAdj);
            pw.println("mServiceBTunnedAdj: "+ mServiceBTunnedAdj);
            pw.println("mAppIdleTimeLimit: "+ mAppIdleTimeLimit);
            pw.println("mRecentAppProtectedTimeLimit: "+ mRecentAppProtectedTimeLimit);
            pw.println("mNotifcationProtectedTimeLimit: " +mNotifcationProtectedTimeLimit);
            pw.println("---------------------------");
        }

        public void dump() {
            Slog.d(TAG, "Dump MemPressureTunning: "+mMemPressure);
            Slog.d(TAG, "---------------------------");
            Slog.d(TAG, "mVisibleTunnedAdj: "+ mVisibleTunnedAdj);
            Slog.d(TAG, "mPerceptibleTunnedAdj: "+ mPerceptibleTunnedAdj);
            Slog.d(TAG, "mServiceTunnedAdj: "+ mServiceTunnedAdj);
            Slog.d(TAG, "mServiceBTunnedAdj: "+ mServiceBTunnedAdj);
            Slog.d(TAG, "mAppIdleTimeLimit: "+ mAppIdleTimeLimit);
            Slog.d(TAG, "mRecentAppProtectedTimeLimit: "+ mRecentAppProtectedTimeLimit);
            Slog.d(TAG, "mNotifcationProtectedTimeLimit: " +mNotifcationProtectedTimeLimit);
            Slog.d(TAG, "---------------------------");
        }
    }

    public void dump(PrintWriter pw) {
        pw.println("Dump OomAdjConstants:");
        pw.println("---------------------------");
        pw.println("mHotAppMinLaunchCount: " + mHotAppMinLaunchCount);
        pw.println("mHotAppAdj: " + mHotAppAdj);
        pw.println("mHotAppCount: " + mHotAppCount);
        pw.println("mRecentAppAdj: " + mRecentAppAdj);
        pw.println("mRelevanceAppAdj: " + mRelevanceAppAdj);
        pw.println("mRelevanceAppCount: " + mRelevanceAppCount);
        pw.println("mRelevanceAppLaunchCount: " + mRelevanceAppLaunchCount);
        for (MemPressureTunning t : mTunnings) {
            t.dump(pw);
        }
        pw.println("---------------------------");
    }

    public void dump() {
        Slog.d(TAG, "Dump OomAdjConstants:");
        Slog.d(TAG, "---------------------------");
        Slog.d(TAG, "mHotAppMinLaunchCount: " + mHotAppMinLaunchCount);
        Slog.d(TAG, "mHotAppAdj: " + mHotAppAdj);
        Slog.d(TAG, "mHotAppCount: " + mHotAppCount);
        Slog.d(TAG, "mRecentAppAdj: " + mRecentAppAdj);
        Slog.d(TAG, "mRelevanceAppAdj: " + mRelevanceAppAdj);
        Slog.d(TAG, "mRelevanceAppCount: " + mRelevanceAppCount);
        Slog.d(TAG, "mRelevanceAppLaunchCount: " + mRelevanceAppLaunchCount);
        for (MemPressureTunning t : mTunnings) {
            t.dump();
        }
        Slog.d(TAG, "---------------------------");
    }
}