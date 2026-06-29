package com.sumitrack.android.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val SumitrackShapes = Shapes(
    small      = RoundedCornerShape(10.dp),                                                              // inputs
    medium     = RoundedCornerShape(16.dp),                                                              // cards
    large      = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 0.dp, bottomEnd = 0.dp), // bottom sheets
    extraLarge = RoundedCornerShape(20.dp),                                                              // chips y badges
)

val ButtonShape = RoundedCornerShape(12.dp)
