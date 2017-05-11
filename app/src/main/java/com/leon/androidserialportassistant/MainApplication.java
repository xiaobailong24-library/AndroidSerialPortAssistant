package com.leon.androidserialportassistant;

import android.app.Application;

import com.blankj.utilcode.util.Utils;

/**
 * Created by xiaobailong24 on 2017/5/11.
 * MainApplication
 */

public class MainApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Utils.init(this);
    }
}
