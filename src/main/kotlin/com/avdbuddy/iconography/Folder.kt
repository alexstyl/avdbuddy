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

val Icons.Folder: ImageVector
    get() {
        if (_Folder != null) return _Folder!!
        
        _Folder = ImageVector.Builder(
            name = "folder",
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
                moveTo(2.25f, 12.75f)
                verticalLineTo(12f)
                arcTo(2.25f, 2.25f, 0f, false, true, 4.5f, 9.75f)
                horizontalLineToRelative(15f)
                arcTo(2.25f, 2.25f, 0f, false, true, 21.75f, 12f)
                verticalLineToRelative(0.75f)
                moveToRelative(-8.69f, -6.44f)
                lineToRelative(-2.12f, -2.12f)
                arcToRelative(1.5f, 1.5f, 0f, false, false, -1.061f, -0.44f)
                horizontalLineTo(4.5f)
                arcTo(2.25f, 2.25f, 0f, false, false, 2.25f, 6f)
                verticalLineToRelative(12f)
                arcToRelative(2.25f, 2.25f, 0f, false, false, 2.25f, 2.25f)
                horizontalLineToRelative(15f)
                arcTo(2.25f, 2.25f, 0f, false, false, 21.75f, 18f)
                verticalLineTo(9f)
                arcToRelative(2.25f, 2.25f, 0f, false, false, -2.25f, -2.25f)
                horizontalLineToRelative(-5.379f)
                arcToRelative(1.5f, 1.5f, 0f, false, true, -1.06f, -0.44f)
                close()
            }
        }.build()
        
        return _Folder!!
    }

private var _Folder: ImageVector? = null

