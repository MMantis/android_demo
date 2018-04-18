
package com.qihoo.videocloud.interactbrocast.adapter;

import android.app.Activity;
import android.content.Context;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;

import com.qihoo.livecloudrefactor.R;
import com.qihoo.videocloud.interactbrocast.party.PartyItemBaseView;
import com.qihoo.videocloud.interactbrocast.party.PartyRoleItem;

import java.util.ArrayList;

/**
 * Created by liuyanqing on 2018/3/8.
 */

public class PartyGridViewAdapter extends ArrayAdapter<PartyRoleItem> {

    private Context mContext;
    private int mlayoutResourceId;
    private int itemWidth;
    private int itemHeight;

    private ArrayList<PartyRoleItem> mAllData;

    public PartyGridViewAdapter(@NonNull Context context, @LayoutRes int resource, ArrayList<PartyRoleItem> data) {
        super(context, resource, data);
        mlayoutResourceId = resource;
        mContext = context;
        mAllData = data;
    }

    public void setItemWidth(int width) {
        this.itemWidth = width;
    }

    public void setItemHeight(int height) {
        this.itemHeight = height;
    }

    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View row = convertView;
        ViewHolder viewHolder;

        PartyRoleItem roleItem = mAllData.get(position);

        if (row == null) {
            //TODO test
            LayoutInflater inflater = ((Activity) mContext).getLayoutInflater();
            row = inflater.inflate(mlayoutResourceId, parent, false);
            viewHolder = new ViewHolder();

            viewHolder.defImage = (ImageView) row.findViewById(R.id.default_image);
            viewHolder.baseView = (PartyItemBaseView) row.findViewById(R.id.party_grid_video_baseview);

            AbsListView.LayoutParams param = new AbsListView.LayoutParams(itemWidth, itemHeight);
            row.setLayoutParams(param);

            viewHolder.baseView.setVideoView(roleItem.getVideoView(), itemWidth, itemHeight);

            row.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) row.getTag();
        }

        viewHolder.defImage.setImageBitmap(roleItem.getImage());

        return row;
    }

    static class ViewHolder {
        ImageView defImage;
        PartyItemBaseView baseView;
    }

}
