package com.darahz.volumecontrol.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight

private val Default = Typography()

// Material defaults, with heavier headings
val Typography = Typography(
    displaySmall = Default.displaySmall.copy(fontWeight = FontWeight.Bold),
    headlineMedium = Default.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Default.titleMedium.copy(fontWeight = FontWeight.SemiBold),
)
