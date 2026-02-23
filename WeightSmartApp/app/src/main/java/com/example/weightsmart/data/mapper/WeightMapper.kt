package com.example.weightsmart.data.mapper

import com.example.weightsmart.data.entity.WeightEntryEntity
import com.example.weightsmart.domain.model.Weight
import java.time.Instant

fun WeightEntryEntity.toDomain(): Weight =
    Weight.of(
        id = id,
        userId = userId,
        value = weight,
        measuredAt = Instant.ofEpochSecond(measuredAtEpochSec)
    )
