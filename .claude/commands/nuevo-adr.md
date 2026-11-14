---
description: Crea un ADR nuevo con la plantilla del proyecto y actualiza el índice
argument-hint: "<título de la decisión>"
allowed-tools: Read, Write, Edit, Glob
---

Crea un ADR para esta decisión: **$ARGUMENTS**

1. Lista `docs/adr/` y toma el siguiente número libre (4 dígitos). El archivo se llama `NNNN-titulo-en-kebab-case.md`.
2. Lee `docs/adr/README.md` para respetar la plantilla y el índice, y un ADR existente (por ejemplo el 0003) para igualar el tono y la profundidad.
3. Redacta, en español, con estas secciones: Estado, Fecha (hoy), Implementación, Contexto, Decisión, Alternativas consideradas y Consecuencias (con ventajas y costos reales).
4. Si la decisión reemplaza a otra, **no edites el ADR anterior**: solo cambia su campo Estado a "Reemplazada por ADR-NNNN".
5. Agrega la fila al índice de `docs/adr/README.md` con su estado de implementación (Implementada o Pendiente).
6. Si te falta información para el contexto o las alternativas, pregúntala en vez de inventarla.
