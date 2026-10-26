-- Permite a la API actualizar ventas editadas (mismo uuid). Solo las columnas indicadas.
GRANT UPDATE (fecha, total, items, metodo_pago) ON ventas TO tfg_app;
