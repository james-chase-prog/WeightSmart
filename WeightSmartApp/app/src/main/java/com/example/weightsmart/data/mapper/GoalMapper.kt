package com.example.weightsmart.data.mapper

import com.example.weightsmart.data.entity.GoalWeightEntity
import com.example.weightsmart.domain.model.GoalWeight
import java.time.Instant

fun GoalWeightEntity.toDomain(): GoalWeight =
    GoalWeight.of(
        userId = userId,
        valueLb = goalWeight,
        goalDate = goalDateEpochSec?.let(Instant::ofEpochSecond),
        notes = notes,
        updatedAt = Instant.ofEpochSecond(updatedAtEpochSec)
    )
