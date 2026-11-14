---
description: Compila y ejecuta las pruebas del proyecto, y revisa las reglas del hexágono
argument-hint: "[unit | all]  (por defecto: unit)"
allowed-tools: Bash(./mvnw:*), Bash(grep:*), Read
---

Verifica el estado del proyecto. Modo: `$ARGUMENTS` (si está vacío, usa `unit`).

1. Si falta `mvnw`, avísalo y detente: indica `mvn -N wrapper:wrapper`.
2. Modo `unit`: ejecuta `./mvnw test`. Modo `all`: ejecuta `./mvnw verify` (los `*IT` requieren Docker; si Docker no está disponible, dilo y ejecuta solo `test`).
3. Si hay fallos, muestra para cada uno la clase, el test y la causa en una línea. No arregles nada todavía.
4. Comprueba a mano las reglas del hexágono sobre `services/*/src/main/java`:
   - `domain` no importa nada fuera de `java.*` y su propio paquete.
   - `application` no importa `infrastructure`.
   - `adapter.in` no importa `adapter.out` ni `application.service`.
5. Resume en pocas líneas: qué corrió, qué pasó, qué falló y qué no pudiste ejecutar. No afirmes que algo funciona si no lo ejecutaste.
