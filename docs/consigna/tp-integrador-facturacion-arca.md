
Trabajo práctico integrador de facturación con Spring Boot
1 Presentación
Desarrollen una aplicación de facturación desde el modelo persistente hasta una API REST consumida por un frontend en TypeScript. El trabajo se organiza en cinco etapas evaluadas sobre un mismo repositorio. Cada etapa amplía lo construido y exige conservar el funcionamiento anterior.
La aplicación reproduce un circuito académico: crear un borrador, editarlo, emitirlo con autorización simulada y, cuando corresponda, anularlo. No se conecta con los servicios fiscales de ARCA. El CAE generado es ficticio y las transiciones describen las reglas de esta aplicación. El identificador de usuario se utiliza para registrar auditoría; no implementa autenticación ni autorización.
Aspecto
Organización
Institución
Universidad Nacional Tecnologica
Modalidad
Individual; un repositorio Git personal por estudiante
Duración
11 semanas: 10 de desarrollo y una de defensa final
Backend
Java 17, Spring Boot 3.3.5, Gradle 8.10.2, JPA/Hibernate, Lombok, H2 y perfil PostgreSQL
Reportes
OpenPDF 2.0.3, Apache POI 5.3.0 y exportación TXT UTF-8
Frontend
HTML, CSS y TypeScript 5.6, sin framework; servidor separado
Evaluación
Promedio ponderado de las etapas: 70 % de la nota; defensa individual: 30 %

Un solo proyecto permite observar cómo las decisiones del modelo repercuten en las consultas, los contratos HTTP y la interfaz. Una cascada incorrecta puede impedir guardar una factura; un DTO incompleto puede ocultar la moneda; una actualización concurrente puede invalidar una regla de estado. La evaluación considera tanto el resultado como la capacidad de explicar estas relaciones.
El alcance obligatorio incluye facturas, consulta de catálogos, reportes y auditoría. No incluye inicio de sesión, administración de catálogos, stock, cobros, conversión de monedas, notas de crédito, paginación ni integración fiscal. La presencia de campos relacionados con cobros o resultados fiscales en el modelo no implica que existan esos procesos de negocio.
Entreguen el código de cada etapa con su tag, un README con instrucciones reproducibles y un informe breve de decisiones y verificaciones. Los materiales o kits de nivelación que publique la cátedra podrán utilizarse conforme a la metodología indicada más adelante; su uso debe quedar declarado.
2 Objetivos de aprendizaje
Etapa
Al finalizar, cada estudiante debe poder…
1. Persistencia
Mapear herencia mediante superclases, asociaciones y agregados; explicar quién posee cada clave foránea; persistir una factura con sus detalles y comprobar cascada, unicidad y eliminación de huérfanos.
2. Lombok, DTO y reportes
Aplicar Lombok sin recursión entre asociaciones; distinguir entidades de DTO; construir una proyección JPQL y exportarla a PDF, Excel y TXT con reglas de moneda y estado.
3. Lectura REST
Separar controlador, servicio y repositorio; implementar consultas con filtros opcionales; devolver DTO y descargas con códigos, tipos de contenido y errores coherentes.
4. Escritura y reglas
Validar requests, calcular importes en el servidor, conservar atomicidad, controlar transiciones y concurrencia, registrar auditoría y diferenciar errores 400, 404 y 409.
5. Frontend
Consumir la API con TypeScript; gestionar formularios y edición; presentar mensajes accesibles y datos como texto; evitar dobles envíos y respuestas obsoletas.

Transversalmente, deberán interpretar errores del framework, escribir pruebas que distingan un comportamiento correcto de uno defectuoso, documentar decisiones y gestionar su trabajo mediante Git. Cada estudiante debe poder explicar la totalidad del código que entrega.
3 El dominio y el modelo
Una factura pertenece a un punto de venta y tiene condición de IVA y moneda. Puede identificar un cliente; si no lo identifica, se presenta como «Consumidor Final». Sus renglones se vinculan con un artículo de una lista de precios. Por eso el request de un renglón referencia listaPrecioArticuloId: el precio se obtiene de ese registro y no se acepta como un valor decidido por el navegador.
El modelo comprende 14 entidades y 2 superclases mapeadas. EntityId aporta un identificador Long generado con IDENTITY. AuditoriaApp incorpora fechas y usuarios de alta, modificación y baja. Ambas usan @MappedSuperclass y no generan tablas propias. EstadoFactura es un enum y UsuarioActual es un auxiliar de contexto; no son entidades.

Figura 1. Jerarquía del modelo: catorce entidades y dos superclases de mapeo.
Entidad o grupo
Herencia
Responsabilidad y relaciones principales
Usuario
EntityId
Identificador de auditoría, clave, nombre y apellido. Su existencia no implementa un login.
FacturaVenta
AuditoriaApp
Cabecera, estado, importes y datos de autorización simulada; cliente opcional; punto de venta, condición IVA y moneda obligatorios. Contiene sus detalles.
FacturaVentaDetalle
EntityId
Cantidad, descripción, precio, bonificación, neto, IVA y subtotal; referencias obligatorias a factura y artículo de lista de precios.
Cliente
AuditoriaApp
CUIT/CUIL y denominación; posee contacto y domicilio mediante relaciones uno a uno obligatorias.
Contacto y Domicilio
EntityId
Datos dependientes del cliente, persistidos y eliminados con él.
PuntoVenta
AuditoriaApp
Número único y datos descriptivos; delimita la numeración de comprobantes.
CondicionIva y TipoMoneda
AuditoriaApp
Catálogos identificados por código y denominación; la moneda también tiene símbolo.
Articulo
AuditoriaApp
Código, denominación y alícuota de IVA opcional; marca y rubro opcionales.
ListaPrecio
AuditoriaApp
Código y denominación de la lista.
ListaPrecioArticulo
AuditoriaApp
Relaciona una lista con un artículo y su precio; la combinación lista/artículo es única.
Marca y Rubro
AuditoriaApp
Catálogos de clasificación con código y denominación.


Figura 2. Factura y detalles como agregado.

Figura 3. Relaciones con catálogos y composición de Cliente.
FacturaVenta es la raíz de su agregado. Sus detalles se guardan y eliminan mediante la cabecera, con cascade = ALL y orphanRemoval = true. La relación utiliza mappedBy = "factura": el detalle contiene la clave foránea. Un request válido de creación o edición incluye al menos un renglón; mientras se construye un objeto JPA en memoria, su colección puede estar temporalmente vacía.
Cliente es la raíz de otro agregado: contacto y domicilio dependen de él. Los catálogos referenciados por una factura tienen existencia independiente. Eliminar un borrador no debe eliminar un artículo, una moneda ni un cliente.
Término
Significado en esta aplicación
Borrador
Factura editable y eliminable, todavía sin número ni CAE.
Emitida
Factura con número asignado y CAE simulado. Se consulta o anula; no se modifica ni elimina.
Anulada
Factura conservada para consulta, con fecha de anulación y baja lógica de auditoría.
CAE simulado
Cadena de 14 dígitos generada localmente al emitir, sin validez fiscal.
Consumidor Final
Representación de una factura cuyo cliente es nulo.
Bonificación
Descuento porcentual aplicado al renglón antes del IVA.
Neto, IVA y subtotal
Componentes calculados por el backend. El subtotal suma neto e IVA redondeados; la factura suma los subtotales.
Moneda
Unidad del comprobante. Los reportes separan los totales por código; no convierten monedas.

4 Metodología de trabajo
Trabajen en un repositorio y cierren las cinco entregas con los tags etapa-1 a etapa-5. Presenten un pull request hacia la rama principal y conserven el historial de decisiones. La versión evaluada será la del tag entregado. Cada estudiante debe registrar commits comprensibles y revisar sus cambios antes de integrarlos.
Cuando la cátedra publique un kit de nivelación, podrán usarlo para continuar la etapa siguiente. Indiquen qué incorporaron y qué modificaron. La entrega anterior conserva su evaluación; durante la defensa deberán explicar también el código proveniente del kit.
El informe docs/etapa-N.md, de extensión aproximada de una carilla, debe registrar decisiones y alternativas, dificultades y su resolución, y evidencia de las verificaciones relevantes. No repitan el enunciado. Si utilizan asistentes de IA, expliquen para qué los usaron, qué resultados revisaron y qué corrigieron. Todo código entregado debe ser comprendido por su autor.
Cada estudiante es responsable de revisar sus pull requests, ejecutar las comprobaciones antes de integrar los cambios y preparar el tag de cada entrega.
La arquitectura separa frontend, controladores REST, servicios y repositorios. Los controladores manejan contratos HTTP y DTO; los servicios coordinan reglas y transacciones; los repositorios acceden a datos. La consulta de reportes proyecta directamente a FacturaReporteDTO mediante JPQL. El servicio de reportes transforma esos DTO en archivos. Las entidades no se devuelven como respuestas HTTP.
Etapa 0 Puesta en marcha
Semana 1, sin nota
Meta. Ejecutar el backend y el frontend, comprender su configuración y dejar el repositorio preparado para las entregas.
Consignas
1. Utilicen Java 17 y el wrapper Gradle 8.10.2 del proyecto. Verifiquen la versión de Java. No es necesario instalar Gradle globalmente.
2. Configuren el proyecto facturacion-arca, grupo ar.edu.utn.frm, paquete ar.edu.utn.frm.facturacion, con Spring Boot 3.3.5. Incluyan Web, Data JPA, Validation, Lombok, H2, PostgreSQL y las dependencias de pruebas. Mantengan las versiones de OpenPDF y Apache POI indicadas en la presentación.
3. Organicen los paquetes model, dto, repository, service, controller, exception, config y report de acuerdo con sus responsabilidades.
4. Usen application.yml. En desarrollo, conecten H2 a jdbc:h2:mem:facturacion;DB_CLOSE_DELAY=-1, usuario sa y contraseña vacía; activen la consola /h2-console, ddl-auto: update, la salida SQL y su formato. Configuren open-in-view: false para evitar depender de una sesión de persistencia abierta durante la respuesta HTTP.
5. Ejecuten ./gradlew bootRun o, en Windows, gradlew.bat bootRun. Comprueben el puerto 8080 y la consola H2. La base en memoria dura lo que dura el proceso; no se conserva después de reiniciar la aplicación.
6. En frontend, ejecuten npm ci y npm run dev con una versión de Node compatible con el proyecto (18.13 o posterior). Verifiquen el servidor en el puerto 5173 y la configuración de API mediante el elemento meta llamado api-base-url.
7. Versionen código, configuración y wrapper; excluyan artefactos generados, dependencias instaladas y archivos locales del IDE. Documenten los comandos de arranque, pruebas y compilación en el README.
Perfil PostgreSQL
Conserven el perfil postgres y el servicio PostgreSQL 16 de docker-compose.yml como alternativa a H2. La configuración local utiliza la base facturacion en el puerto 5432 y usuario/contraseña facturacion. Si ese puerto está ocupado, adapten conjuntamente la publicación del contenedor y la URL. Declaren qué motor probaron; un resultado en H2 no demuestra por sí solo el mismo resultado en PostgreSQL.
Verificación
- El backend inicia y permite conectar a H2 con la URL configurada.
- ./gradlew test bootJar finaliza correctamente y genera build/libs/facturacion-arca-1.0.0.jar.
- El frontend compila y se sirve por HTTP.
- El estudiante puede reproducir el arranque siguiendo el README.
Etapa 1 Persistencia con JPA Hibernate
Semanas 2 y 3, 20 %
Meta. Implementar el modelo persistente y demostrar que sus agregados conservan relaciones, restricciones y dependencias.
Qué deben comprender
La diferencia entre entidad y superclase mapeada; propiedad de una relación y clave foránea; mappedBy; carga LAZY; cascadas; eliminación de huérfanos; restricciones de nulidad y unicidad; alcance de una transacción.
Consignas
1. Mapeen EntityId y AuditoriaApp como superclases. Las fechas de auditoría son LocalDateTime; las fechas de negocio, como emisión y anulación, son LocalDate. Las referencias de auditoría a Usuario son ManyToOne con carga diferida. Alta y usuario de carga no se actualizan después de persistir.
2. Mapeen las 14 entidades y sus tablas, respetando los campos obligatorios y las longitudes del modelo. Incluyan la unicidad de usuario, número de punto de venta, combinación lista/artículo y combinación punto de venta/número de factura. Un borrador mantiene el número nulo.
3. Representen el estado con EstadoFactura y EnumType.STRING. No almacenen posiciones ordinales del enum.
4. Configuren las asociaciones según el modelo. Factura tiene punto de venta, condición IVA y moneda obligatorios, y cliente opcional. Sus detalles tienen factura y artículo de lista obligatorios. Artículo admite marca y rubro nulos. Cliente tiene contacto y domicilio obligatorios con cascada y eliminación de huérfanos.
5. Implementen agregarDetalle para establecer ambos extremos de la relación. Inicialicen la colección y, al reemplazar renglones, vacíen la colección gestionada sin sustituir su instancia. Eviten propagar persistencia o borrado desde una factura hacia los catálogos.
6. Definan los repositorios que necesita la aplicación. No creen operaciones independientes para guardar detalles, contacto o domicilio cuando pertenecen a sus respectivos agregados.
7. Preparen datos de ejemplo mediante DataLoader, como CommandLineRunner transaccional y excluido del perfil test. La carga se omite si ya existen usuarios. Incluyan usuarios sistema y acortez, catálogos y seis facturas representativas. En la versión integrada, creen las facturas y cambien sus estados mediante FacturaService; los catálogos pueden cargarse mediante sus repositorios. Preparen el contexto de auditoría y límpienlo al finalizar. Hasta incorporar el servicio, comprueben la persistencia mediante pruebas controladas, sin convertir una carga provisoria en la regla final.
8. Escriban pruebas que guarden una factura con dos detalles, limpien el contexto de persistencia y vuelvan a consultarla. Verifiquen la relación inversa, el cliente opcional y la conservación de los catálogos. Comprueben también la eliminación de huérfanos y las restricciones relevantes con datos que realmente las violen.
Pistas y errores frecuentes
Si aparece una tabla intermedia inesperada, revisen mappedBy y el lado propietario. Si guardar la cabecera no inserta detalles, revisen cascada y asociación bidireccional. Una restricción de auditoría incumplida exige comprobar el usuario del contexto. Observar SQL ayuda a entender el mapeo, pero contar sentencias no reemplaza comprobar los datos persistidos.
Entregable tag etapa-1
Modelo y repositorios compilables, pruebas de persistencia en verde, README actualizado e informe con un ejemplo de SQL generado y explicación de las relaciones. Justifiquen por qué la factura es una raíz de agregado y qué datos permanecen al eliminar un borrador.
Etapa 2 Lombok DTO consultas JPQL y reportes
Semanas 4 y 5, 20 %
Meta. Obtener un reporte mediante proyección JPQL y producir PDF, Excel y TXT sin exponer entidades ni mezclar monedas.
Qué deben comprender
DTO frente a entidad; constructores usados por JPQL; JOIN y LEFT JOIN; agregaciones COUNT, GROUP BY y COALESCE; efectos de Lombok sobre asociaciones; tipos de celda y fórmulas en Excel; codificación y escapes de texto.
Parte A Lombok y modelo
1. Configuren Lombok como compileOnly y annotationProcessor. Utilicen getters, setters y los constructores necesarios sin aplicar @Data indiscriminadamente.
2. Eviten recursión en toString, equals y hashCode: la cabecera excluye detalles y el detalle excluye factura. Mantengan las exclusiones coherentes con la herencia y las asociaciones. Pueden conservar constructores explícitos para la inyección de dependencias.
3. Expliquen en el informe por qué incluir ambos lados de una relación bidireccional en métodos generados puede provocar recursión. Si realizan una demostración con una variante incorrecta, háganla en una prueba o rama descartable; esa variante no debe quedar en la entrega.
Parte B Proyección DTO con JPQL
4. Definan FacturaReporteDTO con id, numeroFactura, fechaEmision, clienteDenominacion, condicionIva, puntoVentaDescripcion, importeTotal, cantidadItems, estado y codigoMoneda. Admitan número nulo mientras la factura sea borrador.
5. Implementen un fragmento de repositorio con EntityManager y una consulta SELECT new. El constructor de la proyección debe aceptar los tipos que devuelve la consulta, incluidos Long para COUNT y EstadoFactura; el DTO expone el estado como texto.
6. Utilicen LEFT JOIN para cliente y detalles, COALESCE para mostrar «Consumidor Final» y joins para condición IVA, punto de venta y moneda. Agrupen los campos no agregados y ordenen por fecha de emisión descendente y, como desempate, ID descendente. La consulta debe conservar una factura sin cliente y permitir consultar una cabecera sin detalles sin perderla por el join.
7. Verifiquen cantidad de ítems, cliente opcional, número nulo, estado y moneda. Expliquen por qué proyectar directamente a DTO evita cargar información que el reporte no utiliza y por qué el orden del constructor importa.
Parte C PDF Excel y TXT
8. Implementen ReporteFacturasService con métodos que reciban los DTO y devuelvan los bytes de cada formato. El resultado final se descargará por HTTP en la etapa 3; no debe depender de que el DataLoader escriba archivos en una carpeta local.
9. Presenten nueve columnas: número, fecha, cliente, condición IVA, punto de venta, ítems, total, estado y moneda. El ID interno no es una columna del archivo. Para borradores, PDF muestra un guion en número y Excel/TXT dejan ese campo vacío.
10. En PDF, utilicen OpenPDF, A4 apaisado, título, fecha de generación y cantidad de registros. Repitan los encabezados en cada página y muestren un mensaje cuando no haya resultados. Incrusten NotoSansCJKsc-Regular.otf con Identity-H, conservando su licencia OFL. Si un texto contiene un glifo no disponible, rechacen la exportación con un error explícito; no sustituyan silenciosamente caracteres. TXT o Excel deben seguir disponibles como alternativas.
11. En Excel, utilicen Apache POI y una hoja Facturas, encabezados destacados, fechas y números como celdas de sus tipos correspondientes, formatos legibles y ajuste de columnas. Los totales deben ser fórmulas SUMIFS que seleccionen estado EMITIDA y código de moneda; evalúen las fórmulas y habiliten su recálculo.
12. En PDF y Excel, calculen el total facturado únicamente con facturas emitidas y separen los resultados por código de moneda. No sumen borradores ni anuladas, ni conviertan monedas. Una moneda presente en el reporte sin facturas emitidas muestra total cero.
13. En TXT, usen UTF-8, separadores de tabulación y una fila física por factura. Escapen barra invertida, tabulación, retorno de carro y salto de línea de manera reversible. Escriban fechas ISO e importes con dos decimales y punto decimal. El archivo contiene filas de detalle, sin pie de totales facturados.
14. Prueben los tres formatos con lista vacía, múltiples monedas, estados mezclados y texto con caracteres especiales. Para PDF, agreguen un caso de varias páginas y otro con un carácter no soportado. Para Excel, verifiquen tipos y resultados de fórmulas; para TXT, reconstruyan campos escapados y comprueben que se recupera el texto original.
Entregable tag etapa-2
Proyección JPQL y exportadores implementados, pruebas en verde y muestras verificables de los tres archivos. El informe debe explicar los joins, el constructor de proyección, el criterio de totales por estado y moneda y la estrategia de preservación de texto. Revisen visualmente el PDF completo; una prueba de contenido por sí sola no demuestra que el documento sea legible.
Etapa 3 API REST de lectura
Semana 6 — 15 % de la calificación de etapas.
Meta. Exponer consultas de facturas, catálogos y reportes mediante contratos HTTP claros, sin serializar directamente las entidades JPA.
Conocimientos previos. HTTP, JSON, controladores REST, DTO, parámetros de consulta e inyección por constructor.
Consignas
1. Implementen GET /api/facturas con respuesta 200 OK y una lista de FacturaReporteDTO. Admitan los filtros opcionales fechaDesde, fechaHasta, estado y montoMinimo. Apliquen límites inclusivos a fechas e importe; ordenen por fecha descendente y luego por identificador descendente. Una consulta sin resultados debe devolver una lista vacía.
2. Normalicen el estado recibido quitando espacios exteriores y convirtiéndolo a mayúsculas. Rechacen con 400 Bad Request un estado desconocido, fechas invertidas o importes mínimos no finitos. No impongan en el backend un mínimo cero que el contrato no define; el formulario web sí puede restringir ese campo a valores no negativos.
3. Implementen GET /api/facturas/{id} con FacturaDetalladaDTO: identificadores y descripciones necesarios para editar la cabecera, estado, importes, datos de autorización simulada, observaciones y renglones. Devuelvan 404 Not Found si el identificador no existe. Resuelvan las asociaciones necesarias dentro de la capa de servicio, con open-in-view: false.
4. Expongan GET /api/facturas/reporte con los mismos filtros y parámetro formato. El formato predeterminado será PDF; admitan pdf, xlsx o excel, y txt o tsv. Devuelvan los bytes con el tipo de contenido correspondiente y Content-Disposition: attachment. Un formato no admitido debe producir 400.
5. Implementen los catálogos de lectura de la tabla siguiente. Usen OpcionDTO, con id, etiqueta, valor y codigoMoneda: el precio corresponde a las opciones de lista de precios y el código de moneda a las opciones de monedas. No agreguen operaciones de alta, modificación o eliminación de catálogos.
Ruta de catálogo
Contenido
GET /api/catalogos/puntos-venta
Puntos de venta
GET /api/catalogos/clientes
Clientes
GET /api/catalogos/condiciones-iva
Condiciones frente al IVA
GET /api/catalogos/tipos-moneda
Monedas
GET /api/catalogos/lista-precio-articulos
Artículos ofrecidos por cada lista y su precio

6. Configuren CORS para los orígenes locales previstos del frontend: localhost y 127.0.0.1, en los puertos 5173 y 3000. Expliquen por qué esta configuración no autentica al usuario.
7. Centralicen los errores previstos en ProblemDetail, con estado HTTP, título, detalle y marca temporal. Conserven la diferencia entre una consulta vacía y un recurso inexistente.
Comprobación. Prueben filtros individuales y combinados, ausencia de resultados, cliente opcional mostrado como «Consumidor Final», detalle inexistente, formatos admitidos y formato inválido. Comprueben que el archivo descargado corresponde a los mismos filtros de la consulta.
Entregable. Código de lectura, pruebas, ejemplos reproducibles en requests.http y un informe breve que justifique los DTO y el manejo de errores.
Etapa 4 Escritura estados y consistencia transaccional
Semanas 7 y 8 — 25 % de la calificación de etapas.
Meta. Crear y modificar borradores, emitir y anular comprobantes de la simulación y proteger los datos frente a entradas inválidas y operaciones simultáneas.
Conocimientos previos. Bean Validation, transacciones, asociaciones bidireccionales, manejo de excepciones y semántica de los métodos HTTP.
Parte A Contratos de entrada y operaciones
Implementen cuatro operaciones de escritura:
Operación
Condición y comportamiento
Respuesta exitosa
POST /api/facturas
Crear un borrador con cabecera y detalles; todavía sin número ni CAE
201 Created, cabecera Location y FacturaReporteDTO
PUT /api/facturas/{id}
Reemplazar cabecera y detalles de un borrador y recalcular importes
200 OK y FacturaReporteDTO
PATCH /api/facturas/{id}/estado
Emitir un borrador o anular una factura emitida
200 OK y FacturaReporteDTO
DELETE /api/facturas/{id}
Eliminar físicamente un borrador y sus detalles
204 No Content, sin cuerpo

Definan FacturaRequestDTO con cliente opcional; condición de IVA, punto de venta y moneda obligatorios; fecha de emisión opcional, con fecha actual por defecto; observaciones de hasta 500 caracteres, y una colección no vacía de detalles válidos que no admita elementos nulos. La ausencia de cliente representa «Consumidor Final».
Cada FacturaDetalleRequestDTO debe contener listaPrecioArticuloId obligatorio, cantidad positiva, bonificación opcional entre 0 y 100 y descripción opcional de hasta 255 caracteres. La bonificación omitida será cero; si la descripción es nula se tomará la denominación del artículo. El identificador es el de la asociación lista–artículo, no el del artículo aislado. El cliente no enviará el precio unitario: el servicio debe obtenerlo de ListaPrecioArticulo.
Definan CambioEstadoRequestDTO con estado no vacío y observaciones opcionales de hasta 500 caracteres. Una observación no vacía enviada en el cambio de estado sustituirá la observación de la factura.
Resuelvan los identificadores referenciados antes de completar la operación. Una referencia inexistente producirá 404; una validación de entrada fallida, 400. Incluyan errores por campo en la respuesta de validación. Un cuerpo JSON malformado, incompatible con el DTO o ausente cuando es obligatorio debe producir 400 con un mensaje legible, sin exponer detalles internos del analizador JSON.
Parte B Cálculo y persistencia del agregado
1. Calculen importes en el servicio mediante BigDecimal, a partir de valores finitos. Para cada renglón utilicen el precio de lista y la alícuota del artículo; si esta última es nula, utilicen el valor predeterminado de configuración, 21 %.
2. Obtengan primero el neto sin redondear: cantidad × precio × (1 − bonificación / 100). Calculen el IVA sobre ese neto y redondéenlo a dos decimales con HALF_UP. Redondeen el neto por separado con el mismo criterio. El subtotal será la suma de ambos importes redondeados.
3. Sumen los subtotales con BigDecimal para obtener el total de cabecera y calculen saldo = importeTotal − importeCobrado. El alcance no incluye registrar pagos.
4. Conserven los campos double del modelo de referencia. Antes de persistir cada importe calculado, comprueben que la conversión a double es finita y que su reconversión mediante BigDecimal.valueOf conserva el valor decimal calculado. Rechacen desbordes o pérdida de centavos con un conflicto de negocio, sin persistencia parcial. No sustituyan este control por Math.round.
5. Mantengan ambos extremos de la relación al agregar detalles. Para reemplazarlos, vacíen la colección administrada sin sustituirla por otra; apliquen cascade = ALL y orphanRemoval = true. No propaguen altas o bajas hacia los catálogos compartidos.
6. Delimiten la transacción en el servicio. Una falla de validación de negocio o persistencia debe revertir la operación completa. Utilicen lectura transaccional para consultas y transacciones de escritura para las modificaciones.
Parte C Estados numeración y concurrencia
Implementen únicamente las transiciones BORRADOR → EMITIDA → ANULADA. La edición y la eliminación solo son válidas en BORRADOR; no permitan volver a un estado anterior ni anular directamente un borrador. Una operación incompatible con el estado actual producirá 409 Conflict.
Antes de editar, cambiar estado o borrar, obtengan un bloqueo pesimista de escritura sobre la cabecera. Al emitir, bloqueen además el punto de venta antes de consultar el máximo número y asignar el siguiente, comenzando en uno. Mantengan la restricción de unicidad por punto de venta y número. Estas medidas deben impedir duplicados cuando se emitan simultáneamente facturas diferentes del mismo punto de venta y evitar que una edición o eliminación ignore una emisión concurrente.
La emisión exige detalles y genera un CAE simulado de 14 dígitos, vencimiento a diez días desde la fecha actual y resultado de autorización A. La anulación registra fecha y baja lógica de auditoría; no elimina el comprobante. Estas reglas pertenecen al ejercicio académico: no representan una autorización real de ARCA ni un procedimiento fiscal completo.
Parte D Auditoría y pruebas
Resuelvan el usuario de auditoría a partir de X-Usuario; si falta, está vacío o no identifica un usuario existente, utilicen sistema. Mantengan el usuario actual durante la petición y limpien el contexto ThreadLocal al finalizar. Este mecanismo identifica al actor declarado y no autentica ni autoriza operaciones.
Completen altas y modificaciones con los callbacks de AuditoriaApp. Además, registren explícitamente la modificación en cada PUT aceptado, incluso cuando cambien solo los detalles o se reenvíen los mismos valores. Al anular, registren usuario y fecha de baja.
Comprobación. Cubran creación y reemplazo del agregado, eliminación de huérfanos, rollback, límites 255/256 de descripción en POST y PUT, cantidad y bonificación inválidas, referencias inexistentes, redondeos y precisión, todas las transiciones permitidas y rechazadas, auditoría y emisiones concurrentes. Distingan las pruebas ejecutadas en H2 de las ejecutadas en PostgreSQL; una ejecución exitosa en H2 no demuestra el comportamiento del segundo motor.
Entregable. DTO de entrada, servicios, controladores y manejo central de errores; pruebas de integración y concurrencia; ejemplos HTTP e informe breve con decisiones de cálculo, transacciones y bloqueos.
Etapa 5 Frontend TypeScript e integración
Semanas 9 y 10 — 20 % de la calificación de etapas.
Meta. Construir una interfaz web que permita operar el circuito completo y mantenga resultados coherentes ante errores y respuestas asíncronas.
Conocimientos previos. HTML, CSS, DOM, módulos ES, TypeScript, promesas, fetch y async/await.
Parte A Proyecto y cliente HTTP
Organicen el frontend independiente con TypeScript estricto y módulos ES2020, sin framework. Definan interfaces para los DTO utilizados y un servicio HTTP que centralice consultas, catálogos, creación, actualización, cambios de estado, eliminación y construcción de URLs de reportes.
Obtengan la URL base de la API de la etiqueta meta destinada a configuración. Utilicen fetch con manejo explícito de respuestas no exitosas, encabezados JSON cuando correspondan y X-Usuario: sistema. La respuesta 204 debe resolverse sin intentar leer JSON. Conserven el estado HTTP y los detalles disponibles en ApiError; si el servidor devuelve un error con cuerpo no JSON, presenten un mensaje de respaldo comprensible. No oculten los errores de validación por campo.
Documenten npm ci, npm run build, npm run dev y npm test. Sirvan la aplicación por HTTP en el puerto 5173 y verifiquen la conexión con el backend en el puerto 8080.
Parte B Consultas y operaciones
1. Carguen los catálogos para completar los selectores. Muestren un formulario de filtros por fechas, estado y monto mínimo, una tabla de resultados, cantidad de coincidencias y un mensaje cuando no existan datos.
2. Presenten importes con la moneda de cada factura: traduzcan los códigos PES y DOL a los formatos de ARS y USD; si el código no es reconocido, manténganlo visible. No conviertan importes entre monedas.
3. Implementen el formulario de cabecera y renglones. Permitan agregar y quitar detalles, seleccionar la opción lista–artículo, indicar cantidad, bonificación y descripción. Verifiquen cantidad finita positiva, bonificación finita entre 0 y 100 y descripción de hasta 255 caracteres; limiten las observaciones a 500.
4. Muestren el precio de catálogo y un total estimado neto, sin IVA. El backend será la autoridad del precio aceptado y del importe definitivo con IVA. El request no debe enviar el precio unitario ni importes calculados por el navegador.
5. Permitan crear borradores mediante POST, cargar y editar uno existente mediante PUT y cancelar la edición. Conserven los valores del formulario cuando falle el guardado, para permitir corrección y reintento.
6. Incorporen acciones de emisión, anulación y eliminación según el estado. Soliciten confirmación para eliminar un borrador. El motivo de anulación es opcional; cancelar el diálogo debe cancelar la operación y no enviar un PATCH.
7. Incluyan enlaces de descarga de PDF y Excel con los filtros de la consulta vigente. El formato TXT debe estar disponible mediante la API y la construcción de URL del servicio HTTP; no se requiere un tercer botón en esta pantalla.
Parte C Consistencia de la interfaz y pruebas
Bloqueen el guardado duplicado y deshabiliten controles y acciones incompatibles mientras se envía POST o PUT. Liberen el bloqueo al finalizar, tanto con éxito como con error. Una respuesta tardía no debe sobrescribir una búsqueda más reciente ni restaurar una edición cancelada o sustituida por otra. Utilicen identificadores de solicitud o un mecanismo equivalente para descartar respuestas obsoletas; actualicen juntos la lista y sus enlaces de exportación.
Construyan el contenido externo mediante nodos y textContent; no interpreten como HTML las denominaciones, descripciones o mensajes recibidos. Incluyan una región de estado persistente con role="status" y aria-live="polite", y reflejen el guardado con aria-busy. Mantengan los errores visibles hasta una acción posterior; los mensajes de éxito pueden limpiarse a los seis segundos sin retirar la región del DOM.
Comprobación. Prueben el cliente HTTP y la interacción del DOM: respuesta 204, errores JSON y no JSON, validaciones, texto externo que parece HTML, doble envío, conservación de datos tras un error, respuestas recibidas en orden inverso, cancelación de edición y cancelación del diálogo de anulación. Utilicen promesas controladas para reproducir las carreras. Complementen las pruebas automatizadas con el recorrido completo en navegador; una prueba con jsdom no sustituye esa comprobación ni demuestra por sí sola compatibilidad con lectores de pantalla.
Entregable. Frontend compilable, pruebas, instrucciones de ejecución y evidencia del circuito crear–editar–emitir–anular y de la eliminación de un borrador. Adjunten descargas de ejemplo y documenten los límites de la simulación.
Entrega final y defensa
Semana 11. Entreguen el repositorio con las cinco etapas identificables, código fuente, configuración de ejemplo, README, requests.http, pruebas y los informes breves de cada entrega. El README debe permitir instalar dependencias, iniciar backend y frontend, seleccionar H2 o PostgreSQL y ejecutar los controles pertinentes. Indiquen qué comprobaciones realizaron y en qué entorno; no presenten una cantidad fija de pruebas como sustituto de la cobertura de comportamientos.
La evidencia final debe permitir verificar la persistencia de agregados, cálculo y límites monetarios, validaciones y rollback, estados, auditoría, concurrencia, API, reportes e interfaz. Para los reportes incluyan casos vacíos, varias páginas, monedas diferentes y estados diferentes; verifiquen que PDF y Excel totalizan únicamente emitidas por moneda, que TXT conserva sus nueve columnas mediante escapes reversibles y que un carácter sin glifo disponible produce un error explícito al generar PDF. Revisen visualmente los archivos, además de comprobar sus datos.
La defensa individual dura 25 minutos: cinco de demostración, quince de preguntas y cinco para un cambio breve indicado por el docente. Expliquen sus decisiones y demuestren consultas, creación y edición de un borrador, emisión, anulación, eliminación de otro borrador, descarga de reportes y al menos un error controlado.
Prepárense para justificar cascadas y lado propietario, proyección DTO, transacciones y bloqueos, redondeo, códigos HTTP, auditoría y control de respuestas asíncronas. Una modificación propuesta durante la defensa constituye una actividad de evaluación: no convierte funcionalidades ajenas al alcance en requisitos previos de entrega.
La ponderación de la evaluación es la siguiente: las etapas representan el 70 % de la nota final y la defensa el 30 %. Dentro del componente de etapas, los pesos son 20 %, 20 %, 15 %, 25 % y 20 %, respectivamente.
Rúbrica de evaluación
La misma rúbrica se aplica a cada etapa, considerando solamente los comportamientos exigidos hasta ese momento. Los porcentajes siguientes distribuyen la nota de una entrega; no se suman a los pesos de las etapas ni al porcentaje de la defensa.
Criterio
Peso
Evidencia esperada
Funcionamiento y requisitos
40 %
Circuitos y casos límite de la etapa resueltos; resultados verificables y errores controlados.
Diseño y calidad del código
25 %
Responsabilidades claras, asociaciones correctas, DTO adecuados y reglas ubicadas en la capa correspondiente.
Pruebas y verificación
15 %
Pruebas reproducibles que ejercitan comportamientos, casos adversos y regresiones; evidencia manual cuando corresponda.
Informe y documentación
10 %
Instrucciones suficientes para ejecutar la entrega y explicación precisa de las decisiones y limitaciones.
Organización individual y Git
10 %
Historial comprensible, autoría identificable y entregas etiquetadas.

Para obtener la nota de etapas, multipliquen cada calificación por su peso: etapa 1 × 0,20 + etapa 2 × 0,20 + etapa 3 × 0,15 + etapa 4 × 0,25 + etapa 5 × 0,20. La nota final combina el 70 % de ese resultado con el 30 % de la defensa individual, utilizando la misma escala de calificación.
Cronograma sugerido
Semanas
Trabajo central
Hito
1
Entorno, repositorio y ejecución inicial
Etapa 0, sin calificación
2 y 3
Entidades, asociaciones, cascadas y datos de ejemplo
Etapa 1 — 20 %
4 y 5
Lombok, DTO, JPQL y tres formatos de reporte
Etapa 2 — 20 %
6
API REST de lectura, filtros y catálogos
Etapa 3 — 15 %
7 y 8
Escritura, cálculo, estados, auditoría y concurrencia
Etapa 4 — 25 %
9 y 10
Frontend, integración y controles asíncronos
Etapa 5 — 20 %
11
Entrega completa y defensa
Componente individual — 30 % de la nota final

Anexo A Errores frecuentes y cómo reconocerlos
Situación
Revisión necesaria
El detalle no queda asociado a la factura
Actualicen ambos extremos de la relación y revisen el lado propietario y mappedBy.
Se elimina un catálogo al borrar un borrador
Revisen las cascadas: los catálogos se comparten y no pertenecen al ciclo de vida de la factura.
Aparece una recursión al imprimir o comparar entidades
Excluyan asociaciones bidireccionales de toString, equals y hashCode; eviten @Data indiscriminado.
Falla una asociación LAZY después de responder
Construyan el DTO dentro del contexto transaccional y carguen lo necesario; no dependan de Open Session in View.
Los totales cambian por centavos
Revisen el orden de cálculo y HALF_UP a dos decimales. No sustituyan el cálculo decimal por Math.round.
Un valor extremo se guarda con pérdida de precisión
Comprueben finitud y conversión de ida y vuelta entre BigDecimal y double; rechacen la operación completa cuando no sea representable.
El total del reporte mezcla importes incompatibles
Incluyan solamente EMITIDA y separen por código de moneda. No conviertan monedas ni sumen borradores o anuladas.
Dos emisiones reciben el mismo número
Protejan la numeración por punto de venta con bloqueo y mantengan la restricción única en la base.
Una edición modifica una factura recién emitida
Bloqueen la factura antes de verificar su estado y actualizarla dentro de la misma transacción.
El frontend falla después de DELETE
No intenten interpretar como JSON una respuesta 204 sin cuerpo.
Una búsqueda antigua reemplaza los resultados actuales
Descarten respuestas obsoletas y mantengan sincronizados la tabla y los enlaces de descarga.
Una denominación se interpreta como HTML
Inserten texto externo con textContent y nodos DOM, sin construir HTML con esos valores.
TXT genera filas o columnas inesperadas
Escapen barras inversas, tabulaciones y saltos de línea antes de unir las nueve columnas.
PDF muestra caracteres ausentes
Usen la fuente incorporada y detecten glifos no disponibles; informen el error y permitan elegir TXT o Excel.
Se pierde información al reiniciar el proceso
H2 en memoria es temporal. Para practicar persistencia entre reinicios, utilicen el perfil PostgreSQL.

Anexo B Recursos de consulta
Usen primero las versiones y los comandos fijados en el proyecto: build.gradle, el wrapper de Gradle, application.yml, el perfil PostgreSQL, package.json y README. Consulten las pruebas y requests.http para observar ejemplos de contratos y casos límite. Un ejemplo de documentación externa debe adaptarse a estas versiones y a las reglas de este trabajo.
Como apoyo conceptual, consulten la documentación oficial de Java, Spring Boot, Spring Data JPA, Jakarta Persistence, Jakarta Bean Validation, Lombok, TypeScript, OpenPDF y Apache POI. Para HTTP y APIs del navegador, utilicen MDN Web Docs. Distingan las convenciones generales de los requisitos concretos aquí definidos.
