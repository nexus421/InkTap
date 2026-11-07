package bayern.kickner.inktap.core

import kotnexlib.ResultOf2
import qrcode.raw.ErrorCorrectionLevel
import qrcode.raw.QRCodeProcessor

/** Smallest QR module in pixels. 2 px are 0.39 mm on this panel, 1 px would be too small for most cameras. */
const val MIN_QR_MODULE_PX = 2

/** Quiet zone around a QR code in modules. The white frame of the display case adds more on the outside. */
const val QR_QUIET_MODULES = 2

/**
 * Encodes [text] as QR code (error correction M) and draws it as large as possible, centered, into a square of
 * [size] x [size] pixels. Every module gets the same whole number of pixels, otherwise scanners struggle.
 */
fun renderQr(text: String, size: Int): ResultOf2<MonoImage, ContentError> {
    if (text.isEmpty()) return ResultOf2.Failure(ContentError.Empty)
    val modules = runCatching {
        QRCodeProcessor(text, ErrorCorrectionLevel.MEDIUM).encode()
    }.getOrElse { return ResultOf2.Failure(ContentError.QrTooLong) }

    val count = modules.size
    val modulePx = size / (count + 2 * QR_QUIET_MODULES)
    if (modulePx < MIN_QR_MODULE_PX) return ResultOf2.Failure(ContentError.QrTooLong)

    val image = MonoImage(size, size)
    val offset = (size - count * modulePx) / 2
    for (row in 0 until count) for (col in 0 until count) {
        if (modules[row][col].dark) image.fillRect(offset + col * modulePx, offset + row * modulePx, modulePx, modulePx)
    }
    return ResultOf2.Success(image)
}

/** Barcode symbologies the app can draw. */
enum class BarcodeFormat { EAN_13, CODE_128 }

/**
 * A barcode as a row of modules (`true` = bar), quiet zones not included.
 *
 * @property quietLeft Quiet zone the standard demands on the left, in modules.
 * @property quietRight Quiet zone the standard demands on the right, in modules.
 */
class BarcodeModules(val format: BarcodeFormat, val bars: BooleanArray, val quietLeft: Int, val quietRight: Int)

/**
 * Picks the format automatically: 13 digits with a valid check digit become EAN-13 (the code on most customer
 * cards and products), everything else Code 128. Code 128 allows printable ASCII only (no umlauts).
 */
fun encodeBarcode(value: String): ResultOf2<BarcodeModules, ContentError> {
    if (value.isEmpty()) return ResultOf2.Failure(ContentError.Empty)
    val isEan13 = value.length == 13 && value.all { it in '0'..'9' } && ean13CheckDigit(value.take(12)) == value[12]
    if (isEan13) return ResultOf2.Success(BarcodeModules(BarcodeFormat.EAN_13, ean13(value), 11, 7))
    val printableAscii = value.all { it.code in 32..126 }
    if (printableAscii.not()) return ResultOf2.Failure(ContentError.BarcodeInvalidChars)
    return ResultOf2.Success(BarcodeModules(BarcodeFormat.CODE_128, code128(value), 10, 10))
}

/** Check digit of the first 12 digits of an EAN-13. */
fun ean13CheckDigit(first12: String): Char {
    val sum = first12.mapIndexed { i, c -> (c - '0') * (if (i % 2 == 0) 1 else 3) }.sum()
    return '0' + (10 - sum % 10) % 10
}

private val EAN_L = listOf("0001101", "0011001", "0010011", "0111101", "0100011", "0110001", "0101111", "0111011", "0110111", "0001011")
private val EAN_PARITY = listOf("LLLLLL", "LLGLGG", "LLGGLG", "LLGGGL", "LGLLGG", "LGGLLG", "LGGGLL", "LGLGLG", "LGLGGL", "LGGLGL")

private fun ean13(value: String): BooleanArray {
    val digits = value.map { it - '0' }
    val r = EAN_L.map { code -> code.map { if (it == '0') '1' else '0' }.joinToString("") }
    val g = r.map { it.reversed() }
    val left = (1..6).joinToString("") { i -> if (EAN_PARITY[digits[0]][i - 1] == 'L') EAN_L[digits[i]] else g[digits[i]] }
    val right = (7..12).joinToString("") { i -> r[digits[i]] }
    return ("101" + left + "01010" + right + "101").map { it == '1' }.toBooleanArray()
}

/** Bar and space widths of the 107 Code 128 symbols (values 0 to 102, then Start A, Start B, Start C). */
internal val CODE128 = listOf(
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
    "114131", "311141", "411131", "211412", "211214", "211232",
)
internal const val CODE128_STOP = "2331112"
private const val START_B = 104
private const val START_C = 105
private const val SWITCH_TO_B = 100

/** Digits only (2 or more) use code set C, which halves the width. Odd digit counts switch to B for the last one. */
private fun code128(value: String): BooleanArray {
    val digitsOnly = value.length >= 2 && value.all { it in '0'..'9' }
    val symbols = if (digitsOnly) {
        val pairs = value.length / 2
        val list = mutableListOf(START_C)
        for (i in 0 until pairs) list += value.substring(2 * i, 2 * i + 2).toInt()
        if (value.length % 2 == 1) list += listOf(SWITCH_TO_B, value.last().code - 32)
        list
    } else {
        listOf(START_B) + value.map { it.code - 32 }
    }
    val checksum = (symbols.first() + symbols.drop(1).mapIndexed { i, v -> (i + 1) * v }.sum()) % 103
    val widths = (symbols + checksum).joinToString("") { CODE128[it] } + CODE128_STOP
    val bars = mutableListOf<Boolean>()
    widths.forEachIndexed { i, w -> repeat(w - '0') { bars += i % 2 == 0 } }
    return bars.toBooleanArray()
}
