package com.membora.app.receipt

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import com.membora.app.data.asMoney
import com.membora.app.data.pretty
import java.io.File
import java.io.FileOutputStream
import java.time.format.DateTimeFormatter
import kotlin.math.max

object ReceiptOutput {
    private val dateTime = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")

    fun print(context: Context, receipt: ReceiptData) {
        val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        manager.print(receipt.receiptNumber, ReceiptPrintAdapter(receipt), null)
    }

    fun createPdf(context: Context, receipt: ReceiptData): File {
        val directory = File(context.cacheDir, "receipts").apply { mkdirs() }
        directory.listFiles()?.forEach { if (it.name != "${receipt.receiptNumber}.pdf") it.delete() }
        val file = File(directory, "${receipt.receiptNumber}.pdf")
        val document = PdfDocument()
        val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
        drawReceipt(page.canvas, receipt, 595f, 842f)
        document.finishPage(page)
        FileOutputStream(file).use(document::writeTo)
        document.close()
        return file
    }

    fun sharePdf(context: Context, receipt: ReceiptData, file: File = createPdf(context, receipt)) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Receipt ${receipt.receiptNumber}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share payment receipt"))
    }

    internal fun drawReceipt(canvas: Canvas, receipt: ReceiptData, width: Float, height: Float) {
        val left = 48f
        val right = width - 48f
        var y = 52f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(28, 40, 54) }
        fun text(value: String, x: Float = left, size: Float = 11f, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT) {
            paint.textSize = size
            paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            paint.textAlign = align
            canvas.drawText(value, x, y, paint)
        }
        fun line(spacing: Float = 16f) { y += spacing }
        fun rule() { canvas.drawLine(left, y, right, y, paint.apply { strokeWidth = 1f; color = Color.LTGRAY }); paint.color = Color.rgb(28, 40, 54); y += 16f }
        fun row(label: String, value: String, bold: Boolean = false) {
            text(label)
            text(value, right, 11f, bold, Paint.Align.RIGHT)
            line(18f)
        }
        paint.color = Color.rgb(19, 91, 144)
        canvas.drawRoundRect(left, y - 25f, left + 54f, y + 29f, 10f, 10f, paint)
        paint.color = Color.WHITE
        text(receipt.organizationName.take(1).uppercase(), left + 27f, 24f, true, Paint.Align.CENTER)
        paint.color = Color.rgb(28, 40, 54)
        text(receipt.organizationName, left + 70f, 20f, true)
        line(20f)
        text(receipt.branchName, left + 70f, 10f)
        y += 28f
        text("PAYMENT RECEIPT", right, 16f, true, Paint.Align.RIGHT)
        line(20f)
        text(receipt.receiptNumber, right, 11f, false, Paint.Align.RIGHT)
        line(24f)
        rule()
        receipt.organizationAddress.lines().filter(String::isNotBlank).forEach { text(it, size = 9f); line(13f) }
        if (receipt.organizationPhone.isNotBlank()) { text("Phone: ${receipt.organizationPhone}", size = 9f); line(13f) }
        if (receipt.organizationEmail.isNotBlank()) { text("Email: ${receipt.organizationEmail}", size = 9f); line(13f) }
        if (receipt.taxId.isNotBlank()) { text("Tax / GST ID: ${receipt.taxId}", size = 9f, bold = true); line(13f) }
        y += 8f
        rule()
        text("RECEIVED FROM", size = 9f, bold = true); line(18f)
        row("Member", "${receipt.memberName} (${receipt.memberCode})", true)
        if (receipt.memberPhone.isNotBlank()) row("Mobile", receipt.memberPhone)
        if (receipt.memberEmail.isNotBlank()) row("Email", receipt.memberEmail)
        row("Receipt date", receipt.paidOn.format(dateTime))
        rule()
        text("PAYMENT DETAILS", size = 9f, bold = true); line(20f)
        row("Membership", receipt.planName, true)
        if (receipt.membershipPeriod.isNotBlank()) row("Validity", receipt.membershipPeriod)
        row("Membership amount", money(receipt, receipt.membershipAmount))
        if (receipt.registrationFee > 0.009) row("Registration fee", money(receipt, receipt.registrationFee))
        if (receipt.discount > 0.009) row("Discount", "- ${money(receipt, receipt.discount)}")
        row("Membership total", money(receipt, receipt.membershipTotal), true)
        rule()
        row("AMOUNT RECEIVED", money(receipt, receipt.amountReceived), true)
        row("Payment method", receipt.paymentMethod.pretty())
        if (receipt.paymentReference.isNotBlank()) row("Transaction reference", receipt.paymentReference)
        row("Total paid", money(receipt, receipt.cumulativePaid))
        row("Balance due", money(receipt, receipt.balanceAfterPayment), receipt.balanceAfterPayment > 0.009)
        if (receipt.notes.isNotBlank()) row("Notes", receipt.notes)
        row("Received by", receipt.receivedBy)
        rule()
        text(receipt.receiptFooter, width / 2f, 10f, true, Paint.Align.CENTER)
        y = max(y + 34f, height - 45f)
        text("Computer-generated receipt • ${receipt.currencyCode}", width / 2f, 8f, false, Paint.Align.CENTER)
    }

    internal fun money(receipt: ReceiptData, value: Double): String =
        "${receipt.currencySymbol}${value.asMoney()} ${receipt.currencyCode}"
}

private class ReceiptPrintAdapter(private val receipt: ReceiptData) : PrintDocumentAdapter() {
    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback?,
        extras: Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) return callback?.onLayoutCancelled() ?: Unit
        callback?.onLayoutFinished(
            PrintDocumentInfo.Builder("${receipt.receiptNumber}.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(1)
                .build(),
            true
        )
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor?,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback?
    ) {
        if (destination == null) return callback?.onWriteFailed("No print destination") ?: Unit
        val document = PdfDocument()
        try {
            val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
            ReceiptOutput.drawReceipt(page.canvas, receipt, 595f, 842f)
            document.finishPage(page)
            FileOutputStream(destination.fileDescriptor).use(document::writeTo)
            callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (error: Exception) {
            callback?.onWriteFailed(error.localizedMessage)
        } finally {
            document.close()
        }
    }
}
