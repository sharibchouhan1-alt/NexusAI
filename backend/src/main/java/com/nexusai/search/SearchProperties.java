package com.nexusai.search;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** {@code nexusai.search.*} */
@Validated
@ConfigurationProperties("nexusai.search")
record SearchProperties(@Min(1) int defaultLimit, @Min(1) @Max(200) int maxLimit) {
}
