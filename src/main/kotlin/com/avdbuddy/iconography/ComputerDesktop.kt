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

val Icons.ComputerDesktop: ImageVector
    get() {
        if (_ComputerDesktop != null) return _ComputerDesktop!!
        
        _ComputerDesktop = ImageVector.Builder(
            name = "computer-desktop",
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
                moveTo(9f, 17.25f)
                verticalLineToRelative(1.007f)
                arcToRelative(3f, 3f, 0f, false, true, -0.879f, 2.122f)
                lineTo(7.5f, 21f)
                horizontalLineToRelative(9f)
                lineToRelative(-0.621f, -0.621f)
                arcTo(3f, 3f, 0f, false, true, 15f, 18.257f)
                verticalLineTo(17.25f)
                moveToRelative(6f, -12f)
                verticalLineTo(15f)
                arcToRelative(2.25f, 2.25f, 0f, false, true, -2.25f, 2.25f)
                horizontalLineTo(5.25f)
                arcTo(2.25f, 2.25f, 0f, false, true, 3f, 15f)
                verticalLineTo(5.25f)
                moveToRelative(18f, 0f)
                arcTo(2.25f, 2.25f, 0f, false, false, 18.75f, 3f)
                horizontalLineTo(5.25f)
                arcTo(2.25f, 2.25f, 0f, false, false, 3f, 5.25f)
                moveToRelative(18f, 0f)
                verticalLineTo(12f)
                arcToRelative(2.25f, 2.25f, 0f, false, true, -2.25f, 2.25f)
                horizontalLineTo(5.25f)
                arcTo(2.25f, 2.25f, 0f, false, true, 3f, 12f)
                verticalLineTo(5.25f)
            }
        }.build()
        
        return _ComputerDesktop!!
    }

private var _ComputerDesktop: ImageVector? = null

