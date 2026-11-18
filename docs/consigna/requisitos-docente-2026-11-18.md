# Requisitos verbales del docente (compartidos el 2026-11-18)

Transcripción de dos fotografías de la pantalla proyectada en clase. El estudiante informó que el docente dijo que esto tenía que quedar hecho para aprobar. La segunda foto es la versión más completa: agrega «+ Alta + Edición» en la primera línea.

**Esto no está en la consigna escrita** (`tp-integrador-facturacion-arca.md`), que además declara fuera de alcance la paginación y la integración fiscal real. Ante cualquier duda manda esta nota, por ser posterior y del docente, y debe confirmarse con él (ver preguntas abiertas de `knowledge-base/10_preguntas_abiertas.md`).

```
CRUD FACTURA VENTA + Alta + Edición
FRONT->HTML + TYPESCRIPT
BACK->SPRING
CONEXION ARCA/AFIP

    Filtros de Búsqueda
        Nro Factura, FechaDesde - FechaHasta, Cliente
        Si están o no informadas a ARCA
    Paginación la Grilla de Facturas
    Buscar Cliente y Buscar ListaPrecioArticulo deben
    ser interfaces explorer (ventanas modales)
    Una vez informada la factura a ARCA se debe bloquear
    su modificación, no se puede eliminar, solo Anular
    Imprimir PDF Factura con CAI y Fecha Vto ARCA
```

## Interpretación adoptada (supuestos, a confirmar con el docente)

- **Conexión ARCA/AFIP:** sigue simulada, como en la consigna escrita. Una conexión real requiere CUIT, certificado digital y credenciales de homologación.
- **«CAI»:** se entiende como el CAE que ya genera la aplicación. El CAI corresponde a comprobantes preimpresos.
- **«Informada a ARCA»:** una factura con CAE, es decir las emitidas y las anuladas. Un borrador no está informado.
- **Paginación:** en el servidor, con página, tamaño y total. Los reportes siguen exportando todo lo filtrado.
- **Explorador:** ventana modal accesible con búsqueda, para elegir un cliente y para elegir un renglón de lista de precios.
- **Tag a evaluar:** pendiente de definir con el docente.
