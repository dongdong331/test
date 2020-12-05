package com.sprd.keyguard;

import android.app.AddonManager;
import android.text.format.DateFormat;
import java.util.Locale;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.widget.TextClock;
import android.content.Context;
import android.util.Log;

import com.android.systemui.R;

public class KeyguardSupportAmPm {

    static KeyguardSupportAmPm sInstance;
    public static Context mContext;

    public static KeyguardSupportAmPm getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new KeyguardSupportAmPm(context);
        }
        return sInstance;
    }

    private KeyguardSupportAmPm(Context context) {
        mContext = context;
    }
    public void setFormat12Hour(int textSize,TextClock clockView){
        int amPmFontSize = (int) textSize;
        String skeleton = "hma";
        String pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton);
        // Remove the am/pm
        if (amPmFontSize <= 0) {
            pattern.replaceAll("a", "").trim();
        }
        // Replace spaces with "Hair Space"
        pattern = pattern.replaceAll(" ", "\u200A");
        // Build a spannable so that the am/pm will be formatted
        int amPmPos = pattern.indexOf('a');
        if (amPmPos == -1) {
            clockView.setFormat12Hour(pattern);
            return;
        }
        Spannable sp = new SpannableString(pattern);
        sp.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), amPmPos, amPmPos + 1,
                Spannable.SPAN_POINT_MARK);
        sp.setSpan(new AbsoluteSizeSpan(amPmFontSize), amPmPos, amPmPos + 1,
                Spannable.SPAN_POINT_MARK);
        sp.setSpan(new TypefaceSpan("sans-serif-condensed"), amPmPos, amPmPos + 1,
                Spannable.SPAN_POINT_MARK);
        clockView.setFormat12Hour(sp);
    }

    public boolean isEnabled(){
        return mContext.getResources().getBoolean(
                R.bool.config_status_bar_ampm);
    }
}
