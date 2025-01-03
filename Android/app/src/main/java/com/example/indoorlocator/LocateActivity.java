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
import android.os.Handler;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;

public class LocateActivity extends AppCompatActivity {

    private static final int WIFI_SCAN_PERMISSION_CODE = 1;
    private WifiManager wifiManager;
    private TextView tvResult;
    private Button btnStartLocate, btnStopLocate;
    private long scanInterval = 3000; // 扫描间隔（毫秒）
    private Handler handler;
    private boolean isLocating = false; // 定位活动状态
    private StringBuilder wifiData = new StringBuilder();
    private Map<String, List<Integer>> wifiRssiData = new HashMap<>();
    private EditText etServerAddress;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_locate);

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        etServerAddress = findViewById(R.id.et_server_address);
        tvResult = findViewById(R.id.tv_result);
        btnStartLocate = findViewById(R.id.btn_start_locate);
        btnStopLocate = findViewById(R.id.btn_stop_locate);
        handler = new Handler();

        // 开始定位按钮逻辑
        btnStartLocate.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, WIFI_SCAN_PERMISSION_CODE);
            } else {
                startWifiScan();
            }
        });

        // 停止定位按钮逻辑
        btnStopLocate.setOnClickListener(v -> stopWifiScan());
    }

    private void startWifiScan() {
        if (isLocating) {
            Toast.makeText(this, "定位已经在进行中", Toast.LENGTH_SHORT).show();
            return;
        }

        isLocating = true;
        wifiRssiData.clear();
        wifiData.setLength(0); // 清空之前的数据
        tvResult.setText("开始定位...");
        registerReceiver(wifiScanReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        wifiManager.startScan();
        handler.post(scanRunnable);
    }

    private void stopWifiScan() {
        if (!isLocating) {
            Toast.makeText(this, "定位尚未开始", Toast.LENGTH_SHORT).show();
            return;
        }

        isLocating = false;
        handler.removeCallbacks(scanRunnable);
        try {
            unregisterReceiver(wifiScanReceiver);
        } catch (IllegalArgumentException e) {
            // 接收器未注册
        }
        tvResult.setText("定位已停止");
    }

    private final Runnable scanRunnable = new Runnable() {
        @Override
        public void run() {
            if (isLocating) {
                wifiManager.startScan();
                wifiManager.startScan();
                handler.postDelayed(this, scanInterval); // 继续下次扫描
            }
        }
    };

    private final BroadcastReceiver wifiScanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // 检查是否具有访问位置的权限
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                // 获取 WiFi 扫描结果
                List<ScanResult> results = wifiManager.getScanResults();
                StringBuilder builder = new StringBuilder();

                for (ScanResult result : results) {
                    builder.append("SSID: ").append(result.SSID)
                            .append(", BSSID: ").append(result.BSSID)
                            .append(", RSSI: ").append(result.level).append("dBm\n");
                }
                //***************
                wifiData.append("当前位置坐标: ").append(0).append("\n");
                //***************
                // 过滤目标 WiFi 并处理 RSSI 数据
                for (ScanResult result : results) {
                    // 检查是否是目标 WiFi（可以修改为您的实际需求）
                    if (result.SSID.equals("SUSTech-wifi") || result.SSID.equals("SUSTech-wifi-5G") || result.SSID.equals("SUSTech-802.1x")) {
                        String key = "BSSID: " + result.BSSID + ", SSID: " + result.SSID;

                        // 如果当前 BSSID 未被记录，则初始化
                        if (!wifiRssiData.containsKey(key)) {
                            wifiRssiData.put(key, new ArrayList<>());
                        }
                        // 添加当前 RSSI 到相应的 BSSID 组
                        wifiRssiData.get(key).add(result.level);
                    }
                }


                for (Map.Entry<String, List<Integer>> entry : wifiRssiData.entrySet()) {
                    wifiData.append(entry.getKey()).append("\n");
                    for (Integer rssi : entry.getValue()) {
                        wifiData.append(rssi).append("\n");
                    }
                    wifiData.append("\n"); // 每组之间空一行
                }
                uploadWifiData(wifiData.toString());
                wifiData.setLength(0);

            } else {
                // 如果没有权限，提示用户
                Toast.makeText(LocateActivity.this, "没有 WiFi 扫描权限，请授予权限", Toast.LENGTH_SHORT).show();
            }
        }
    };



    private void uploadWifiData(String wifiData) {
        OkHttpClient client = new OkHttpClient();

        // 获取用户输入的服务器地址
        String serverAddress = etServerAddress.getText().toString().trim();
        // 检查服务器地址是否为空
        if (serverAddress.isEmpty()) {
            runOnUiThread(() -> Toast.makeText(this, "请输入服务器地址", Toast.LENGTH_SHORT).show());
            return;
        }

        // 创建 RequestBody，传入 byte[]
        RequestBody requestBody = RequestBody.create(
                MediaType.parse("text/plain"), wifiData.getBytes());

        // 创建请求
        Request request = new Request.Builder()
                .url("http://" + serverAddress + ":5000/upload_wifi_data")
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> Toast.makeText(LocateActivity.this, "上传失败" + e.getMessage(), Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String result = response.body().string();
                    runOnUiThread(() -> tvResult.setText("定位结果: " + result));
                } else {
                    runOnUiThread(() -> Toast.makeText(LocateActivity.this, "服务器返回错误", Toast.LENGTH_SHORT).show());
                }
            }
        });
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopWifiScan();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == WIFI_SCAN_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startWifiScan();
            } else {
                Toast.makeText(this, "需要WiFi扫描权限", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
