/*
 * Kalmi · Nodo único para Arduino Uno (prototipo de prueba)
 * Diseño: docs/superpowers/specs/2026-11-19-kalmi-uno-palabras-design.md
 *
 * Enlace con la app por Serial (pines 0/1, 9600):
 *   - Tinkercad: cable cruzado al Uno "Teléfono" (kalmi-telefono.ino), o el Monitor serie.
 *   - Uno real:  módulo BLE HM-10 en 0/1 (divisor de tensión en su RX; desconéctalo al subir el sketch).
 *
 * Protocolo (una línea por mensaje, CUERPO*XX con XX = XOR en hex):
 *   Kalmi → App: TEL,seq,luz,presion · EVT,seq,E1..E5 · PAL,seq,i[,j] · OK,id · CFG,...
 *   App → Kalmi: ACK,seq · CMD,id,ATN|MSG,k|RESP|BRI,v|ASK,n · CFG,PAL,i,texto · CFG,ID?
 *
 * Todo es no bloqueante (millis), sin delay().
 */
#include <LiquidCrystal.h>
#include <EEPROM.h>

// ---------- Pines (informe §4, más el botón Quiero en el pin 2) ----------
LiquidCrystal lcd(10, 11, A2, A3, A4, A5);
const byte PIN_LDR = A0, PIN_FSR = A1, PIN_LED = 9, PIN_MOTOR = 13;
// Bien, Triste, Frustrado, Muy enojado, Respirar, Ayuda, Quiero
const byte BOTON[7] = {4, 5, 6, 7, 8, 12, 2};
enum { B_BIEN, B_TRISTE, B_FRUSTRADO, B_ENOJADO, B_RESPIRAR, B_AYUDA, B_QUIERO };

// ---------- Ajustes (perillas de calibración: el hardware real nunca es ideal) ----------
const unsigned long TEL_CADA_MS = 2000;
const unsigned long REINTENTO_MS = 1500;   // EVT/PAL sin ACK se reenvía cada 1,5 s
const byte MAX_ENVIOS = 4;                 // envío original + 3 reintentos
const byte LUZ_NOCHE = 15, LUZ_DESPIERTA = 25;     // % de luz
const byte PRESION_SUAVE = 30, PRESION_FUERTE = 75; // % de presión en la zona de calma
const unsigned long PRESION_SOSTENIDA_MS = 3000;
const unsigned long RESPIRACION_MS = 60000, MEDIO_CICLO_MS = 4000; // 4 + 4 s
const unsigned long PALABRA_ELEGIDA_MS = 3000, SEGUNDA_PALABRA_MS = 5000;
// ponytail: seq se guarda cada 50 envíos (~100 s con TEL cada 2 s ≈ 860 escrituras/día; la EEPROM aguanta
// ~100 000 → meses de prototipo). Al reiniciar se salta 50 y la app ve ese salto como "perdidos" una vez.
// En el ESP32 final: NVS (con nivelación de desgaste).
const byte SEQ_GUARDAR_CADA = 50;

// ---------- EEPROM (diseño §5.5) ----------
const uint16_t MARCA = 0x4B31;             // si no coincide: valores por defecto
const int EE_MARCA = 0, EE_SEQ = 2, EE_ID = 6, EE_PALABRAS = 14, LARGO_PALABRA = 11;
const char NUCLEO[4][7] PROGMEM = {"Quiero", "No", "Mas", "Parar"};
const char PERSONALES_DEFECTO[6][7] PROGMEM = {"Agua", "Comer", "Bano", "Duele", "Abrazo", "Jugar"};
const char EMOCION[6][12] PROGMEM = {"Bien", "Triste", "Frustrado", "Muy enojado", "Pide ayuda", "Tension"};
const char CARA[6][6] PROGMEM = {"(^_^)", "(;_;)", "(>_<)", "(>o<)", "(o_o)", "(>_<)"};
const char MENSAJE[3][12] PROGMEM = {"Ya voy", "Te quiero", "Estoy aqui"};

// ---------- Estado ----------
enum Estado : byte { REPOSO, EMOCION_E, RESPIRA, PALABRAS, TEXTO, NOCHE, PREGUNTA };
Estado estado = REPOSO;
unsigned long desde = 0;          // cuándo empezó el estado actual
unsigned long duracion = 0;       // cuánto dura un estado temporal (TEXTO, EMOCION_E, PREGUNTA)
char linea1[17], linea2[17];      // lo que muestra el LCD
bool redibujar = true;

uint32_t seq = 0;
byte sinGuardar = 0;
int ultimoCmd = -1;               // ignora un CMD repetido por reintento
byte brillo = 60;
byte luz = 0, presion = 0;
unsigned long proximaTel = 0;
unsigned long motorHasta = 0, proximoPulso = 0;
byte pulsos = 0;
unsigned long proximoParpadeo = 0, presionFuerteDesde = 0;
bool ronroneando = false, faseInhala = false;
byte emocionActual = 0;

// Palabras (barrido con el botón Quiero)
byte palabraIdx = 0, elegidas[2], nElegidas = 0;
unsigned long ultimoToque = 0;
bool esperandoSegunda = false;

// Eventos sin ACK
struct Pendiente { uint32_t seq; char cuerpo[22]; byte envios; unsigned long ultimo; };
Pendiente pend[3];
byte nPend = 0;

// Botones con antirrebote
byte nivelBoton[7];
unsigned long cambioBoton[7];
bool flanco[7];

// Recepción
char rx[48];
byte nRx = 0;

// =====================================================================
void setup() {
  Serial.begin(9600);
  lcd.begin(16, 2);
  for (byte i = 0; i < 7; i++) { pinMode(BOTON[i], INPUT_PULLUP); nivelBoton[i] = HIGH; }
  pinMode(PIN_LED, OUTPUT);
  pinMode(PIN_MOTOR, OUTPUT);
  cargarEeprom();
  aplicarBrillo(brillo);
  irA(REPOSO);
}

void loop() {
  leerBotones();
  leerSensores();
  leerSerie();
  reintentarPendientes();
  enviarTelemetria();
  atenderBotones();
  actualizarEstado();
  actualizarMotor();
  dibujar();
}

// ---------------------------- Protocolo ------------------------------
void enviar(const char* cuerpo) {
  byte x = 0;
  for (const char* p = cuerpo; *p; p++) x ^= *p;
  Serial.print(cuerpo);
  Serial.print('*');
  if (x < 16) Serial.print('0');
  Serial.println(x, HEX);  // HEX en mayúsculas, termina en \r\n (la app hace trim)
}

uint32_t siguienteSeq() {
  seq++;
  if (++sinGuardar >= SEQ_GUARDAR_CADA) { EEPROM.put(EE_SEQ, seq); sinGuardar = 0; }
  return seq;
}

// Crea un EVT o PAL, lo envía y lo deja pendiente hasta recibir ACK.
void avisar(const char* tipo, const char* datos) {
  if (nPend == 3) quitarPendiente(pend[0].seq);  // cola llena: el más antiguo se descarta
  Pendiente& p = pend[nPend++];
  p.seq = siguienteSeq();
  snprintf(p.cuerpo, sizeof(p.cuerpo), "%s,%lu,%s", tipo, (unsigned long)p.seq, datos);
  enviar(p.cuerpo);
  p.envios = 1;
  p.ultimo = millis();
}

void quitarPendiente(uint32_t s) {
  for (byte i = 0; i < nPend; i++) {
    if (pend[i].seq == s) {
      for (byte j = i; j + 1 < nPend; j++) pend[j] = pend[j + 1];
      nPend--;
      return;
    }
  }
}

void reintentarPendientes() {
  for (byte i = 0; i < nPend; i++) {
    Pendiente& p = pend[i];
    if (millis() - p.ultimo < REINTENTO_MS) continue;
    if (p.envios >= MAX_ENVIOS) {
      // Nadie confirmó (lejos o interferencia): se le dice al niño, sin alarmar.
      quitarPendiente(p.seq);
      mostrarTexto("Busca a un", "adulto (o_o)", 4000);
      return;
    }
    enviar(p.cuerpo);
    p.envios++;
    p.ultimo = millis();
  }
}

void enviarTelemetria() {
  if (millis() < proximaTel) return;
  proximaTel = millis() + TEL_CADA_MS;
  char b[32];
  snprintf(b, sizeof(b), "TEL,%lu,%u,%u", (unsigned long)siguienteSeq(), luz, presion);
  enviar(b);
}

void leerSerie() {
  while (Serial.available()) {
    char c = Serial.read();
    if (c == '\n') { rx[nRx] = 0; procesar(rx); nRx = 0; }
    else if (c != '\r' && nRx < sizeof(rx) - 1) rx[nRx++] = c;
  }
}

// Verifica el checksum y deja la línea sin "*XX". Si no coincide, se descarta.
bool validar(char* l) {
  char* ast = strrchr(l, '*');
  if (!ast) return false;
  *ast = 0;
  byte x = 0;
  for (char* p = l; *p; p++) x ^= *p;
  return x == (byte)strtol(ast + 1, NULL, 16);
}

void procesar(char* l) {
  if (!validar(l)) return;
  char* c[5];
  byte n = 0;
  for (char* t = strtok(l, ","); t && n < 5; t = strtok(NULL, ",")) c[n++] = t;
  if (n == 0) return;

  if (!strcmp(c[0], "ACK") && n >= 2) {
    uint32_t s = strtoul(c[1], NULL, 10);
    quitarPendiente(s);
    if (estado == EMOCION_E || estado == TEXTO) { strcpy(linea2, "Aviso listo"); redibujar = true; }
  } else if (!strcmp(c[0], "CMD") && n >= 3) {
    int id = atoi(c[1]);
    char ok[12];
    snprintf(ok, sizeof(ok), "OK,%d", id);
    if (id != ultimoCmd) { ultimoCmd = id; ejecutar(c[2], n >= 4 ? atoi(c[3]) : 0); }
    enviar(ok);  // también a un repetido: así la app deja de reintentar
  } else if (!strcmp(c[0], "CFG") && n >= 2) {
    configurar(c, n);
  }
}

void ejecutar(const char* cmd, int arg) {
  if (!strcmp(cmd, "ATN")) {
    mostrarTexto("Ya viene", "alguien (^_^)", 4000);
    vibrar(300);
  } else if (!strcmp(cmd, "MSG") && arg >= 0 && arg <= 2) {
    char t[12];
    strcpy_P(t, MENSAJE[arg]);
    mostrarTexto(t, "     <3", 4000);
    vibrar(200);
  } else if (!strcmp(cmd, "RESP")) {
    iniciarRespiracion();
  } else if (!strcmp(cmd, "BRI")) {
    brillo = constrain(arg, 10, 100);
    aplicarBrillo(brillo);
  } else if (!strcmp(cmd, "ASK") && arg >= 1 && arg <= 6) {
    strcpy(linea1, "Te sientes asi?");
    strcpy_P(linea2, EMOCION[arg - 1]);
    irA(PREGUNTA);
    duracion = 10000;  // si no responde, vuelve a reposo sin insistir
  }
}

void configurar(char** c, byte n) {
  if (!strcmp(c[1], "ID?")) {
    char id[9], b[32];
    for (byte i = 0; i < 8; i++) id[i] = EEPROM.read(EE_ID + i);
    id[8] = 0;
    snprintf(b, sizeof(b), "CFG,ID,kalmi-uno-%s", id);
    enviar(b);
  } else if (!strcmp(c[1], "PAL") && n >= 4) {
    int i = atoi(c[2]);
    if (i >= 4 && i <= 9 && strlen(c[3]) > 0 && strlen(c[3]) <= 10) {
      guardarPalabra(i - 4, c[3]);
      enviar("CFG,PAL,OK");
    } else {
      enviar("CFG,PAL,ERR");
    }
  }
}

// ---------------------------- EEPROM ---------------------------------
void cargarEeprom() {
  uint16_t m;
  EEPROM.get(EE_MARCA, m);
  if (m != MARCA) {
    EEPROM.put(EE_MARCA, MARCA);
    uint32_t cero = 0;
    EEPROM.put(EE_SEQ, cero);
    randomSeed(analogRead(PIN_LDR) ^ analogRead(PIN_FSR) ^ micros());
    char id[9];
    snprintf(id, sizeof(id), "%04X", (unsigned)random(0x10000));
    for (byte i = 0; i < 8; i++) EEPROM.update(EE_ID + i, i < strlen(id) ? id[i] : 0);
    char t[7];
    for (byte i = 0; i < 6; i++) { strcpy_P(t, PERSONALES_DEFECTO[i]); guardarPalabra(i, t); }
  }
  // seq nunca vuelve atrás tras un reinicio: se salta lo que pudo quedar sin guardar.
  EEPROM.get(EE_SEQ, seq);
  seq += SEQ_GUARDAR_CADA;
  EEPROM.put(EE_SEQ, seq);
}

void guardarPalabra(byte i, const char* texto) {
  for (byte k = 0; k < LARGO_PALABRA; k++) {
    char ch = k < strlen(texto) ? texto[k] : 0;
    EEPROM.update(EE_PALABRAS + i * LARGO_PALABRA + k, ch);
  }
}

// idx 0–3: núcleo fijo; 4–9: personales (EEPROM)
void leerPalabra(byte idx, char* out) {
  if (idx < 4) { strcpy_P(out, NUCLEO[idx]); return; }
  for (byte k = 0; k < LARGO_PALABRA; k++) out[k] = EEPROM.read(EE_PALABRAS + (idx - 4) * LARGO_PALABRA + k);
  out[LARGO_PALABRA - 1] = 0;
}

// ---------------------------- Entradas -------------------------------
void leerBotones() {
  for (byte i = 0; i < 7; i++) {
    flanco[i] = false;
    byte v = digitalRead(BOTON[i]);
    if (v != nivelBoton[i] && millis() - cambioBoton[i] > 30) {
      cambioBoton[i] = millis();
      nivelBoton[i] = v;
      if (v == LOW) flanco[i] = true;  // pull-up: presionado = LOW
    }
  }
}

void leerSensores() {
  luz = map(analogRead(PIN_LDR), 0, 1023, 0, 100);
  presion = map(analogRead(PIN_FSR), 0, 1023, 0, 100);
}

void atenderBotones() {
  // Ayuda funciona siempre, en cualquier estado (principio "Ayuda a un toque").
  if (flanco[B_AYUDA]) {
    avisar("EVT", "E5");
    mostrarTexto("Pedi ayuda", "Respira conmigo", 2500);
    emocionActual = 5;
    return;
  }
  if (estado == NOCHE) {
    for (byte i = 0; i < 7; i++) if (flanco[i]) { irA(REPOSO); return; }  // cualquier botón despierta
  }
  for (byte e = 0; e < 4; e++) {
    if (flanco[e]) {
      char datos[4];
      snprintf(datos, sizeof(datos), "E%u", e + 1);
      avisar("EVT", datos);
      emocionActual = e + 1;
      strcpy_P(linea1, CARA[e]);
      strcat(linea1, " ");
      char nombre[12];
      strcpy_P(nombre, EMOCION[e]);
      strncat(linea1, nombre, 16 - strlen(linea1));
      strcpy(linea2, "Avisando...");
      irA(EMOCION_E);
      duracion = 2500;
      return;
    }
  }
  if (flanco[B_RESPIRAR]) { iniciarRespiracion(); return; }  // solo para el niño: no avisa
  if (flanco[B_QUIERO]) tocarQuiero();
}

// ---------------------------- Palabras -------------------------------
void tocarQuiero() {
  ultimoToque = millis();
  if (estado != PALABRAS) {
    irA(PALABRAS);
    nElegidas = 0;
    palabraIdx = 0;
    esperandoSegunda = false;
  } else if (esperandoSegunda) {
    esperandoSegunda = false;  // empieza a elegir la segunda desde el principio
    palabraIdx = 0;
  } else {
    palabraIdx = (palabraIdx + 1) % 10;
  }
  mostrarPalabra();
}

void mostrarPalabra() {
  char w[LARGO_PALABRA];
  leerPalabra(palabraIdx, w);
  snprintf(linea1, sizeof(linea1), "%-11s%2u/10", w, palabraIdx + 1);
  strcpy(linea2, nElegidas ? "+ otra palabra" : "Toca: siguiente");
  redibujar = true;
}

void actualizarPalabras() {
  unsigned long quieto = millis() - ultimoToque;
  if (!esperandoSegunda && quieto >= PALABRA_ELEGIDA_MS) {
    elegidas[nElegidas++] = palabraIdx;
    if (nElegidas == 2) { enviarPalabras(); return; }
    esperandoSegunda = true;
    char w[LARGO_PALABRA];
    leerPalabra(palabraIdx, w);
    snprintf(linea1, sizeof(linea1), "Elegi: %s", w);
    strcpy(linea2, "Otra? toca 5s");
    redibujar = true;
  } else if (esperandoSegunda && quieto >= PALABRA_ELEGIDA_MS + SEGUNDA_PALABRA_MS) {
    enviarPalabras();
  }
}

void enviarPalabras() {
  char datos[8];
  if (nElegidas == 2) snprintf(datos, sizeof(datos), "%u,%u", elegidas[0], elegidas[1]);
  else snprintf(datos, sizeof(datos), "%u", elegidas[0]);
  avisar("PAL", datos);
  mostrarTexto("(^_^) Te escuche", "Avisando...", 3000);
}

// ---------------------------- Estados --------------------------------
void irA(Estado e) {
  estado = e;
  desde = millis();
  redibujar = true;
  if (e == REPOSO) {
    strcpy(linea1, "(^_^)  Kalmi");
    strcpy(linea2, "");
    aplicarBrillo(brillo);
    proximoParpadeo = millis() + random(4000, 8000);
  } else if (e == NOCHE) {
    strcpy(linea1, "(-_-)  zzz");
    strcpy(linea2, "");
    aplicarBrillo(10);
  }
}

void mostrarTexto(const char* l1, const char* l2, unsigned long ms) {
  strncpy(linea1, l1, 16); linea1[16] = 0;
  strncpy(linea2, l2, 16); linea2[16] = 0;
  irA(TEXTO);
  duracion = ms;
}

void iniciarRespiracion() {
  irA(RESPIRA);
  faseInhala = false;
}

void actualizarEstado() {
  unsigned long t = millis() - desde;
  switch (estado) {
    case REPOSO:
      if (luz < LUZ_NOCHE) { irA(NOCHE); break; }
      mascotaViva();
      break;
    case NOCHE:
      if (luz > LUZ_DESPIERTA) irA(REPOSO);
      break;
    case EMOCION_E:
      // Zona amarilla o roja: invita a respirar a los 2,5 s. Verde o azul: vuelve a reposo.
      if (t >= duracion) { if (emocionActual >= 3) iniciarRespiracion(); else irA(REPOSO); }
      break;
    case TEXTO:
      if (t >= duracion) { if (emocionActual == 5) { emocionActual = 0; iniciarRespiracion(); } else irA(REPOSO); }
      break;
    case PREGUNTA:
      if (t >= duracion) irA(REPOSO);
      break;
    case PALABRAS:
      actualizarPalabras();
      break;
    case RESPIRA:
      respirar(t);
      break;
  }
}

// Respiración guiada 4 + 4 s durante 1 minuto: la pantalla y el LED "se inflan"; vibra al inicio de cada inhalación.
void respirar(unsigned long t) {
  if (t >= RESPIRACION_MS) { emocionActual = 0; irA(REPOSO); return; }
  bool inhala = ((t / MEDIO_CICLO_MS) % 2) == 0;
  if (inhala && !faseInhala) vibrar(150);
  if (inhala != faseInhala) {
    faseInhala = inhala;
    strcpy(linea1, inhala ? "(o.o)  Inhala" : "(-.-)  Exhala");
    redibujar = true;
  }
  float f = (t % MEDIO_CICLO_MS) / (float)MEDIO_CICLO_MS;
  aplicarBrillo(10 + (brillo - 10) * (inhala ? f : 1 - f));
  static unsigned long ultimoSeg = 0;
  unsigned long resta = (RESPIRACION_MS - t) / 1000;
  if (resta != ultimoSeg) { ultimoSeg = resta; snprintf(linea2, sizeof(linea2), "       %2lu s", resta); redibujar = true; }
}

// Reposo "vivo": parpadea, ronronea con presión suave, invita a respirar con presión fuerte sostenida.
void mascotaViva() {
  if (millis() >= proximoParpadeo) {
    strcpy(linea1, "(-_-)  Kalmi");
    redibujar = true;
    if (millis() >= proximoParpadeo + 150) {
      strcpy(linea1, "(^_^)  Kalmi");
      proximoParpadeo = millis() + random(4000, 8000);
    }
  }
  if (presion >= PRESION_FUERTE) {
    if (!presionFuerteDesde) presionFuerteDesde = millis();
    if (millis() - presionFuerteDesde >= PRESION_SOSTENIDA_MS) { presionFuerteDesde = 0; iniciarRespiracion(); }
  } else {
    presionFuerteDesde = 0;
    if (presion >= PRESION_SUAVE && !ronroneando) {
      ronroneando = true;
      strcpy(linea1, "(^o^)  Prrr...");
      redibujar = true;
      pulsos = 3;
    } else if (presion < PRESION_SUAVE - 10 && ronroneando) {
      ronroneando = false;
      strcpy(linea1, "(^_^)  Kalmi");
      redibujar = true;
    }
  }
}

// ---------------------------- Salidas --------------------------------
void vibrar(unsigned int ms) { motorHasta = millis() + ms; }

void actualizarMotor() {
  if (pulsos && millis() >= proximoPulso) {  // ronroneo: pulsos cortos y suaves
    vibrar(80);
    pulsos--;
    proximoPulso = millis() + 250;
  }
  digitalWrite(PIN_MOTOR, millis() < motorHasta ? HIGH : LOW);
}

void aplicarBrillo(byte pct) { analogWrite(PIN_LED, map(pct, 0, 100, 0, 255)); }

void dibujar() {
  if (!redibujar) return;
  redibujar = false;
  lcd.setCursor(0, 0);
  lcd.print(linea1);
  for (byte i = strlen(linea1); i < 16; i++) lcd.print(' ');
  lcd.setCursor(0, 1);
  lcd.print(linea2);
  for (byte i = strlen(linea2); i < 16; i++) lcd.print(' ');
}
