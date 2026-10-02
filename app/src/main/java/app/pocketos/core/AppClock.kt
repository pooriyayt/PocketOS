package app.pocketos.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Injectable time source so scheduling, insights and tests are deterministic. */
interface AppClock {
    fun now(): Instant
    fun zone(): ZoneId
    fun today(): LocalDate = LocalDate.ofInstant(now(), zone())
    fun localNow(): LocalDateTime = LocalDateTime.ofInstant(now(), zone())
    fun nowMs(): Long = now().toEpochMilli()
}

object SystemAppClock : AppClock {
    override fun now(): Instant = Instant.now()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}

class FixedAppClock(var instant: Instant, var zoneId: ZoneId = ZoneId.of("UTC")) : AppClock {
    override fun now(): Instant = instant
    override fun zone(): ZoneId = zoneId
    fun advanceMinutes(minutes: Long) {
        instant = instant.plusSeconds(minutes * 60)
    }
}
