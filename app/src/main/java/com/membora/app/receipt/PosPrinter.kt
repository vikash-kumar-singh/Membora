package com.membora.app.receipt

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.membora.app.data.asMoney
import com.membora.app.data.pretty
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.time.format.DateTimeFormatter
import java.util.UUID

data class PosPrinterDevice(val name: String, val address: String)

object PosPrinter {
    private const val TAG = "MemboraPosPrinter"
    private val serialPortUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val dateTime = DateTimeFormatter.ofPattern("dd-MM-yyyy hh:mm a")

    fun hasPermission(context: Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun pairedDevices(context: Context): List<PosPrinterDevice> {
        require(hasPermission(context)) { "Nearby devices permission is required" }
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: error("Bluetooth is not available on this device")
        require(adapter.isEnabled) { "Turn on Bluetooth and pair the POS printer first" }
        return adapter.bondedDevices.map { PosPrinterDevice(it.name ?: "POS printer", it.address) }.sortedBy { it.name }
    }

    @SuppressLint("MissingPermission")
    fun print(context: Context, address: String, receipt: ReceiptData, paperColumns: Int, openDrawer: Boolean) {
        require(hasPermission(context)) { "Nearby devices permission is required" }
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: error("Bluetooth is not available")
        val device = adapter.getRemoteDevice(address)
        connect(device.createRfcommSocketToServiceRecord(serialPortUuid), "secure SPP").use { socket ->
            socket.outputStream.use { output ->
                buildEscPos(
                    receipt = receipt,
                    columns = paperColumns,
                    openDrawer = openDrawer,
                    cutPaper = !device.name.orEmpty().contains("RPP02", ignoreCase = true)
                ).asList().chunked(256).forEach { chunk ->
                    output.write(chunk.toByteArray())
                    output.flush()
                    Thread.sleep(25)
                }
                output.flush()
                // Portable RPP printers can drop the final buffer if RFCOMM closes immediately.
                Thread.sleep(750)
            }
        }
    }

    @SuppressLint("MissingPermission", "DiscouragedPrivateApi")
    private fun connect(firstSocket: BluetoothSocket, firstMode: String): BluetoothSocket {
        var lastError: Exception? = null
        val device = firstSocket.remoteDevice
        val attempts = listOf<Pair<String, () -> BluetoothSocket>>(
            firstMode to { firstSocket },
            "insecure SPP" to { device.createInsecureRfcommSocketToServiceRecord(serialPortUuid) },
            "RFCOMM channel 1" to {
                val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                method.invoke(device, 1) as BluetoothSocket
            }
        )
        attempts.forEach { (mode, createSocket) ->
            val socket = runCatching(createSocket).getOrElse { error ->
                lastError = error as? Exception ?: IllegalStateException(error)
                return@forEach
            }
            try {
                Log.i(TAG, "Connecting to ${device.name} using $mode")
                socket.connect()
                Log.i(TAG, "Connected to ${device.name} using $mode")
                return socket
            } catch (error: Exception) {
                lastError = error
                Log.w(TAG, "$mode failed for ${device.name}: ${error.message}")
                runCatching { socket.close() }
            }
        }
        throw IllegalStateException(
            "Unable to connect to ${device.name ?: "POS printer"}. Turn it on, disconnect it from other phones, and try again. ${lastError?.message.orEmpty()}".trim()
        )
    }

    private fun buildEscPos(receipt: ReceiptData, columns: Int, openDrawer: Boolean, cutPaper: Boolean): ByteArray {
        val bytes = ByteArrayOutputStream()
        val charset = Charset.forName("CP437")
        fun command(vararg value: Int) = bytes.write(value.map(Int::toByte).toByteArray())
        fun text(value: String = "") = bytes.write((value.asciiSafe() + "\n").toByteArray(charset))
        fun center(value: String) { command(0x1B, 0x61, 1); wrap(value, columns).forEach(::text); command(0x1B, 0x61, 0) }
        fun separator() = text("-".repeat(columns))
        fun row(label: String, value: String) {
            val safeValue = value.asciiSafe()
            val room = columns - label.length - 1
            if (room >= safeValue.length) text(label + " ".repeat(room - safeValue.length + 1) + safeValue)
            else { text(label); wrap(safeValue, columns).forEach { text(it.padStart(columns)) } }
        }
        command(0x1B, 0x40)
        command(0x1B, 0x45, 1)
        center(receipt.organizationName)
        command(0x1B, 0x45, 0)
        center(receipt.branchName)
        receipt.organizationAddress.lines().filter(String::isNotBlank).forEach(::center)
        if (receipt.organizationPhone.isNotBlank()) center("Tel: ${receipt.organizationPhone}")
        if (receipt.taxId.isNotBlank()) center("Tax/GST: ${receipt.taxId}")
        separator()
        center("PAYMENT RECEIPT")
        center(receipt.receiptNumber)
        separator()
        row("Date", receipt.paidOn.format(dateTime))
        row("Member", receipt.memberName)
        row("Member ID", receipt.memberCode)
        row("Plan", receipt.planName)
        if (receipt.membershipPeriod.isNotBlank()) row("Validity", receipt.membershipPeriod)
        separator()
        row("Membership", thermalMoney(receipt, receipt.membershipAmount))
        if (receipt.registrationFee > 0.009) row("Registration", thermalMoney(receipt, receipt.registrationFee))
        if (receipt.discount > 0.009) row("Discount", "-${thermalMoney(receipt, receipt.discount)}")
        row("Plan total", thermalMoney(receipt, receipt.membershipTotal))
        separator()
        command(0x1B, 0x45, 1)
        row("RECEIVED", thermalMoney(receipt, receipt.amountReceived))
        command(0x1B, 0x45, 0)
        row("Method", receipt.paymentMethod.pretty())
        if (receipt.paymentReference.isNotBlank()) row("Reference", receipt.paymentReference)
        row("Total paid", thermalMoney(receipt, receipt.cumulativePaid))
        row("Balance", thermalMoney(receipt, receipt.balanceAfterPayment))
        if (receipt.notes.isNotBlank()) { text("Notes:"); wrap(receipt.notes, columns).forEach(::text) }
        separator()
        center(receipt.receiptFooter)
        center("Received by: ${receipt.receivedBy}")
        text(); text(); text()
        if (openDrawer) command(0x1B, 0x70, 0, 50, 250)
        if (cutPaper) command(0x1D, 0x56, 0x41, 3)
        return bytes.toByteArray()
    }

    private fun thermalMoney(receipt: ReceiptData, value: Double) = "${receipt.currencyCode} ${value.asMoney()}"
    private fun String.asciiSafe() = replace('₹', ' ').replace(Regex("[^\\x20-\\x7E]"), "?").trim()
    private fun wrap(value: String, columns: Int): List<String> {
        if (value.length <= columns) return listOf(value)
        val lines = mutableListOf<String>()
        var current = ""
        value.split(Regex("\\s+")).forEach { word ->
            if (current.isNotEmpty() && current.length + word.length + 1 > columns) {
                lines += current
                current = word
            } else current = if (current.isEmpty()) word else "$current $word"
        }
        if (current.isNotEmpty()) lines += current
        return lines.flatMap { line -> if (line.length <= columns) listOf(line) else line.chunked(columns) }
    }
}
