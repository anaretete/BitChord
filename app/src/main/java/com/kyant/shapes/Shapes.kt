package com.kyant.shapes

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val Capsule: Shape = RoundedCornerShape(percent = 50)
fun Capsule(): Shape = Capsule

fun RoundedRectangularShape(cornerRadius: Dp = 24.dp): Shape =
    RoundedCornerShape(cornerRadius)
