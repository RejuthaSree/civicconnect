import csv
import random
from datetime import date, timedelta

random.seed(42)

cities = ["Bengaluru", "Mysuru", "Mangaluru"]

areas = {
    "Bengaluru": ["North", "South", "East", "West", "Central"],
    "Mysuru": ["North", "South", "East", "West"],
    "Mangaluru": ["North", "South", "Central"]
}

issue_types = [
    "ROAD",
    "GARBAGE",
    "WATER",
    "ELECTRICITY",
    "DRAINAGE",
    "SAFETY",
    "OTHER"
]

# Base demand for each service
base_demand = {
    "ROAD": 12,
    "GARBAGE": 15,
    "WATER": 10,
    "ELECTRICITY": 9,
    "DRAINAGE": 8,
    "SAFETY": 5,
    "OTHER": 4
}

rows = []

start_date = date(2025, 1, 1)

# Generate approximately 2 years of historical data
for day_number in range(120):

    current_date = start_date + timedelta(days=day_number)

    day_of_week = current_date.weekday()
    is_weekend = 1 if day_of_week >= 5 else 0

    # Randomly mark a small number of days as holidays
    is_holiday = 1 if random.random() < 0.04 else 0

    for city in cities:

        for area in areas[city]:

            for issue_type in issue_types:

                # Different demand depending on hour
                for hour in [8, 10, 12, 14, 16, 18, 20]:

                    demand = base_demand[issue_type]

                    # Morning/evening generally have more service requests
                    if hour in [10, 18, 20]:
                        demand += 4

                    # Weekend effect
                    if is_weekend:
                        demand += 2

                    # Holiday effect
                    if is_holiday:
                        demand += random.randint(-1, 2)

                    # Area variation
                    demand += random.randint(-3, 4)

                    # City variation
                    if city == "Bengaluru":
                        demand += 3
                    elif city == "Mysuru":
                        demand += 1

                    # Random variation
                    previous_demand = max(
                        0,
                        demand + random.randint(-4, 4)
                    )

                    demand = max(
                        0,
                        demand + random.randint(-3, 5)
                    )

                    rows.append([
                        current_date.isoformat(),
                        area,
                        city,
                        issue_type,
                        hour,
                        is_weekend,
                        is_holiday,
                        previous_demand,
                        demand
                    ])


file_path = "data/demand_data.csv"

with open(file_path, "w", newline="", encoding="utf-8") as file:

    writer = csv.writer(file)

    writer.writerow([
        "date",
        "area",
        "city",
        "issue_type",
        "hour",
        "is_weekend",
        "is_holiday",
        "previous_demand",
        "demand"
    ])

    writer.writerows(rows)

print(f"Dataset created successfully!")
print(f"Total rows: {len(rows)}")
print(f"Saved to: {file_path}")