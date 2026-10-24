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

val Icons.ArrowPath: ImageVector
    get() {
        if (_ArrowPath != null) return _ArrowPath!!
        
        _ArrowPath = ImageVector.Builder(
            name = "arrow-path",
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
                moveTo(16.023f, 9.348f)
                horizontalLineToRelative(4.992f)
                verticalLineToRelative(-0.001f)
                moveTo(2.985f, 19.644f)
                verticalLineToRelative(-4.992f)
                moveToRelative(0f, 0f)
                horizontalLineToRelative(4.992f)
                moveToRelative(-4.993f, 0f)
                lineToRelative(3.181f, 3.183f)
                arcToRelative(8.25f, 8.25f, 0f, false, false, 13.803f, -3.7f)
                moveTo(4.031f, 9.865f)
                arcToRelative(8.25f, 8.25f, 0f, false, true, 13.803f, -3.7f)
                lineToRelative(3.181f, 3.182f)
                moveToRelative(0f, -4.991f)
                verticalLineToRelative(4.99f)
            }
        }.build()
        
        return _ArrowPath!!
    }

private var _ArrowPath: ImageVector? = null

