import pandas as pd
import numpy as np

from sklearn.impute import SimpleImputer
from sklearn.neighbors import KNeighborsClassifier

def compute_KNN():
    train_file_path = 'wifi_data.csv'
    test_file_path = 'knn.csv'

    wifi_data_test = pd.read_csv(train_file_path)
    wifi_data01 = pd.read_csv(test_file_path)

    # Align the datasets
    train_labels = wifi_data_test['Location'].values
    test_labels = wifi_data01['Location'].values

    train_bssids = set(wifi_data_test.columns[1:])
    test_bssids = set(wifi_data01.columns[1:])
    all_bssids = train_bssids.union(test_bssids)

    wifi_data_test_aligned = wifi_data_test.reindex(columns=['Location'] + list(all_bssids), fill_value=np.nan)
    wifi_data01_aligned = wifi_data01.reindex(columns=['Location'] + list(all_bssids), fill_value=np.nan)

    train_features = wifi_data_test_aligned.drop(columns=['Location']).values
    test_features = wifi_data01_aligned.drop(columns=['Location']).values

    imputer = SimpleImputer(missing_values=np.nan, strategy='constant', fill_value=-100)
    train_features = imputer.fit_transform(train_features)
    test_features = imputer.transform(test_features)

    knn = KNeighborsClassifier(n_neighbors=3)
    knn.fit(train_features, train_labels)

    predicted_labels = knn.predict(test_features)
    print(predicted_labels[0])
    return int(predicted_labels[0])

