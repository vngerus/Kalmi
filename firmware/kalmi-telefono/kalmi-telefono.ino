/*
 * Kalmi · "Teléfono" para Tinkercad (Arduino Uno)
 * Hace el papel de la app Android para demostrar la conexión sin hardware real.
 * Habla EXACTAMENTE el mismo protocolo que la app; en la vida real lo reemplaza el teléfono.
 * Diseño: docs/superpowers/specs/2026-11-19-kalmi-uno-palabras-design.md §5.8
 *
 * Enlace: Serial (pines 0/1, 9600) cruzado con el Uno "Kalmi". El Monitor serie de cada placa
 * muestra lo que esa placa envía.
 *
 * Lógica copiada de la app (KalmiRepository + ReglasInterpretador):
 *   ACK siempre (también a duplicados), deduplicación por seq, conteo de perdidos y descartados,
 *   tensión (3 lecturas ≥ 75 %, se rearma bajo 60 %), CMD con 2 reintentos de 1 s y RTT,
 *   interpretación de palabras con las mismas plantillas, ¿Acertó? Sí/No.
 */
#include <LiquidCrystal.h>

LiquidCrystal lcd(10, 11, A2, A3, A4, A5);
const byte B_ATN = 4, B_MSG = 5, B_RESP = 6, B_SI = 7, B_NO = 8, PIN_POT = A0, PIN_MOTOR = 9;
const byte BOTON[5] = {B_ATN, B_MSG, B_RESP, B_SI, B_NO};

const unsigned long CMD_ESPERA_MS = 1000;
const byte CMD_MAX_ENVIOS = 3;  // envío + 2 reintentos
const byte TENSION_ALTA = 75, TENSION_REARME = 60;

const char NUCLEO[4][7] PROGMEM = {"Quiero", "No", "Mas", "Parar"};
// La lista personal del "cuidador": se envía a Kalmi al iniciar (CFG,PAL).
const char PERSONALES[6][7] PROGMEM = {"Agua", "Comer", "Bano", "Duele", "Abrazo", "Jugar"};
const char EMOCION[6][13] PROGMEM = {"Bien", "Triste", "Frustrado", "Muy enojado", "Pide ayuda", "Tension alta"};
const char MENSAJE[3][11] PROGMEM = {"Ya voy", "Te quiero", "Estoy aqui"};

// ---------- Calidad del enlace ----------
uint32_t ultimoSeq = 0;
unsigned int recibidos = 0, perdidos = 0, descartados = 0;
uint32_t vistos[8];  // últimos eventos mostrados (deduplicación de reintentos)
byte nVistos = 0;
byte lecturasAltas = 0;

// ---------- Comandos ----------
int siguienteId = 1;
struct { int id; char cuerpo[20]; byte envios; unsigned long ultimo; bool activo; } cmd;
byte msgIdx = 0, ultimoBrillo = 60;
unsigned long proximoPot = 0;
unsigned int aciertos = 0, fallos = 0;

// ---------- Pantalla ----------
char linea1[17] = "Kalmi Telefono", linea2[17] = "Esperando...";
bool redibujar = true, alertaPalabras = false;
unsigned long motorHasta = 0, configurarEn = 0;

byte nivel[5];
unsigned long cambio[5];
char rx[48];
byte nRx = 0;

void setup() {
  Serial.begin(9600);
  lcd.begin(16, 2);
  for (byte i = 0; i < 5; i++) { pinMode(BOTON[i], INPUT_PULLUP); nivel[i] = HIGH; }
  pinMode(PIN_MOTOR, OUTPUT);
  configurarEn = millis() + 1500;  // da tiempo a que Kalmi arranque
}

void loop() {
  if (configurarEn && millis() >= configurarEn) { configurarEn = 0; enviarPalabras(); }
  leerSerie();
  revisarComando();
  leerBotones();
  leerPotenciometro();
  digitalWrite(PIN_MOTOR, millis() < motorHasta ? HIGH : LOW);
  dibujar();
}

// ---------------------------- Protocolo ------------------------------
void enviar(const char* cuerpo) {
  byte x = 0;
  for (const char* p = cuerpo; *p; p++) x ^= *p;
  Serial.print(cuerpo);
  Serial.print('*');
  if (x < 16) Serial.print('0');
  Serial.println(x, HEX);
}

void enviarPalabras() {
  char t[7], b[24];
  for (byte i = 0; i < 6; i++) {
    strcpy_P(t, PERSONALES[i]);
    snprintf(b, sizeof(b), "CFG,PAL,%u,%s", i + 4, t);
    enviar(b);
  }
}

void leerSerie() {
  while (Serial.available()) {
    char c = Serial.read();
    if (c == '\n') { rx[nRx] = 0; procesar(rx); nRx = 0; }
    else if (c != '\r' && nRx < sizeof(rx) - 1) rx[nRx++] = c;
  }
}

bool validar(char* l) {
  char* ast = strrchr(l, '*');
  if (!ast) return false;
  *ast = 0;
  byte x = 0;
  for (char* p = l; *p; p++) x ^= *p;
  return x == (byte)strtol(ast + 1, NULL, 16);
}

// Igual que KalmiRepository.registrarSeq: cuenta huecos como perdidos. false = ya visto.
bool registrarSeq(uint32_t s) {
  if (s <= ultimoSeq) return false;
  if (ultimoSeq > 0 && s > ultimoSeq + 1) perdidos += s - ultimoSeq - 1;
  ultimoSeq = s;
  recibidos++;
  return true;
}

bool yaVisto(uint32_t s) {
  static byte pos = 0;  // anillo: guarda los últimos 8
  for (byte i = 0; i < nVistos; i++) if (vistos[i] == s) return true;
  vistos[pos] = s;
  pos = (pos + 1) % 8;
  if (nVistos < 8) nVistos++;
  return false;
}

void procesar(char* l) {
  if (strlen(l) == 0) return;
  if (!validar(l)) { descartados++; return; }
  char* c[5];
  byte n = 0;
  for (char* t = strtok(l, ","); t && n < 5; t = strtok(NULL, ",")) c[n++] = t;
  if (n == 0) return;

  if (!strcmp(c[0], "TEL") && n >= 4) {
    registrarSeq(strtoul(c[1], NULL, 10));
    byte pres = atoi(c[3]);
    if (pres >= TENSION_ALTA) {
      if (++lecturasAltas == 3) alerta(6, NULL);
    } else if (pres < TENSION_REARME) {
      lecturasAltas = 0;
    }
    if (!cmd.activo && (linea2[0] == 'L' || linea2[0] == 'E')) estadoEnlace();  // no tapa alertas ni respuestas
  } else if ((!strcmp(c[0], "EVT") || !strcmp(c[0], "PAL")) && n >= 3) {
    uint32_t s = strtoul(c[1], NULL, 10);
    char ack[16];
    snprintf(ack, sizeof(ack), "ACK,%lu", (unsigned long)s);
    enviar(ack);  // siempre: así Kalmi deja de reintentar
    registrarSeq(s);
    if (yaVisto(s)) return;
    if (c[0][0] == 'E') alerta(atoi(c[2] + 1), NULL);
    else alertaDePalabras(c + 2, n - 2);
  } else if (!strcmp(c[0], "OK") && n >= 2) {
    if (cmd.activo && atoi(c[1]) == cmd.id) {
      cmd.activo = false;
      snprintf(linea2, sizeof(linea2), "OK %lu ms", millis() - cmd.ultimo);  // RTT del último intento
      redibujar = true;
    }
  }
}

// ---------------------------- Alertas --------------------------------
void alerta(byte codigo, const char* texto) {
  if (codigo < 1 || codigo > 6) return;
  if (texto) strncpy(linea1, texto, 16);
  else strcpy_P(linea1, EMOCION[codigo - 1]);
  linea1[16] = 0;
  strcpy(linea2, "1:Atendido");
  alertaPalabras = false;
  motorHasta = millis() + 300;
  redibujar = true;
}

void palabra(byte idx, char* out) {
  if (idx < 4) strcpy_P(out, NUCLEO[idx]);
  else strcpy_P(out, PERSONALES[idx - 4]);
}

void minusculas(char* s) { for (; *s; s++) *s = tolower(*s); }

// Mismas plantillas que ReglasInterpretador (app), en versión compacta para 16 columnas.
void alertaDePalabras(char** idx, byte n) {
  char a[7] = "", b[7] = "", t[17];
  palabra(atoi(idx[0]) % 10, a);
  if (n >= 2) palabra(atoi(idx[1]) % 10, b);
  minusculas(b);
  if (!strcmp(a, "Duele") || !strcmp(b, "duele")) strcpy(t, "Le duele algo");
  else if (!strcmp(a, "Quiero") && b[0]) snprintf(t, sizeof(t), "Quiere %s", b);
  else if (!strcmp(a, "No") && b[0]) snprintf(t, sizeof(t), "No quiere %s", b);
  else if (!strcmp(a, "Mas") && b[0]) snprintf(t, sizeof(t), "Quiere mas %s", b);
  else if (!strcmp(a, "Parar")) strcpy(t, "Quiere parar");
  else { minusculas(a); snprintf(t, sizeof(t), b[0] ? "Dice:%s+%s" : "Dice: %s", a, b); }
  alerta(5, t);  // código 5 solo para pasar el control; el texto manda
  strcpy(linea2, "Acerto? 4Si 5No");
  alertaPalabras = true;
}

// ---------------------------- Comandos -------------------------------
void enviarCmd(const char* resto) {
  if (cmd.activo) return;  // uno a la vez: la pantalla muestra "Enviando..."
  cmd.id = siguienteId++;
  snprintf(cmd.cuerpo, sizeof(cmd.cuerpo), "CMD,%d,%s", cmd.id, resto);
  enviar(cmd.cuerpo);
  cmd.envios = 1;
  cmd.ultimo = millis();
  cmd.activo = true;
  strcpy(linea2, "Enviando...");
  redibujar = true;
}

void revisarComando() {
  if (!cmd.activo || millis() - cmd.ultimo < CMD_ESPERA_MS) return;
  if (cmd.envios >= CMD_MAX_ENVIOS) {
    cmd.activo = false;
    strcpy(linea2, "Sin confirmacion");
    redibujar = true;
    return;
  }
  enviar(cmd.cuerpo);  // mismo id: Kalmi no lo ejecuta dos veces
  cmd.envios++;
  cmd.ultimo = millis();
}

// ---------------------------- Entradas -------------------------------
void leerBotones() {
  for (byte i = 0; i < 5; i++) {
    byte v = digitalRead(BOTON[i]);
    if (v == nivel[i] || millis() - cambio[i] < 30) continue;
    cambio[i] = millis();
    nivel[i] = v;
    if (v != LOW) continue;  // solo al presionar
    switch (BOTON[i]) {
      case B_ATN:
        strcpy(linea1, "Todo tranquilo");
        lecturasAltas = 0;
        alertaPalabras = false;
        enviarCmd("ATN");
        break;
      case B_MSG: {
        char m[20], t[11];
        snprintf(m, sizeof(m), "MSG,%u", msgIdx);
        strcpy_P(t, MENSAJE[msgIdx]);
        snprintf(linea1, sizeof(linea1), "> %s", t);
        enviarCmd(m);
        msgIdx = (msgIdx + 1) % 3;  // cada toque envía el siguiente mensaje
        break;
      }
      case B_RESP:
        strcpy(linea1, "> Respirar");
        enviarCmd("RESP");
        break;
      case B_SI:
      case B_NO:
        if (!alertaPalabras) break;
        if (BOTON[i] == B_SI) aciertos++; else fallos++;
        snprintf(linea2, sizeof(linea2), "Acerto %u/%u", aciertos, aciertos + fallos);
        alertaPalabras = false;
        redibujar = true;
        break;
    }
  }
}

// Brillo: solo si cambió ≥ 5 y como máximo cada 300 ms (guía §15.3).
void leerPotenciometro() {
  if (millis() < proximoPot) return;
  proximoPot = millis() + 300;
  byte v = map(analogRead(PIN_POT), 0, 1023, 10, 100);
  if (abs((int)v - (int)ultimoBrillo) < 5 || cmd.activo) return;
  ultimoBrillo = v;
  char b[12];
  snprintf(b, sizeof(b), "BRI,%u", v);
  enviarCmd(b);
}

// ---------------------------- Pantalla -------------------------------
// "L=recibidos P=perdidos D=descartados": la evidencia del diagnóstico (criterio 1.1.4.5).
void estadoEnlace() {
  snprintf(linea2, sizeof(linea2), "L%u P%u D%u", recibidos, perdidos, descartados);
  redibujar = true;
}

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
