import numpy as np
import re

def load_wifi_data(filename):
    with open(filename, 'r', encoding='utf-8') as file:
        data = file.readlines()
    return data

def get_wifi_data(data):
    locations = []
    bssids = []
    rssi_dict = {}

    current_location = None
    for line in data:
        line = line.strip()
        if line.startswith('当前位置坐标:'):
            current_location = line.split(':')[1].strip()
            locations.append(current_location)
        elif line.startswith('BSSID:'):
            bssid_match = re.match(r'BSSID: ([^,]+),', line)
            if bssid_match:
                bssid = bssid_match.group(1)
                if bssid not in bssids:
                    bssids.append(bssid)
                if bssid not in rssi_dict:
                    rssi_dict[bssid] = {}
                rssi_dict[bssid][current_location] = []
        elif line and current_location is not None:
            try:
                rssi_value = int(line)
                rssi_dict[bssid][current_location].append(rssi_value)
            except ValueError:
                pass
    return locations, bssids, rssi_dict


def create_csv_data(locations, bssids, rssi_dict):
    locations=sorted(locations)
    csv_data = [['Location'] + bssids]

    for location in locations:
        row = [location]
        for bssid in bssids:
            if location in rssi_dict[bssid]:
                avg_rssi = np.mean(rssi_dict[bssid][location])
                row.append(int(avg_rssi))
            else:
                row.append('')
        csv_data.append(row)
    return csv_data

def save_csv_data(filename, csv_data):
    with open(filename, 'w', encoding='utf-8') as file:
        for row in csv_data:
            file.write(','.join(map(str, row)) + '\n')

def convert_to_csv(txt_filename="knn.txt", csv_filename="knn.csv"):
    data = load_wifi_data(txt_filename)
    locations, bssids, rssi_dict = get_wifi_data(data)
    csv_data = create_csv_data(locations, bssids, rssi_dict)
    save_csv_data(csv_filename, csv_data)
