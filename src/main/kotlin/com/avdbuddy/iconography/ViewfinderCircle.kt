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

val Icons.ViewfinderCircle: ImageVector
    get() {
        if (_ViewfinderCircle != null) return _ViewfinderCircle!!
        
        _ViewfinderCircle = ImageVector.Builder(
            name = "viewfinder-circle",
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
                moveTo(7.5f, 3.75f)
                horizontalLineTo(6f)
                arcTo(2.25f, 2.25f, 0f, false, false, 3.75f, 6f)
                verticalLineToRelative(1.5f)
                moveTo(16.5f, 3.75f)
                horizontalLineTo(18f)
                arcTo(2.25f, 2.25f, 0f, false, true, 20.25f, 6f)
                verticalLineToRelative(1.5f)
                moveToRelative(0f, 9f)
                verticalLineTo(18f)
                arcTo(2.25f, 2.25f, 0f, false, true, 18f, 20.25f)
                horizontalLineToRelative(-1.5f)
                moveToRelative(-9f, 0f)
                horizontalLineTo(6f)
                arcTo(2.25f, 2.25f, 0f, false, true, 3.75f, 18f)
                verticalLineToRelative(-1.5f)
                moveTo(15f, 12f)
                arcToRelative(3f, 3f, 0f, true, true, -6f, 0f)
                arcToRelative(3f, 3f, 0f, false, true, 6f, 0f)
                close()
            }
        }.build()
        
        return _ViewfinderCircle!!
    }

private var _ViewfinderCircle: ImageVector? = null

