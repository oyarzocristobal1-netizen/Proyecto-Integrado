package com.example.myapplication.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleNoticesTest {

    @Test
    fun cadaRolRecibeSoloSusAvisos() {
        val encargado = setOf(NoticeKind.ALERTS, NoticeKind.REVIEW_REQUESTS, NoticeKind.OVERDUE_LOTS)
        assertEquals(encargado, RoleNotices.kindsFor(Role.ENCARGADO))
        assertEquals(encargado, RoleNotices.kindsFor(Role.ADMIN))
        assertEquals(setOf(NoticeKind.REVIEW_ANSWERED), RoleNotices.kindsFor(Role.LECHERO))
    }

    @Test
    fun avisaDeLoNuevoYGuardaLoVigente() {
        val plan = RoleNotices.plan(
            Role.ENCARGADO,
            current = mapOf(NoticeKind.ALERTS to setOf("a1", "a2")),
            notified = emptyMap()
        )
        assertEquals(1, plan.notices.size)
        assertEquals(NoticeKind.ALERTS, plan.notices[0].kind)
        assertTrue(plan.notices[0].text.contains("2 alertas nuevas"))
        assertEquals(setOf("a1", "a2"), plan.nextNotified[NoticeKind.ALERTS])
    }

    @Test
    fun noRepiteLoQueYaSeAviso() {
        val plan = RoleNotices.plan(
            Role.ENCARGADO,
            current = mapOf(NoticeKind.ALERTS to setOf("a1", "a2")),
            notified = mapOf(NoticeKind.ALERTS to setOf("a1", "a2"))
        )
        assertTrue(plan.notices.isEmpty())
    }

    @Test
    fun siLlegaUnaNuevaAvisaSoloDeEsaYDiceElTotal() {
        val plan = RoleNotices.plan(
            Role.ENCARGADO,
            current = mapOf(NoticeKind.ALERTS to setOf("a1", "a2", "a3")),
            notified = mapOf(NoticeKind.ALERTS to setOf("a1", "a2"))
        )
        assertEquals(1, plan.notices.size)
        assertTrue(plan.notices[0].text.contains("1 alerta(s) nueva(s)"))
        assertTrue(plan.notices[0].text.contains("3 sin atender"))
    }

    @Test
    fun loResueltoSeOlvidaYSiVuelveAparecerSeAvisa() {
        val resuelto = RoleNotices.plan(
            Role.ENCARGADO,
            current = mapOf(NoticeKind.ALERTS to emptySet()),
            notified = mapOf(NoticeKind.ALERTS to setOf("a1"))
        )
        assertTrue(resuelto.notices.isEmpty())
        assertEquals(emptySet<String>(), resuelto.nextNotified[NoticeKind.ALERTS])

        val denuevo = RoleNotices.plan(
            Role.ENCARGADO,
            current = mapOf(NoticeKind.ALERTS to setOf("a1")),
            notified = resuelto.nextNotified
        )
        assertEquals(1, denuevo.notices.size)
    }

    @Test
    fun elLecheroNoRecibeAvisosDelEncargadoAunqueLlegueEseDato() {
        val plan = RoleNotices.plan(
            Role.LECHERO,
            current = mapOf(
                NoticeKind.ALERTS to setOf("a1"),
                NoticeKind.REVIEW_REQUESTS to setOf("m1"),
                NoticeKind.OVERDUE_LOTS to setOf("l1@5"),
                NoticeKind.REVIEW_ANSWERED to setOf("m9")
            ),
            notified = emptyMap()
        )
        assertEquals(listOf(NoticeKind.REVIEW_ANSWERED), plan.notices.map { it.kind })
        assertEquals(setOf(NoticeKind.REVIEW_ANSWERED), plan.nextNotified.keys)
    }

    @Test
    fun elEncargadoNoRecibeElAvisoDeRevisionRespondida() {
        val plan = RoleNotices.plan(
            Role.ENCARGADO,
            current = mapOf(NoticeKind.REVIEW_ANSWERED to setOf("m9")),
            notified = emptyMap()
        )
        assertTrue(plan.notices.isEmpty())
        assertTrue(NoticeKind.REVIEW_ANSWERED !in plan.nextNotified.keys)
    }

    @Test
    fun variosTiposJuntosDanUnaNotificacionPorTipo() {
        val plan = RoleNotices.plan(
            Role.ADMIN,
            current = mapOf(
                NoticeKind.ALERTS to setOf("a1"),
                NoticeKind.REVIEW_REQUESTS to setOf("m1", "m2"),
                NoticeKind.OVERDUE_LOTS to setOf("l1@100")
            ),
            notified = emptyMap()
        )
        assertEquals(
            listOf(NoticeKind.ALERTS, NoticeKind.REVIEW_REQUESTS, NoticeKind.OVERDUE_LOTS),
            plan.notices.map { it.kind }
        )
        assertTrue(plan.notices[1].text.contains("2 mediciones"))
    }

    @Test
    fun elLoteAtrasadoVuelveAAvisarSiSeMideYOtraVezSeAtrasa() {
        assertTrue(RoleNotices.overdueKey("L1", 100) != RoleNotices.overdueKey("L1", 200))
        val plan = RoleNotices.plan(
            Role.ENCARGADO,
            current = mapOf(NoticeKind.OVERDUE_LOTS to setOf(RoleNotices.overdueKey("L1", 200))),
            notified = mapOf(NoticeKind.OVERDUE_LOTS to setOf(RoleNotices.overdueKey("L1", 100)))
        )
        assertEquals(1, plan.notices.size)
        assertTrue(plan.notices[0].text.contains("1 lote"))
    }

    @Test
    fun lasRespuestasDelEncargadoSeAnunciaEnSingularYPlural() {
        val uno = RoleNotices.notice(NoticeKind.REVIEW_ANSWERED, 1, 1)
        val dos = RoleNotices.notice(NoticeKind.REVIEW_ANSWERED, 2, 2)
        assertTrue(uno.text.contains("revisó tu medición"))
        assertTrue(dos.text.contains("2 de tus revisiones"))
    }
}
