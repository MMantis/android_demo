
package com.qihoo.videocloud.utils;

import android.content.Context;
import android.os.Environment;

import com.qihoo.livecloud.tools.MD5;
import com.qihoo.livecloud.tools.SDKUtils;
import com.qihoo.livecloud.utils.FileUtils;

import java.io.File;

/**
 * Created by LeiXiaojun on 2017/8/18.
 */

public class Utils {

    public static String getCacheDir() {
        String dir = Environment.getExternalStorageDirectory().getAbsolutePath();
        dir = dir + File.separator + "LiveCloud" + File.separator + "LocalServerCache" + File.separator;
        FileUtils.createDir(dir);
        return dir;
    }

    public static String getDownloadDir() {
        String dir = Environment.getExternalStorageDirectory().getAbsolutePath();
        dir = dir + File.separator + "LiveCloud" + File.separator + "LocalServerDownload" + File.separator;
        FileUtils.createDir(dir);
        return dir;
    }

    public static String getDeviceId(Context context) {
        return SDKUtils.getM2(context);
    }

    public static String getSign(String sn, String cid) {
        String token;
        if (cid != null && cid.equals("live_yingshi")) {
            token = "channel__" + cid + "sn__" + sn + "key_" + "0Zjurl^y5t{6O;<6L";
        } else {
            token = "channel__" + cid + "sn__" + sn + "key_" + "2Zjurl^y5t{6O;<6L";
        }
        return MD5.encryptMD5(token);
    }

    public static String caluTime(int time) {
        StringBuffer sbTime = new StringBuffer();
        int minutes = time / 1000 / 60;
        int seconds = (time / 1000) % 60;
        if (minutes < 10) {
            sbTime.append(0);
        }
        sbTime.append(minutes);
        sbTime.append(":");
        if (seconds < 10) {
            sbTime.append(0);
        }
        sbTime.append(seconds);
        return sbTime.toString();
    }
}
