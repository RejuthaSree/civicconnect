package com.civic_connect.backend.common.config;

import com.civic_connect.backend.common.enums.IssueScope;
import com.civic_connect.backend.common.enums.IssueType;
import com.civic_connect.backend.common.enums.WorkerSkill;
import com.civic_connect.backend.common.exceptionHandler.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
public class IssuePricingConfig {

    private static final Set<IssueType> PUBLIC_ONLY_TYPES = EnumSet.of(
            IssueType.ROAD,
            IssueType.GARBAGE,
            IssueType.WATER,
            IssueType.ELECTRICITY,
            IssueType.DRAINAGE,
            IssueType.SAFETY,
            IssueType.OTHER
    );

    private static final Set<IssueType> HOUSEHOLD_ONLY_TYPES = EnumSet.of(
            IssueType.DEEP_CLEANING,
            IssueType.WALL_REPAIR,
            IssueType.PAINTING,
            IssueType.PLUMBING
    );

    private static final Map<IssueType, Double> PRICING_TABLE = new EnumMap<>(IssueType.class);

    static {
        PRICING_TABLE.put(IssueType.ROAD, 2500.0);
        PRICING_TABLE.put(IssueType.GARBAGE, 800.0);
        PRICING_TABLE.put(IssueType.WATER, 1500.0);
        PRICING_TABLE.put(IssueType.ELECTRICITY, 1200.0);
        PRICING_TABLE.put(IssueType.DRAINAGE, 1800.0);
        PRICING_TABLE.put(IssueType.SAFETY, 3000.0);
        PRICING_TABLE.put(IssueType.OTHER, 1000.0);

        PRICING_TABLE.put(IssueType.DEEP_CLEANING, 1500.0);
        PRICING_TABLE.put(IssueType.WALL_REPAIR, 2000.0);
        PRICING_TABLE.put(IssueType.PAINTING, 2500.0);
        PRICING_TABLE.put(IssueType.PLUMBING, 1000.0);
    }

    public IssueScope getRequiredScope(IssueType type) {
        if (PUBLIC_ONLY_TYPES.contains(type)) return IssueScope.PUBLIC;
        if (HOUSEHOLD_ONLY_TYPES.contains(type)) return IssueScope.HOUSEHOLD;
        throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown issue type: " + type);
    }

    public boolean isValidScopeForType(IssueType type, IssueScope scope) {
        return getRequiredScope(type) == scope;
    }

    public void validateScopeForType(IssueType type, IssueScope scope) {
        IssueScope required = getRequiredScope(type);
        if (required != scope) {
            String msg = required == IssueScope.PUBLIC
                    ? "Issue type '" + type + "' is a PUBLIC issue — it must be paid by the government/admin."
                    : "Issue type '" + type + "' is a HOUSEHOLD/PRIVATE issue — it must be paid by the citizen.";
            throw new ApiException(HttpStatus.BAD_REQUEST, msg);
        }
    }

    public Double getFixedPrice(IssueType type) {
        Double price = PRICING_TABLE.get(type);
        if (price == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No pricing configured for issue type: " + type);
        }
        return price;
    }

    public WorkerSkill getRequiredSkill(IssueType type) {
        return switch (type) {
            case ELECTRICITY -> WorkerSkill.ELECTRICIAN;
            case WATER, DRAINAGE, PLUMBING -> WorkerSkill.PLUMBER;
            case GARBAGE, DEEP_CLEANING -> WorkerSkill.SANITATION;
            case ROAD -> WorkerSkill.ROAD_REPAIR;
            case PAINTING -> WorkerSkill.PAINTER;
            case WALL_REPAIR -> WorkerSkill.CONTRACTOR;
            default -> WorkerSkill.CONTRACTOR;
        };
    }

    public Set<IssueType> getPublicTypes() { return PUBLIC_ONLY_TYPES; }
    public Set<IssueType> getHouseholdTypes() { return HOUSEHOLD_ONLY_TYPES; }
}
