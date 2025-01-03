import os
import pandas as pd
from flask import Flask, request, jsonify
from flask_cors import CORS
from convert import convert_to_csv
from KNN import compute_KNN

app = Flask(__name__)
CORS(app)

csv_file_path = 'dict.csv'
mapping_df = pd.read_csv(csv_file_path)
mapping_dict = mapping_df.set_index('编号')[['x', 'y']].to_dict(orient='index')

@app.route('/upload_wifi_data', methods=['POST'])
def upload_wifi_data():
    # 获取上传的 WiFi 数据
    try:
        wifi_data = request.data.decode('utf-8')
        print(f"Received WiFi Data: \n{wifi_data}")

        with open("knn.txt", "w", encoding="utf-8") as file:
            file.write(wifi_data)

        convert_to_csv()
        location = compute_KNN()

        # 发送数据给Android
        return jsonify({"location": location}), 200

    except Exception as e:
        print(f"Error: {e}")
        return jsonify({"error": str(e)}), 500


@app.route('/get_location', methods=['GET'])
def get_location():
    try:
        # 调用 KNN 算法获取编号
        position_id = compute_KNN()  # 返回一个整数

        if position_id in mapping_dict:
            x = mapping_dict[position_id]['x']
            y = mapping_dict[position_id]['y']
        else:
            return jsonify({"error": "Position ID not found in mapping table"}), 400

        # 返回二维坐标
        return jsonify({"location": f"x={x},y={y}"}), 200

    except Exception as e:
        return jsonify({"error": str(e)}), 500

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=True)
