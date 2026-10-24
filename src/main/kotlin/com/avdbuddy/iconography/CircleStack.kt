/*
 * Icon from Heroicons (heroicons).
 * License: MIT
 * 
 * MIT License
 * 
 * Copyright (c) Tailwind Labs, Inc.
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.avdbuddy.iconography

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Icons.CircleStack: ImageVector
    get() {
        if (_CircleStack != null) return _CircleStack!!
        
        _CircleStack = ImageVector.Builder(
            name = "circle-stack",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(20.25f, 6.375f)
                curveToRelative(0f, 2.278f, -3.694f, 4.125f, -8.25f, 4.125f)
                reflectiveCurveTo(3.75f, 8.653f, 3.75f, 6.375f)
                moveToRelative(16.5f, 0f)
                curveToRelative(0f, -2.278f, -3.694f, -4.125f, -8.25f, -4.125f)
                reflectiveCurveTo(3.75f, 4.097f, 3.75f, 6.375f)
                moveToRelative(16.5f, 0f)
                verticalLineToRelative(11.25f)
                curveToRelative(0f, 2.278f, -3.694f, 4.125f, -8.25f, 4.125f)
                reflectiveCurveToRelative(-8.25f, -1.847f, -8.25f, -4.125f)
                verticalLineTo(6.375f)
                moveToRelative(16.5f, 0f)
                verticalLineToRelative(3.75f)
                moveToRelative(-16.5f, -3.75f)
                verticalLineToRelative(3.75f)
                moveToRelative(16.5f, 0f)
                verticalLineToRelative(3.75f)
                curveTo(20.25f, 16.153f, 16.556f, 18f, 12f, 18f)
                reflectiveCurveToRelative(-8.25f, -1.847f, -8.25f, -4.125f)
                verticalLineToRelative(-3.75f)
                moveToRelative(16.5f, 0f)
                curveToRelative(0f, 2.278f, -3.694f, 4.125f, -8.25f, 4.125f)
                reflectiveCurveToRelative(-8.25f, -1.847f, -8.25f, -4.125f)
            }
        }.build()
        
        return _CircleStack!!
    }

private var _CircleStack: ImageVector? = null

