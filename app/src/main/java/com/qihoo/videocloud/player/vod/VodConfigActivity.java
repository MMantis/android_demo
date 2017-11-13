
package com.qihoo.videocloud.player.vod;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import com.qihoo.livecloudrefactor.R;
import com.qihoo.videocloud.player.preview.MediaPlayerActivity;
import com.qihoo.videocloud.widget.ViewHeader;

public class VodConfigActivity extends Activity implements View.OnClickListener, View.OnLongClickListener {

    private ViewHeader viewHeaderMine;
    private RadioGroup rgDecodedMode;
    private RadioButton rbConfigDecodedAuto;
    private RadioButton rbConfigDecodedSoft;
    private ImageView ivPlay;

    private EditText etBusunessId;
    private EditText etChannelId;
    private EditText etUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vod_config);

        initView();
    }

    private void initView() {
        rgDecodedMode = (RadioGroup) findViewById(R.id.rg_decoded_mode);
        rbConfigDecodedAuto = (RadioButton) findViewById(R.id.rb_config_decoded_auto);
        rbConfigDecodedSoft = (RadioButton) findViewById(R.id.rb_config_decoded_soft);

        etBusunessId = (EditText) findViewById(R.id.et_busuness_id);
        etChannelId = (EditText) findViewById(R.id.et_channel_id);
        etUrl = (EditText) findViewById(R.id.et_url);

        ivPlay = (ImageView) findViewById(R.id.iv_play);
        ivPlay.setOnClickListener(this);
        ivPlay.setOnLongClickListener(this);

        viewHeaderMine = (ViewHeader) findViewById(R.id.viewHeaderMine);
        viewHeaderMine.setCenterText("点播播放器");
        viewHeaderMine.getLeftIcon().setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.headerLeftIcon: {
                finish();
                break;
            }
            case R.id.iv_play: {
                Intent intent = new Intent(VodConfigActivity.this, VodActivity.class);
                intent.putExtra("businessId", etBusunessId.getText().toString().trim());
                intent.putExtra("channelId", etChannelId.getText().toString().trim());
                intent.putExtra("url", etUrl.getText().toString().trim());
                intent.putExtra("autoDecoded", rbConfigDecodedAuto.isChecked());
                startActivity(intent);
                break;
            }
        }
    }

    @Override
    public boolean onLongClick(View view) {
        switch (view.getId()) {
            case R.id.iv_play: {
                Intent intent = new Intent(VodConfigActivity.this, MediaPlayerActivity.class);
                intent.putExtra("url", etUrl.getText().toString().trim());
                startActivity(intent);
                return true;
            }
            default: {
                return false;
            }
        }
    }
}
