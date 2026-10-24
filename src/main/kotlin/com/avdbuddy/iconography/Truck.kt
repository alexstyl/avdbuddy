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

val Icons.Truck: ImageVector
    get() {
        if (_Truck != null) return _Truck!!
        
        _Truck = ImageVector.Builder(
            name = "truck",
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
                moveTo(8.25f, 18.75f)
                arcToRelative(1.5f, 1.5f, 0f, false, true, -3f, 0f)
                moveToRelative(3f, 0f)
                arcToRelative(1.5f, 1.5f, 0f, false, false, -3f, 0f)
                moveToRelative(3f, 0f)
                horizontalLineToRelative(6f)
                moveToRelative(-9f, 0f)
                horizontalLineTo(3.375f)
                arcToRelative(1.125f, 1.125f, 0f, false, true, -1.125f, -1.125f)
                verticalLineTo(14.25f)
                moveToRelative(17.25f, 4.5f)
                arcToRelative(1.5f, 1.5f, 0f, false, true, -3f, 0f)
                moveToRelative(3f, 0f)
                arcToRelative(1.5f, 1.5f, 0f, false, false, -3f, 0f)
                moveToRelative(3f, 0f)
                horizontalLineToRelative(1.125f)
                curveToRelative(0.621f, 0f, 1.129f, -0.504f, 1.09f, -1.124f)
                arcToRelative(17.902f, 17.902f, 0f, false, false, -3.213f, -9.193f)
                arcToRelative(2.056f, 2.056f, 0f, false, false, -1.58f, -0.86f)
                horizontalLineTo(14.25f)
                moveTo(16.5f, 18.75f)
                horizontalLineToRelative(-2.25f)
                moveToRelative(0f, -11.177f)
                verticalLineToRelative(-0.958f)
                curveToRelative(0f, -0.568f, -0.422f, -1.048f, -0.987f, -1.106f)
                arcToRelative(48.554f, 48.554f, 0f, false, false, -10.026f, 0f)
                arcToRelative(1.106f, 1.106f, 0f, false, false, -0.987f, 1.106f)
                verticalLineToRelative(7.635f)
                moveToRelative(12f, -6.677f)
                verticalLineToRelative(6.677f)
                moveToRelative(0f, 4.5f)
                verticalLineToRelative(-4.5f)
                moveToRelative(0f, 0f)
                horizontalLineToRelative(-12f)
            }
        }.build()
        
        return _Truck!!
    }

private var _Truck: ImageVector? = null

