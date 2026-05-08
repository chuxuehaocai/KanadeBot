package moe.cuteyuki.kanadebot.utils

import moe.cuteyuki.kanadebot.managers.ConfigManager
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object CipherAES {
    private const val BLOCK_SIZE = 128 // bits

    private val AES_KEY: ByteArray
    private val AES_IV: ByteArray

    // AES 加密参数 (from reverseMai/config.py)
    private val AES_KEY_STR = ConfigManager.getConfig().aesKey
    private val AES_IV_STR = ConfigManager.getConfig().aesIv

    init {
        AES_KEY = parseKeyOrIv(AES_KEY_STR)
        AES_IV = parseKeyOrIv(AES_IV_STR)
    }

    /**
     * 判断字符串是否是十六进制
     */
    private fun isHexString(str: String): Boolean =
        str.matches("^[0-9a-fA-F]+$".toRegex())

    /**
     * 转换配置中的 key/iv
     */
    private fun parseKeyOrIv(value: String): ByteArray {
        return if (isHexString(value)) {
            hexStringToBytes(value)
        } else {
            // 普通字符串，先转 hex 再 decode
            val hex = bytesToHex(value.toByteArray(Charsets.UTF_8))
            hexStringToBytes(hex)
        }
    }

    /**
     * AES CBC PKCS#7 加密
     */
    @JvmStatic
    @Throws(Exception::class)
    fun encrypt(plaintext: ByteArray): ByteArray {
        val padded = pad(plaintext)

        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        val keySpec = SecretKeySpec(AES_KEY, "AES")
        val ivSpec = IvParameterSpec(AES_IV)

        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        return cipher.doFinal(padded)
    }

    /**
     * AES CBC PKCS#7 解密
     */
    @JvmStatic
    @Throws(Exception::class)
    fun decrypt(ciphertext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        val keySpec = SecretKeySpec(AES_KEY, "AES")
        val ivSpec = IvParameterSpec(AES_IV)

        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        val decrypted = cipher.doFinal(ciphertext)
        return unpad(decrypted)
    }

    /**
     * PKCS#7 padding
     */
    private fun pad(data: ByteArray): ByteArray {
        val blockSize = BLOCK_SIZE / 8
        val paddingLength = blockSize - (data.size % blockSize)
        val padded = data.copyOf(data.size + paddingLength)
        padded.fill(paddingLength.toByte(), data.size, padded.size)
        return padded
    }

    /**
     * 移除 PKCS#7 padding
     */
    private fun unpad(paddedData: ByteArray): ByteArray {
        val padChar = paddedData.last().toInt() and 0xFF
        require(padChar in 1..BLOCK_SIZE / 8) { "Invalid padding" }
        return paddedData.copyOf(paddedData.size - padChar)
    }

    /**
     * hex -> byte[]
     */
    private fun hexStringToBytes(hex: String): ByteArray {
        val len = hex.length
        require(len % 2 == 0) { "Invalid hex string length" }
        val data = ByteArray(len / 2)
        for (i in hex.indices step 2) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4)
                    + Character.digit(hex[i + 1], 16)).toByte()
        }
        return data
    }

    /**
     * byte[] -> hex
     */
    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }
}
