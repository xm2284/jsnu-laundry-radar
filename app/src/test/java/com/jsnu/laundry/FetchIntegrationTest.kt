package com.jsnu.laundry

import com.jsnu.laundry.data.LaundryRepository
import com.jsnu.laundry.data.api.HaierApi
import com.jsnu.laundry.data.prefs.WatchPoint
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.nio.charset.Charset

/**
 * 集成链路验证：本地 TCP 简易 HTTP Server 模拟海乐接口，
 * 验证 Retrofit 实际发出的请求体 + DTO 解析 + 业务映射全链路。
 */
class FetchIntegrationTest {

    private val realJson = """
        {"code":0,"message":"success","data":{"page":1,"pageSize":100,"total":3,"items":[
        {"id":85821388,"name":"18号楼4楼2号机","imei":"869737061970007","floorCode":"","state":1,"enableReserve":false,"reserveState":0,"lastMaintenanceTime":null,"finishTime":null,"deviceId":50908078},
        {"id":85804140,"name":"18号楼1楼1号机","imei":"869737067997160","floorCode":"","state":1,"enableReserve":false,"reserveState":0,"lastMaintenanceTime":null,"finishTime":null,"deviceId":50947639},
        {"id":85531113,"name":"18号楼1层10号机","imei":"868680050004019","floorCode":"1","state":2,"enableReserve":false,"reserveState":0,"lastMaintenanceTime":null,"finishTime":"2026-09-11 00:35:00","deviceId":50841901}
        ]}}
    """.trimIndent()

    @Test
    fun `fetch默认点位全链路_请求体与解析正确`() {
        val server = ServerSocket(0)
        val captured = StringBuilder()
        val thread = Thread {
            runCatching {
                val client = server.accept()
                client.soTimeout = 5000
                val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charset.forName("UTF-8")))
                var len = -1
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) break
                    if (line.startsWith("Content-Length:", ignoreCase = true)) {
                        len = line.substringAfter(':').trim().toInt()
                    }
                }
                if (len > 0) {
                    val body = CharArray(len)
                    val read = reader.read(body)
                    captured.append(String(body, 0, read.coerceAtLeast(0)))
                }
                val bytes = realJson.toByteArray()
                val resp = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray()
                client.getOutputStream().use { it.write(resp + bytes) }
                client.close()
            }
        }
        thread.start()
        try {
            val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
            val api: HaierApi = Retrofit.Builder()
                .baseUrl("http://127.0.0.1:${server.localPort}/")
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(HaierApi::class.java)

            val repo = LaundryRepository(api)
            val result = runBlocking { repo.fetch(WatchPoint()) }

            thread.join(10_000)
            assertTrue("请求体应含 positionId=24615，实际: $captured", captured.contains("24615"))
            assertEquals(3, result.devices.size)
            // 两台 IDLE 按名称排（18号楼1楼1号机 < 18号楼4楼2号机），第三台运行中排后
            assertEquals("18号楼1楼1号机", result.devices[0].name)
            assertEquals("18号楼4楼2号机", result.devices[1].name)
            assertNull(result.error)
            assertEquals(com.jsnu.laundry.data.model.BizStatus.IDLE, result.devices[0].bizStatus)
            assertEquals(com.jsnu.laundry.data.model.BizStatus.LOCKED, result.devices[2].bizStatus)
        } finally {
            server.close()
        }
    }
}
