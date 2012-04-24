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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.SystemUtils;
import org.apache.log4j.Logger;

import de.sub.goobi.config.ConfigMain;

/**
 * Helper class for file system operations.
 * 
 * @author Matthias Ronge <matthias.ronge@zeutschel.de>
 */
public class FilesystemHelper {
	private static final Logger logger = Logger
			.getLogger(FilesystemHelper.class);

	/**
	 * Creates a directory with a name given. Under Linux a script is used to
	 * set the file system permissions accordingly. This cannot be done from
	 * within java code before version 1.7.
	 * 
	 * @param dirName
	 *            Name of directory to create
	 * @throws InterruptedException
	 *             If the thread running the script is interrupted by another
	 *             thread while it is waiting, then the wait is ended and an
	 *             InterruptedException is thrown.
	 * @throws IOException
	 *             If an I/O error occurs.
	 */
	public static void createDirectory(String dirName) throws IOException,
			InterruptedException {
		if (!new File(dirName).exists()) {
			ShellScript createDirScript = new ShellScript(new File(
					ConfigMain.getParameter("script_createDirMeta")));
			createDirScript.run(Arrays.asList(new String[] { dirName }));
		}
	}

	/**
	 * Creates a directory with a name given and assigns permissions to the
	 * given user. Under Linux a script is used to set the file system
	 * permissions accordingly. This cannot be done from within java code before
	 * version 1.7.
	 * 
	 * @param dirName
	 *            Name of directory to create
	 * @throws InterruptedException
	 *             If the thread running the script is interrupted by another
	 *             thread while it is waiting, then the wait is ended and an
	 *             InterruptedException is thrown.
	 * @throws IOException
	 *             If an I/O error occurs.
	 */
	public static void createDirectoryForUser(String dirName, String userName)
			throws IOException, InterruptedException {
		if (!new File(dirName).exists()) {
			ShellScript createDirScript = new ShellScript(new File(
					ConfigMain.getParameter("script_createDirUserHome")));
			createDirScript.run(Arrays
					.asList(new String[] { userName, dirName }));
		}
	}

	public static void deleteSymLink(String symLink) {
		String command = ConfigMain.getParameter("script_deleteSymLink");
		ShellScript deleteSymLinkScript;
		try {
			deleteSymLinkScript = new ShellScript(new File(command));
			deleteSymLinkScript.run(Arrays.asList(new String[] { symLink }));
		} catch (FileNotFoundException e) {
			logger.error("FileNotFoundException in deleteSymLink()", e);
			Helper.setFehlerMeldung("Couldn't find script file, error",
					e.getMessage());
		} catch (IOException e) {
			logger.error("IOException in deleteSymLink()", e);
			Helper.setFehlerMeldung("Aborted deleteSymLink(), error",
					e.getMessage());
		} catch (InterruptedException e) {
			logger.error("InterruptedException in deleteSymLink()", e);
			Helper.setFehlerMeldung("Command '" + command
					+ "' is interrupted in deleteSymLink()!");
		}
	}

	/**
	 * This function implements file renaming. Renaming of files is full of
	 * mischief under Windows which unaccountably holds locks on files.
	 * Sometimes running the JVM’s garbage collector puts things right.
	 * 
	 * @param oldFileName
	 *            File to move or rename
	 * @param newFileName
	 *            New file name / destination
	 * @throws IOException
	 *             is thrown if the rename fails permanently
	 */
	public static void renameFile(String oldFileName, String newFileName)
			throws IOException {
		final int SLEEP_INTERVAL_MILLIS = 20;
		final int MAX_WAIT_MILLIS = 150000; // 2½ minutes
		File oldFile;
		File newFile;
		boolean success;
		int millisWaited = 0;

		if (oldFileName != null && newFileName != null) {
			oldFile = new File(oldFileName);
			newFile = new File(newFileName);
			do {
				if (SystemUtils.IS_OS_WINDOWS
						&& millisWaited == SLEEP_INTERVAL_MILLIS) {
					logger.warn("renameMetadataFile(): This is Windows. Running the garbage collector may yield good results. Forcing immediate garbage collection now!");
					System.gc();
				}
				success = oldFile.renameTo(newFile);
				if (!success) {
					if (millisWaited == 0)
						logger.info("renameMetadataFile(): Rename failed. File may be locked. Retrying.");
					try {
						Thread.sleep(SLEEP_INTERVAL_MILLIS);
					} catch (InterruptedException e) {
					}
					millisWaited += SLEEP_INTERVAL_MILLIS;
				}
			} while (!success && millisWaited < MAX_WAIT_MILLIS);
			if (!success) {
				logger.error("renameMetadataFile(): Rename failed. This is a permanent error. Giving up.");
				throw new IOException("Renaming of " + oldFileName + " into "
						+ newFileName + " failed.");
			} else if (millisWaited > 0)
				logger.info("renameMetadataFile(): Rename finally succeeded after"
						+ Integer.toString(millisWaited) + " milliseconds.");
		}
	}
}
