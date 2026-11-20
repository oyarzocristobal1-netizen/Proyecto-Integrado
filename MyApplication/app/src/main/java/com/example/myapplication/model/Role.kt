package com.example.myapplication.model

/**
 * Roles del sistema y sus permisos (Tabla 31 del informe: matriz de permisos por rol).
 * El rol se guarda en Firestore, en el campo "rol" del documento usuarios/{uid}.
 * La app solo oculta lo que el rol no puede hacer; las reglas de Firestore lo hacen cumplir en el servidor.
 */
enum class Role(val id: String, val label: String) {
    ADMIN("admin", "Administrador"),
    ENCARGADO("encargado", "Encargado"),
    LECHERO("lechero", "Lechero");

    /** Tomar medicion, ver resultado, crear lote y recibir alertas: todos los roles. */
    val canMeasure: Boolean get() = true

    /** Historial: administrador y encargado ven todo; el lechero, solo lo propio. */
    val canViewAllHistory: Boolean get() = this != LECHERO

    /** Editar un lote. */
    val canEditLots: Boolean get() = this != LECHERO

    /** Marcar una alerta como atendida. */
    val canAttendAlerts: Boolean get() = this != LECHERO

    /** Administrar el catalogo de estanques y cerrar o reabrir lotes. */
    val canManageTanks: Boolean get() = this != LECHERO

    /** Anular una medicion registrada por error (queda guardada, con motivo y responsable). */
    val canAnnulMeasurements: Boolean get() = this != LECHERO

    /** Exportar reportes. */
    val canExportReports: Boolean get() = this != LECHERO

    /** Calibrar el dispositivo (incluye ver la configuracion y el codigo de la caja). */
    val canCalibrateDevice: Boolean get() = this == ADMIN

    /** Gestionar usuarios y permisos. */
    val canManageUsers: Boolean get() = this == ADMIN

    /** Modificar los rangos de calidad. */
    val canEditQualityRanges: Boolean get() = this == ADMIN

    companion object {
        fun fromId(id: String?): Role? = entries.firstOrNull { it.id == id?.trim()?.lowercase() }
    }
}

/** Usuario con sesion iniciada. */
data class UserSession(
    val uid: String,
    val email: String,
    val name: String,
    val role: Role
)
