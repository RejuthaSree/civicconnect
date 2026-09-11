import pandas as pd
import joblib

from sklearn.model_selection import train_test_split
from sklearn.compose import ColumnTransformer
from sklearn.preprocessing import OneHotEncoder
from sklearn.ensemble import RandomForestRegressor
from sklearn.pipeline import Pipeline
from sklearn.metrics import mean_absolute_error, mean_squared_error, r2_score


# --------------------------------------------------
# 1. Load dataset
# --------------------------------------------------

data = pd.read_csv("data/demand_data.csv")

print("Dataset loaded successfully!")
print("Rows:", len(data))


# --------------------------------------------------
# 2. Select features and target
# --------------------------------------------------

features = [
    "area",
    "city",
    "issue_type",
    "hour",
    "is_weekend",
    "is_holiday",
    "previous_demand"
]

target = "demand"

X = data[features]
y = data[target]


# --------------------------------------------------
# 3. Train-test split
# --------------------------------------------------

X_train, X_test, y_train, y_test = train_test_split(
    X,
    y,
    test_size=0.2,
    random_state=42
)

print("Training rows:", len(X_train))
print("Testing rows:", len(X_test))


# --------------------------------------------------
# 4. Categorical columns
# --------------------------------------------------

categorical_features = [
    "area",
    "city",
    "issue_type"
]

numeric_features = [
    "hour",
    "is_weekend",
    "is_holiday",
    "previous_demand"
]


# --------------------------------------------------
# 5. Preprocessing
# --------------------------------------------------

preprocessor = ColumnTransformer(
    transformers=[
        (
            "categorical",
            OneHotEncoder(handle_unknown="ignore"),
            categorical_features
        )
    ],
    remainder="passthrough"
)


# --------------------------------------------------
# 6. Create ML model
# --------------------------------------------------

model = RandomForestRegressor(
    n_estimators=100,
    random_state=42,
    n_jobs=-1
)


# --------------------------------------------------
# 7. Create complete pipeline
# --------------------------------------------------

pipeline = Pipeline(
    steps=[
        ("preprocessor", preprocessor),
        ("model", model)
    ]
)


# --------------------------------------------------
# 8. Train model
# --------------------------------------------------

print("Training model...")

pipeline.fit(X_train, y_train)

print("Model training completed!")


# --------------------------------------------------
# 9. Evaluate model
# --------------------------------------------------

predictions = pipeline.predict(X_test)

mae = mean_absolute_error(y_test, predictions)
mse = mean_squared_error(y_test, predictions)
rmse = mse ** 0.5
r2 = r2_score(y_test, predictions)

print("\n========== MODEL RESULTS ==========")
print(f"MAE  : {mae:.2f}")
print(f"RMSE : {rmse:.2f}")
print(f"R2   : {r2:.4f}")
print("===================================")


# --------------------------------------------------
# 10. Save model
# --------------------------------------------------

model_path = "models/demand_model.pkl"

joblib.dump(pipeline, model_path)

print(f"\nModel saved successfully to: {model_path}")