package com.example.data.util

import kotlin.random.Random

object BarcodeGenerator {

    /**
     * Generates a realistic EAN-13 style numeric barcode with check digit
     */
    fun generateEan13(prefix: String = "890"): String {
        val randomDigits = StringBuilder(prefix)
        while (randomDigits.length < 12) {
            randomDigits.append(Random.nextInt(0, 10))
        }
        val digits = randomDigits.toString()
        var sum = 0
        for (i in digits.indices) {
            val d = digits[i].digitToInt()
            sum += if (i % 2 == 0) d else d * 3
        }
        val checkDigit = (10 - (sum % 10)) % 10
        return "$digits$checkDigit"
    }

    /**
     * Generates a formatted alphanumeric SKU code (e.g. "SHP-84920")
     */
    fun generateSku(categoryPrefix: String = "SKU"): String {
        val randomNum = Random.nextInt(10000, 99999)
        return "$categoryPrefix-$randomNum"
    }

    /**
     * Code 128B pattern encoding table for standard ASCII characters 32..126
     * Each pattern represents bar/space widths (sum of 11 modules per pattern).
     */
    private val CODE_128_PATTERNS = arrayOf(
        "212222", "222122", "222221", "121223", "121322", "131222", "122213", "122312", "132212", "221213",
        "221312", "231212", "112232", "122132", "122231", "113222", "123122", "123221", "223211", "221132",
        "221231", "213212", "223112", "312131", "311222", "321122", "321221", "312212", "322112", "322211",
        "212123", "212321", "232121", "111323", "131123", "131321", "112313", "132113", "132311", "211313",
        "231113", "231311", "112133", "112331", "132131", "113123", "113321", "133121", "313121", "211331",
        "231131", "213113", "213311", "213131", "311123", "311321", "331121", "312113", "312311", "332111",
        "314111", "221411", "431111", "111224", "111422", "121124", "121421", "141122", "141221", "112214",
        "112412", "122114", "122411", "142112", "142211", "241211", "221114", "413111", "241112", "134111",
        "111242", "121142", "121241", "114212", "124112", "124211", "411212", "421112", "421211", "212141",
        "214121", "412121", "111143", "111341", "131141", "114113", "114311", "411113", "411311", "113141",
        "114131", "311141", "411131", "211412", "211214", "211232", "2331112" // 106 is stop (13 modules)
    )

    private const val START_CODE_B = 104
    private const val STOP_CODE = 106

    /**
     * Converts a string into an array of boolean flags: true = black bar, false = white space.
     * Uses Code 128 subset B with quiet zones, start/stop bars, and checksum.
     */
    fun encodeToBarSequence(input: String): BooleanArray {
        val sanitized = input.ifBlank { "000000" }.filter { it.code in 32..126 }.take(24)
        val validText = sanitized.ifBlank { "SHOP" }

        val codeIndices = mutableListOf<Int>()
        codeIndices.add(START_CODE_B)

        var checksum = START_CODE_B
        for (i in validText.indices) {
            val charIndex = validText[i].code - 32
            codeIndices.add(charIndex)
            checksum += charIndex * (i + 1)
        }
        val checksumIndex = checksum % 103
        codeIndices.add(checksumIndex)
        codeIndices.add(STOP_CODE)

        // Convert patterns into boolean modules
        val modules = mutableListOf<Boolean>()

        // Left quiet zone (10 modules white)
        repeat(10) { modules.add(false) }

        for (patternIndex in codeIndices) {
            val pattern = if (patternIndex in CODE_128_PATTERNS.indices) {
                CODE_128_PATTERNS[patternIndex]
            } else {
                CODE_128_PATTERNS[0]
            }
            var isBar = true
            for (char in pattern) {
                val width = char.digitToIntOrNull() ?: 1
                repeat(width) {
                    modules.add(isBar)
                }
                isBar = !isBar
            }
        }

        // Right quiet zone (10 modules white)
        repeat(10) { modules.add(false) }

        return modules.toBooleanArray()
    }
}
