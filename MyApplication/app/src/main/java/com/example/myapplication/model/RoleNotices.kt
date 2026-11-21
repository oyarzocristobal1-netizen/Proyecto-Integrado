package com.example.myapplication.model

/** Tipos de aviso que la app puede mostrar como notificacion del telefono. */
enum class NoticeKind {
    /** Alertas de calidad sin atender (encargado y administrador). */
    ALERTS,

    /** Mediciones en las que un trabajador pidio revision (encargado y administrador). */
    REVIEW_REQUESTS,

    /** Lotes de hoy a los que ya les tocaba medir (encargado y administrador). */
    OVERDUE_LOTS,

    /** El encargado respondio una revision pedida por el trabajador (lechero). */
    REVIEW_ANSWERED
}

/** Una notificacion lista para mostrar. */
data class Notice(val kind: NoticeKind, val title: String, val text: String)

/** Que mostrar ahora y que recordar para no repetirlo en la proxima revision. */
data class NoticePlan(
    val notices: List<Notice>,
    /** Claves ya avisadas por tipo: solo las que siguen vigentes (las resueltas se olvidan). */
    val nextNotified: Map<NoticeKind, Set<String>>
)

/**
 * Decide que avisos corresponden a cada rol y evita repetirlos. Es logica pura (se prueba sin Android).
 *
 * Un aviso se muestra solo cuando hay claves nuevas: si ayer ya se aviso de las mismas 3 alertas y
 * siguen pendientes, no se vuelve a molestar; si llega una cuarta, se avisa de nuevo.
 */
object RoleNotices {

    /** Tipos de aviso que recibe cada rol. */
    fun kindsFor(role: Role): Set<NoticeKind> =
        if (role.canAttendAlerts) {
            setOf(NoticeKind.ALERTS, NoticeKind.REVIEW_REQUESTS, NoticeKind.OVERDUE_LOTS)
        } else {
            setOf(NoticeKind.REVIEW_ANSWERED)
        }

    /** Clave de un lote atrasado: cambia si se vuelve a medir y vuelve a atrasarse. */
    fun overdueKey(lotId: String, dueAtMillis: Long): String = "$lotId@$dueAtMillis"

    /**
     * @param current claves vigentes ahora, por tipo (los tipos que no corresponden al rol se ignoran)
     * @param notified claves de las que ya se aviso
     */
    fun plan(
        role: Role,
        current: Map<NoticeKind, Set<String>>,
        notified: Map<NoticeKind, Set<String>>
    ): NoticePlan {
        val notices = mutableListOf<Notice>()
        val next = mutableMapOf<NoticeKind, Set<String>>()
        for (kind in NoticeKind.entries.filter { it in kindsFor(role) }) {
            val now = current[kind].orEmpty()
            val already = notified[kind].orEmpty()
            val fresh = now - already
            if (fresh.isNotEmpty()) notices += notice(kind, fresh.size, now.size)
            // Lo ya avisado que sigue vigente se conserva; lo resuelto se olvida.
            next[kind] = now
        }
        return NoticePlan(notices, next)
    }

    internal fun notice(kind: NoticeKind, fresh: Int, total: Int): Notice = when (kind) {
        NoticeKind.ALERTS -> Notice(
            kind,
            "Alertas por atender",
            if (fresh == total) {
                if (fresh == 1) "Hay 1 alerta nueva sin atender." else "Hay $fresh alertas nuevas sin atender."
            } else {
                "Hay $fresh alerta(s) nueva(s). En total quedan $total sin atender."
            }
        )

        NoticeKind.REVIEW_REQUESTS -> Notice(
            kind,
            "Revisión pedida",
            if (fresh == 1) "Un trabajador pidió que revises una medición."
            else "Los trabajadores pidieron que revises $fresh mediciones."
        )

        NoticeKind.OVERDUE_LOTS -> Notice(
            kind,
            "Medición atrasada",
            if (fresh == 1) "A 1 lote de hoy ya le tocaba medir."
            else "A $fresh lotes de hoy ya les tocaba medir."
        )

        NoticeKind.REVIEW_ANSWERED -> Notice(
            kind,
            "Revisión respondida",
            if (fresh == 1) "El encargado revisó tu medición. Míralo en \"Mis mediciones\"."
            else "El encargado respondió $fresh de tus revisiones. Míralas en \"Mis mediciones\"."
        )
    }
}
