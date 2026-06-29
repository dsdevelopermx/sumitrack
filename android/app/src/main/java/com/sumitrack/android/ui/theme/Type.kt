package com.sumitrack.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val SumitrackTypography = Typography(
    displayLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold,       letterSpacing = (-0.5).sp),
    titleLarge   = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold,       letterSpacing = (-0.3).sp),
    titleMedium  = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold,       letterSpacing = (-0.3).sp),
    bodyLarge    = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold,   letterSpacing = 0.sp),
    bodyMedium   = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal,     letterSpacing = 0.sp),
    bodySmall    = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal,     letterSpacing = 0.sp),
    labelLarge   = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold,       letterSpacing = 0.5.sp),
    labelSmall   = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold,       letterSpacing = 0.5.sp),
)
