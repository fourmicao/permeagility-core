package permeagility.util;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;

/**
 * This class extends the URLClassLoader to load jars named 'plus-*' from the plus directory
 * each 'plus' jar may have a ClassPath set to load additional libraries (not named plus-)
 * the additional libraries must be in the root of the plus directory to ensure no conflicts
 */
public class PlusClassLoader extends URLClassLoader {

	public static final String PLUS_DIRECTORY = "plus";
	public static final String PLUS_PREFIX = "plus-";
	
	private static ClassLoader instance = null;  // There can only be one and it can only be loaded once
	
	public PlusClassLoader(URL[] urls) {
		super(urls);
	}

	public static ClassLoader get() {
		if (instance != null) {
			return instance;
		}
		ArrayList<URL> urls = new ArrayList<URL>();
		File d = new File(PLUS_DIRECTORY);
		if (!d.exists()) {
			d.mkdir();
		}
		if (d.isDirectory()) {
			for (File f : d.listFiles()) {
				if (f.getName().startsWith(PLUS_PREFIX)) {
					System.out.println("Loading plus module "+f.getName());
					try {
						urls.add(f.toURI().toURL());
					} catch (MalformedURLException e) {
						System.out.println("Cannot make URL plus module "+f.getName()+": "+e.getMessage());
					}
				}
			}
			URL[] urlArray = new URL[urls.size()];
			int i = 0;
			for (URL u : urls) {
				urlArray[i++] = u;
			}
			instance = new PlusClassLoader(urlArray);
		} else {
			System.out.println("Exit condition: "+PLUS_DIRECTORY+" is not a directory - no plus modules loaded");
			instance = ClassLoader.getSystemClassLoader();
		}
		return instance;
	}

}