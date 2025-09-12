package dev.skillter.synaxic.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "An item in an analytics breakdown, representing a dimension and its count")
public record BreakdownItem(
        @Schema(description = "The name of the item being counted (e.g., an endpoint path, country code, or API key prefix)", example = "/v1/ip")
        String item,
        @Schema(description = "The total count for this item", example = "5120")
        long count
) {}