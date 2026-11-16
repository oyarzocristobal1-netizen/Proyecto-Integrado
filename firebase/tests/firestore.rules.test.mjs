// Pruebas de las reglas de Firestore (firebase/firestore.rules) con el emulador.
// Ejecutar desde esta carpeta:  npm install  y luego  npm test   (requiere Java 11+).
import { readFileSync } from 'node:fs';
import { after, before, beforeEach, describe, test } from 'node:test';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import {
  Timestamp,
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  query,
  serverTimestamp,
  setDoc,
  updateDoc,
  where,
  writeBatch,
} from 'firebase/firestore';

const LOTE = '20261113-estanque-1';
let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-calidad-leche',
    firestore: {
      rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8'),
      host: '127.0.0.1',
      port: 8080,
    },
  });
});

after(async () => {
  await env?.cleanup();
});

beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const usuario = (rol, activo = true) => ({ nombre: rol, correo: `${rol}@prueba.cl`, rol, activo });
    await setDoc(doc(db, 'usuarios', 'admin'), usuario('admin'));
    await setDoc(doc(db, 'usuarios', 'encargado'), usuario('encargado'));
    await setDoc(doc(db, 'usuarios', 'lechero'), usuario('lechero'));
    await setDoc(doc(db, 'usuarios', 'lechero2'), usuario('lechero'));
    await setDoc(doc(db, 'usuarios', 'inactivo'), usuario('lechero', false));
    await setDoc(doc(db, 'dispositivos', 'simulador'), { nombre: 'Simulador', ubicacion: 'App', estado: 'activo' });
    await setDoc(doc(db, 'dispositivos', 'caja-01'), { nombre: 'Caja 1', ubicacion: 'Sala', estado: 'activo' });
    await setDoc(doc(db, 'lotes', LOTE), {
      estanque: 'Estanque 1', fecha: Timestamp.now(), creadoPor: 'lechero', nombreCreador: 'lechero', observacion: '',
    });
    await setDoc(doc(db, 'mediciones', 'existente'), {
      idDispositivo: 'simulador', idLote: LOTE, uidLechero: 'lechero', nombreLechero: 'lechero',
      fechaHora: Timestamp.now(), registradoEn: Timestamp.now(), origen: 'simulado',
      sensores: { temp: 3.8, ph: 6.7, cond: 4.8, turb: 85 }, resultado: { estado: 'verde', titulo: 'Calidad Excelente' },
    });
    await setDoc(doc(db, 'alertas', 'existente-ph'), {
      idMedicion: 'existente', idLote: LOTE, tipo: 'ph', severidad: 'alta', fecha: Timestamp.now(),
      atendida: false, valor: 7.15, mensaje: 'pH alto', nombreLechero: 'lechero', origen: 'simulado',
    });
  });
});

/** Firestore como un usuario con sesion (correo verificado por defecto). */
const como = (uid, verificado = true) => env.authenticatedContext(uid, { email_verified: verificado }).firestore();

const medicion = (cambios = {}) => ({
  idDispositivo: 'simulador',
  idLote: LOTE,
  uidLechero: 'lechero',
  nombreLechero: 'lechero',
  fechaHora: Timestamp.now(),
  registradoEn: serverTimestamp(),
  origen: 'simulado',
  sensores: { temp: 3.8, ph: 6.7, cond: 4.8, turb: 85 },
  resultado: { estado: 'verde', titulo: 'Calidad Excelente' },
  ...cambios,
});

const alerta = (idMedicion, cambios = {}) => ({
  idMedicion,
  idLote: LOTE,
  tipo: 'ph',
  severidad: 'alta',
  fecha: Timestamp.now(),
  atendida: false,
  valor: 7.15,
  mensaje: 'pH alcalino',
  nombreLechero: 'lechero',
  origen: 'simulado',
  ...cambios,
});

describe('sesion y cuenta', () => {
  test('sin sesion no se lee nada', async () => {
    await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), 'lotes', LOTE)));
  });
  test('correo sin verificar no puede leer lotes', async () => {
    await assertFails(getDoc(doc(como('lechero', false), 'lotes', LOTE)));
  });
  test('cuenta desactivada no puede leer lotes', async () => {
    await assertFails(getDoc(doc(como('inactivo'), 'lotes', LOTE)));
  });
  test('cuenta desactivada si puede leer su perfil (para saber que esta desactivada)', async () => {
    await assertSucceeds(getDoc(doc(como('inactivo'), 'usuarios', 'inactivo')));
  });
});

describe('usuarios', () => {
  test('el lechero lee su perfil pero no el de otros', async () => {
    await assertSucceeds(getDoc(doc(como('lechero'), 'usuarios', 'lechero')));
    await assertFails(getDoc(doc(como('lechero'), 'usuarios', 'admin')));
  });
  test('solo el administrador crea cuentas, con formato valido', async () => {
    const nuevo = { nombre: 'Nuevo', correo: 'nuevo@prueba.cl', rol: 'lechero', activo: true };
    await assertSucceeds(setDoc(doc(como('admin'), 'usuarios', 'nuevo'), nuevo));
    await assertFails(setDoc(doc(como('encargado'), 'usuarios', 'otro'), nuevo));
    await assertFails(setDoc(doc(como('admin'), 'usuarios', 'malo'), { ...nuevo, rol: 'operario' }));
    await assertFails(setDoc(doc(como('admin'), 'usuarios', 'malo2'), { ...nuevo, extra: 1 }));
  });
  test('el administrador no puede quitarse su rol ni desactivarse', async () => {
    await assertFails(updateDoc(doc(como('admin'), 'usuarios', 'admin'), { rol: 'lechero' }));
    await assertFails(updateDoc(doc(como('admin'), 'usuarios', 'admin'), { activo: false }));
    await assertSucceeds(updateDoc(doc(como('admin'), 'usuarios', 'admin'), { nombre: 'Admin Fundo' }));
  });
  test('las cuentas no se borran', async () => {
    await assertFails(deleteDoc(doc(como('admin'), 'usuarios', 'lechero2')));
  });
});

describe('lotes', () => {
  const lote = (cambios = {}) => ({
    estanque: 'Estanque 2', fecha: Timestamp.now(), creadoPor: 'lechero', nombreCreador: 'lechero', observacion: '', ...cambios,
  });
  test('el lechero crea un lote valido', async () => {
    await assertSucceeds(setDoc(doc(como('lechero'), 'lotes', '20261113-estanque-2'), lote()));
  });
  test('rechaza lotes mal formados o a nombre de otro', async () => {
    const db = como('lechero');
    await assertFails(setDoc(doc(db, 'lotes', 'a'), lote({ estanque: 'x'.repeat(41) })));
    await assertFails(setDoc(doc(db, 'lotes', 'b'), lote({ estanque: '' })));
    await assertFails(setDoc(doc(db, 'lotes', 'c'), lote({ creadoPor: 'lechero2' })));
    await assertFails(setDoc(doc(db, 'lotes', 'd'), lote({ extra: true })));
  });
  test('solo encargado o administrador editan estanque y observacion', async () => {
    await assertFails(updateDoc(doc(como('lechero'), 'lotes', LOTE), { observacion: 'x' }));
    await assertSucceeds(updateDoc(doc(como('encargado'), 'lotes', LOTE), { observacion: 'Retirado 18:00' }));
    await assertFails(updateDoc(doc(como('encargado'), 'lotes', LOTE), { creadoPor: 'encargado' }));
  });
});

describe('mediciones', () => {
  test('medicion simulada valida junto con su alerta, en una sola escritura', async () => {
    const db = como('lechero');
    const lote = writeBatch(db);
    lote.set(doc(db, 'mediciones', 'm1'), medicion({ sensores: { temp: 3.8, ph: 7.15, cond: 4.8, turb: 85 } }));
    lote.set(doc(db, 'alertas', 'm1-ph'), alerta('m1'));
    await assertSucceeds(lote.commit());
  });
  test('medicion del sensor con la caja real', async () => {
    await assertSucceeds(setDoc(doc(como('lechero'), 'mediciones', 'm2'), medicion({ origen: 'sensor', idDispositivo: 'caja-01' })));
  });
  test('un dato simulado no puede hacerse pasar por medicion del sensor', async () => {
    const db = como('lechero');
    await assertFails(setDoc(doc(db, 'mediciones', 'x1'), medicion({ origen: 'simulado', idDispositivo: 'caja-01' })));
    await assertFails(setDoc(doc(db, 'mediciones', 'x2'), medicion({ origen: 'sensor', idDispositivo: 'simulador' })));
    await assertFails(setDoc(doc(db, 'mediciones', 'x3'), medicion({ origen: 'otro' })));
  });
  test('rechaza lecturas incompletas o fuera de los limites fisicos', async () => {
    const db = como('lechero');
    await assertFails(setDoc(doc(db, 'mediciones', 'y1'), medicion({ sensores: { temp: 3.8, ph: 6.7, cond: 4.8 } })));
    await assertFails(setDoc(doc(db, 'mediciones', 'y2'), medicion({ sensores: { temp: 3.8, ph: 15, cond: 4.8, turb: 85 } })));
    await assertFails(setDoc(doc(db, 'mediciones', 'y3'), medicion({ sensores: { temp: -127, ph: 6.7, cond: 4.8, turb: 85 } })));
    await assertFails(setDoc(doc(db, 'mediciones', 'y4'), medicion({ sensores: { temp: '3.8', ph: 6.7, cond: 4.8, turb: 85 } })));
    await assertFails(setDoc(doc(db, 'mediciones', 'y5'), medicion({ resultado: { estado: 'azul', titulo: 'x' } })));
  });
  test('la hora del servidor es obligatoria y la fecha debe ser coherente', async () => {
    const db = como('lechero');
    await assertFails(setDoc(doc(db, 'mediciones', 'z1'), medicion({ registradoEn: Timestamp.now() })));
    const hace10Dias = Timestamp.fromMillis(Date.now() - 10 * 24 * 3600 * 1000);
    await assertFails(setDoc(doc(db, 'mediciones', 'z2'), medicion({ fechaHora: hace10Dias })));
    const enUnaHora = Timestamp.fromMillis(Date.now() + 3600 * 1000);
    await assertFails(setDoc(doc(db, 'mediciones', 'z3'), medicion({ fechaHora: enUnaHora })));
  });
  test('no se registra a nombre de otro lechero ni en un lote o caja inexistente', async () => {
    const db = como('lechero');
    await assertFails(setDoc(doc(db, 'mediciones', 'w1'), medicion({ uidLechero: 'lechero2' })));
    await assertFails(setDoc(doc(db, 'mediciones', 'w2'), medicion({ idLote: 'no-existe' })));
    await assertFails(setDoc(doc(db, 'mediciones', 'w3'), medicion({ origen: 'sensor', idDispositivo: 'caja-99' })));
  });
  test('cuenta desactivada no registra', async () => {
    await assertFails(setDoc(doc(como('inactivo'), 'mediciones', 'v1'), medicion({ uidLechero: 'inactivo' })));
  });
  test('las mediciones son inmutables', async () => {
    await assertFails(updateDoc(doc(como('admin'), 'mediciones', 'existente'), { 'sensores.ph': 6.6 }));
    await assertFails(deleteDoc(doc(como('admin'), 'mediciones', 'existente')));
  });
  test('el lechero solo lee lo propio; el encargado lee todo', async () => {
    await assertSucceeds(getDocs(query(collection(como('lechero'), 'mediciones'), where('uidLechero', '==', 'lechero'))));
    await assertFails(getDocs(collection(como('lechero2'), 'mediciones')));
    await assertFails(getDoc(doc(como('lechero2'), 'mediciones', 'existente')));
    await assertSucceeds(getDocs(collection(como('encargado'), 'mediciones')));
  });
});

describe('alertas', () => {
  test('una alerta sin su medicion se rechaza', async () => {
    await assertFails(setDoc(doc(como('lechero'), 'alertas', 'no-existe-ph'), alerta('no-existe')));
  });
  test('el ID de la alerta debe ser medicion-tipo', async () => {
    const db = como('lechero');
    const lote = writeBatch(db);
    lote.set(doc(db, 'mediciones', 'm3'), medicion());
    lote.set(doc(db, 'alertas', 'otro-id'), alerta('m3'));
    await assertFails(lote.commit());
  });
  test('no se crea una alerta ya atendida ni con tipo desconocido', async () => {
    const db = como('lechero');
    const lote = writeBatch(db);
    lote.set(doc(db, 'mediciones', 'm4'), medicion());
    lote.set(doc(db, 'alertas', 'm4-ph'), alerta('m4', { atendida: true }));
    await assertFails(lote.commit());
  });
  test('solo encargado o administrador la marcan como atendida, sin tocar otros campos', async () => {
    await assertFails(updateDoc(doc(como('lechero'), 'alertas', 'existente-ph'), { atendida: true }));
    await assertFails(updateDoc(doc(como('encargado'), 'alertas', 'existente-ph'), { atendida: true, severidad: 'media' }));
    await assertSucceeds(updateDoc(doc(como('encargado'), 'alertas', 'existente-ph'), { atendida: true }));
  });
});

describe('rangos de calidad', () => {
  const rangos = { tempMax: 4, phMin: 6.6, phMax: 6.8, condMin: 4, condMax: 5.5, turbMin: 50 };
  test('solo el administrador guarda rangos coherentes', async () => {
    await assertSucceeds(setDoc(doc(como('admin'), 'configuracion', 'rangos'), rangos));
    await assertFails(setDoc(doc(como('encargado'), 'configuracion', 'rangos'), rangos));
    await assertFails(setDoc(doc(como('admin'), 'configuracion', 'rangos'), { ...rangos, phMin: 6.9 }));
    await assertFails(setDoc(doc(como('admin'), 'configuracion', 'otro'), rangos));
  });
  test('todos los usuarios activos leen los rangos', async () => {
    await assertSucceeds(getDoc(doc(como('lechero'), 'configuracion', 'rangos')));
  });
});
