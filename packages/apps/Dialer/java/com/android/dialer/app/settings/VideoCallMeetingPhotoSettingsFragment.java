package com.android.dialer.app.settings;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.dialer.app.R;

/**
 * video call metting photo
 */
public class VideoCallMeetingPhotoSettingsFragment extends PreferenceFragment {

    public static final String TAG = "VideoCallMeetingPhotoSettingsFragment";
    private final String KEY_SELECT_PHOTO = "photo_select";
    private final String KEY_HORIZONTAL_PHOTO = "horizontal_photo";
    private final String KEY_VERTICAL_PHOTO = "vertical_photo";
    private final String HORIZONTAL_PHOTO = "horizontal_photo";
    private final int ORDER_FIRST = 1;

    private Context mContext;
    private ImageViewPreference mHorizontalPreference;
    private ImageViewPreference mVerticalPreference;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = getActivity();
        createPreferences();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    private void createPreferences() {
        addPreferencesFromResource(R.layout.video_call_meeting_photo_select_layout_ex);

        PreferenceScreen photoPreferenceScreen =
                (PreferenceScreen) findPreference(KEY_SELECT_PHOTO);
        photoPreferenceScreen.setTitle(R.string.video_photo_select_title);
        mVerticalPreference = (ImageViewPreference) new ImageViewPreference(getActivity());
        mVerticalPreference.setOrder(ORDER_FIRST);
        mVerticalPreference.setKey(KEY_VERTICAL_PHOTO);
        mVerticalPreference.setTitle(R.string.select_vertical_photo);
        photoPreferenceScreen.addPreference(mVerticalPreference);

    }

    /**
     * Click listener for toggle events.
     */
    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        Intent intent = new Intent(mContext, PhotoSettingsActivity.class);
        if (preference == mVerticalPreference) {
            Log.d(TAG, "onPreferenceTreeClick mVerticalPreference");
            intent.putExtra(HORIZONTAL_PHOTO, false);
        } else if (preference == mHorizontalPreference) {
            intent.putExtra(HORIZONTAL_PHOTO, true);
        }
        mContext.startActivity(intent);
        return true;
    }

    public class ImageViewPreference extends Preference {
        ImageView mImageView;
        TextView mTextView;

        public ImageViewPreference(Context context) {
            super(context);
            setLayoutResource(R.layout.image_view_preference_ex);
        }

        @Override
        protected void onBindView(View view) {
            super.onBindView(view);
            mImageView = (ImageView) view.findViewById(R.id.photo);
            mImageView.setVisibility(View.GONE);
            mTextView = (TextView) view.findViewById(R.id.no_photo);
            mTextView.setText(R.string.no_photo);
            mTextView.setVisibility(View.GONE);
        }
    }
}
