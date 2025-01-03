import pandas as pd
import numpy as np
from sklearn.impute import SimpleImputer
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import LabelEncoder
import torch
import torch.nn as nn
import torch.optim as optim
from torch.utils.data import DataLoader, TensorDataset

train_file_path = 'wifi_data01.csv'
test_file_path = 'wifi_data_test.csv'

wifi_data_test = pd.read_csv(train_file_path)
wifi_data01 = pd.read_csv(test_file_path)

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

encoder = LabelEncoder()
train_labels = encoder.fit_transform(train_labels)
test_labels = encoder.transform(test_labels)
num_classes = len(encoder.classes_)

def one_hot_encode(labels, num_classes):
    return np.eye(num_classes)[labels]

train_labels_onehot = one_hot_encode(train_labels, num_classes)
test_labels_onehot = one_hot_encode(test_labels, num_classes)

X_train, X_val, y_train, y_val = train_test_split(train_features, train_labels_onehot, test_size=0.2, random_state=42)

X_train_tensor = torch.tensor(X_train, dtype=torch.float32)
y_train_tensor = torch.tensor(y_train, dtype=torch.float32)
X_val_tensor = torch.tensor(X_val, dtype=torch.float32)
y_val_tensor = torch.tensor(y_val, dtype=torch.float32)
X_test_tensor = torch.tensor(test_features, dtype=torch.float32)
y_test_tensor = torch.tensor(test_labels_onehot, dtype=torch.float32)

batch_size = 32
train_dataset = TensorDataset(X_train_tensor, y_train_tensor)
val_dataset = TensorDataset(X_val_tensor, y_val_tensor)
test_dataset = TensorDataset(X_test_tensor, y_test_tensor)

train_loader = DataLoader(train_dataset, batch_size=batch_size, shuffle=True)
val_loader = DataLoader(val_dataset, batch_size=batch_size, shuffle=False)
test_loader = DataLoader(test_dataset, batch_size=batch_size, shuffle=False)

class MLP(nn.Module):
    def __init__(self, input_size, num_classes):
        super(MLP, self).__init__()
        self.fc1 = nn.Linear(input_size, 128)
        self.relu1 = nn.ReLU()
        self.dropout1 = nn.Dropout(0.3)
        self.fc2 = nn.Linear(128, 64)
        self.relu2 = nn.ReLU()
        self.dropout2 = nn.Dropout(0.3)
        self.fc3 = nn.Linear(64, num_classes)

    def forward(self, x):
        x = self.fc1(x)
        x = self.relu1(x)
        x = self.dropout1(x)
        x = self.fc2(x)
        x = self.relu2(x)
        x = self.dropout2(x)
        x = self.fc3(x)
        return x

input_size = train_features.shape[1]
model = MLP(input_size, num_classes)
criterion = nn.CrossEntropyLoss()
optimizer = optim.Adam(model.parameters(), lr=0.00001)

def train_model(model, train_loader, val_loader, criterion, optimizer, epochs=2000):
    for epoch in range(epochs):
        model.train()
        train_loss = 0
        for X_batch, y_batch in train_loader:
            optimizer.zero_grad()
            outputs = model(X_batch)
            loss = criterion(outputs, torch.argmax(y_batch, dim=1))
            loss.backward()
            optimizer.step()
            train_loss += loss.item()

        val_loss = 0
        model.eval()
        with torch.no_grad():
            for X_batch, y_batch in val_loader:
                outputs = model(X_batch)
                loss = criterion(outputs, torch.argmax(y_batch, dim=1))
                val_loss += loss.item()

        print(f"Epoch {epoch+1}/{epochs}, Train Loss: {train_loss/len(train_loader):.4f}, Val Loss: {val_loss/len(val_loader):.4f}")

train_model(model, train_loader, val_loader, criterion, optimizer)

def predict_MLP(model, sample_features):
    model.eval()
    with torch.no_grad():
        sample_tensor = torch.tensor(sample_features, dtype=torch.float32).unsqueeze(0)  # Add batch dimension
        outputs = model(sample_tensor)
        _, predicted = torch.max(outputs, 1)
        return predicted.item()

