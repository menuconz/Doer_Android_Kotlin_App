package nz.co.doer.util

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object Constants {
    const val DEVICE_TYPE_ANDROID = 2
    const val SITE_ID = 1
    const val LID = 1

    private val NZ_ZONE = ZoneId.of("Pacific/Auckland")

    /** Returns current NZ time formatted as ISO datetime string (matches server's DateTimeService) */
    fun nowNz(): String =
        LocalDateTime.now(NZ_ZONE).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
}
