package com.example.weightsmart.domain.model

import java.math.BigDecimal
import java.math.RoundingMode

// Use a uniquely named helper to avoid clashing with any other round1() in the project
private fun round1Local(value: Double): Double =
    BigDecimal(value).setScale(1, RoundingMode.HALF_UP).toDouble()

fun GoalProgress.isAtGoal(): Boolean =
    direction == GoalProgress.Direction.AT_GOAL

fun GoalProgress.remainingLabel(): String =
    "${round1Local(remainingLb)} lb remaining"

fun GoalProgress.directionLabel(): String = when (direction) {
    GoalProgress.Direction.LOSE -> "to lose"
    GoalProgress.Direction.GAIN -> "to gain"
    GoalProgress.Direction.AT_GOAL -> "at goal"
}

fun GoalProgress.percentLabel(): String? =
    percentTowardGoal?.let { "${round1Local(it)}%" }
