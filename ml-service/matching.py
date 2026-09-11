import math


# --------------------------------------------------
# Issue type → required worker skill
# --------------------------------------------------

SKILL_MAP = {
    "WATER": "PLUMBER",
    "DRAINAGE": "PLUMBER",
    "ELECTRICITY": "ELECTRICIAN",
    "GARBAGE": "SANITATION",
    "ROAD": "ROAD_REPAIR",
    "SAFETY": "CONTRACTOR",
    "OTHER": "CONTRACTOR"
}


# --------------------------------------------------
# Calculate distance between two coordinates
# --------------------------------------------------

def calculate_distance(lat1, lon1, lat2, lon2):

    earth_radius_km = 6371

    lat1 = math.radians(lat1)
    lat2 = math.radians(lat2)

    delta_lat = math.radians(lat2 - lat1)
    delta_lon = math.radians(lon2 - lon1)

    a = (
        math.sin(delta_lat / 2) ** 2
        + math.cos(lat1)
        * math.cos(lat2)
        * math.sin(delta_lon / 2) ** 2
    )

    c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))

    return earth_radius_km * c


# --------------------------------------------------
# Calculate worker match score
# --------------------------------------------------

def calculate_worker_score(worker, complaint):

    required_skill = SKILL_MAP.get(
        complaint["issue_type"],
        "CONTRACTOR"
    )

    score = 0
    reasons = []


    # --------------------------------------------------
    # 1. Skill match - 35 points
    # --------------------------------------------------

    if worker["skill"] == required_skill:
        score += 35
        reasons.append("skill match")


    # --------------------------------------------------
    # 2. Availability - 15 points
    # --------------------------------------------------

    if worker["available"]:
        score += 15
        reasons.append("available")
    else:
        return None


    # --------------------------------------------------
    # 3. Verification - mandatory
    # --------------------------------------------------

    if worker["verification_status"] != "VERIFIED":
        return None

    score += 0
    reasons.append("verified")


    # --------------------------------------------------
    # 4. Distance - 20 points
    # --------------------------------------------------

    distance = calculate_distance(
        complaint["latitude"],
        complaint["longitude"],
        worker["latitude"],
        worker["longitude"]
    )

    work_radius = worker.get("work_radius_km", 10)

    if distance > work_radius:
        return None

    # Closer worker gets more points
    distance_score = max(
        0,
        20 * (1 - distance / work_radius)
    )

    score += distance_score
    reasons.append(f"{distance:.1f} km away")


    # --------------------------------------------------
    # 5. Rating - 10 points
    # --------------------------------------------------

    rating = max(0, min(worker.get("rating", 0), 5))

    rating_score = (rating / 5) * 10

    score += rating_score

    if rating > 0:
        reasons.append(f"rating {rating:.1f}")


    # --------------------------------------------------
    # 6. Experience - 5 points
    # --------------------------------------------------

    experience = worker.get("experience_years", 0)

    experience_score = min(experience / 10, 1) * 5

    score += experience_score


    # --------------------------------------------------
    # 7. Workload fairness - 15 points
    # --------------------------------------------------

    completed_tasks = worker.get("completed_tasks", 0)

    fairness_score = 15 / (1 + completed_tasks / 20)

    score += fairness_score

    reasons.append("workload considered")


    return {
        "worker_id": worker["id"],
        "score": round(score, 2),
        "distance_km": round(distance, 2),
        "reason": ", ".join(reasons)
    }


# --------------------------------------------------
# Find best worker
# --------------------------------------------------

def find_best_worker(workers, complaint):

    results = []

    for worker in workers:

        result = calculate_worker_score(
            worker,
            complaint
        )

        if result is not None:
            results.append(result)


    if not results:
        return None


    results.sort(
        key=lambda x: x["score"],
        reverse=True
    )

    return results[0]