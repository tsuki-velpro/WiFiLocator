package com.example.indoorlocator;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WifiCollectActivity extends AppCompatActivity {

    private static final int WIFI_SCAN_PERMISSION_CODE = 1;
    private static final int WRITE_PERMISSION_CODE = 2;
    private WifiManager wifiManager;
    private EditText etCoordinates;
    private TextView tvStatus;
    private EditText etMaxScanCount;
    private EditText etScanInterval;
    private int maxScanCount;
    private long scanInterval;
    private Handler handler;
    private int scanCount = 0;
    private StringBuilder wifiData = new StringBuilder(); // 存储WiFi数据
    private Map<String, List<Integer>> wifiRssiData = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wifi_collect);

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        etCoordinates = findViewById(R.id.et_coordinates);
        Button btnCollect = findViewById(R.id.btn_collect);
        Button btnExport = findViewById(R.id.btn_export); // 导出按钮
        Button btnClear = findViewById(R.id.btn_clear);
        tvStatus = findViewById(R.id.tv_status);
        etMaxScanCount = findViewById(R.id.et_max_scan_count);
        etScanInterval = findViewById(R.id.et_scan_interval);
        handler = new Handler();

        btnCollect.setOnClickListener(v -> {
            String coordinates = etCoordinates.getText().toString();
            if (TextUtils.isEmpty(coordinates)) {
                Toast.makeText(WifiCollectActivity.this, "请输入当前位置坐标", Toast.LENGTH_SHORT).show();
            } else {
                wifiData.append("当前位置坐标: ").append(coordinates).append("\n");
                startWifiScan();
            }
        });

        btnExport.setOnClickListener(v -> {
            // 检查写入外部存储的权限
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, WRITE_PERMISSION_CODE);
            } else {
                exportDataToFile();
            }
        });

        btnClear.setOnClickListener(v -> {
            wifiData.setLength(0);
            Toast.makeText(WifiCollectActivity.this, "已清空", Toast.LENGTH_SHORT).show();
        });

        // 动态申请WiFi扫描权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, WIFI_SCAN_PERMISSION_CODE);
        }
    }

    private void startWifiScan() {
        try {
            maxScanCount = Integer.parseInt(etMaxScanCount.getText().toString());
        } catch (NumberFormatException e) {
            maxScanCount = 10; // 默认值
        }
        try {
            scanInterval = Long.parseLong(etScanInterval.getText().toString());
        } catch (NumberFormatException e) {
            scanInterval = 1000; // 默认值，单位为毫秒
        }
        scanCount = 0;
        wifiRssiData.clear();
        tvStatus.setText("正在采集");
        registerReceiver(wifiScanReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        wifiManager.startScan();
        handler.post(scanRunnable);
    }

    private Runnable scanRunnable = new Runnable() {
        @SuppressLint("SetTextI18n")
        @Override
        public void run() {
            if (scanCount < maxScanCount) {
                wifiManager.startScan();
                handler.postDelayed(this, scanInterval);  // 1000ms 间隔
                tvStatus.setText("第" + scanCount + "/" + maxScanCount + "次采集完成");
            } else {
                unregisterReceiver(wifiScanReceiver);
                for (Map.Entry<String, List<Integer>> entry : wifiRssiData.entrySet()) {
                    wifiData.append(entry.getKey()).append("\n");
                    for (Integer rssi : entry.getValue()) {
                        wifiData.append(rssi).append("\n");
                    }
                    wifiData.append("\n"); // 每组之间空一行
                }
                tvStatus.setText("采集完成");
            }
        }
    };

    private final BroadcastReceiver wifiScanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // 检查权限是否被授予
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                List<ScanResult> results = wifiManager.getScanResults();
                StringBuilder builder = new StringBuilder();
                for (ScanResult result : results) {
                    builder.append("SSID: ").append(result.SSID)
                            .append(", BSSID: ").append(result.BSSID)
                            .append(", RSSI: ").append(result.level).append("dBm\n");
                }
                scanCount++;
                for (ScanResult result : results) {
                    if (result.SSID.equals("SUSTech-wifi") || result.SSID.equals("SUSTech-wifi-5G") || result.SSID.equals("SUSTech-802.1x")) {
                        String key = "BSSID: " + result.BSSID + ", SSID: " + result.SSID;
                        // 如果当前BSSID还没有被记录，初始化
                        if (!wifiRssiData.containsKey(key)) {
                            wifiRssiData.put(key, new ArrayList<>());
                        }
                        // 添加当前RSSI到相应的BSSID组
                        wifiRssiData.get(key).add(result.level);
                    }
                }
            } else {
                Toast.makeText(WifiCollectActivity.this, "没有WiFi扫描权限", Toast.LENGTH_SHORT).show();
            }
        }
    };

    private void exportDataToFile() {
        if (isExternalStorageWritable()) {
            File file = new File(getExternalFilesDir(null), "wifi_data.txt");
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(wifiData.toString().getBytes());
                Toast.makeText(this, "数据已导出到: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
            } catch (IOException e) {
                Toast.makeText(this, "文件写入失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "外部存储不可写", Toast.LENGTH_SHORT).show();
        }
    }

    // 检查外部存储是否可写
    private boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(scanRunnable);
        try {
            unregisterReceiver(wifiScanReceiver);
        } catch (IllegalArgumentException e) {
            // 接收器未注册
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);  // 调用父类方法
        if (requestCode == WIFI_SCAN_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // WiFi扫描权限已授予
            } else {
                Toast.makeText(this, "需要WiFi扫描权限", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == WRITE_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 写入权限已授予
                exportDataToFile();
            } else {
                Toast.makeText(this, "需要文件写入权限", Toast.LENGTH_SHORT).show();
            }
        }
    }
}

