package com.leon.androidserialportassistant;

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.blankj.utilcode.util.ConvertUtils;
import com.fntech.m10a.gpio.M10A_GPIO;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidParameterException;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getName();

    private OutputStream mOutputStream;
    private InputStream mInputStream;
    private TextView mTextRecv;
    private EditText mEditSend;
    private Button mButtonSerialPort;
    private Button mButtonSend;
    private FragmentManager mFragmentManager;
    private SerialPort mSerialPort;
    private RecvThread mRecvThread;

    final byte[] data = new byte[]{
            0x10, 0x00,//T
            0x00, 0x08,//L
            //V (OpMode)
            0x20, 0x00,//T
            0x00, 0x04,//L
            0x00, 0x00, 0x00, 0x00//V 单次
    };

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /* Set setting fragment */
        mFragmentManager = getFragmentManager();
        FragmentTransaction fragmentTransaction = mFragmentManager.beginTransaction();
        fragmentTransaction.add(R.id.layoutSetting, new SettingFragment(), "SETTING_FRAGMENT");
        fragmentTransaction.commit();

        mTextRecv = (TextView) findViewById(R.id.tvRecv);
        mEditSend = (EditText) findViewById(R.id.etSend);
        mButtonSerialPort = (Button) findViewById(R.id.btnSerialPort);
        mButtonSerialPort.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SettingFragment fragment = (SettingFragment) mFragmentManager.findFragmentByTag("SETTING_FRAGMENT");
                if (mSerialPort == null) {
                    /* Read serial port parameters */
                    SharedPreferences sharedPreferences = getSharedPreferences("com.leon.androidserialportassistant_preferences", MODE_PRIVATE);
                    String path = sharedPreferences.getString("DEVICE", "");
                    int baudrate = Integer.decode(sharedPreferences.getString("BAUDRATE", "-1"));
                    /* Check parameters */
                    if ((path.length() == 0) || (baudrate == -1)) {
                        throw new InvalidParameterException();
                    }
                    try {
                        mEditSend.setText(ConvertUtils.bytes2HexString(data));

                        M10A_GPIO.PowerOn();
                        /* Open serial port */
                        mSerialPort = new SerialPort(new File(path), baudrate, 0);
                        mInputStream = mSerialPort.getInputStream();
                        mOutputStream = mSerialPort.getOutputStream();
                        mButtonSerialPort.setText("关闭串口");
                        /* Start read serial port thread */
                        mRecvThread = new RecvThread();
                        mRecvThread.start();
                        /* Disable setting fragment parameters */
                        fragment.mListBaudrate.setEnabled(false);
                        fragment.mListDevice.setEnabled(false);

                    } catch (IOException e) {
                        M10A_GPIO.PowerOff();
                        Toast.makeText(MainActivity.this, "串口打开失败！", Toast.LENGTH_SHORT).show();
                        e.printStackTrace();
                    }
                } else {
                    try {
                        M10A_GPIO.PowerOff();
                        mRecvThread.interrupt();
                     /* Close serial port */
                        mSerialPort.close();
                        mSerialPort = null;
                        mInputStream.close();
                        mInputStream = null;
                        mOutputStream.close();
                        mOutputStream = null;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    mButtonSerialPort.setText("打开串口");
                    /* Enable setting fragment parameters */
                    fragment.mListDevice.setEnabled(true);
                    fragment.mListBaudrate.setEnabled(true);
                }
            }
        });

        mButtonSend = (Button) findViewById(R.id.btnSend);
        mButtonSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                /* Check serial port */
                if (mSerialPort == null) {
                    Toast.makeText(MainActivity.this, "请先打开串口", Toast.LENGTH_SHORT).show();
                    return;
                }
                /* Check messages */
                //                byte[] data = new byte[]{
                //                        0x10, 0x00,//T
                //                        0x00, 0x08,//L
                //                        //V (OpMode)
                //                        0x20, 0x00,//T
                //                        0x00, 0x04,//L
                //                        0x00, 0x00, 0x00, 0x00//V 单次
                //                };
                //                String msg = "1000 0008 2000 0004 0000 0000";//单次
                //0210160004200E00000000000600730500000000000000000000

                //                String msg = "1000 0008 2000 0004 0200 0064";//100次
                //0210160004200E00000000000600730500000000000000000000

                //                String msg = "1102 0000";//设备配置查询消息
                //0210160004200E00000000000600730500000000000000000000

                //                String msg = ConvertUtils.bytes2HexString(data);
                //                mEditSend.setText(ConvertUtils.bytes2HexString(data));
                String msg = mEditSend.getText().toString();
                if (TextUtils.isEmpty(msg)) {
                    Toast.makeText(MainActivity.this, "输入为空", Toast.LENGTH_SHORT).show();
                    return;
                }
                // TODO: 2016-05-05 setting_send 
                /* Write serial port */
                try {
                    //                    mOutputStream.write(ConvertUtils.hexString2Bytes(msg.replaceAll(" ", "")));
                    //                    mOutputStream.write(ConvertUtils.hexString2Bytes(msg.replaceAll(" ", "")));
                    mOutputStream.write(ConvertUtils.hexString2Bytes(msg.replaceAll(" ", "")));
                    mOutputStream.flush();
                    Log.w(TAG, "onClick: " + Arrays.toString(ConvertUtils.hexString2Bytes(msg.replaceAll(" ", ""))));
                    onDataSend(ConvertUtils.hexString2Bytes(msg.replaceAll(" ", "")),
                            msg.length());
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        });
    }

    private class RecvThread extends Thread {
        int timeoutAdder = 0;//

        @Override
        public void run() {
            while (!isInterrupted()) {
                byte[] buffer;
                if (mInputStream == null) {
                    Log.e(TAG, "run: mInputStream == null");
                    return;
                }
                try {
                    //                    while (++timeoutAdder < 1000) {
                    while (mInputStream.available() > 0) {
                        //循环读取数据
                        buffer = new byte[mInputStream.available()];
                        int readLen = mInputStream.read(buffer);
                        Log.d(TAG, "run: readLen=" + readLen);
                        if (readLen > 0) {
                            Log.d(TAG, "receive: " + ConvertUtils.bytes2HexString(buffer));
                            onDataReceived(buffer, readLen);
                        }
                        //                        }
                        //                        Thread.sleep(1);
                    }
                    timeoutAdder = 0;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void onDataReceived(final byte[] buffer, final int size) {
        // TODO: 2016-05-05 setting_recv 
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mTextRecv.append("--->" + ConvertUtils.bytes2HexString(buffer), 0, size);
                mTextRecv.append("\n");
            }
        });
    }

    private void onDataSend(final byte[] buffer, final int size) {
        // TODO: 2016-05-05 setting_recv
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mTextRecv.append("<---" + ConvertUtils.bytes2HexString(buffer), 0, size);
                mTextRecv.append("\n");
            }
        });
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                if (mSerialPort != null) {
                    Toast.makeText(MainActivity.this, "请先关闭串口", Toast.LENGTH_SHORT).show();
                    return true;
                }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        M10A_GPIO.PowerOff();
    }
}
