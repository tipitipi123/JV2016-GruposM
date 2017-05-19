/** 
 * Proyecto: Juego de la vida.
 *  Resuelve todos los aspectos del almacenamiento del DTO Simulacion utilizando un ArrayList.
 *  utilizando almaacenamiento de base de datos db4o.
 *  @since: prototipo2.0
 *  @source: SimulacionesDAO.java 
 *  @version: 2.0 - 2017.05.19 
 *  @author: ajp
 */

package accesoDatos.db4o;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import com.db4o.ObjectContainer;
import com.db4o.ObjectSet;
import com.db4o.query.Query;

import accesoDatos.OperacionesDAO;
import accesoDatos.fichero.MundosDAO;
import accesoDatos.fichero.UsuariosDAO;
import modelo.ModeloException;
import modelo.Mundo;
import modelo.Simulacion;
import modelo.Simulacion.EstadoSimulacion;
import modelo.Usuario;
import util.Fecha;

public class SimulacionesDAO implements OperacionesDAO {

	// Requerido por el Singleton 
	private static SimulacionesDAO instancia;

	// Elemento de almacenamiento.
	private ObjectContainer db;
	

	/**
	 * Constructor por defecto de uso interno.
	 * Sólo se ejecutará una vez.
	 */
	private SimulacionesDAO() {
		db = Conexion.getDB();
		//db.store(new Hashtable<String,String>());
		cargarPredeterminados();
	}

	/**
	 *  Método estático de acceso a la instancia única.
	 *  Si no existe la crea invocando al constructor interno.
	 *  Utiliza inicialización diferida.
	 *  Sólo se crea una vez; instancia única -patrón singleton-
	 *  @return instancia
	 */
	public static SimulacionesDAO getInstancia() {
		if (instancia == null) {
			instancia = new SimulacionesDAO();
		}
		return instancia;
	}

	/**
	 *  Método para generar de datos predeterminados.
	 */
	private void cargarPredeterminados() {
		// Obtiene usuario (invitado) y mundo predeterminados.
		Usuario usrDemo = UsuariosDAO.getInstancia().obtener("III1R");
		Mundo mundoDemo = MundosDAO.getInstancia().obtener("MundoDemo");
		Simulacion simulacionDemo = null;
		try {
			simulacionDemo = new Simulacion(usrDemo, new Fecha(), mundoDemo, EstadoSimulacion.PREPARADA);
		} catch (ModeloException e) {
			e.printStackTrace();
		}
		db.store(simulacionDemo);
	}

	/**
	 *  Cierra datos.
	 */
	@Override
	public void cerrar() {
		// Nada que hacer si no hay persistencia.
	}

	// OPERACIONES DAO
	/**
	 * Búsqueda de Simulacion dado idUsr y fecha.
	 * @param idSimulacion - el idUsr+fecha de la Simulacion a buscar. 
	 * @return - la Simulacion encontrada; null si no existe.
	 */	
	@Override
	public Simulacion obtener(String idSimulacion) {
		ObjectSet <Simulacion> result;
		Query consulta = db.query();
		
		consulta.constrain(Simulacion.class);
		consulta.descend("idSimulacion").equals(idSimulacion);
		result = consulta.execute();
		if(result.size()>0){
			return result.get(0);
		}
		
		return null;
	}

	/**
	 *  Obtiene por búsqueda binaria, la posición que ocupa, o ocuparía,  una simulación en 
	 *  la estructura.
	 *	@param idSimulacion - id de Simulacion a buscar.
	 *	@return - la posición, en base 1, que ocupa un objeto o la que ocuparía (negativo).
	 */
	private int obtenerPosicion(String idSimulacion) {
		//Cambiar metodo por obtener desde la base de datos.
		int comparacion;
		int inicio = 0;
		int fin = db.size() - 1;
		int medio = 0;
		while (inicio <= fin) {
			medio = (inicio + fin) / 2;			// Calcula posición central.
			// Obtiene > 0 si idSimulacion va después que medio.
			comparacion = idSimulacion.compareTo(db.get(medio).getIdSimulacion());
			if (comparacion == 0) {			
				return medio + 1;   			// Posción ocupada, base 1	  
			}		
			if (comparacion > 0) {
				inicio = medio + 1;
			}			
			else {
				fin = medio - 1;
			}
		}	
		return -(inicio + 1);					// Posición que ocuparía -negativo- base 1
	}

	/**
	 * Búsqueda de simulacion dado un objeto, reenvía al método que utiliza idSimulacion.
	 * @param obj - la Simulacion a buscar.
	 * @return - la Simulacion encontrada; null si no existe.
	 */
	public Simulacion obtener(Object obj)  {
		return this.obtener(((Simulacion) obj).getIdSimulacion());
	}

	/**
	 * Búsqueda de todas la simulaciones de un usuario.
	 * @param idUsr - el identificador de usuario a buscar.
	 * @return - Sublista con las simulaciones encontrada; null si no existe ninguna.
	 */
	public List<Simulacion> obtenerTodasMismoUsr(String idUsr) {
		Simulacion aux = null;
		try {
			aux = new Simulacion();
		} catch (ModeloException e) {
			e.printStackTrace();
		}
		aux.setUsr(UsuariosDAO.getInstancia().obtener(idUsr));
		// Busca posición inserción (negativa base 1) ordenada por idUsr + fecha. 
		// La última para el mismo usuario.
		int posicion = -obtenerPosicion(aux.getIdSimulacion());
		// Separa las simulaciones del mismo usuario.
		return separarSimulacionesUsr(posicion-2);
	}

	/**
	 * Separa en una lista independiente todas la simulaciones de un mismo usuario.
	 * @param ultima - el indice de la última simulación ya encontrada.
	 * @return - Sublista con las simulaciones encontrada; null si no existe ninguna.
	 */
	private List<Simulacion> separarSimulacionesUsr(int ultima) {
		// Localiza primera simulación del mismo usuario.
		String idUsr = db.get(ultima).getUsr().getIdUsr();
		int primera = ultima;
		for (int i = ultima; i >= 0 && db.get(i).getUsr().getIdUsr().equals(idUsr); i--) {
			primera = i;
		}
		// devuelve la sublista de simulaciones buscadas.
		return db.subList(primera, ultima+1);
	}

	/**
	 *  Alta de una nueva Simulacion en orden y sin repeticiones según los idUsr más fecha. 
	 *  Busca previamente la posición que le corresponde por búsqueda binaria.
	 *  @param obj - Simulación a almacenar.
	 *  @ - si ya existe.
	 */	
	public void alta(Object obj)  {
		assert obj != null;
		Simulacion simulNueva = (Simulacion) obj;								// Para conversión cast
		int posicionInsercion = obtenerPosicion(simulNueva.getIdSimulacion()); 
		if (posicionInsercion < 0) {
			db.store(-posicionInsercion - 1);
			db.store(simulNueva); 			// Inserta la simulación en orden.
			return;
		}
	}

	/**
	 * Elimina el objeto, dado el id utilizado para el almacenamiento.
	 * @param idSimulacion - identificador de la Simulacion a eliminar.
	 * @return - la Simulacion eliminada. null - si no existe.
	 */
	@Override
	public Simulacion baja(String idSimulacion)  {
		assert (idSimulacion != null);
		int posicion = obtenerPosicion(idSimulacion); 							// En base 1
		if (posicion > 0) {
			return db.remove(posicion - 1); 						// En base 0
		}
		return null;
	}

	/**
	 *  Actualiza datos de una Simulacion reemplazando el almacenado por el recibido.
	 *  No admitirá cambios en usr ni en la fecha.
	 *	@param obj - Patron con las modificaciones.
	 *  @ - si no existe.
	 */
	@Override
	public void actualizar(Object obj)  {
		assert obj != null;
		Simulacion simulActualizada = (Simulacion) obj;							// Para conversión cast
		int posicion = obtenerPosicion(simulActualizada.getIdSimulacion()); 	// En base 1
		if (posicion > 0) {
			// Reemplaza elemento
			db.set(posicion - 1, simulActualizada);  			// En base 0		
			return;
		}
	}

	/**
	 * Obtiene el listado de todos las simulaciones almacenadas.
	 * @return el texto con el volcado de datos.
	 */
	@Override
	public String listarDatos() {
		StringBuilder listado = new StringBuilder();
		for (Simulacion simulacion: db) {
			if (simulacion != null) {
				listado.append("\n" + simulacion);
			}
		}
		return listado.toString();
	}

	/**
	 * Obtiene el listado de todos los identificadres de las simulaciones almacenadas.
	 * @return el texto con los identificadores.
	 */
	public String listarIdSimulaciones() {
		StringBuilder listado = new StringBuilder();
		for (Simulacion simulacion: db) {
			if (simulacion != null) {
				listado.append("\n" + simulacion.getIdSimulacion());
			}
		}
		return listado.toString();
	}
	
	/**
	 * Elimina todos las simulaciones almacenadas y regenera la demo predeterminada.
	 */
	@Override
	public void borrarTodo() {
		instancia = null;
	}

} //class
