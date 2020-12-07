package com.android.dialer.calllog;

import android.app.ActionBar;
import android.app.ListActivity;
import android.app.SearchManager;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.Manifest.permission;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.PhoneLookup;
import android.support.annotation.MainThread;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.PhoneNumberUtils;
import android.telephony.SubscriptionInfo;
import android.telephony.TelephonyManager;
import android.text.format.DateUtils;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;

import com.android.dialer.app.calllog.IntentProvider;
import com.android.dialer.calldetails.CallDetailsEntries;
import com.android.dialer.calldetails.CallDetailsEntries.CallDetailsEntry;
import com.android.dialer.calllogutils.PhoneAccountUtils;
import com.android.dialer.common.Assert;
import com.android.dialer.dialercontact.DialerContact;
import com.android.dialer.phonenumbercache.CallLogQuery;
import com.android.dialer.R;
import com.android.dialer.util.DialerUtils;
import com.google.common.collect.Lists;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

//Add by Sprd
public class SearchResultActivity extends ListActivity implements CallLogClearColumn {
    private static final String TAG = "SearchResultActivity";
    // TODO
    private static final boolean DBG = true;
    private static final Executor sDefaultExecute =
            Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 4);

    private String mQueryFilter = null;

    public static final int ITEM_SEARCH = Menu.FIRST;
    private static final int CALL_PHONE_PERMISSION_REQUEST_CODE = 1;

    /**
     * The projection to use when querying the phones table
     */
    static final String[] PHONES_PROJECTION = new String[]{
            PhoneLookup._ID,
            PhoneLookup.DISPLAY_NAME,
            PhoneLookup.TYPE,
            PhoneLookup.LABEL,
            PhoneLookup.NUMBER
    };

    static final int PERSON_ID_COLUMN_INDEX = 0;
    static final int NAME_COLUMN_INDEX = 1;
    static final int PHONE_TYPE_COLUMN_INDEX = 2;
    static final int LABEL_COLUMN_INDEX = 3;
    static final int MATCHED_NUMBER_COLUMN_INDEX = 4;

    /**
     * For the name first letter matching
     */
    public static final String CACHED_NORMALIZED_SIMPLE_NAME = "normalized_simple_name";
    /**
     * For the name all letter matching
     */
    public static final String CACHED_NORMALIZED_FULL_NAME = "normalized_full_name";

    private SearchCallLogAdapter mAdapter;
    private TelecomManager mTelecomManager;

    /* SPRD: add for bug696421 @{ */
    private boolean mFlag = false;
    private static final int COUNT_CALL_DETAILS_ENTRIES = 1;
    /* @} */

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.calls_delete_activity_ex);

        mAdapter = new SearchCallLogAdapter(this, null);
        mTelecomManager =
                (TelecomManager) getSystemService(Context.TELECOM_SERVICE);
        String emptyText = getString(R.string.call_log_all_empty);
        ((TextView) (getListView().getEmptyView())).setText(emptyText);
        setListAdapter(mAdapter);
        getListView().setItemsCanFocus(true);

        handleIntent(getIntent());
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowHomeEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowTitleEnabled(true);
        }
    }

    /**
     * SPRD: modify for bug505688 @{
     */
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case CALL_PHONE_PERMISSION_REQUEST_CODE:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    handleIntent(getIntent());
                }
                break;
            default:
                break;
        }
    }

    /**
     * @}
     */

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (DBG) {
            Log.d(TAG, "action = " + intent.getAction());
        }
        if (checkSelfPermission(permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{permission.CALL_PHONE},
                    CALL_PHONE_PERMISSION_REQUEST_CODE);
        } else {
            mQueryFilter = null;
            if (Intent.ACTION_VIEW.equals(intent.getAction())) {
                // handles a click on a search suggestion
                /* SPRD: add for bug696421 @{ */
                mFlag = true;
                new AsyncQueryThread(this).executeOnExecutor(sDefaultExecute, intent.getData());
                /* @} */
                return;
            } else if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
                mQueryFilter = intent.getStringExtra(SearchManager.QUERY);
            }
            AsyncQueryThread thread = new AsyncQueryThread(
                    getApplicationContext());
            thread.executeOnExecutor(sDefaultExecute);
        }
    }

    private final class SearchCallLogAdapter extends ResourceCursorAdapter {
        public SearchCallLogAdapter(Context context, Cursor cursor) {
            super(context, R.layout.delete_calls_list_child_item_ex, cursor,
                    FLAG_REGISTER_CONTENT_OBSERVER);
        }

        @Override
        public void bindView(View view, Context context, Cursor c) {
            ImageView iconView = (ImageView) view.findViewById(R.id.call_type_icon);
            ImageView simView = (ImageView) view.findViewById(R.id.sim);
            TextView line1View = (TextView) view.findViewById(R.id.line1);

            // TextView labelView = (TextView) view.findViewById(R.id.label);
            TextView numberView = (TextView) view.findViewById(R.id.number);
            TextView dateView = (TextView) view.findViewById(R.id.date);
            CheckBox checkBox = (CheckBox) view.findViewById(R.id.call_icon);
            View dividerView = (View) view.findViewById(R.id.divider);
            dividerView.setVisibility(View.INVISIBLE);
            checkBox.setVisibility(View.INVISIBLE);

            final long id = c.getLong(ID_COLUMN_INDEX);
            long date = c.getLong(DATE_COLUMN_INDEX);

            String number = c.getString(NUMBER_COLUMN_INDEX);
            String formattedNumber = number;
            String name = c.getString(CALLER_NAME_COLUMN_INDEX);
            final PhoneAccountHandle accountHandle = PhoneAccountUtils.getAccount(
                    c.getString(ACCOUNT_COMPONENT_NAME),
                    c.getString(ACCOUNT_ID));
            int subId = -1;
            if (accountHandle != null) {
                try {
                    // Now, accountHandle.getId() return iccId, Use following method to get subId
                    String iccId = accountHandle.getId();
                    /* SPRD: add for bug510314 @{ */
                    TelephonyManager telephonyManager = (TelephonyManager) context
                            .getSystemService(Context.TELEPHONY_SERVICE);
                    final int phoneCount = telephonyManager.getPhoneCount();
                    for (int i = 0; i < phoneCount; i++) {
                        SubscriptionInfo subInfo = DialerUtils
                                .getActiveSubscriptionInfo(context, i, false);
                        if (iccId != null && subInfo != null && iccId.equals(subInfo.getIccId())) {
                            subId = subInfo.getSubscriptionId();
                        }
                    }
                    /* @} */
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            PhoneAccount account = mTelecomManager.getPhoneAccount(accountHandle);
            // TODO
            if (account != null && !(PhoneNumberUtils.isEmergencyNumber(number))
                    && subId > -1) {
                final Icon icon = account.getIcon();
                simView.setImageIcon(icon);
                simView.setVisibility(View.VISIBLE);
            } else {
                simView.setVisibility(View.GONE);
            }
            if (!TextUtils.isEmpty(name)) {
                line1View.setText(name);
                numberView.setText(formattedNumber);
                numberView.setVisibility(View.VISIBLE);
            } else {
                line1View.setText(number);
                numberView.setVisibility(View.GONE);
            }

            if (iconView != null) {
                int type = c.getInt(CALL_TYPE_COLUMN_INDEX);
                Drawable drawable = getResources().getDrawable(
                        SourceUtils.getDrawableFromCallType(type));
                iconView.setImageDrawable(drawable);
            }
            int flags = DateUtils.FORMAT_ABBREV_RELATIVE;
            dateView.setText(DateUtils.getRelativeTimeSpanString(date, System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS, flags));
            // SPRD: modify for bug696421
            view.setTag(IntentProvider.getCallDetailIntentProvider(
                    createCallDetailsEntries(c, COUNT_CALL_DETAILS_ENTRIES),
                    buildContact(c), false, false));
        }

        @Override
        protected void onContentChanged() {
            super.onContentChanged();
            AsyncQueryThread thread = new AsyncQueryThread(getApplicationContext());
            thread.executeOnExecutor(sDefaultExecute);
        }
    }

    /**
     * SPRD: add for bug696421 @{
     */
    private DialerContact buildContact(Cursor c) {
        DialerContact.Builder contact = DialerContact.newBuilder();
        contact.setPhotoId(c.getLong(CACHED_PHOTO_ID_INDEX));
        if (c.getString(CACHED_LOOKUP_URI_INDEX) != null) {
            contact.setContactUri(c.getString(CACHED_LOOKUP_URI_INDEX));
        }
        /* UNISOC: add for bug965550*/
        if (!TextUtils.isEmpty(c.getString(CACHED_FORMATTED_NUMBER_INDEX))) {
            contact.setNumber(c.getString(CACHED_FORMATTED_NUMBER_INDEX));
        }
        /* second line of contact view. */
        if (!TextUtils.isEmpty(c.getString(CACHED_FORMATTED_NUMBER_INDEX))) {
            contact.setDisplayNumber(c.getString(CACHED_FORMATTED_NUMBER_INDEX));
        }
        /* phone number type (e.g. mobile) in second line of contact view */
        if (!TextUtils.isEmpty(c.getString(CALLER_NUMBERLABEL_COLUMN_INDEX))) {
            contact.setNumberLabel(c.getString(CALLER_NUMBERLABEL_COLUMN_INDEX));
        }
        return contact.build();
    }

    @MainThread
    private CallDetailsEntries createCallDetailsEntries(Cursor cursor, int count) {
        Assert.isMainThread();
        int position = cursor.getPosition();
        CallDetailsEntries.Builder entries = CallDetailsEntries.newBuilder();
        for (int i = 0; i < count; i++) {
            CallDetailsEntries.CallDetailsEntry.Builder entry =
                    CallDetailsEntries.CallDetailsEntry.newBuilder()
                            .setCallId(cursor.getLong(ID_COLUMN_INDEX))
                            .setCallType(cursor.getInt(CALL_TYPE_COLUMN_INDEX))
                            .setDataUsage(cursor.getLong(DATA_USAGE_INDEX))
                            .setDate(cursor.getLong(DATE_COLUMN_INDEX))
                            .setDuration(cursor.getLong(DURATION_COLUMN_INDEX))
                            .setFeatures(cursor.getInt(CALL_FEATURES_INDEX));
            entries.addEntries(entry.build());
            cursor.moveToNext();
        }
        cursor.moveToPosition(position);
        return entries.build();
    }
    /** @} */

    private class AsyncQueryThread extends AsyncTask<Uri, Void, Cursor> {
        private Context aContext;

        public AsyncQueryThread(Context context) {
            aContext = context;
        }

        @Override
        protected Cursor doInBackground(Uri... params) {
            ContentResolver cr = aContext.getContentResolver();

            String where = null;
            String[] selectArgs = null;
            if (!TextUtils.isEmpty(mQueryFilter)) {
                StringBuffer whereArg = new StringBuffer();
                List<String> args = Lists.newArrayList();
                whereArg.append(String.format("(%s like ?)", Calls.CACHED_NAME));
                whereArg.append(" OR ");
                whereArg.append(String.format("(%s like ?)", Calls.NUMBER));
                whereArg.append(" OR ");
                whereArg.append(String.format("(%s like ?)", CACHED_NORMALIZED_SIMPLE_NAME));
                whereArg.append(" OR ");
                whereArg.append(String.format("(%s like ?)", CACHED_NORMALIZED_FULL_NAME));

                args.add("%" + mQueryFilter + "%");
                args.add("%" + mQueryFilter + "%");
                args.add("%" + mQueryFilter + "%");
                args.add(mQueryFilter + "%");

                where = whereArg.toString();
                selectArgs = args.toArray(new String[0]);
            }
            /**
             * SPRD: add for bug696421 @{
             */
            Cursor c;
            if (0 == params.length) {
                c = cr.query(Calls.CONTENT_URI, CALL_LOG_PROJECTION, where, selectArgs, null);
            } else {
                c = cr.query(params[0], CALL_LOG_PROJECTION, where, selectArgs, null);
            }
            /** @} */
            return c;
        }

        @Override
        protected void onPostExecute(Cursor result) {
            /**
             * SPRD: add for bug696421 @{
             */
            result.moveToPosition(0);
            if (mFlag) {
                Intent intent = IntentProvider.getCallDetailIntentProvider(
                        createCallDetailsEntries(result, COUNT_CALL_DETAILS_ENTRIES),
                        buildContact(result), false, false).getIntent(aContext);
                result.close();
                if (intent != null) {
                    DialerUtils.startActivityWithErrorToast(aContext, intent);
                    finish();
                }
            } else {
                if (mAdapter != null) {
                    mAdapter.changeCursor(result);
                } else {
                    result.close();
                }
            }
            /** @} */
        }
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        /**
         * SPRD: add for bug696421 @{
         */
        Log.d(TAG, "position = " + position);
        final IntentProvider intentProvider = (IntentProvider) v.getTag();
        if (intentProvider != null) {
            final Intent intent = intentProvider.getIntent(this);
            // See IntentProvider.getCallDetailIntentProvider() for why this may be null.
            if (intent != null) {
                DialerUtils.startActivityWithErrorToast(this, intent);
            }
        }
        /** @} */
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (null != mAdapter) {
            mAdapter.changeCursor(null);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == android.R.id.home) {  // See ActionBar#setDisplayHomeAsUpEnabled()
            if (isResumed()) {
                onBackPressed();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
