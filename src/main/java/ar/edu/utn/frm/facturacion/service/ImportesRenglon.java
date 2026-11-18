package ar.edu.utn.frm.facturacion.service;

import java.math.BigDecimal;

/** Importes de un renglón ya redondeados a 2 decimales: neto, IVA y subtotal (neto + IVA). */
public record ImportesRenglon(BigDecimal neto, BigDecimal iva, BigDecimal subtotal) {}
