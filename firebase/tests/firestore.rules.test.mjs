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
  deleteField,
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
    await setDoc(doc(db, 'usuarios', 'adminInactivo'), usuario('admin', false));
    await setDoc(doc(db, 'usuarios', 'encargadoInactivo'), usuario('encargado', false));
    await setDoc(doc(db, 'calibraciones', 'cal-1'), { idDispositivo: 'caja-01', fecha: Timestamp.now() });
    await setDoc(doc(db, 'configuracion', 'rangos'), { tempMax: 4, phMin: 6.6, phMax: 6.8, condMin: 4, condMax: 5.5, turbMin: 50 });
    await setDoc(doc(db, 'estanques', 'est-1'), { nombre: 'Estanque 1', activo: true, creadoPor: 'encargado' });
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
  test('solo el administrador elimina un perfil, y nunca el suyo', async () => {
    await assertFails(deleteDoc(doc(como('encargado'), 'usuarios', 'lechero2')));
    await assertFails(deleteDoc(doc(como('admin'), 'usuarios', 'admin')));
    await assertSucceeds(deleteDoc(doc(como('admin'), 'usuarios', 'lechero2')));
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
  test('los valores medidos no se pueden cambiar (ni el administrador)', async () => {
    await assertFails(updateDoc(doc(como('admin'), 'mediciones', 'existente'), { 'sensores.ph': 6.6 }));
    await assertFails(deleteDoc(doc(como('encargado'), 'mediciones', 'existente')));
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

// ======================================================================================
// PRUEBAS DE SEGURIDAD: ataques y errores (borrado, roles, suplantacion, datos mal formados)
// ======================================================================================

/** Anotacion de una eliminacion en la bitacora (la hace el administrador). */
const baja = (cambios = {}) => ({
  tipo: 'medicion',
  idElemento: 'existente',
  descripcion: 'Medicion de prueba',
  eliminadoPor: 'admin',
  fecha: serverTimestamp(),
  ...cambios,
});

/** Todos los documentos que existen en las pruebas, para intentar borrarlos. */
const DOCUMENTOS = [
  ['usuarios', 'lechero2'],
  ['dispositivos', 'caja-01'],
  ['lotes', LOTE],
  ['mediciones', 'existente'],
  ['alertas', 'existente-ph'],
  ['calibraciones', 'cal-1'],
  ['estanques', 'est-1'],
  ['configuracion', 'rangos'],
];

describe('seguridad: eliminar de verdad solo lo hace el administrador', () => {
  for (const uid of ['encargado', 'lechero']) {
    test(`${uid} no puede borrar ningun documento de ninguna coleccion`, async () => {
      const db = como(uid);
      for (const [col, id] of DOCUMENTOS) {
        await assertFails(deleteDoc(doc(db, col, id)));
      }
    });
  }
  test('sin sesion, con correo sin verificar o con cuenta desactivada tampoco', async () => {
    const dbs = [env.unauthenticatedContext().firestore(), como('admin', false), como('adminInactivo'), como('encargadoInactivo')];
    for (const db of dbs) {
      for (const [col, id] of DOCUMENTOS) {
        await assertFails(deleteDoc(doc(db, col, id)));
      }
    }
  });
  test('el administrador elimina cualquier documento, salvo la configuracion de rangos', async () => {
    const db = como('admin');
    for (const [col, id] of DOCUMENTOS.filter(([col]) => col !== 'configuracion')) {
      await assertSucceeds(deleteDoc(doc(db, col, id)));
    }
    await assertFails(deleteDoc(doc(db, 'configuracion', 'rangos')));
  });
  test('borrado disfrazado: vaciar campos, sobrescribir o borrar en lote sin ser administrador falla', async () => {
    const admin = como('admin');
    await assertFails(updateDoc(doc(admin, 'mediciones', 'existente'), { sensores: deleteField() }));
    await assertFails(setDoc(doc(admin, 'mediciones', 'existente'), medicion()));
    await assertFails(updateDoc(doc(admin, 'usuarios', 'lechero2'), { rol: deleteField() }));
    const encargado = como('encargado');
    const lote = writeBatch(encargado);
    lote.delete(doc(encargado, 'mediciones', 'existente'));
    lote.delete(doc(encargado, 'alertas', 'existente-ph'));
    await assertFails(lote.commit());
  });
  test('el administrador elimina una medicion con sus alertas y la anota en la bitacora, todo junto', async () => {
    const db = como('admin');
    const lote = writeBatch(db);
    lote.delete(doc(db, 'mediciones', 'existente'));
    lote.delete(doc(db, 'alertas', 'existente-ph'));
    lote.set(doc(db, 'eliminaciones', 'e1'), baja());
    await assertSucceeds(lote.commit());
  });
});

describe('bitacora de eliminaciones', () => {
  test('solo el administrador anota y lee; con su nombre, hora del servidor y datos validos', async () => {
    await assertSucceeds(setDoc(doc(como('admin'), 'eliminaciones', 'a'), baja()));
    await assertFails(setDoc(doc(como('encargado'), 'eliminaciones', 'b'), baja({ eliminadoPor: 'encargado' })));
    await assertFails(setDoc(doc(como('lechero'), 'eliminaciones', 'c'), baja({ eliminadoPor: 'lechero' })));
    await assertSucceeds(getDoc(doc(como('admin'), 'eliminaciones', 'a')));
    await assertFails(getDoc(doc(como('encargado'), 'eliminaciones', 'a')));
  });
  test('rechaza nombre ajeno, hora falsa, tipo desconocido, campos extra o faltantes', async () => {
    const db = como('admin');
    await assertFails(setDoc(doc(db, 'eliminaciones', 'd'), baja({ eliminadoPor: 'encargado' })));
    await assertFails(setDoc(doc(db, 'eliminaciones', 'e'), baja({ fecha: Timestamp.now() })));
    await assertFails(setDoc(doc(db, 'eliminaciones', 'f'), baja({ tipo: 'configuracion' })));
    await assertFails(setDoc(doc(db, 'eliminaciones', 'g'), baja({ extra: 1 })));
    await assertFails(setDoc(doc(db, 'eliminaciones', 'h'), baja({ descripcion: '' })));
  });
  test('la bitacora no se edita ni se borra, ni siquiera el administrador', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'eliminaciones', 'x'), { tipo: 'lote', idElemento: 'l', descripcion: 'd', eliminadoPor: 'admin', fecha: Timestamp.now() });
    });
    await assertFails(updateDoc(doc(como('admin'), 'eliminaciones', 'x'), { descripcion: 'otra' }));
    await assertFails(deleteDoc(doc(como('admin'), 'eliminaciones', 'x')));
  });
});

describe('seguridad: escalada de privilegios entre roles', () => {
  test('un lechero no puede subirse el rol ni activarse a si mismo', async () => {
    const db = como('lechero');
    await assertFails(updateDoc(doc(db, 'usuarios', 'lechero'), { rol: 'admin' }));
    await assertFails(updateDoc(doc(db, 'usuarios', 'lechero'), { rol: 'encargado' }));
    await assertFails(updateDoc(doc(como('inactivo'), 'usuarios', 'inactivo'), { activo: true }));
  });
  test('un encargado no crea cuentas, no cambia roles ni activa a otros', async () => {
    const db = como('encargado');
    const nuevo = { nombre: 'Intruso', correo: 'intruso@prueba.cl', rol: 'admin', activo: true };
    await assertFails(setDoc(doc(db, 'usuarios', 'intruso'), nuevo));
    await assertFails(updateDoc(doc(db, 'usuarios', 'encargado'), { rol: 'admin' }));
    await assertFails(updateDoc(doc(db, 'usuarios', 'inactivo'), { activo: true }));
    await assertFails(updateDoc(doc(db, 'usuarios', 'lechero'), { rol: 'admin' }));
  });
  test('alguien con cuenta verificada pero sin perfil creado por el administrador no accede a nada', async () => {
    const db = como('fantasma');
    await assertFails(getDoc(doc(db, 'lotes', LOTE)));
    await assertFails(getDoc(doc(db, 'configuracion', 'rangos')));
    await assertFails(getDocs(collection(db, 'alertas')));
    await assertFails(setDoc(doc(db, 'lotes', 'fantasma-lote'), {
      estanque: 'X', fecha: Timestamp.now(), creadoPor: 'fantasma', nombreCreador: 'fantasma', observacion: '',
    }));
    await assertFails(setDoc(doc(db, 'usuarios', 'fantasma'), { nombre: 'Yo', correo: 'yo@prueba.cl', rol: 'admin', activo: true }));
  });
  test('un administrador desactivado o con correo sin verificar pierde todos sus poderes', async () => {
    const nuevo = { nombre: 'Otro', correo: 'otro@prueba.cl', rol: 'lechero', activo: true };
    for (const db of [como('adminInactivo'), como('admin', false)]) {
      await assertFails(setDoc(doc(db, 'usuarios', 'otro'), nuevo));
      await assertFails(updateDoc(doc(db, 'usuarios', 'lechero'), { rol: 'admin' }));
      await assertFails(setDoc(doc(db, 'configuracion', 'rangos'), { tempMax: 10, phMin: 6, phMax: 7, condMin: 3, condMax: 6, turbMin: 10 }));
    }
  });
  test('encargado y lechero no tocan dispositivos, calibraciones ni rangos', async () => {
    for (const uid of ['encargado', 'lechero']) {
      const db = como(uid);
      await assertFails(setDoc(doc(db, 'dispositivos', 'caja-02'), { nombre: 'Caja 2', ubicacion: 'x', estado: 'activo' }));
      await assertFails(updateDoc(doc(db, 'dispositivos', 'caja-01'), { estado: 'inactivo' }));
      await assertFails(getDoc(doc(db, 'calibraciones', 'cal-1')));
      await assertFails(setDoc(doc(db, 'calibraciones', 'cal-2'), { idDispositivo: 'caja-01' }));
      await assertFails(updateDoc(doc(db, 'configuracion', 'rangos'), { tempMax: 20 }));
    }
  });
  test('un encargado desactivado no puede atender alertas ni editar lotes', async () => {
    const db = como('encargadoInactivo');
    await assertFails(updateDoc(doc(db, 'alertas', 'existente-ph'), { atendida: true }));
    await assertFails(updateDoc(doc(db, 'lotes', LOTE), { observacion: 'x' }));
  });
});

describe('seguridad: lectura de datos ajenos', () => {
  test('el lechero no lista usuarios ni ve el perfil de otros', async () => {
    const db = como('lechero');
    await assertFails(getDocs(collection(db, 'usuarios')));
    await assertFails(getDoc(doc(db, 'usuarios', 'lechero2')));
  });
  test('el encargado no lee los perfiles de otras personas, pero si el suyo', async () => {
    const db = como('encargado');
    await assertFails(getDocs(collection(db, 'usuarios')));
    await assertFails(getDoc(doc(db, 'usuarios', 'lechero')));
    await assertSucceeds(getDoc(doc(db, 'usuarios', 'encargado')));
  });
  test('el administrador si lista usuarios', async () => {
    await assertSucceeds(getDocs(collection(como('admin'), 'usuarios')));
  });
  test('una coleccion que no existe en el diseno esta denegada para todos, incluido el administrador', async () => {
    const admin = como('admin');
    await assertFails(getDoc(doc(admin, 'secretos', 'clave')));
    await assertFails(setDoc(doc(admin, 'secretos', 'clave'), { valor: 1 }));
  });
});

describe('seguridad: suplantacion y datos manipulados', () => {
  /** Escribe una medicion valida con una alerta modificada y devuelve si fue aceptada. */
  const conAlerta = (uid, idMed, cambiosAlerta, cambiosMedicion = {}) => {
    const db = como(uid);
    const lote = writeBatch(db);
    lote.set(doc(db, 'mediciones', idMed), medicion({ uidLechero: uid, nombreLechero: uid, ...cambiosMedicion }));
    lote.set(doc(db, 'alertas', `${idMed}-ph`), alerta(idMed, { nombreLechero: uid, ...cambiosAlerta }));
    return lote.commit();
  };

  test('control: con las reglas endurecidas una medicion y su alerta legitimas siguen funcionando', async () => {
    await assertSucceeds(conAlerta('lechero', 'ctl-1', {}));
  });
  test('no se puede atribuir una medicion, un lote ni una alerta a otra persona (nombre falso)', async () => {
    const db = como('lechero');
    await assertFails(setDoc(doc(db, 'mediciones', 'n1'), medicion({ nombreLechero: 'admin' })));
    await assertFails(setDoc(doc(db, 'lotes', 'n2'), {
      estanque: 'X', fecha: Timestamp.now(), creadoPor: 'lechero', nombreCreador: 'encargado', observacion: '',
    }));
    await assertFails(conAlerta('lechero', 'n3', { nombreLechero: 'admin' }));
  });
  test('un lechero no crea alertas sobre la medicion de otra persona', async () => {
    await assertFails(setDoc(doc(como('lechero2'), 'alertas', 'existente-temperatura'),
      alerta('existente', { tipo: 'temperatura', nombreLechero: 'lechero', valor: 20 })));
  });
  test('una alerta existente no se puede sobrescribir ni reabrir', async () => {
    await assertFails(setDoc(doc(como('lechero'), 'alertas', 'existente-ph'), alerta('existente')));
    await updateDoc(doc(como('encargado'), 'alertas', 'existente-ph'), { atendida: true });
    await assertFails(updateDoc(doc(como('encargado'), 'alertas', 'existente-ph'), { atendida: false }));
    await assertFails(updateDoc(doc(como('encargado'), 'alertas', 'existente-ph'), { valor: 0 }));
  });
  test('alertas con lote falso, mensaje gigante o de tipo incorrecto se rechazan', async () => {
    await assertFails(conAlerta('lechero', 'a1', { idLote: 'otro-lote' }));
    await assertFails(conAlerta('lechero', 'a2', { mensaje: 'x'.repeat(201) }));
    await assertFails(conAlerta('lechero', 'a3', { mensaje: 12345 }));
    await assertFails(conAlerta('lechero', 'a4', { mensaje: '' }));
    await assertFails(conAlerta('lechero', 'a5', { valor: 99999 }));
    await assertFails(conAlerta('lechero', 'a6', { valor: '7.15' }));
  });
  test('mediciones con campos extra, resultado manipulado o datos enormes se rechazan', async () => {
    const db = como('lechero');
    await assertFails(setDoc(doc(db, 'mediciones', 'm1'), medicion({ aprobada: true })));
    await assertFails(setDoc(doc(db, 'mediciones', 'm2'), medicion({ resultado: { estado: 'verde', titulo: 'x', extra: 'y' } })));
    await assertFails(setDoc(doc(db, 'mediciones', 'm3'), medicion({ resultado: { estado: 'verde', titulo: 'x'.repeat(81) } })));
    await assertFails(setDoc(doc(db, 'mediciones', 'm4'), medicion({ resultado: 'verde' })));
    await assertFails(setDoc(doc(db, 'mediciones', 'm5'), medicion({ sensores: { temp: 3.8, ph: 6.7, cond: 4.8, turb: 85, extra: 1 } })));
    await assertFails(setDoc(doc(db, 'mediciones', 'm6'), medicion({ nombreLechero: 'x'.repeat(5000) })));
  });
  test('un lote no se edita cambiando quien lo creo ni su fecha', async () => {
    const db = como('encargado');
    await assertFails(updateDoc(doc(db, 'lotes', LOTE), { creadoPor: 'encargado' }));
    await assertFails(updateDoc(doc(db, 'lotes', LOTE), { nombreCreador: 'encargado' }));
    await assertFails(updateDoc(doc(db, 'lotes', LOTE), { fecha: Timestamp.now() }));
    await assertSucceeds(updateDoc(doc(db, 'lotes', LOTE), { estanque: 'Estanque 1 corregido' }));
  });
  test('un lechero no edita lotes (ni siquiera el suyo)', async () => {
    await assertFails(updateDoc(doc(como('lechero'), 'lotes', LOTE), { estanque: 'Otro' }));
  });
});

// ---------------------------------------------------------------- CRUD ampliado

describe('estanques (catalogo)', () => {
  const estanque = (cambios = {}) => ({ nombre: 'Estanque 2', activo: true, creadoPor: 'encargado', ...cambios });

  test('encargado y administrador crean estanques; el lechero no', async () => {
    await assertSucceeds(setDoc(doc(como('encargado'), 'estanques', 'est-2'), estanque()));
    await assertSucceeds(setDoc(doc(como('admin'), 'estanques', 'est-3'), estanque({ nombre: 'Estanque 3', creadoPor: 'admin' })));
    await assertFails(setDoc(doc(como('lechero'), 'estanques', 'est-4'), estanque({ creadoPor: 'lechero' })));
  });
  test('rechaza nombres vacios o muy largos, campos extra y creacion a nombre de otro', async () => {
    const db = como('encargado');
    await assertFails(setDoc(doc(db, 'estanques', 'a'), estanque({ nombre: '' })));
    await assertFails(setDoc(doc(db, 'estanques', 'b'), estanque({ nombre: 'x'.repeat(41) })));
    await assertFails(setDoc(doc(db, 'estanques', 'c'), estanque({ extra: 1 })));
    await assertFails(setDoc(doc(db, 'estanques', 'd'), estanque({ creadoPor: 'admin' })));
    await assertFails(setDoc(doc(db, 'estanques', 'e'), estanque({ activo: false })));
  });
  test('se edita el nombre y se desactiva (no se borra); no se cambia quien lo creo', async () => {
    const db = como('encargado');
    await assertSucceeds(updateDoc(doc(db, 'estanques', 'est-1'), { nombre: 'Estanque principal' }));
    await assertSucceeds(updateDoc(doc(db, 'estanques', 'est-1'), { activo: false }));
    await assertFails(updateDoc(doc(db, 'estanques', 'est-1'), { creadoPor: 'admin' }));
    await assertFails(updateDoc(doc(db, 'estanques', 'est-1'), { activo: 'no' }));
    await assertFails(updateDoc(doc(como('lechero'), 'estanques', 'est-1'), { activo: false }));
  });
  test('todos los usuarios activos leen el catalogo; los desactivados no', async () => {
    await assertSucceeds(getDoc(doc(como('lechero'), 'estanques', 'est-1')));
    await assertFails(getDoc(doc(como('inactivo'), 'estanques', 'est-1')));
  });
});

describe('cierre de lotes', () => {
  const cierre = (nombre = 'encargado', cambios = {}) => ({ cerrado: true, estadoPor: nombre, estadoEn: serverTimestamp(), ...cambios });

  test('encargado y administrador cierran y reabren un lote', async () => {
    await assertSucceeds(updateDoc(doc(como('encargado'), 'lotes', LOTE), cierre()));
    await assertSucceeds(updateDoc(doc(como('admin'), 'lotes', LOTE), cierre('admin', { cerrado: false })));
  });
  test('el lechero no cierra lotes', async () => {
    await assertFails(updateDoc(doc(como('lechero'), 'lotes', LOTE), cierre('lechero')));
  });
  test('el cierre exige quien lo hizo (con su nombre) y la hora del servidor', async () => {
    const db = como('encargado');
    await assertFails(updateDoc(doc(db, 'lotes', LOTE), { cerrado: true }));
    await assertFails(updateDoc(doc(db, 'lotes', LOTE), cierre('admin')));
    await assertFails(updateDoc(doc(db, 'lotes', LOTE), cierre('encargado', { estadoEn: Timestamp.now() })));
    await assertFails(updateDoc(doc(db, 'lotes', LOTE), cierre('encargado', { cerrado: 'si' })));
  });
  test('un lote cerrado no recibe mediciones y al reabrirlo vuelve a recibirlas', async () => {
    await assertSucceeds(updateDoc(doc(como('encargado'), 'lotes', LOTE), cierre()));
    await assertFails(setDoc(doc(como('lechero'), 'mediciones', 'cerrada-1'), medicion()));
    await assertSucceeds(updateDoc(doc(como('encargado'), 'lotes', LOTE), cierre('encargado', { cerrado: false })));
    await assertSucceeds(setDoc(doc(como('lechero'), 'mediciones', 'abierta-1'), medicion()));
  });
  test('cerrar un lote no permite cambiar quien lo creo ni su fecha', async () => {
    await assertFails(updateDoc(doc(como('encargado'), 'lotes', LOTE), cierre('encargado', { creadoPor: 'encargado' })));
    await assertFails(updateDoc(doc(como('encargado'), 'lotes', LOTE), cierre('encargado', { fecha: Timestamp.now() })));
  });
});

describe('liberar lote para venta', () => {
  const venta = (nombre = 'encargado', cambios = {}) => ({
    venta: 'apto', ventaNota: 'Revisado', ventaPor: nombre, ventaEn: serverTimestamp(), ...cambios,
  });

  test('encargado y administrador marcan el lote como apto o no apto; el lechero no', async () => {
    await assertSucceeds(updateDoc(doc(como('encargado'), 'lotes', LOTE), venta()));
    await assertSucceeds(updateDoc(doc(como('admin'), 'lotes', LOTE), venta('admin', { venta: 'no_apto' })));
    await assertFails(updateDoc(doc(como('lechero'), 'lotes', LOTE), venta('lechero')));
  });
  test('la misma persona puede volver a decidir (aunque su nombre no cambie)', async () => {
    const db = como('encargado');
    await assertSucceeds(updateDoc(doc(db, 'lotes', LOTE), venta()));
    await assertSucceeds(updateDoc(doc(db, 'lotes', LOTE), venta('encargado', { venta: 'no_apto', ventaNota: 'Se repitio la medicion' })));
    await assertSucceeds(updateDoc(doc(db, 'lotes', LOTE), venta('encargado', { venta: 'no_apto', ventaNota: 'Se repitio la medicion' })));
  });
  test('exige decision valida, nombre propio, hora del servidor y nota de hasta 200 caracteres', async () => {
    const db = como('encargado');
    await assertFails(updateDoc(doc(db, 'lotes', LOTE), venta('encargado', { venta: 'quizas' })));
    await assertFails(updateDoc(doc(db, 'lotes', LOTE), venta('admin')));
    await assertFails(updateDoc(doc(db, 'lotes', LOTE), venta('encargado', { ventaEn: Timestamp.now() })));
    await assertFails(updateDoc(doc(db, 'lotes', LOTE), venta('encargado', { ventaNota: 'x'.repeat(201) })));
    await assertFails(updateDoc(doc(db, 'lotes', LOTE), { venta: 'apto' }));
    await assertSucceeds(updateDoc(doc(db, 'lotes', LOTE), { venta: 'apto', ventaPor: 'encargado', ventaEn: serverTimestamp() }));
  });
  test('liberar un lote no cierra el lote ni cambia otros campos', async () => {
    await assertFails(updateDoc(doc(como('encargado'), 'lotes', LOTE), venta('encargado', { fecha: Timestamp.now() })));
    await assertFails(updateDoc(doc(como('encargado'), 'lotes', LOTE), venta('encargado', { creadoPor: 'encargado' })));
    await assertSucceeds(updateDoc(doc(como('encargado'), 'lotes', LOTE), venta()));
    await assertSucceeds(setDoc(doc(como('lechero'), 'mediciones', 'sigue-abierto'), medicion()));
  });
});

describe('anular mediciones (baja logica)', () => {
  const anular = (nombre = 'encargado', cambios = {}) => ({
    anulada: true, motivoAnulacion: 'Sensor fuera de la muestra', anuladaPor: nombre, anuladaEn: serverTimestamp(), ...cambios,
  });

  test('encargado y administrador anulan una medicion con motivo', async () => {
    await assertSucceeds(updateDoc(doc(como('encargado'), 'mediciones', 'existente'), anular()));
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'mediciones', 'otra'), medicion({ registradoEn: Timestamp.now() }));
    });
    await assertSucceeds(updateDoc(doc(como('admin'), 'mediciones', 'otra'), anular('admin')));
  });
  test('el lechero no anula, ni siquiera su propia medicion', async () => {
    await assertFails(updateDoc(doc(como('lechero'), 'mediciones', 'existente'), anular('lechero')));
  });
  test('exige motivo de al menos 3 caracteres, quien la anulo y la hora del servidor', async () => {
    const db = como('encargado');
    await assertFails(updateDoc(doc(db, 'mediciones', 'existente'), anular('encargado', { motivoAnulacion: 'ok' })));
    await assertFails(updateDoc(doc(db, 'mediciones', 'existente'), anular('encargado', { motivoAnulacion: 'x'.repeat(201) })));
    await assertFails(updateDoc(doc(db, 'mediciones', 'existente'), anular('admin')));
    await assertFails(updateDoc(doc(db, 'mediciones', 'existente'), anular('encargado', { anuladaEn: Timestamp.now() })));
    await assertFails(updateDoc(doc(db, 'mediciones', 'existente'), { anulada: true }));
  });
  test('anular no permite cambiar los datos medidos ni otros campos', async () => {
    const db = como('encargado');
    await assertFails(updateDoc(doc(db, 'mediciones', 'existente'), anular('encargado', { 'sensores.ph': 6.5 })));
    await assertFails(updateDoc(doc(db, 'mediciones', 'existente'), anular('encargado', { uidLechero: 'encargado' })));
    await assertFails(updateDoc(doc(db, 'mediciones', 'existente'), anular('encargado', { idLote: 'otro' })));
  });
  test('no se anula dos veces ni se deshace la anulacion', async () => {
    const db = como('encargado');
    await assertSucceeds(updateDoc(doc(db, 'mediciones', 'existente'), anular()));
    await assertFails(updateDoc(doc(db, 'mediciones', 'existente'), anular('encargado', { motivoAnulacion: 'Otro motivo' })));
    await assertFails(updateDoc(doc(db, 'mediciones', 'existente'), { anulada: false }));
  });
  test('no se crea una medicion ya anulada', async () => {
    await assertFails(setDoc(doc(como('lechero'), 'mediciones', 'nueva'), medicion({ anulada: true })));
  });
});

describe('nota y peticion de revision en la medicion', () => {
  const revisar = (nombre = 'encargado', cambios = {}) => ({
    revisionAtendida: true, revisionNota: 'Se enfrió y se volvió a medir', revisionPor: nombre, revisionEn: serverTimestamp(), ...cambios,
  });
  const pedida = async (id) => {
    await assertSucceeds(setDoc(doc(como('lechero'), 'mediciones', id), medicion({ nota: 'Vaca con mastitis', revision: true })));
  };

  test('el trabajador guarda una nota y pide revision al registrar', async () => {
    await pedida('con-revision');
    await assertSucceeds(setDoc(doc(como('lechero'), 'mediciones', 'solo-nota'), medicion({ nota: 'Se limpió el estanque' })));
  });
  test('la nota no puede superar 200 caracteres ni ser de otro tipo', async () => {
    await assertFails(setDoc(doc(como('lechero'), 'mediciones', 'larga'), medicion({ nota: 'x'.repeat(201) })));
    await assertFails(setDoc(doc(como('lechero'), 'mediciones', 'numero'), medicion({ nota: 12 })));
  });
  test('no se crea una medicion con la revision ya hecha ni con otros campos nuevos', async () => {
    await assertFails(setDoc(doc(como('lechero'), 'mediciones', 'x1'), medicion({ revision: false })));
    await assertFails(setDoc(doc(como('lechero'), 'mediciones', 'x2'), medicion({ revision: true, revisionAtendida: true })));
    await assertFails(setDoc(doc(como('lechero'), 'mediciones', 'x3'), medicion({ comentarioExtra: 'hola' })));
  });
  test('encargado y administrador marcan la revision con su nombre y la hora del servidor', async () => {
    await pedida('r1');
    await assertSucceeds(updateDoc(doc(como('encargado'), 'mediciones', 'r1'), revisar()));
    await pedida('r2');
    await assertSucceeds(updateDoc(doc(como('admin'), 'mediciones', 'r2'), revisar('admin', { revisionNota: deleteField() })));
  });
  test('el lechero no marca revisiones, ni las suyas', async () => {
    await pedida('r3');
    await assertFails(updateDoc(doc(como('lechero'), 'mediciones', 'r3'), revisar('lechero')));
  });
  test('exige firmar con su nombre, hora del servidor y no se hace dos veces', async () => {
    await pedida('r4');
    const db = como('encargado');
    await assertFails(updateDoc(doc(db, 'mediciones', 'r4'), revisar('admin')));
    await assertFails(updateDoc(doc(db, 'mediciones', 'r4'), revisar('encargado', { revisionEn: Timestamp.now() })));
    await assertFails(updateDoc(doc(db, 'mediciones', 'r4'), revisar('encargado', { revisionNota: 'x'.repeat(201) })));
    await assertSucceeds(updateDoc(doc(db, 'mediciones', 'r4'), revisar()));
    await assertFails(updateDoc(doc(db, 'mediciones', 'r4'), revisar('encargado', { revisionNota: 'otra' })));
  });
  test('solo se revisan las mediciones donde se pidio revision y no se toca nada mas', async () => {
    await assertFails(updateDoc(doc(como('encargado'), 'mediciones', 'existente'), revisar()));
    await pedida('r5');
    await assertFails(updateDoc(doc(como('encargado'), 'mediciones', 'r5'), revisar('encargado', { 'sensores.ph': 6.5 })));
  });
});

describe('alertas atendidas con nota', () => {
  const atender = (nombre = 'encargado', cambios = {}) => ({
    atendida: true, nota: 'Se revisó el estanque', atendidaPor: nombre, atendidaEn: serverTimestamp(), ...cambios,
  });

  test('el encargado atiende con nota, quien y cuando', async () => {
    await assertSucceeds(updateDoc(doc(como('encargado'), 'alertas', 'existente-ph'), atender()));
  });
  test('la nota es opcional', async () => {
    const { nota, ...sinNota } = atender();
    await assertSucceeds(updateDoc(doc(como('admin'), 'alertas', 'existente-ph'), { ...sinNota, atendidaPor: 'admin' }));
  });
  test('el lechero no atiende', async () => {
    await assertFails(updateDoc(doc(como('lechero'), 'alertas', 'existente-ph'), atender('lechero')));
  });
  test('rechaza notas gigantes, quien falso, hora falsa o falta de datos', async () => {
    const db = como('encargado');
    await assertFails(updateDoc(doc(db, 'alertas', 'existente-ph'), atender('encargado', { nota: 'x'.repeat(301) })));
    await assertFails(updateDoc(doc(db, 'alertas', 'existente-ph'), atender('admin')));
    await assertFails(updateDoc(doc(db, 'alertas', 'existente-ph'), atender('encargado', { atendidaEn: Timestamp.now() })));
    await assertFails(updateDoc(doc(db, 'alertas', 'existente-ph'), { atendida: true, nota: 'sin quien ni cuando' }));
    await assertFails(updateDoc(doc(db, 'alertas', 'existente-ph'), atender('encargado', { severidad: 'media' })));
  });
  test('despues de atendida se edita solo la nota; no se reabre', async () => {
    const db = como('encargado');
    await assertSucceeds(updateDoc(doc(db, 'alertas', 'existente-ph'), atender()));
    await assertSucceeds(updateDoc(doc(db, 'alertas', 'existente-ph'), { nota: 'Nota corregida' }));
    await assertFails(updateDoc(doc(db, 'alertas', 'existente-ph'), { nota: 'x'.repeat(301) }));
    await assertFails(updateDoc(doc(db, 'alertas', 'existente-ph'), { nota: 'ok', atendidaPor: 'admin' }));
    await assertFails(updateDoc(doc(db, 'alertas', 'existente-ph'), { atendida: false }));
    await assertFails(updateDoc(doc(como('lechero'), 'alertas', 'existente-ph'), { nota: 'x' }));
  });
  test('no se edita la nota de una alerta que aun no esta atendida', async () => {
    await assertFails(updateDoc(doc(como('encargado'), 'alertas', 'existente-ph'), { nota: 'antes de atender' }));
  });
});

describe('dispositivos y calibraciones (administrador)', () => {
  const calibracion = (cambios = {}) => ({
    idDispositivo: 'caja-01', fecha: serverTimestamp(), realizadaPor: 'admin', parametro: 'ph', nota: 'Buffer 7.0', ...cambios,
  });

  test('el administrador registra y edita dispositivos; el encargado no', async () => {
    const dispositivo = { nombre: 'Caja 2', ubicacion: 'Sala', estado: 'activo' };
    await assertSucceeds(setDoc(doc(como('admin'), 'dispositivos', 'caja-02'), dispositivo));
    await assertSucceeds(updateDoc(doc(como('admin'), 'dispositivos', 'caja-02'), { estado: 'inactivo' }));
    await assertFails(setDoc(doc(como('encargado'), 'dispositivos', 'caja-03'), dispositivo));
    await assertFails(setDoc(doc(como('admin'), 'dispositivos', 'caja-04'), { ...dispositivo, estado: 'roto' }));
  });
  test('una calibracion valida se guarda junto con la fecha en el dispositivo', async () => {
    const db = como('admin');
    const lote = writeBatch(db);
    lote.set(doc(db, 'calibraciones', 'cal-nueva'), calibracion());
    lote.update(doc(db, 'dispositivos', 'caja-01'), { ultimaCalibracion: serverTimestamp() });
    await assertSucceeds(lote.commit());
  });
  test('rechaza parametro desconocido, dispositivo inexistente, nombre o fecha falsos y campos extra', async () => {
    const db = como('admin');
    await assertFails(setDoc(doc(db, 'calibraciones', 'a'), calibracion({ parametro: 'sabor' })));
    await assertFails(setDoc(doc(db, 'calibraciones', 'b'), calibracion({ idDispositivo: 'no-existe' })));
    await assertFails(setDoc(doc(db, 'calibraciones', 'c'), calibracion({ realizadaPor: 'encargado' })));
    await assertFails(setDoc(doc(db, 'calibraciones', 'd'), calibracion({ fecha: Timestamp.now() })));
    await assertFails(setDoc(doc(db, 'calibraciones', 'e'), calibracion({ extra: 1 })));
    await assertFails(setDoc(doc(db, 'calibraciones', 'f'), calibracion({ nota: 'x'.repeat(201) })));
  });
  test('solo el administrador crea, lee y corrige la nota de una calibracion', async () => {
    await assertFails(setDoc(doc(como('encargado'), 'calibraciones', 'g'), calibracion({ realizadaPor: 'encargado' })));
    await assertFails(getDoc(doc(como('encargado'), 'calibraciones', 'cal-1')));
    await assertSucceeds(updateDoc(doc(como('admin'), 'calibraciones', 'cal-1'), { nota: 'Corregida' }));
    await assertFails(updateDoc(doc(como('admin'), 'calibraciones', 'cal-1'), { parametro: 'ph' }));
    await assertFails(updateDoc(doc(como('admin'), 'calibraciones', 'cal-1'), { idDispositivo: 'simulador' }));
  });
});

