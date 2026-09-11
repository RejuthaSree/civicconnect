from fastapi import FastAPI
from pydantic import BaseModel
import joblib
import pandas as pd
from matching import find_best_worker
from typing import List


# Create FastAPI application
app = FastAPI(
    title="CivicConnect AI Service",
    description="AI service for demand forecasting",
    version="1.0"
)


# Load trained model
model = joblib.load("models/demand_model.pkl")


# Request structure
class ForecastRequest(BaseModel):
    area: str
    city: str
    issue_type: str
    hour: int
    is_weekend: int
    is_holiday: int
    previous_demand: int

class WorkerInput(BaseModel):
    id: int
    skill: str
    latitude: float
    longitude: float
    work_radius_km: float = 10.0
    available: bool = True
    rating: float = 0.0
    experience_years: int = 0
    completed_tasks: int = 0
    verification_status: str = "PENDING"

class AllocationRequest(BaseModel):
    issue_type: str
    latitude: float
    longitude: float
    priority: str
    workers: List[WorkerInput]


# Health check
@app.get("/health")
def health():
    return {
        "status": "healthy",
        "model_loaded": True
    }


# Demand prediction
@app.post("/forecast")
def forecast(request: ForecastRequest):

    input_data = pd.DataFrame([{
    "area": request.area,
    "city": request.city,
    "issue_type": request.issue_type,
    "hour": request.hour,
    "is_weekend": request.is_weekend,
    "is_holiday": request.is_holiday,
    "previous_demand": request.previous_demand
}])

    prediction = model.predict(input_data)[0]

    return {
        "area": request.area,
        "city": request.city,
        "issue_type": request.issue_type,
        "predicted_demand": round(float(prediction), 2)
    }

@app.post("/allocate")
def allocate_worker(request: AllocationRequest):

    complaint = {
        "issue_type": request.issue_type,
        "latitude": request.latitude,
        "longitude": request.longitude,
        "priority": request.priority
    }

    workers = [
        worker.model_dump()
        for worker in request.workers
    ]

    best_worker = find_best_worker(
        workers,
        complaint
    )

    if best_worker is None:
        return {
            "success": False,
            "message": "No suitable worker found"
        }

    return {
        "success": True,
        "recommended_worker_id": best_worker["worker_id"],
        "match_score": best_worker["score"],
        "distance_km": best_worker["distance_km"],
        "reason": best_worker["reason"]
    }