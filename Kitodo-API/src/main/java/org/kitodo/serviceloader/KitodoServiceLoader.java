/*
 * (c) Kitodo. Key to digital objects e. V. <contact@kitodo.org>
 *
 * This file is part of the Kitodo project.
 *
 * It is licensed under GNU General Public License version 3 or later.
 *
 * For the full copyright and license information, please read the
 * GPL3-License.txt file that was distributed with this source code.
 */

package org.kitodo.serviceloader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import javax.faces.context.FacesContext;
import javax.servlet.http.HttpSession;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class KitodoServiceLoader<T> {
    private Class clazz;
    private String modulePath = "";

    private static final String POM_PROPERTIES_FILE = "pom.properties";
    private static final String ARTIFACT_ID_PROPERTY = "artifactId";
    private static final String TEMP_DIR_PREFIX = "kitodo_";
    private static final String META_INF_FOLDER = "META-INF";
    private static final String RESOURCES_FOLDER = "resources";
    private static final String PAGES_FOLDER = "pages";

    private static final Path SYSTEM_TEMP_FOLDER = FileSystems.getDefault()
            .getPath(System.getProperty("java.io.tmpdir"));

    private static final Logger logger = LogManager.getLogger(KitodoServiceLoader.class);

    /**
     * Constructor for KitodoServiceLoader.
     *
     * @param clazz
     *            interface class of module to load
     * @param modulePath
     *            path to module folder
     */
    public KitodoServiceLoader(Class clazz, String modulePath) {
        this.clazz = clazz;
        if (!new File(modulePath).exists()) {
                logger.error("Specified module folder does not exist: " + modulePath);
        } else {
            this.modulePath = modulePath;
        }
    }

    /**
     * Loads a module from the classpath which implements the constructed clazz.
     * Frontend files of all modules will be loaded into the core module.
     *
     * @return A module with type T.
     */
    @SuppressWarnings("unchecked")
    public T loadModule() {

        loadModulesIntoClasspath();
        loadBeans();
        loadFrontendFilesIntoCore();

        ServiceLoader<T> loader = ServiceLoader.load(clazz);

        return loader.iterator().next();
    }

    /**
     * Loads bean classes and registers them to the FacesContext. Afterwards they can be used in all
     * frontend files
     */
    private void loadBeans() {
        Path moduleFolder = FileSystems.getDefault().getPath(modulePath);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(moduleFolder, "*.jar")) {

            for (Path f : stream) {
                try (JarFile jarFile = new JarFile(f.toString())) {

                    if (hasFrontendFiles(jarFile)) {

                        Enumeration<JarEntry> e = jarFile.entries();

                        URL[] urls = {new URL("jar:file:" + f.toString() + "!/") };
                        URLClassLoader cl = URLClassLoader.newInstance(urls);

                        while (e.hasMoreElements()) {
                            JarEntry je = e.nextElement();

                        /*
                         * IMPORTANT: Naming convention: the name of the java class has to be in upper
                         * camel case or "pascal case" and must be equal to the file name of the
                         * corresponding facelet file concatenated with the word "Form".
                         *
                         * Example: template filename "sample.xhtml" => "SampleForm.java"
                         *
                         * That is the reason for the following check (e.g. whether the JarEntry name
                         * ends with "Form.class")
                         */
                            if (je.isDirectory() || !je.getName().endsWith("Form.class")) {
                                continue;
                            }

                            String className = je.getName().substring(0, je.getName().length() - 6);
                            className = className.replace('/', '.');
                            Class c = cl.loadClass(className);

                            String beanName = className.substring(className.lastIndexOf(".") + 1).trim();

                            FacesContext facesContext = FacesContext.getCurrentInstance();
                            HttpSession session = (HttpSession) facesContext.getExternalContext().getSession(false);

                            session.getServletContext().setAttribute(beanName, c.newInstance());
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Classpath could not be accessed", e.getMessage());
        }
    }

    /**
     * If the found jar files have frontend files, they will be extracted and copied into the frontend folder
     * of the core module. Before copying, existing frontend files of the same module will be deleted from the
     * core module. Afterwards the created temporary folder will be deleted as well.
     */
    private void loadFrontendFilesIntoCore() {

        Path moduleFolder = FileSystems.getDefault().getPath(modulePath);

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(moduleFolder, "*.jar")) {

            for (Path f : stream) {
                File loc = new File(f.toString());
                try (JarFile jarFile = new JarFile(loc)) {

                    if (hasFrontendFiles(jarFile)) {

                        Path temporaryFolder = Files.createTempDirectory(SYSTEM_TEMP_FOLDER, TEMP_DIR_PREFIX);

                        File tempDir = new File(Paths.get(temporaryFolder.toUri()).toAbsolutePath().toString());

                        extractFrontEndFiles(loc.getAbsolutePath(), tempDir);

                        String moduleName = extractModuleName(tempDir);
                        if (!moduleName.isEmpty()) {
                            FacesContext facesContext = FacesContext.getCurrentInstance();
                            HttpSession session = (HttpSession) facesContext.getExternalContext().getSession(false);

                            String filePath = session.getServletContext().getRealPath(File.separator + PAGES_FOLDER)
                                    + File.separator + moduleName;
                            FileUtils.deleteDirectory(new File(filePath));

                            String resourceFolder = String.join(File.separator,
                                    Arrays.asList(tempDir.getAbsolutePath(), META_INF_FOLDER, RESOURCES_FOLDER));
                            copyFrontEndFiles(resourceFolder, filePath);
                        } else {
                            logger.info("No module found in JarFile '" + jarFile.getName() + "'.");
                        }
                        FileUtils.deleteDirectory(tempDir);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Classpath could not be accessed", e.getMessage());
        }
    }

    /**
     * Extracts the module name of the current module by finding the pom.properties
     * in the given temporary folder
     *
     * @param temporaryFolder
     *            folder in which the pom.properties file will be searched for
     *
     * @return boolean
     */
    private String extractModuleName(File temporaryFolder) throws IOException {
        String moduleName = "";
        File properties = findFile(POM_PROPERTIES_FILE, temporaryFolder);
        try (InputStream input = new FileInputStream(properties)) {
            Properties prop = new Properties();
            prop.load(input);
            moduleName = prop.getProperty(ARTIFACT_ID_PROPERTY);
        } catch (FileNotFoundException e) {
            logger.error(e.getMessage());
        }
        return moduleName;
    }

    /**
     * Copies extracted frontend files by a given source folder name to the destination Folder
     * given by a destination folder name
     *
     * @param sourceFolder
     *            copies all extracted frontend files
     * @param destinationFolder
     *            jarFile that will be checked for frontend files
     */
    private void copyFrontEndFiles(String sourceFolder, String destinationFolder) throws IOException {
        FileUtils.copyDirectory(new File(sourceFolder), new File(destinationFolder));
    }

    /**
     * Checks, whether a passed jarFile has frontend files or not. Returns true,
     * when the jar contains a folder with the name "resources"
     *
     * @param jarPath
     *            jarFile that will be checked for frontend files
     * @param destinationFolder
     *            destination path, where the frontend files will be extracted to
     *
     */
    private void extractFrontEndFiles(String jarPath, File destinationFolder) throws IOException {
        if (!destinationFolder.exists()) {
            destinationFolder.mkdir();
        }

        try (JarFile jar = new JarFile(jarPath)) {
            Enumeration jarEntries = jar.entries();
            while (jarEntries.hasMoreElements()) {
                JarEntry currentJarEntry = (JarEntry) jarEntries.nextElement();

                if (currentJarEntry.getName().contains(RESOURCES_FOLDER)
                        || currentJarEntry.getName().contains(POM_PROPERTIES_FILE)) {
                    File resourceFile = new File(destinationFolder + File.separator + currentJarEntry.getName());
                    if (currentJarEntry.isDirectory()) {
                        resourceFile.mkdirs();
                        continue;
                    }
                    if (currentJarEntry.getName().contains(POM_PROPERTIES_FILE)) {
                        resourceFile.getParentFile().mkdirs();
                    }

                    try (InputStream is = jar.getInputStream(currentJarEntry);
                            FileOutputStream fos = new FileOutputStream(resourceFile)) {
                        while (is.available() > 0) {
                            fos.write(is.read());
                        }
                    }
                }
            }
        }
    }

    /**
     * Checks, whether a passed jarFile has frontend files or not. Returns true, when the jar contains
     * a folder with the name "resources"
     *
     * @param jarFile
     *            jarFile that will be checked for frontend files
     *
     * @return boolean
     */
    private boolean hasFrontendFiles(JarFile jarFile) {
        Enumeration enums = jarFile.entries();
        while (enums.hasMoreElements()) {
            JarEntry jarEntry = (JarEntry)enums.nextElement();
            if (jarEntry.getName().contains(RESOURCES_FOLDER) && jarEntry.isDirectory()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tries to find a file by a given file name in a folder by folder name
     *
     * @param name
     *            file name that will be searched for
     * @param folder
     *            folder that will be searched
     *
     * @return File, null
     */
    private File findFile(String name, File folder) throws FileNotFoundException
    {
        Collection<File> s = FileUtils.listFiles(folder, null, true);
        for (File f : s) {
            if (f.getName().equals(name))
                return f;
        }
        throw new FileNotFoundException(
                "ERROR: file '" + name + "' not found in folder '" + folder.getAbsolutePath() + "'!");
    }

    /**
     * Loads jars from the pluginsFolder to the classpath, so the ServiceLoader can
     * find them.
     */
    private void loadModulesIntoClasspath() {
        Path moduleFolder = FileSystems.getDefault().getPath(modulePath);

        URLClassLoader sysLoader;
        try {
            DirectoryStream<Path> stream = Files.newDirectoryStream(moduleFolder, "*.jar");
            for (Path f : stream) {
                File loc = new File(f.toString());
                sysLoader = (URLClassLoader) this.getClass().getClassLoader();
                ArrayList<URL> urls = new ArrayList<>(Arrays.asList(sysLoader.getURLs()));
                URL udir = loc.toURI().toURL();

                if (!urls.contains(udir)) {
                    Class<URLClassLoader> sysClass = URLClassLoader.class;
                    Method method = sysClass.getDeclaredMethod("addURL", URL.class);
                    method.setAccessible(true);
                    method.invoke(sysLoader, udir);
                }
            }
        } catch (IOException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            logger.error("Classpath could not be accessed", e.getMessage());
        }
    }

}
