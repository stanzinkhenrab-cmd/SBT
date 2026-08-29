package com.sbt.geostamp.render

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.sbt.geostamp.model.StampContent
import com.sbt.geostamp.model.StampOptions
import com.sbt.geostamp.model.Template

/**
 * Draws the location card onto a photo.
 *
 * Every dimension is derived from [sizeBase] so a stamp looks identical whether it is burned
 * into a 12 MP capture or a small preview bitmap.
 */
object StampRenderer {

    /**
     * @param photo the untouched picture; it is never modified
     * @param mapThumbnail optional map image, already square-ish
     * @param qr optional QR bitmap
     * @return a new bitmap with the stamp applied
     */
    fun render(
        photo: Bitmap,
        content: StampContent,
        options: StampOptions,
        mapThumbnail: Bitmap? = null,
        qr: Bitmap? = null
    ): Bitmap {
        val lines = buildLines(content, options)
        val extraFooter = if (options.template == Template.POLAROID) {
            polaroidFooterHeight(photo, options, lines.size)
        } else {
            0f
        }

        val output = Bitmap.createBitmap(
            photo.width,
            photo.height + extraFooter.toInt(),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(output)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(photo, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))

        val sizeBase = sizeBase(photo, options)
        when (options.template) {
            Template.CLASSIC -> drawClassic(canvas, photo, sizeBase, lines, options, mapThumbnail, qr)
            Template.COMPACT -> drawCompact(canvas, photo, sizeBase, content, options)
            Template.RIBBON -> drawRibbon(canvas, photo, sizeBase, lines, options, qr)
            Template.POLAROID -> drawPolaroid(
                canvas, photo, sizeBase, lines, options, mapThumbnail, qr, extraFooter
            )
        }
        return output
    }

    /** Height of the stamp card for a given photo — used to size the map thumbnail request. */
    fun suggestedThumbnailSize(photo: Bitmap, options: StampOptions): Int {
        val sizeBase = sizeBase(photo, options)
        return (sizeBase * 0.24f).toInt().coerceIn(96, 640)
    }

    private fun sizeBase(photo: Bitmap, options: StampOptions): Float {
        // Wide photos would otherwise get comically large text, so cap against the height.
        val base = minOf(photo.width.toFloat(), photo.height * 1.25f)
        return base * options.scale
    }

    // ---------------------------------------------------------------- content

    private data class StampLine(val text: String, val weight: Weight) {
        /** Place names and addresses may wrap; the note stays on one line. */
        val maxLines: Int get() = if (weight == Weight.CAPTION) 1 else 2
    }

    private enum class Weight { TITLE, BODY, CAPTION }

    private fun buildLines(content: StampContent, options: StampOptions): List<StampLine> =
        buildList {
            val title = content.title.trim()
            if (title.isNotEmpty()) add(StampLine(title, Weight.TITLE))

            val address = content.addressLine.trim()
            if (address.isNotEmpty() && address != title) add(StampLine(address, Weight.BODY))

            if (options.showCoordinates && content.hasLocation) {
                val coordinates = buildString {
                    append(content.formattedCoordinates(options.coordinatesAsDms))
                    content.altitudeMeters?.let { append(String.format("  •  %.0f m", it)) }
                }
                add(StampLine(coordinates, Weight.BODY))
            }

            if (options.showDateTime) {
                add(StampLine(content.formattedDateTime(options.use24Hour), Weight.BODY))
            }

            val note = content.note.trim()
            if (options.showNote && note.isNotEmpty()) {
                add(StampLine("Note : $note", Weight.CAPTION))
            }
        }

    private fun textPaint(weight: Weight, sizeBase: Float, color: Int): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = when (weight) {
                Weight.TITLE -> sizeBase * 0.052f
                Weight.BODY -> sizeBase * 0.034f
                Weight.CAPTION -> sizeBase * 0.030f
            }
            typeface = when (weight) {
                Weight.TITLE -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                else -> Typeface.DEFAULT
            }
        }

    private fun layoutOf(
        text: String,
        paint: TextPaint,
        width: Int,
        maxLines: Int,
        alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL
    ): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width.coerceAtLeast(1))
            .setAlignment(alignment)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setIncludePad(false)
            .setLineSpacing(0f, 1f)
            .build()

    // ---------------------------------------------------------------- templates

    private fun drawClassic(
        canvas: Canvas,
        photo: Bitmap,
        sizeBase: Float,
        lines: List<StampLine>,
        options: StampOptions,
        mapThumbnail: Bitmap?,
        qr: Bitmap?
    ) {
        val margin = sizeBase * 0.028f
        val padding = sizeBase * 0.030f
        val gap = sizeBase * 0.012f
        val cardLeft = margin
        val cardRight = photo.width - margin
        val cardWidth = cardRight - cardLeft

        val thumbnailSide = (sizeBase * 0.22f).takeIf { options.showMap && mapThumbnail != null } ?: 0f
        val qrSide = (sizeBase * 0.18f).takeIf { options.showQr && qr != null } ?: 0f
        val columnGap = sizeBase * 0.020f

        val textWidth = (cardWidth - 2 * padding -
            (if (thumbnailSide > 0) thumbnailSide + columnGap else 0f) -
            (if (qrSide > 0) qrSide + columnGap else 0f)).toInt().coerceAtLeast(1)

        val layouts = lines.map { line ->
            val paint = textPaint(line.weight, sizeBase, Color.WHITE)
            if (line.weight == Weight.BODY) paint.color = 0xFFF2F2F2.toInt()
            if (line.weight == Weight.CAPTION) paint.color = 0xFFE0E0E0.toInt()
            layoutOf(line.text, paint, textWidth, line.maxLines)
        }

        val textHeight = layouts.sumOf { it.height } + gap * (layouts.size - 1).coerceAtLeast(0)
        val contentHeight = maxOf(textHeight, thumbnailSide, qrSide)
        val cardHeight = contentHeight + 2 * padding
        val cardTop = photo.height - margin - cardHeight
        val cardRect = RectF(cardLeft, cardTop, cardRight, photo.height - margin)
        val radius = sizeBase * 0.022f

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = 0xB8000000.toInt()
        canvas.drawRoundRect(cardRect, radius, radius, paint)

        // Accent hairline along the top edge of the card.
        paint.color = options.accentColor
        canvas.save()
        canvas.clipRect(cardRect.left, cardRect.top, cardRect.right, cardRect.top + sizeBase * 0.008f)
        canvas.drawRoundRect(cardRect, radius, radius, paint)
        canvas.restore()

        var cursorX = cardLeft + padding
        if (thumbnailSide > 0f && mapThumbnail != null) {
            val target = RectF(
                cursorX,
                cardTop + padding + (contentHeight - thumbnailSide) / 2f,
                cursorX + thumbnailSide,
                cardTop + padding + (contentHeight + thumbnailSide) / 2f
            )
            drawRoundedBitmap(canvas, mapThumbnail, target, sizeBase * 0.016f)
            cursorX += thumbnailSide + columnGap
        }

        var cursorY = cardTop + padding + (contentHeight - textHeight) / 2f
        layouts.forEach { layout ->
            canvas.save()
            canvas.translate(cursorX, cursorY)
            layout.draw(canvas)
            canvas.restore()
            cursorY += layout.height + gap
        }

        if (qrSide > 0f && qr != null) {
            val target = RectF(
                cardRight - padding - qrSide,
                cardTop + padding + (contentHeight - qrSide) / 2f,
                cardRight - padding,
                cardTop + padding + (contentHeight + qrSide) / 2f
            )
            paint.color = Color.WHITE
            val backdrop = RectF(target).apply { inset(-sizeBase * 0.010f, -sizeBase * 0.010f) }
            canvas.drawRoundRect(backdrop, sizeBase * 0.012f, sizeBase * 0.012f, paint)
            canvas.drawBitmap(qr, null, target, Paint(Paint.FILTER_BITMAP_FLAG))
        }
    }

    private fun drawCompact(
        canvas: Canvas,
        photo: Bitmap,
        sizeBase: Float,
        content: StampContent,
        options: StampOptions
    ) {
        val padding = sizeBase * 0.030f
        val titlePaint = textPaint(Weight.TITLE, sizeBase, Color.WHITE).apply {
            textSize = sizeBase * 0.044f
        }
        val detailPaint = textPaint(Weight.BODY, sizeBase, 0xFFE6E6E6.toInt())

        val detail = buildList {
            if (options.showCoordinates && content.hasLocation) {
                add(content.formattedCoordinates(options.coordinatesAsDms))
            }
            if (options.showDateTime) add(content.formattedDateTime(options.use24Hour))
        }.joinToString("   •   ")

        val accentWidth = sizeBase * 0.010f
        val textLeft = padding + accentWidth + sizeBase * 0.020f
        val textWidth = (photo.width - textLeft - padding).toInt().coerceAtLeast(1)

        val title = content.title.ifBlank { content.addressLine }
        val titleLayout = layoutOf(title, titlePaint, textWidth, 1)
        val detailLayout = detail.takeIf { it.isNotBlank() }
            ?.let { layoutOf(it, detailPaint, textWidth, 1) }

        val gap = sizeBase * 0.010f
        val barHeight = padding * 2 + titleLayout.height +
            (detailLayout?.let { gap + it.height } ?: 0f)
        val barTop = photo.height - barHeight

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(
            0f, barTop, 0f, photo.height.toFloat(),
            0x00000000, 0xE0000000.toInt(), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, barTop - sizeBase * 0.06f, photo.width.toFloat(), photo.height.toFloat(), paint)
        paint.shader = null

        paint.color = options.accentColor
        canvas.drawRoundRect(
            RectF(padding, barTop + padding, padding + accentWidth, photo.height - padding),
            accentWidth / 2f,
            accentWidth / 2f,
            paint
        )

        canvas.save()
        canvas.translate(textLeft, barTop + padding)
        titleLayout.draw(canvas)
        canvas.restore()

        detailLayout?.let {
            canvas.save()
            canvas.translate(textLeft, barTop + padding + titleLayout.height + gap)
            it.draw(canvas)
            canvas.restore()
        }
    }

    private fun drawRibbon(
        canvas: Canvas,
        photo: Bitmap,
        sizeBase: Float,
        lines: List<StampLine>,
        options: StampOptions,
        qr: Bitmap?
    ) {
        val padding = sizeBase * 0.034f
        val qrSide = (sizeBase * 0.18f).takeIf { options.showQr && qr != null } ?: 0f
        val columnGap = sizeBase * 0.024f
        val textWidth = (photo.width - 2 * padding -
            (if (qrSide > 0) qrSide + columnGap else 0f)).toInt().coerceAtLeast(1)

        val layouts = lines.map { line ->
            val color = when (line.weight) {
                Weight.TITLE -> Color.WHITE
                Weight.BODY -> 0xFFF0F0F0.toInt()
                Weight.CAPTION -> 0xFFDDDDDD.toInt()
            }
            val paint = textPaint(line.weight, sizeBase, color)
            if (line.weight == Weight.TITLE) paint.textSize = sizeBase * 0.058f
            layoutOf(line.text, paint, textWidth, line.maxLines)
        }

        val gap = sizeBase * 0.010f
        val textHeight = layouts.sumOf { it.height } + gap * (layouts.size - 1).coerceAtLeast(0)
        val bandHeight = maxOf(textHeight, qrSide) + 2 * padding
        val bandTop = photo.height - bandHeight

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(
            0f, bandTop, photo.width.toFloat(), photo.height.toFloat(),
            withAlpha(options.accentColor, 0xF0), 0xD9101010.toInt(), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, bandTop, photo.width.toFloat(), photo.height.toFloat(), paint)
        paint.shader = null

        paint.color = withAlpha(options.accentColor, 0xFF)
        canvas.drawRect(0f, bandTop, photo.width.toFloat(), bandTop + sizeBase * 0.008f, paint)

        var cursorY = bandTop + padding + (maxOf(textHeight, qrSide) - textHeight) / 2f
        layouts.forEach { layout ->
            canvas.save()
            canvas.translate(padding, cursorY)
            layout.draw(canvas)
            canvas.restore()
            cursorY += layout.height + gap
        }

        if (qrSide > 0f && qr != null) {
            val target = RectF(
                photo.width - padding - qrSide,
                bandTop + (bandHeight - qrSide) / 2f,
                photo.width - padding,
                bandTop + (bandHeight + qrSide) / 2f
            )
            paint.color = Color.WHITE
            val backdrop = RectF(target).apply { inset(-sizeBase * 0.008f, -sizeBase * 0.008f) }
            canvas.drawRoundRect(backdrop, sizeBase * 0.010f, sizeBase * 0.010f, paint)
            canvas.drawBitmap(qr, null, target, Paint(Paint.FILTER_BITMAP_FLAG))
        }
    }

    private fun polaroidFooterHeight(photo: Bitmap, options: StampOptions, lineCount: Int): Float {
        val sizeBase = sizeBase(photo, options)
        val padding = sizeBase * 0.034f
        val perLine = sizeBase * 0.050f
        // One spare line so a long place name can wrap without overflowing the footer.
        return maxOf((lineCount + 1) * perLine + 2 * padding, sizeBase * 0.26f)
    }

    private fun drawPolaroid(
        canvas: Canvas,
        photo: Bitmap,
        sizeBase: Float,
        lines: List<StampLine>,
        options: StampOptions,
        mapThumbnail: Bitmap?,
        qr: Bitmap?,
        footerHeight: Float
    ) {
        val footerTop = photo.height.toFloat()
        val padding = sizeBase * 0.032f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = 0xFFFAFAF7.toInt()
        canvas.drawRect(0f, footerTop, photo.width.toFloat(), footerTop + footerHeight, paint)
        paint.color = withAlpha(options.accentColor, 0xFF)
        canvas.drawRect(0f, footerTop, photo.width.toFloat(), footerTop + sizeBase * 0.007f, paint)

        val contentHeight = footerHeight - 2 * padding
        val thumbnailSide = (contentHeight).takeIf { options.showMap && mapThumbnail != null } ?: 0f
        val qrSide = (contentHeight * 0.80f).takeIf { options.showQr && qr != null } ?: 0f
        val columnGap = sizeBase * 0.022f
        val textWidth = (photo.width - 2 * padding -
            (if (thumbnailSide > 0) thumbnailSide + columnGap else 0f) -
            (if (qrSide > 0) qrSide + columnGap else 0f)).toInt().coerceAtLeast(1)

        val layouts = lines.map { line ->
            val color = when (line.weight) {
                Weight.TITLE -> 0xFF161616.toInt()
                Weight.BODY -> 0xFF3C3C3C.toInt()
                Weight.CAPTION -> 0xFF6B6B6B.toInt()
            }
            layoutOf(line.text, textPaint(line.weight, sizeBase, color), textWidth, line.maxLines)
        }

        val gap = sizeBase * 0.008f
        val textHeight = layouts.sumOf { it.height } + gap * (layouts.size - 1).coerceAtLeast(0)

        var cursorX = padding
        if (thumbnailSide > 0f && mapThumbnail != null) {
            val target = RectF(
                cursorX,
                footerTop + padding,
                cursorX + thumbnailSide,
                footerTop + padding + thumbnailSide
            )
            drawRoundedBitmap(canvas, mapThumbnail, target, sizeBase * 0.014f)
            cursorX += thumbnailSide + columnGap
        }

        var cursorY = footerTop + padding + (contentHeight - textHeight) / 2f
        layouts.forEach { layout ->
            canvas.save()
            canvas.translate(cursorX, cursorY)
            layout.draw(canvas)
            canvas.restore()
            cursorY += layout.height + gap
        }

        if (qrSide > 0f && qr != null) {
            val target = RectF(
                photo.width - padding - qrSide,
                footerTop + padding + (contentHeight - qrSide) / 2f,
                photo.width - padding,
                footerTop + padding + (contentHeight + qrSide) / 2f
            )
            canvas.drawBitmap(qr, null, target, Paint(Paint.FILTER_BITMAP_FLAG))
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun drawRoundedBitmap(canvas: Canvas, bitmap: Bitmap, target: RectF, radius: Float) {
        val shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val matrix = Matrix()
        matrix.setRectToRect(
            RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat()),
            target,
            Matrix.ScaleToFit.CENTER
        )
        shader.setLocalMatrix(matrix)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = shader
        canvas.drawRoundRect(target, radius, radius, paint)

        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = radius * 0.22f
        paint.color = 0x33FFFFFF
        canvas.drawRoundRect(target, radius, radius, paint)
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    @Suppress("unused")
    private fun Rect.toRectF(): RectF = RectF(this)
}
