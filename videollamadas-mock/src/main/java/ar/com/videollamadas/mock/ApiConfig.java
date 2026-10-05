package ar.com.videollamadas.mock;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/** Version en la URI, como hacen los proveedores reales. */
@ApplicationPath("/api/v1")
public class ApiConfig extends Application {
}
