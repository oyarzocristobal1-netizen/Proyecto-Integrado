package com.example.myapplication.viewmodel

import com.example.myapplication.model.MilkQualityReading
import com.example.myapplication.model.ReadingSource
import com.example.myapplication.sensor.DemoScenario
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Pantalla Medir sin hardware: la caja se reemplaza por FakeSensorSource. */
@OptIn(ExperimentalCoroutinesApi::class)
class MeasureViewModelTest {

    private val sensor = FakeSensorSource()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun deviceReading(ph: Float = 6.7f, timestamp: Long = System.currentTimeMillis()) =
        MilkQualityReading(
            temperature = 3.8f,
            ph = ph,
            conductivity = 4.8f,
            turbidity = 85f,
            timestamp = timestamp,
            source = ReadingSource.DEVICE
        )

    @Test
    fun modoCajaSinConexionNoInventaLecturas() {
        val vm = MeasureViewModel(sensor)
        vm.setMode(MeasureMode.DEVICE)
        val st = vm.state.value
        assertNull(st.reading)
        assertFalse(st.canRecord)
        assertTrue(st.recordHint!!.contains("Conecta la caja"))
    }

    @Test
    fun lecturasDeLaCajaSoloCuentanEnModoCaja() {
        val vm = MeasureViewModel(sensor)
        // En modo DEMO una lectura de la caja se ignora
        sensor.latestReading.value = deviceReading(ph = 6.65f)
        assertNull(vm.state.value.reading)

        vm.setMode(MeasureMode.DEVICE)
        val r = deviceReading(ph = 6.71f)
        sensor.latestReading.value = r
        assertEquals(r, vm.state.value.reading)
        assertFalse(vm.state.value.reading!!.isSimulated)
    }

    @Test
    fun soloSeHabilitaRegistrarConLecturaEstable() {
        val vm = MeasureViewModel(sensor)
        vm.setMode(MeasureMode.DEVICE)
        val now = System.currentTimeMillis()
        repeat(3) { i ->
            sensor.latestReading.value = deviceReading(timestamp = now + i)
            assertFalse(vm.state.value.canRecord)
            assertTrue(vm.state.value.recordHint!!.startsWith("Estabilizando"))
        }
        sensor.latestReading.value = deviceReading(timestamp = now + 3)
        assertTrue(vm.state.value.stability.isStable)
        assertTrue(vm.state.value.canRecord)
        assertNull(vm.state.value.recordHint)
    }

    @Test
    fun lecturasQueVarianMuchoNoSonEstables() {
        val vm = MeasureViewModel(sensor)
        vm.setMode(MeasureMode.DEVICE)
        val now = System.currentTimeMillis()
        listOf(6.5f, 6.7f, 6.9f, 6.6f).forEachIndexed { i, ph ->
            sensor.latestReading.value = deviceReading(ph = ph, timestamp = now + i)
        }
        assertFalse(vm.state.value.stability.isStable)
        assertFalse(vm.state.value.canRecord)
    }

    @Test
    fun lecturaAntiguaNoSePuedeRegistrar() {
        val vm = MeasureViewModel(sensor)
        vm.setMode(MeasureMode.DEVICE)
        val old = System.currentTimeMillis() - 20_000
        repeat(4) { i -> sensor.latestReading.value = deviceReading(timestamp = old + i) }
        assertTrue(vm.state.value.stability.isStable)
        assertFalse(vm.state.value.isReadingFresh)
        assertFalse(vm.state.value.canRecord)
        assertTrue(vm.state.value.recordHint!!.contains("15 s"))
    }

    @Test
    fun conectarYVolverASimulacionCierraLaConexion() {
        val vm = MeasureViewModel(sensor)
        vm.connect("00:11:22:33:44:55")
        assertEquals(MeasureMode.DEVICE, vm.state.value.mode)
        assertEquals("00:11:22:33:44:55", sensor.connectedTo)
        assertTrue(vm.state.value.isConnected)

        vm.setMode(MeasureMode.DEMO)
        assertEquals(1, sensor.disconnectCalls)
        assertFalse(vm.state.value.isConnected)
        assertNull(vm.state.value.reading)
    }

    @Test
    fun laSimulacionGeneraLecturasMarcadasComoSimuladas() {
        val vm = MeasureViewModel(sensor)
        vm.onScreenVisible(true)
        val reading = vm.state.value.reading
        assertNotNull(reading)
        assertTrue(reading!!.isSimulated)
        assertEquals(1, vm.state.value.stability.samples)
        vm.onScreenVisible(false)
    }

    @Test
    fun laSimulacionNoCorreConLaPantallaOculta() {
        val vm = MeasureViewModel(sensor)
        assertNull(vm.state.value.reading)
        vm.setScenario(DemoScenario.DILUTED_WATER)
        assertNull(vm.state.value.reading)
    }

    @Test
    fun cambiarDeMuestraReiniciaLaEstabilidad() {
        val vm = MeasureViewModel(sensor)
        vm.onScreenVisible(true)
        vm.setScenario(DemoScenario.DILUTED_WATER)
        val st = vm.state.value
        assertEquals(DemoScenario.DILUTED_WATER, st.scenario)
        assertEquals(1, st.stability.samples)
        assertTrue(st.reading!!.turbidity < 40f)
        vm.onScreenVisible(false)
    }

    @Test
    fun sinPermisoNoSeListanCajas() {
        sensor.permission = false
        val vm = MeasureViewModel(sensor)
        vm.openDeviceDialog()
        assertTrue(vm.state.value.showDeviceDialog)
        assertTrue(vm.state.value.pairedDevices.isEmpty())
        assertFalse(vm.hasBluetoothPermission())
    }
}
