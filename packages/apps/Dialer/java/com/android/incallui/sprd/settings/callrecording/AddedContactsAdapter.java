package com.android.incallui.sprd.settings.callrecording;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import com.android.dialer.app.R;
import java.util.ArrayList;

/**
 *This class used as brigde between listview ui and contacts number list
 */

public class AddedContactsAdapter extends BaseAdapter {

    Context context;
    ArrayList<String> numberList;

    public AddedContactsAdapter(Context context, ArrayList<String> numberList) {
        this.context = context;
        this.numberList = numberList;
    }

    public void onDataSetChanged(ArrayList<String> numberList) {
        this.numberList = numberList;
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return numberList.size();
    }

    @Override
    public String getItem(int position) {
        return numberList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View rowView = convertView;
        if (rowView == null) {
            LayoutInflater inflater = (LayoutInflater) context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            rowView = inflater.inflate(R.layout.row_added_number, null);
            ViewHolder viewHolder = new ViewHolder(rowView);
            rowView.setTag(viewHolder);
        }
        ViewHolder holder = (ViewHolder) rowView.getTag();
        holder.number.setText(numberList.get(position));
        return rowView;
    }

    public static class ViewHolder {
        TextView number;
        ImageView removeNumber;
        protected ViewHolder(View view) {
            number = (TextView)view.findViewById(R.id.number);
            removeNumber = (ImageView)view.findViewById(R.id.remove);
        }
    }
}
