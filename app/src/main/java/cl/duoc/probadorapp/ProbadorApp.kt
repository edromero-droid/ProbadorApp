package cl.duoc.probadorapp

import android.app.Application

class ProbadorApp : Application() {
    lateinit var contenedor: Contenedor
        private set

    override fun onCreate() {
        super.onCreate()
        contenedor = Contenedor(this)
        contenedor.iniciar()
    }
}
