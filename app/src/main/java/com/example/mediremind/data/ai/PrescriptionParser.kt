package com.example.mediremind.data.ai

import android.graphics.Bitmap
import com.example.mediremind.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import org.json.JSONArray

data class ParsedMedicineUiModel(
    val medicineName: String,
    val dosage: String,
    val timesPerDay: Int,
    val scheduleTimes: List<String>,
    val instruction: String
)

class PrescriptionParser {
    private val model = GenerativeModel(
        modelName = BuildConfig.GEMINI_MODEL,
        apiKey = BuildConfig.GEMINI_API_KEY,
        generationConfig = generationConfig {
            responseMimeType = "application/json"
        }
    )

    suspend fun parsePrescription(bitmap: Bitmap): Result<List<ParsedMedicineUiModel>> {
        if (BuildConfig.GEMINI_API_KEY.isBlank()) {
            return Result.failure(IllegalStateException("Chưa cấu hình GEMINI_API_KEY trong ứng dụng."))
        }

        return try { Result.success(run {
            val response = model.generateContent(
                content {
                    image(bitmap)
                    text(PRESCRIPTION_PROMPT)
                }
            )
            val medicines = parseJsonArray(response.text.orEmpty())
            if (medicines.isEmpty()) {
                error("Gemini không tìm thấy thuốc nào trong ảnh đơn thuốc.")
            }
            medicines
        }) } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (failure: Exception) { Result.failure(failure) }
    }

    private fun parseJsonArray(rawJson: String): List<ParsedMedicineUiModel> {
        val json = rawJson
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val array = JSONArray(json)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val name = item.optString("medicineName").trim()
                if (name.isBlank()) continue

                val scheduleTimesJson = item.optJSONArray("scheduleTimes")
                val scheduleTimes = buildList {
                    if (scheduleTimesJson != null) {
                        for (timeIndex in 0 until scheduleTimesJson.length()) {
                            val time = scheduleTimesJson.optString(timeIndex).trim()
                            if (time.matches(HOUR_MINUTE_REGEX) && runCatching { java.time.LocalTime.parse(time) }.isSuccess) {
                                add(time)
                            }
                        }
                    }
                }

                add(
                    ParsedMedicineUiModel(
                        medicineName = name,
                        dosage = item.optString("dosage").trim(),
                        timesPerDay = item.optInt("timesPerDay", scheduleTimes.size),
                        scheduleTimes = scheduleTimes.ifEmpty { listOf("08:00") },
                        instruction = item.optString("instruction").trim()
                    )
                )
            }
        }
    }

    companion object {
        private val HOUR_MINUTE_REGEX = Regex("""^\d{2}:\d{2}$""")

        private const val PRESCRIPTION_PROMPT = """
Bạn là trợ lý y tế chỉ làm nhiệm vụ OCR và chuẩn hóa dữ liệu từ ảnh đơn thuốc tiếng Việt.
Hãy trích xuất danh sách thuốc trong ảnh thành một JSON array hợp lệ, không thêm markdown.
Mỗi phần tử phải có đúng các field:
- medicineName: tên thuốc.
- dosage: liều lượng mỗi lần uống, ví dụ "1 viên", "2 gói".
- timesPerDay: số lần uống mỗi ngày, kiểu số nguyên.
- scheduleTimes: mảng giờ uống dạng "HH:mm"; nếu đơn thuốc chỉ nói sáng/trưa/tối thì suy luận lần lượt "08:00", "12:30", "20:00".
- instruction: hướng dẫn uống, ví dụ "Sau ăn", "Trước ăn", "Uống sau ăn no".
Nếu không đọc được thuốc nào, trả về [].
"""
    }
}
