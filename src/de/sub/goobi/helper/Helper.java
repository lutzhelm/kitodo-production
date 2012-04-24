/*
 * This file is part of the Goobi Application - a Workflow tool for the support of
 * mass digitization.
 *
 * Visit the websites for more information.
 *     - http://gdz.sub.uni-goettingen.de
 *     - http://www.goobi.org
 *     - http://launchpad.net/goobi-production
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details. You
 * should have received a copy of the GNU General Public License along with this
 * program; if not, write to the Free Software Foundation, Inc., 59 Temple Place,
 * Suite 330, Boston, MA 02111-1307 USA
 */

package de.sub.goobi.helper;

import java.io.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.text.DateFormat;
import java.util.*;

import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.servlet.http.HttpServletRequest;

import org.apache.log4j.Logger;
import org.hibernate.Session;
import org.jdom.Element;

import de.sub.goobi.beans.Benutzer;
import de.sub.goobi.forms.LoginForm;
import de.sub.goobi.persistence.HibernateUtilOld;
import de.sub.goobi.config.ConfigMain;

//TODO: Check if more method can be made static
public class Helper implements Serializable, Observer {

	private static final Logger myLogger = Logger.getLogger(Helper.class);
	private static final long serialVersionUID = -7449236652821237059L;

	private String myMetadatenVerzeichnis;
	private String myConfigVerzeichnis;
	static ResourceBundle bundle;
	static ResourceBundle localBundle;

	/**
	 * Ermitteln eines bestimmten Paramters des Requests
	 * 
	 * @return Paramter als String
	 */
	@SuppressWarnings("unchecked")
	public static String getRequestParameter(String Parameter) {
		/* einen bestimmten übergebenen Parameter ermitteln */
		FacesContext context = FacesContext.getCurrentInstance();
		// TODO: Use generics
		Map requestParams = context.getExternalContext().getRequestParameterMap();
		String myParameter = (String) requestParams.get(Parameter);
		if (myParameter == null) {
			myParameter = "";
		}
		return myParameter;
	}

	// TODO: Get rid of this - create a API for application properties
	public String getGoobiDataDirectory() {
		if (myMetadatenVerzeichnis == null) {
			myMetadatenVerzeichnis = ConfigMain.getParameter("MetadatenVerzeichnis");
		}
		return myMetadatenVerzeichnis;
	}

	public String getGoobiConfigDirectory() {
		if (myConfigVerzeichnis == null) {
			myConfigVerzeichnis = ConfigMain.getParameter("KonfigurationVerzeichnis");
		}
		return myConfigVerzeichnis;
	}

	public static String getStacktraceAsString(Exception inException) {
		StringWriter sw = new StringWriter();
		inException.printStackTrace(new PrintWriter(sw));
		return sw.toString();
	}

	public static void setFehlerMeldung(String meldung) {
		setMeldung(null, meldung, "", false);
	}

	public static void setFehlerMeldung(String meldung, String beschreibung) {
		setMeldung(null, meldung, beschreibung, false);
	}

	public static void setFehlerMeldung(String control, String meldung, String beschreibung) {
		setMeldung(control, meldung, beschreibung, false);
	}

	public static void setFehlerMeldung(Exception e) {
		setFehlerMeldung("Error (" + e.getClass().getName() + "): ", getExceptionMessage(e));
	}

	public static void setFehlerMeldung(String meldung, Exception e) {
		setFehlerMeldung(meldung + " (" + e.getClass().getSimpleName() + "): ", getExceptionMessage(e));
	}

	public static void setFehlerMeldung(String control, String meldung, Exception e) {
		setFehlerMeldung(control, meldung + " (" + e.getClass().getSimpleName() + "): ", getExceptionMessage(e));
	}

	private static String getExceptionMessage(Throwable e) {
		String message = e.getMessage();
		if (message == null) {
			StringWriter sw = new StringWriter();
			e.printStackTrace(new PrintWriter(sw));
			message = sw.toString();
		}
		return message;
	}

	public static void setMeldung(String meldung) {
		setMeldung(null, meldung, "", true);
	}

	public static void setMeldung(String meldung, String beschreibung) {
		setMeldung(null, meldung, beschreibung, true);
	}

	public static void setMeldung(String control, String meldung, String beschreibung) {
		setMeldung(control, meldung, beschreibung, true);
	}

	/**
	 * Dem aktuellen Formular eine Fehlermeldung für ein bestimmtes Control übergeben
	 */
	private static void setMeldung(String control, String meldung, String beschreibung, boolean nurInfo) {
		FacesContext context = FacesContext.getCurrentInstance();

		// Never forget: Strings are immutable
		meldung = meldung.replaceAll("<", "&lt;");
		meldung = meldung.replaceAll(">", "&gt;");
		beschreibung = beschreibung.replaceAll("<", "&lt;");
		beschreibung = beschreibung.replaceAll(">", "&gt;");
		/* wenn kein Kontext da ist, dann die Meldungen in Log */
		if (context == null) {
			if (nurInfo) {
				myLogger.info(meldung + " " + beschreibung);
			} else {
				myLogger.error(meldung + " " + beschreibung);
			}
			return;
		}

		String msg = "";
		String beschr = "";
		try {
			msg = bundle.getString(meldung);
		} catch (RuntimeException e) {
			msg = meldung;
		}
		try {
			beschr = bundle.getString(beschreibung);
		} catch (RuntimeException e) {
			beschr = beschreibung;
		}

		if (nurInfo) {
			context.addMessage(control, new FacesMessage(FacesMessage.SEVERITY_INFO, msg, beschr));
		} else {
			context.addMessage(control, new FacesMessage(FacesMessage.SEVERITY_ERROR, msg, beschr));
		}
	}

	public static String getDateAsFormattedString(Date inDate) {
		if (inDate == null) {
			return "-";
		} else {
			return DateFormat.getDateInstance().format(inDate) + " " + DateFormat.getTimeInstance(DateFormat.MEDIUM).format(inDate);
		}
	}

	public static Object getManagedBeanValue(String expr) {
		FacesContext context = FacesContext.getCurrentInstance();
		if (context == null) {
			return null;
		} else {
			return context.getApplication().createValueBinding(expr).getValue(context);
		}
	}

	public static Session getHibernateSession() {
		// Fix for Hibernate-Session-Management, old version - START
		Session sess;
		try {
			sess = (Session) getManagedBeanValue("#{HibernateSessionLong.session}");
			if (sess == null) {
				sess = HibernateUtilOld.getSession();
			}
		} catch (Exception e) {
			sess = HibernateUtilOld.getSession();
		}
		return sess;
		// Fix for Hibernate-Session-Management, old version - END
	}

	public static void loadLanguageBundle() {
		myLogger.info("Loading message bundles.");
		bundle = ResourceBundle.getBundle("messages.messages", FacesContext.getCurrentInstance().getViewRoot().getLocale());
		localBundle = loadLocalMessageBundleIfAvailable();
	}

	/**
	  * Load local message bundle from file system only if file exists.
	  *
	  * @return Resource bundle for local messages. Returns NULL if no local message bundle could be found.
	  */
	private static ResourceBundle loadLocalMessageBundleIfAvailable() {
		String localMessages = ConfigMain.getParameter("localMessages");
		if (localMessages != null) {
			File file = new File(localMessages);
			if (file.exists()) {
				myLogger.info("Local message bundle found: " + localMessages);
				try {
					URL resourceURL = file.toURI().toURL();
					URLClassLoader urlLoader = new URLClassLoader(new URL[] { resourceURL });
					return ResourceBundle.getBundle("messages", FacesContext.getCurrentInstance().getViewRoot().getLocale(), urlLoader);
				} catch (java.net.MalformedURLException muex) {
					myLogger.error("Error reading local message bundle", muex);
				}
			}
		}
		return null;
	}

	public static String getTranslation(String dbTitel) {
		// running instance of ResourceBundle doesn't respond on user language changes, workaround by instanciating it every time

		try {
			if (localBundle != null) {
				if (localBundle.containsKey(dbTitel)) {
					String trans = localBundle.getString(dbTitel);
					return trans;
				}
				if (localBundle.containsKey(dbTitel.toLowerCase())) {
					return localBundle.getString(dbTitel.toLowerCase());
				}
			}
		} catch (RuntimeException e) {
		}
		try {
			String msg = bundle.getString(dbTitel);
			return msg;
		} catch (RuntimeException e) {
			return dbTitel;
		}
	}

	/**
	 * for easy access of the implemented Interface Observer
	 * 
	 * @return Observer -> can be added to an Observable
	 */
	public Observer createObserver() {
		return this;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Observer#update(java.util.Observable, java.lang.Object)
	 */
	public void update(Observable o, Object arg) {
		if (!(arg instanceof String)) {
			Helper.setFehlerMeldung("Usernotification failed by object: '" + arg.toString()
					+ "' which isn't an expected String Object. This error is caused by an implementation of the Observer Interface in Helper");
		} else {
			Helper.setFehlerMeldung((String) arg);
		}
	}

	public static String getBaseUrl() {
		FacesContext context = FacesContext.getCurrentInstance();
		HttpServletRequest req = (HttpServletRequest) context.getExternalContext().getRequest();
		String fullpath = req.getRequestURL().toString();
		String servletpath = context.getExternalContext().getRequestServletPath();
		return fullpath.substring(0, fullpath.indexOf(servletpath));
	}

	public static Benutzer getCurrentUser() {
		LoginForm login = (LoginForm) Helper.getManagedBeanValue("#{LoginForm}");
		return login.getMyBenutzer();
	}

	/**
	 * Copies src file to dst file. If the dst file does not exist, it is created
	 */
	public static void copyFile(File src, File dst) throws IOException {
		myLogger.debug("copy " + src.getCanonicalPath() + " to " + dst.getCanonicalPath());
		InputStream in = new FileInputStream(src);
		OutputStream out = new FileOutputStream(dst);

		// Transfer bytes from in to out
		byte[] buf = new byte[1024];
		int len;
		while ((len = in.read(buf)) > 0) {
			out.write(buf, 0, len);
		}
		in.close();
		out.close();
	}

	/**
	 * Deletes all files and subdirectories under dir. Returns true if all deletions were successful. If a deletion fails, the method stops attempting
	 * to delete and returns false.
	 */
	public static boolean deleteDir(File dir) {
		if (!dir.exists())
			return true;
		if (dir.isDirectory()) {
			String[] children = dir.list();
			for (int i = 0; i < children.length; i++) {
				boolean success = deleteDir(new File(dir, children[i]));
				if (!success) {
					return false;
				}
			}
		}
		// The directory is now empty so delete it
		return dir.delete();
	}

	/**
	 * Deletes all files and subdirectories under dir. But not the dir itself
	 */
	public static boolean deleteInDir(File dir) {
		if (dir.exists() && dir.isDirectory()) {
			String[] children = dir.list();
			for (int i = 0; i < children.length; i++) {
				boolean success = deleteDir(new File(dir, children[i]));
				if (!success) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * Copies all files under srcDir to dstDir. If dstDir does not exist, it will be created.
	 */
	// TODO: Replace this method with two, one for the file stuff and another one for the checksum
	public static void copyDirectoryWithCrc32Check(File srcDir, File dstDir, int goobipathlength, Element inRoot) throws IOException {
		if (srcDir.isDirectory()) {
			if (!dstDir.exists()) {
				dstDir.mkdir();
				dstDir.setLastModified(srcDir.lastModified());
			}
			String[] children = srcDir.list();
			for (int i = 0; i < children.length; i++) {
				copyDirectoryWithCrc32Check(new File(srcDir, children[i]), new File(dstDir, children[i]), goobipathlength, inRoot);
			}
		} else {
			Long crc = CopyFile.start(srcDir, dstDir);
			Element file = new Element("file");
			file.setAttribute("path", srcDir.getAbsolutePath().substring(goobipathlength));
			file.setAttribute("crc32", String.valueOf(crc));
			inRoot.addContent(file);
		}
	}

	public FilenameFilter getFilter() {
		return imageNameFilter;
	}

	FilenameFilter imageNameFilter = new FilenameFilter() {
		public boolean accept(File dir, String name) {
			boolean fileOk = false;
			String prefix = ConfigMain.getParameter("ImagePrefix", "\\d{8}");

			if (name.matches(prefix + "\\.[Tt][Ii][Ff][Ff]?")) {
				fileOk = true;
			} else if (name.matches(prefix + "\\.[jJ][pP][eE]?[gG]")) {
				fileOk = true;
			} else if (name.matches(prefix + "\\.[jJ][pP][2]")) {
				fileOk = true;
			} else if (name.matches(prefix + "\\.[pP][nN][gG]")) {
				fileOk = true;
			} else if (name.matches(prefix + "\\.[gG][iI][fF]")) {
				fileOk = true;
			}
			return fileOk;
		}
	};
}
