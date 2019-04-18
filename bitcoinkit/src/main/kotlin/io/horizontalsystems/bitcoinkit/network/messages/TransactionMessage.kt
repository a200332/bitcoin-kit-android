package io.horizontalsystems.bitcoinkit.network.messages

import io.horizontalsystems.bitcoinkit.io.BitcoinInput
import io.horizontalsystems.bitcoinkit.serializers.TransactionSerializer
import io.horizontalsystems.bitcoinkit.storage.FullTransaction
import java.io.ByteArrayInputStream

class TransactionMessage(var transaction: FullTransaction) : IMessage {
    override val command: String = "tx"

    override fun toString(): String {
        return "TransactionMessage(${transaction.header.hashHexReversed})"
    }
}

class TransactionMessageParser : IMessageParser {
    override val command: String = "tx"

    override fun parseMessage(payload: ByteArray): IMessage {
        BitcoinInput(ByteArrayInputStream(payload)).use { input ->
            val transaction = TransactionSerializer.deserialize(input)
            return TransactionMessage(transaction)
        }
    }
}

class TransactionMessageSerializer : IMessageSerializer {
    override val command: String = "tx"

    override fun serialize(message: IMessage): ByteArray {
        if (message !is TransactionMessage) throw WrongSerializer()

        return TransactionSerializer.serialize(message.transaction)
    }
}