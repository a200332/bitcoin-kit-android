package io.horizontalsystems.bitcoinkit.network.messages

import io.horizontalsystems.bitcoinkit.exceptions.BitcoinException
import io.horizontalsystems.bitcoinkit.io.BitcoinInput
import io.horizontalsystems.bitcoinkit.io.BitcoinOutput
import io.horizontalsystems.bitcoinkit.utils.HashUtils
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.*

interface IMessageParser {
    val command: String
    fun parseMessage(payload: ByteArray): IMessage
}

interface IMessageSerializer {
    val command: String
    fun serialize(message: IMessage): ByteArray
}

interface IMessage {
    val command: String
}

class NetworkMessageParser(private val magic: Long) {
    private var messageParsers = hashMapOf<String, IMessageParser>()

    /**
     * Parse stream as message.
     */
    @Throws(IOException::class)
    fun parseMessage(input: BitcoinInput): IMessage {
        val magic = input.readUnsignedInt()
        if (magic != this.magic) {
            throw BitcoinException("Bad magic. (local) ${this.magic}!=$magic")
        }

        val command = getCommandFrom(input.readBytes(12))
        val payloadLength = input.readInt()
        val expectedChecksum = ByteArray(4)
        input.readFully(expectedChecksum)
        val payload = ByteArray(payloadLength)
        input.readFully(payload)

        // check:
        val actualChecksum = getCheckSum(payload)
        if (!Arrays.equals(expectedChecksum, actualChecksum)) {
            throw BitcoinException("Checksum failed.")
        }

        try {
            return messageParsers[command]?.parseMessage(payload) ?: UnknownMessage(command, payload)
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    fun add(messageParser: IMessageParser) {
        messageParsers[messageParser.command] = messageParser
    }

    private fun getCommandFrom(cmd: ByteArray): String {
        var n = cmd.size - 1
        while (n >= 0) {
            if (cmd[n].toInt() == 0) {
                n--
            } else {
                break
            }
        }
        if (n <= 0) {
            throw BitcoinException("Bad command bytes.")
        }
        val b = Arrays.copyOfRange(cmd, 0, n + 1)
        return String(b, StandardCharsets.UTF_8)
    }

    private fun getCheckSum(payload: ByteArray): ByteArray {
        val hash = HashUtils.doubleSha256(payload)
        return Arrays.copyOfRange(hash, 0, 4)
    }
}

class NetworkMessageSerializer(private val magic: Long) {
    private var messageSerializers = hashMapOf<String, IMessageSerializer>()

    fun serialize(msg: IMessage): ByteArray {
        val payload = messageSerializers[msg.command]?.serialize(msg) ?: throw NoSerializer(msg.command)

        return BitcoinOutput()
                .writeInt32(magic)      // magic
                .write(getCommandBytes(msg.command))    // command: char[12]
                .writeInt(payload.size)         // length: uint32_t
                .write(getCheckSum(payload))    // checksum: uint32_t
                .write(payload)                 // payload:
                .toByteArray()
    }

    fun add(messageSerializer: IMessageSerializer) {
        messageSerializers[messageSerializer.command] = messageSerializer
    }

    private fun getCommandBytes(cmd: String): ByteArray {
        val cmdBytes = cmd.toByteArray()
        if (cmdBytes.isEmpty() || cmdBytes.size > 12) {
            throw IllegalArgumentException("Bad command: $cmd")
        }
        val buffer = ByteArray(12)
        System.arraycopy(cmdBytes, 0, buffer, 0, cmdBytes.size)
        return buffer
    }

    private fun getCheckSum(payload: ByteArray): ByteArray {
        val hash = HashUtils.doubleSha256(payload)
        return Arrays.copyOfRange(hash, 0, 4)
    }
}

class WrongSerializer : Exception()
class NoSerializer(command: String) : Exception("Cannot serialize message command=$command")
